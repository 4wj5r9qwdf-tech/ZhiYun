/*
 * 文件用途：提供受织云权限保护的智能体诊断记录检索和详情接口。
 * 文件版本：1.0.0
 * 功能目的：提供受织云权限保护的智能体诊断记录检索和详情接口。
 * 设计思路：诊断记录只开放授权管理员所需字段；业务工具参数和结果正文不持久化。
 * 实现方法：调用诊断服务分页检索会话和工具事件，按功能权限控制列表和详情。
 * 关键约束：诊断信息经过脱敏和长度限制；不把它作为实时服务健康或业务操作成功的证明。
 * 依赖与引用：Docs/AI/织云AI集成总体规划.md；sql/SnailAI员工对话诊断记录_20261117.sql；Docs/API/API文档.md
 * 初始开发人员：JY（织云项目自研内容）
 * 初始开发时间：待追溯（不以补录时间代替原始开发时间）
 * 当前责任人：JY（项目维护责任）
 * 最近修改人员：JY
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-20
 * 修改标记：ZY-ERP-20261120-注释整理；补齐文件说明和业务逻辑注释，不改变运行行为
 * 最近测试时间：本次整理未执行功能测试
 * 测试人员：本次整理未执行
 * 测试状态：待功能回归；历史测试记录待追溯
 * 测试范围与记录位置：本次为注释/元信息整理；见 Docs/功能任务/ZY-ERP-20261120-既有功能注释整理.md
 */
package org.dromara.ai.controller;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.ai.snailai.SnailAiTraceService;
import org.dromara.ai.snailai.SnailAiHistoryClient;
import com.aizuda.snail.ai.common.model.Result;
import org.dromara.common.core.domain.R;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;

/** 智能体对话诊断管理接口；所有路由都先校验专属权限。 / Administrator endpoints for employee chat diagnostics, guarded by a dedicated permission. */
@RestController
@Slf4j
@RequestMapping("/snail-ai/admin/traces")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "snail-ai.open-api", name = "enabled", havingValue = "true")
public class SnailAiTraceAdminController {

    private final SnailAiTraceService traceService;
    private final SnailAiHistoryClient historyClient;

    /** 按当前筛选条件分页列出单轮诊断记录。 / List individual diagnostic turns using the supplied filters. */
    @GetMapping
    public R<Map<String, Object>> list(@RequestParam(defaultValue = "1") int pageNum,
                                       @RequestParam(defaultValue = "20") int pageSize,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String status) {
        requireTracePermission();
        return R.ok(traceService.list(pageNum, pageSize, trim(keyword, 100), trim(status, 24)));
    }

    /** 按上游会话归并诊断轮次；无会话标识的轮次保持独立。 / List grouped conversations while keeping uncorrelated turns separate. */
    @GetMapping("/conversations")
    public R<Map<String, Object>> conversations(@RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        requireTracePermission();
        return R.ok(traceService.conversationList(pageNum, pageSize, trim(keyword, 100), trim(status, 24)));
    }

    /** 读取会话时间线；诊断编号必须来自授权诊断列表。 / Read a conversation timeline using a trace ID from the authorized diagnostics list. */
    @GetMapping("/conversations/{traceId}")
    public R<Map<String, Object>> conversation(@PathVariable String traceId) {
        requireTracePermission();
        if (!StringUtils.hasText(traceId) || traceId.length() > 36) throw new IllegalArgumentException("诊断记录编号无效");
        return R.ok(traceService.conversationDetail(traceId));
    }

    /** 读取单轮对话和工具事件详情。 / Read one turn and its tool-event details. */
    @GetMapping("/{traceId}")
    public R<Map<String, Object>> detail(@PathVariable String traceId) {
        requireTracePermission();
        if (!StringUtils.hasText(traceId) || traceId.length() > 36) {
            throw new IllegalArgumentException("诊断记录编号无效");
        }
        return R.ok(traceService.detail(traceId));
    }

    /** 限授权管理员查看与指定对话轮次唯一对应的图片。 / Show only unambiguously matched trace images. */
    @GetMapping("/{traceId}/images")
    public R<List<Map<String, String>>> images(@PathVariable String traceId) {
        requireTracePermission();
        if (!StringUtils.hasText(traceId) || traceId.length() > 36) throw new IllegalArgumentException("诊断记录编号无效");
        Map<String, Object> trace = traceService.detail(traceId);
        List<Map<String, Object>> attachments = org.dromara.common.json.utils.JsonUtils.parseObject(
            String.valueOf(trace.get("attachment_summary")), new tools.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
        if (attachments == null || attachments.isEmpty()) return R.ok(List.of());
        // 缺少上游匹配键时无法确认图片归属，保留文字诊断并返回空图片列表。
        // Preserve the text trace when the upstream image cannot be matched safely.
        if (trace.get("agent_id") == null || !StringUtils.hasText((String) trace.get("conversation_id"))) {
            return R.ok(List.of());
        }
        Object started = trace.get("started_at");
        if (!(started instanceof java.sql.Timestamp timestamp)) throw new IllegalStateException("诊断记录时间无效");
        Result<List<Map<String, String>>> result = historyClient.traceImages(Map.of(
            "externalId", String.valueOf(trace.get("user_id")), "agentId", trace.get("agent_id"),
            "conversationId", trace.get("conversation_id"), "question", trace.get("question"),
            "startedAt", timestamp.toLocalDateTime().toString(), "attachments", attachments));
        if (result == null || result.getStatus() != 1 || result.getData() == null) {
            throw new IllegalStateException("读取历史图片失败");
        }
        log.info("智能体对话图片查看 operatorId={}, traceId={}, imageCount={}", StpUtil.getLoginId(), traceId, result.getData().size());
        return R.ok(result.getData());
    }

    /** 检查诊断专属权限，避免普通登录权限隐式开放对话内容。 / Require the dedicated trace permission instead of relying on login alone. */
    private void requireTracePermission() {
        StpUtil.checkLogin();
        StpUtil.checkPermission("system:snailai:trace:list");
    }

    /** 清理并截断可选筛选词，避免空白条件误筛和超长查询。 / Normalize optional filters and cap their length. */
    private String trim(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
