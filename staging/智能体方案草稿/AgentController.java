package org.dromara.web.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 内嵌智能体（方案 A：ERP 内置最小智能体，不引外部框架）。
 *
 * 定位（《内嵌智能体规范》§4 权限红线）：智能体 = 员工权限的放大器，不是越权通道。
 * - 工具（动作）目录 = sys_agent_action 配置表（加动作=插一行，端点均为 ERP 现成 REST）
 * - 可见工具集按【当前登录人】的权限码实时过滤（无权限的动作 LLM 根本看不到）
 * - 工具调用 = 回环调 ERP 自身接口，带发起员工本人 token：@SaCheckPermission /
 *   数据权限 / 脱敏 原样生效；LLM 拿不到 token，只看到工具返回的（被权限过滤过的）结果文本
 * - 留痕：sys_agent_log 记 员工工号 + 提问原文 + 动作调用链 + 回答（渠道=agent，不记 AI）
 *
 * LLM 网关：.env LLM_1_BASE_URL / LLM_1_API_KEY / LLM_1_MODEL（OpenAI 兼容 /chat/completions）。
 * 工具调用走提示词协议（LLM 输出 {"tool":"action_key","args":{...}}），不依赖原生 function-calling，
 * 任何 OpenAI 兼容端点都可用。
 *
 * @author 织云ERP
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/agent")
public class AgentController {

    private final JdbcTemplate jdbcTemplate;

    /** 一轮对话最多执行的动作调用次数（防循环） */
    private static final int MAX_TOOL_ROUNDS = 3;
    /** 单次工具结果回填 LLM 的字符上限（防爆上下文） */
    private static final int TOOL_RESULT_LIMIT = 4000;

    @Value("${LLM_1_BASE_URL:}")
    private String llmBaseUrl;
    @Value("${LLM_1_API_KEY:}")
    private String llmApiKey;
    @Value("${LLM_1_MODEL:}")
    private String llmModel;
    @Value("${server.port:8080}")
    private int serverPort;

    // ===== 动作目录（LLM 工具清单） =====

    /**
     * 当前登录人可见的动作目录（按权限码过滤；超管 * 全见）。
     * 也供对话页展示"你现在能问哪些事"。
     */
    @SaCheckPermission("system:agent:chat")
    @GetMapping("/actions")
    public R<List<Map<String, Object>>> actions() {
        return R.ok(visibleActions());
    }

