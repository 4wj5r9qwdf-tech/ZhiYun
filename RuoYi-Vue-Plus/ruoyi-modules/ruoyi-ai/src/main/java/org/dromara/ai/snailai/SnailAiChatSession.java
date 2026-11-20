/*
 * 文件用途：保存一轮 ERP 智能体对话的员工身份、工具权限、可见工具、审批等待和诊断状态。
 * 文件版本：1.0.0
 * 功能目的：保存一轮 ERP 智能体对话的员工身份、工具权限、可见工具、审批等待和诊断状态。
 * 设计思路：会话在服务端绑定员工与调用上下文；提示词和附件不能更换会话权限；工具调用过程由状态对象记录。
 * 实现方法：管理工具清单、工具事件、对话诊断和待审批动作，并提供终止会话的同步边界。
 * 关键约束：审批只对应本会话具体工具输入；会话完成或超时后旧授权不能继续使用。
 * 依赖与引用：Docs/AI/织云AI集成总体规划.md；RuoYi-Vue-Plus/ruoyi-modules/ruoyi-ai/src/main/java/org/dromara/ai/snailai/SnailAiChatSessionStore.java
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
package org.dromara.ai.snailai;

import lombok.Getter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import org.springframework.ai.tool.ToolCallback;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Short-lived identity context for one ERP chat turn. / Holds identity only while one chat turn runs. */
@Getter
public class SnailAiChatSession {

    private final String sid;
    private final Long userId;
    private final String nickname;
    private final String bearerToken;
    private final String clientId;
    private final String traceId;
    private final SnailAiTraceService traceService;
    private final Set<String> permissions;
    private final Instant expiresAt;
    private final Sinks.Many<Map<String, Object>> approvalSink = Sinks.many().replay().limit(16);

    public SnailAiChatSession(String sid, Long userId, String nickname, String bearerToken,
                              String clientId, Set<String> permissions, Instant expiresAt,
                              String traceId, SnailAiTraceService traceService) {
        this.sid = sid;
        this.userId = userId;
        this.nickname = nickname;
        this.bearerToken = bearerToken;
        this.clientId = clientId;
        this.traceId = traceId;
        this.traceService = traceService;
        this.permissions = Set.copyOf(permissions);
        this.expiresAt = expiresAt;
    }

    @lombok.Setter
    private ErpChatDocumentClient.Reader documentReader;

    /** 暴露当前对话审批事件流，供同一员工页面展示待确认工具动作。 */
    public Flux<Map<String, Object>> approvals() {
        return approvalSink.asFlux();
    }

    /** 将经权限筛选后的工具目录写入诊断记录，供用户确认和授权审查。 */
    public void recordVisibleTools(List<ToolCallback> callbacks) {
        List<Map<String, Object>> tools = new ArrayList<>(callbacks.size());
        for (ToolCallback callback : callbacks) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", callback.getToolDefinition().name());
            item.put("description", callback.getToolDefinition().description());
            item.put("readOnly", SnailAiMcpToolPolicy.isReadOnly(callback));
            tools.add(item);
        }
        traceService.updateVisibleTools(traceId, tools);
    }

    /** 记录某工具开始执行的时间和名称，不把输入参数正文写入诊断库。 */
    public SnailAiTraceService.ToolTraceHandle startTool(String toolName, String description, String arguments) {
        return traceService.startTool(traceId, toolName, description, arguments);
    }

    /** 结束工具执行记录，只保存状态、耗时和脱敏错误摘要。 */
    public void finishTool(SnailAiTraceService.ToolTraceHandle handle, String status, String result, String error) {
        traceService.finishTool(handle, status, result, error);
    }

    /** 向本轮 SSE 订阅者发布待审批工具摘要。 */
    void publishApproval(Map<String, Object> event) {
        approvalSink.tryEmitNext(event);
    }

    /** 关闭审批事件流，防止会话结束后页面继续等待或接收过期授权。 */
    public void finish() {
        approvalSink.tryEmitComplete();
    }
}
