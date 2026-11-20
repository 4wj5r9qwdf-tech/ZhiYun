package org.dromara.ai.snailai;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.json.utils.JsonUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** 保存受权限保护且有长度上限的织云智能体诊断记录，不持久化工具参数和结果正文。 / Persist bounded, permission-protected diagnostics without storing tool arguments or result bodies. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SnailAiTraceService {

    private static final int MAX_TEXT_CHARS = 100_000;
    private static final int MAX_PREVIEW_CHARS = 4_000;
    private static final Pattern SENSITIVE_JSON_VALUE = Pattern.compile(
        "(?i)(\\\"[^\\\"]*(?:password|token|secret|api.?key|phone|mobile|email|id.?card)[^\\\"]*\\\"\\s*:\\s*)(\\\"(?:\\\\.|[^\\\"])*\\\"|[^,}\\s]+)"
    );
    private static final Pattern BEARER_VALUE = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+");

    private final JdbcTemplate jdbcTemplate;

    /** 开始一轮对话并保存提问与附件摘要；文本及字段长度在持久化前统一截断。 / Start a chat turn and store bounded question text and attachment metadata. */
    public String start(Long userId, String nickname, Long agentId, String conversationId,
                       String question, List<Map<String, Object>> attachments) {
        String traceId = IdUtil.fastSimpleUUID();
        jdbcTemplate.update("""
            INSERT INTO sys_snail_ai_chat_trace
                (trace_id, user_id, nickname, agent_id, conversation_id, question,
                 attachment_summary, status, started_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'RUNNING', now())
            """,
            traceId, userId, limit(nickname, 100), agentId, limit(conversationId, 128),
            limit(question, MAX_TEXT_CHARS), toJson(attachments));
        return traceId;
    }

    /** 更新对话中可见的工具目录，供授权诊断人员解释当轮工具范围。 / Update the tool catalog visible during this turn for authorized diagnostics. */
    public void updateVisibleTools(String traceId, List<Map<String, Object>> tools) {
        if (!StringUtils.hasText(traceId)) {
            return;
        }
        jdbcTemplate.update("UPDATE sys_snail_ai_chat_trace SET visible_tools = ? WHERE trace_id = ?",
            toJson(tools), traceId);
    }

    /** 创建工具执行事件；参数正文始终以占位说明保存，避免敏感业务数据进入诊断库。 / Start a tool event while withholding potentially sensitive arguments. */
    public ToolTraceHandle startTool(String traceId, String toolName, String description, String arguments) {
        String eventId = IdUtil.fastSimpleUUID();
        long startedAt = System.currentTimeMillis();
        jdbcTemplate.update("""
            INSERT INTO sys_snail_ai_tool_trace
                (event_id, trace_id, tool_name, tool_description, arguments_preview, status, started_at)
            VALUES (?, ?, ?, ?, ?, 'RUNNING', now())
            """,
            eventId, traceId, limit(toolName, 160), limit(description, 1000), "参数内容未记录");
        return new ToolTraceHandle(eventId, startedAt);
    }

    /** 完成工具事件；仅记录状态、脱敏错误和耗时，不持久化工具结果正文。 / Finish a tool event without persisting its result body. */
    public void finishTool(ToolTraceHandle handle, String status, String result, String error) {
        if (handle == null) {
            return;
        }
        jdbcTemplate.update("""
            UPDATE sys_snail_ai_tool_trace
               SET status = ?, result_preview = ?, error_message = ?,
                   duration_ms = ?, finished_at = now()
             WHERE event_id = ?
            """,
            limit(status, 24), "结果内容未记录", limit(sanitize(error), MAX_PREVIEW_CHARS),
            Math.max(0L, System.currentTimeMillis() - handle.startedAtMillis()), handle.eventId());
    }

    /** 完成对话诊断；保留截断后的回复并对错误内容做敏感信息脱敏。 / Complete a turn with bounded answer text and sanitized errors. */
    public void complete(String traceId, String answer, String status, String error, long durationMs) {
        if (!StringUtils.hasText(traceId)) {
            return;
        }
        jdbcTemplate.update("""
            UPDATE sys_snail_ai_chat_trace
               SET answer = ?, status = ?, error_message = ?, duration_ms = ?, finished_at = now()
             WHERE trace_id = ?
            """,
            limit(answer, MAX_TEXT_CHARS), limit(status, 24), limit(sanitize(error), MAX_PREVIEW_CHARS),
            Math.max(0L, durationMs), traceId);
    }

    /** 按关键字和状态分页查询单轮诊断；页码和页大小在服务端限制范围。 / Search individual turns with bounded server-side pagination. */
    public Map<String, Object> list(int pageNum, int pageSize, String keyword, String status) {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(keyword)) {
            where.append(" AND (nickname ILIKE ? OR question ILIKE ? OR answer ILIKE ? OR conversation_id ILIKE ?) ");
            String match = "%" + keyword.trim() + "%";
            args.add(match);
            args.add(match);
            args.add(match);
            args.add(match);
        }
        if (StringUtils.hasText(status)) {
            where.append(" AND status = ? ");
            args.add(status.trim().toUpperCase());
        }
        Long total = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM sys_snail_ai_chat_trace" + where, Long.class, args.toArray());
        args.add(safeSize);
        args.add((long) (safePage - 1) * safeSize);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT trace_id, user_id, nickname, agent_id, conversation_id,
                   left(question, 240) AS question_preview, status, duration_ms, started_at, finished_at
              FROM sys_snail_ai_chat_trace
            """ + where + " ORDER BY started_at DESC LIMIT ? OFFSET ?", args.toArray());
        return Map.of("rows", rows, "total", total == null ? 0L : total,
            "pageNum", safePage, "pageSize", safeSize);
    }

    /** 查询单轮诊断及工具事件，不扩展到同一会话的其他轮次。 / Load one turn and its tool events without expanding to sibling turns. */
    public Map<String, Object> detail(String traceId) {
        Map<String, Object> trace = jdbcTemplate.queryForMap(
            "SELECT * FROM sys_snail_ai_chat_trace WHERE trace_id = ?", traceId);
        List<Map<String, Object>> tools = jdbcTemplate.queryForList("""
            SELECT event_id, tool_name, tool_description, arguments_preview, result_preview,
                   status, error_message, duration_ms, started_at, finished_at
              FROM sys_snail_ai_tool_trace
             WHERE trace_id = ?
             ORDER BY started_at ASC
            """, traceId);
        Map<String, Object> response = new LinkedHashMap<>(trace);
        response.put("tools", tools);
        return response;
    }

    /** 按员工、智能体和上游会话编号归并；缺失会话编号时每轮独立显示。 / Group by employee, agent and upstream conversation ID; isolate turns without that ID. */
    public Map<String, Object> conversationList(int pageNum, int pageSize, String keyword, String status) {
        int page = Math.max(1, pageNum), size = Math.max(1, Math.min(100, pageSize));
        String search = StringUtils.hasText(keyword) ? keyword.trim() : "";
        String state = StringUtils.hasText(status) ? status.trim().toUpperCase() : "";
        String matching = "%" + search + "%";
        String grouped = """
            FROM sys_snail_ai_chat_trace t
            GROUP BY t.user_id, t.agent_id, t.conversation_id,
                     CASE WHEN NULLIF(btrim(t.conversation_id), '') IS NULL THEN t.trace_id END
            HAVING (? = '' OR bool_or(t.nickname ILIKE ? OR t.question ILIKE ? OR t.answer ILIKE ? OR t.conversation_id ILIKE ?))
               AND (? = '' OR bool_or(t.status = ?))
            """;
        Object[] args = { search, matching, matching, matching, matching, state, state };
        Long total = jdbcTemplate.queryForObject("SELECT count(*) FROM (SELECT 1 " + grouped + ") grouped", Long.class, args);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT (array_agg(t.trace_id ORDER BY t.started_at DESC, t.trace_id DESC))[1] AS trace_id,
                   t.user_id, t.agent_id, t.conversation_id,
                   (array_agg(t.nickname ORDER BY t.started_at DESC, t.trace_id DESC))[1] AS nickname,
                   (array_agg(left(t.question, 240) ORDER BY t.started_at ASC, t.trace_id ASC))[1] AS question_preview,
                   (array_agg(t.status ORDER BY t.started_at DESC, t.trace_id DESC))[1] AS status,
                   min(t.started_at) AS started_at, max(t.started_at) AS last_at,
                   count(*) AS turn_count, count(*) FILTER (WHERE t.status = 'FAILED') AS failed_count
            """ + grouped + " ORDER BY max(t.started_at) DESC, max(t.trace_id) DESC LIMIT ? OFFSET ?",
            search, matching, matching, matching, matching, state, state, size, (long) (page - 1) * size);
        return Map.of("rows", rows, "total", total == null ? 0 : total, "pageNum", page, "pageSize", size);
    }

    /** 使用已授权的诊断记录作锚点；没有上游会话编号时只读取当前轮。 / Use an authorized trace as anchor and isolate it when no upstream conversation ID exists. */
    public Map<String, Object> conversationDetail(String traceId) {
        Map<String, Object> anchor = jdbcTemplate.queryForMap(
            "SELECT user_id, agent_id, conversation_id, nickname FROM sys_snail_ai_chat_trace WHERE trace_id = ?", traceId);
        // 缺少上游对话编号时，本轮只能按自身诊断编号回查。
        // Without an upstream conversation ID, load only the anchored turn.
        String conversationId = Objects.toString(anchor.get("conversation_id"), "");
        if (!StringUtils.hasText(conversationId)) {
            Map<String, Object> response = new LinkedHashMap<>(anchor);
            response.put("turns", List.of(detail(traceId)));
            return response;
        }

        String scope = "user_id = ? AND agent_id IS NOT DISTINCT FROM ? AND conversation_id = ?";
        Object[] args = {anchor.get("user_id"), anchor.get("agent_id"), conversationId};
        var turns = jdbcTemplate.queryForList("SELECT * FROM sys_snail_ai_chat_trace WHERE " + scope
            + " ORDER BY started_at, trace_id", args);

        // 同一对话的工具事件一次查询，避免每轮重复读取诊断记录。
        // Fetch all tool events for the conversation without reloading each turn.
        var events = jdbcTemplate.queryForList("""
            SELECT event_id, trace_id, tool_name, tool_description, arguments_preview, result_preview,
                   status, error_message, duration_ms, started_at, finished_at
            FROM sys_snail_ai_tool_trace
            WHERE trace_id IN (SELECT trace_id FROM sys_snail_ai_chat_trace WHERE
            """ + scope + ") ORDER BY started_at, event_id", args);
        Map<String, List<Map<String, Object>>> toolsByTurn = new LinkedHashMap<>();
        for (var event : events) {
            String turnId = String.valueOf(event.remove("trace_id"));
            toolsByTurn.computeIfAbsent(turnId, key -> new ArrayList<>()).add(event);
        }
        for (var turn : turns) {
            turn.put("tools", toolsByTurn.getOrDefault(String.valueOf(turn.get("trace_id")), List.of()));
        }
        Map<String, Object> response = new LinkedHashMap<>(anchor);
        response.put("turns", turns);
        return response;
    }

    /** 脱敏错误及状态文本中的凭据类字段，再限制可写入诊断记录的字符数。 / Redact credential-like values before applying the storage limit. */
    private String sanitize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String safe = BEARER_VALUE.matcher(value).replaceAll("Bearer <已脱敏>");
        safe = SENSITIVE_JSON_VALUE.matcher(safe).replaceAll("$1\"<已脱敏>\"");
        return limit(safe, MAX_PREVIEW_CHARS);
    }

    /** 使用项目统一 JSON 配置序列化诊断元数据；序列化异常时不阻断业务对话。 / Serialize diagnostic metadata with shared JSON rules without interrupting the chat on failure. */
    private String toJson(Object value) {
        try {
            // 与项目统一 JSON 工具共用 Jackson 3 配置，保留 bigint 字符串序列化等全局规则。
            // Reuse the project Jackson 3 mapper so global bigint-as-string rules stay consistent.
            return JsonUtils.toJsonString(value == null ? List.of() : value);
        } catch (RuntimeException exception) {
            log.warn("Could not serialize SnailAI trace metadata", exception);
            return "[]";
        }
    }

    /** 将可变文本限制在数据库列和诊断页面可承载的长度内。 / Bound text to the size supported by persistence and diagnostics. */
    private String limit(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxChars ? value : value.substring(0, maxChars) + "…";
    }

    /** 保存单次工具事件的计时信息，不包含工具参数或结果正文。 / Carry only the event ID and start time between tool lifecycle callbacks. */
    public record ToolTraceHandle(String eventId, long startedAtMillis) {
    }
}