    /** 取当前登录人可见的动作（perm 空=人人可见；持 * 通配全见）。 */
    private List<Map<String, Object>> visibleActions() {
        Long userId = LoginHelper.getUserId();
        Set<String> perms = currentPerms(userId);
        boolean wildcard = perms.contains("*");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT action_id, module_key, action_key, action_name, description, endpoint, http_method, params_schema, perm " +
            "FROM sys_agent_action WHERE enabled='0' AND del_flag='0' ORDER BY module_key, action_key");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String perm = str(row.get("perm"));
            if ((perm == null || perm.isBlank()) || wildcard || perms.contains(perm)) {
                out.add(row);
            }
        }
        return out;
    }

    // ===== 对话编排 =====

    /**
     * 对话（无状态：历史由前端 messages 数组携带）。
     * 流程：按登录人过滤动作目录 → LLM（工具=提示词协议）→ LLM 要求调动作时，
     * 用【本人 token】回环调 ERP 现成接口（权限/数据范围原样生效）→ 结果回填 → LLM 终答 → 留痕。
     */
    @SaCheckPermission("system:agent:chat")
    @Log(title = "智能体对话", businessType = BusinessType.OTHER)
    @PostMapping("/chat")
    public R<Map<String, Object>> chat(@RequestBody Map<String, Object> body,
                                       @RequestHeader(value = "clientid", required = false) String clientid) {
        Long userId = LoginHelper.getUserId();
        String userName = str(jdbcTemplate.queryForMap(
            "SELECT user_name FROM sys_user WHERE user_id=?", userId).get("user_name"));

        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = body.get("messages") instanceof List<?> m
            ? castMessages(m) : new ArrayList<>();
        if (history.isEmpty()) {
            return R.fail("缺少对话内容");
        }
        String question = lastUserQuestion(history);

        List<Map<String, Object>> tools = visibleActions();
        long t0 = System.currentTimeMillis();
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(msg("system", buildSystemPrompt(tools)));
        messages.addAll(history);

        String answer = null;
        JSONArray toolCalls = new JSONArray();
        String token = StpUtil.getTokenValue();

        try {
            for (int round = 0; round <= MAX_TOOL_ROUNDS; round++) {
                String llmOut = llmChat(messages);
                JSONObject call = tryParseToolCall(llmOut);
                if (call == null || round == MAX_TOOL_ROUNDS) {
                    answer = call == null ? llmOut : llmOut; // 达到轮次上限：直接回显最后一段文本
                    if (round == MAX_TOOL_ROUNDS && call != null) {
                        messages.add(msg("user", "（已达最大工具调用轮次，请基于已有结果直接回答）"));
                        answer = llmChat(messages);
                    }
                    break;
                }
                // 执行工具（回环调 ERP 现成接口，带本人 token）
                String actionKey = call.getStr("tool");
                Object rawArgs = call.get("args");
                JSONObject args = null;
                if (rawArgs instanceof JSONObject) {
                    args = (JSONObject) rawArgs;
                } else if (rawArgs instanceof Map) {
                    args = JSONUtil.parseObj(rawArgs);
                }
                Map<String, Object> toolMeta = tools.stream()
                    .filter(t -> actionKey.equals(t.get("action_key"))).findFirst().orElse(null);
                JSONObject tRec = new JSONObject()
                    .set("tool", actionKey)
                    .set("args", args == null ? new JSONObject() : args);
                if (toolMeta == null) {
                    // LLM 幻觉了一个不存在的工具：原样告知，让它改口（不执行）
                    tRec.set("status", "unknown_tool");
                    toolCalls.add(tRec);
                    messages.add(msg("user", "工具执行反馈：「" + actionKey + "」不在你的可用工具清单里，请只用清单内的工具或直接回答。"));
                    continue;
                }
                long ts = System.currentTimeMillis();
                String result = executeTool(toolMeta, args, token, clientid);
                tRec.set("status", result.startsWith("__ERROR__") ? "error" : "ok")
                   .set("ms", System.currentTimeMillis() - ts);
                toolCalls.add(tRec);
                String toolLine = "工具「" + toolMeta.get("action_key") + "」执行结果：" + truncate(result);
                messages.add(msg("system", toolLine));
                log.info("[智能体] {} 调用 {} -> {}ms", userId, actionKey, System.currentTimeMillis() - ts);
            }
            if (answer == null) {
                answer = "（本轮未能生成回答，请换个问法重试）";
            }
        } catch (Exception e) {
            log.warn("[智能体] LLM 网关调用失败: {}", e.getMessage());
            answer = "AI 网关暂时不可用（" + e.getMessage() + "），请稍后再试。";
        }

        // 留痕（三点：问了什么 / 调了哪些动作 / 结果）
        String traceText = toolCalls.isEmpty() ? null : toolCalls.toString();
        insertLog(userId, userName, question, traceText, answer, (int) (System.currentTimeMillis() - t0));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("answer", answer);
        data.put("toolCalls", toolCalls);
        return R.ok(data);
    }

    // ===== 内部 =====

    /** 回环调 ERP 自身接口（GET 参数拼接；带本人 token + clientid + X-Via=agent，权限/数据范围原样生效）。 */
    private String executeTool(Map<String, Object> meta, JSONObject args, String token, String clientid) {
        String endpoint = str(meta.get("endpoint"));
        StringBuilder url = new StringBuilder("http://127.0.0.1:" + serverPort + endpoint);
        if (args != null && !args.isEmpty()) {
            url.append(endpoint.contains("?") ? "&" : "?");
            boolean first = !endpoint.contains("?");
            for (String k : args.keySet()) {
                Object v = args.get(k);
                if (v == null) {
                    continue;
                }
                url.append(first ? "" : "&").append(k).append('=').append(v.toString());
                first = false;
            }
        }
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(5000);
            factory.setReadTimeout(60000);
            RestTemplate rt = new RestTemplate(factory);
            HttpHeaders hh = new HttpHeaders();
            if (token != null) {
                hh.set("Authorization", "Bearer " + token);
            }
            hh.set("X-Via", "agent"); // 留痕渠道标记（二期 sys_oper_log 加 source=AI 时用）
            String cid = clientid;
            if (cid == null) {
                try {
                    cid = jdbcTemplate.queryForObject("SELECT client_id FROM sys_client WHERE status='0' LIMIT 1", String.class);
                } catch (Exception ignored) {
                    cid = null;
                }
            }
            if (cid != null) {
                hh.set("clientid", cid);
            }
            ResponseEntity<String> resp = rt.exchange(url.toString(), HttpMethod.GET, new HttpEntity<>(hh), String.class);
            return resp.getBody() == null ? "{}" : resp.getBody();
        } catch (Exception e) {
            return "__ERROR__ 工具执行失败（" + e.getMessage() + "）";
        }
    }

    /** 调 LLM 网关（OpenAI 兼容 /chat/completions，提示词协议，不依赖原生 function-calling）。 */
    private String llmChat(List<Map<String, String>> messages) {
        if (llmBaseUrl == null || llmBaseUrl.isBlank()) {
            throw new IllegalStateException("LLM 网关未配置（.env LLM_1_BASE_URL）");
        }
        String base = llmBaseUrl.replaceAll("/$", "");
        String url = base.endsWith("/v1") ? base + "/chat/completions" : base + "/v1/chat/completions";
        JSONObject req = new JSONObject()
            .set("model", llmModel)
            .set("temperature", 0.2)
            .set("max_tokens", 1500)
            .set("messages", messages);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(120000);
        RestTemplate rt = new RestTemplate(factory);
        HttpHeaders hh = new HttpHeaders();
        hh.set("Content-Type", "application/json");
        if (llmApiKey != null && !llmApiKey.isBlank()) {
            hh.set("Authorization", "Bearer " + llmApiKey);
        }
        ResponseEntity<String> resp = rt.exchange(url, HttpMethod.POST, new HttpEntity<>(req.toString(), hh), String.class);
        JSONObject out = JSONUtil.parseObj(resp.getBody());
        JSONArray choices = out.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("LLM 无返回: " + truncate(resp.getBody()));
        }
        return choices.getJSONObject(0).getJSONObject("message").getStr("content", "");
    }

    /** 从 LLM 输出里解析工具调用协议 {"tool":"x","args":{...}}；不是工具调用返回 null。 */
    private JSONObject tryParseToolCall(String content) {
        if (content == null) {
            return null;
        }
        String s = content.trim();
        int start = s.indexOf('{');
        if (start < 0 || !s.contains("\"tool\"")) {
            return null;
        }
        int depth = 0;
        int end = -1;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') { depth--; if (depth == 0) { end = i; break; } }
        }
        if (end < 0) {
            return null;
        }
        try {
            JSONObject obj = JSONUtil.parseObj(s.substring(start, end + 1));
            String tool = obj.getStr("tool");
            if (tool == null || tool.isBlank()) {
                return null; // 没有 tool 字段 → 视为自然语言回答
            }
            return obj;
        } catch (Exception e) {
            return null;
        }
    }

    private String buildSystemPrompt(List<Map<String, Object>> tools) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是织云服装厂 ERP 的内嵌助理。你通过调用【动作】获取厂内真实数据后回答员工的问题；数据以外的内容你不知道就直说，禁止编造。\n");
        sb.append("规则：\n");
        sb.append("1. 需要厂内数据时，回复【且只回复】一行 JSON：{\"tool\":\"<动作key>\",\"args\":{...参数...}}；\n");
        sb.append("2. 数据齐了就直接用中文自然回答（可列点），不要再输出 JSON；\n");
        sb.append("3. 只能使用下面清单里的动作，参数按参数说明填（无参数就 {}）；\n");
        sb.append("4. 工具结果里出现 __ERROR__ 表示该动作执行失败，向员工说明失败原因，不要假装成功。\n");
        sb.append("当前可用动作（已按提问员工的权限过滤，清单外的一律不可用）：\n");
        if (tools.isEmpty()) {
            sb.append("（当前员工没有可用的数据查询动作，只能做通用解答）");
        }
        for (Map<String, Object> t : tools) {
            sb.append("- ").append(t.get("action_key"))
              .append(" 名称:").append(t.get("action_name"))
              .append(" | 用途:").append(str(t.get("description")))
              .append(" | 参数:").append(str(t.get("params_schema")))
              .append("\n");
        }
        return sb.toString();
    }

    private void insertLog(Long userId, String userName, String question, String traceText, String answer, int durationMs) {
        try {
            jdbcTemplate.update(
                "INSERT INTO sys_agent_log (log_id, user_id, user_name, question, tool_trace, answer, duration_ms, create_time) " +
                "VALUES (?,?,?,?,?,?,?,now())",
                IdUtil.getSnowflakeNextId(), userId, userName, question, traceText, truncate(answer), durationMs);
        } catch (Exception e) {
            log.warn("[智能体] 留痕写入失败: {}", e.getMessage());
        }
    }

    /** 当前登录人权限码集合（与 /system/profile/permissions 同口径：超管=*）。 */
    private Set<String> currentPerms(Long userId) {
        if (LoginHelper.isSuperAdmin(userId)) {
            return new LinkedHashSet<>(Set.of("*"));
        }
        return new LinkedHashSet<>(StpUtil.getPermissionList());
    }

    private static Map<String, String> msg(String role, String content) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> castMessages(List<?> raw) {
        List<Map<String, String>> out = new ArrayList<>();
        for (Object o : raw) {
            if (o instanceof Map<?, ?> mm) {
                String role = str(mm.get("role"));
                String content = str(mm.get("content"));
                if (role != null && content != null && !content.isBlank()) {
                    out.add(msg("assistant".equals(role) ? "assistant" : "user", content));
                }
            }
        }
        return out;
    }

    private static String lastUserQuestion(List<Map<String, String>> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("user".equals(history.get(i).get("role"))) {
                return history.get(i).get("content");
            }
        }
        return "";
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > TOOL_RESULT_LIMIT ? s.substring(0, TOOL_RESULT_LIMIT) + "…(截断)" : s;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
