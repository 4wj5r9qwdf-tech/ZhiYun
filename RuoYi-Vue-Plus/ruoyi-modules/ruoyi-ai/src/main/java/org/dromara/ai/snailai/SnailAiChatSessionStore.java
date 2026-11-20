/*
 * 文件用途：管理 ERP 智能体会话生命周期、待审批工具动作和跨请求等待/唤醒。
 * 文件版本：1.0.0
 * 功能目的：管理 ERP 智能体会话生命周期、待审批工具动作和跨请求等待/唤醒。
 * 设计思路：以会话 SID 为索引，审批动作保存在服务端，客户端只提交短期审批标识和确认结果。
 * 实现方法：创建/查找会话，等待用户审批，校验审批标识并通知执行线程，结束后清理会话。
 * 关键约束：用户必须匹配会话本人；超时、取消、重复确认和会话关闭均不得放行未确认动作。
 * 依赖与引用：Docs/AI/织云AI集成总体规划.md；RuoYi-Vue-Plus/ruoyi-modules/ruoyi-ai/src/main/java/org/dromara/ai/snailai/SnailAiChatSession.java
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

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Keeps ERP credentials out of the model request and binds them to an unguessable per-turn sid. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SnailAiChatSessionStore {

    private static final Duration SESSION_TTL = Duration.ofMinutes(6);
    private static final long APPROVAL_TIMEOUT_SECONDS = 120;
    private static final int MAX_ACTIVE_SESSIONS = 1000;

    private final ConcurrentHashMap<String, SnailAiChatSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PendingApproval> approvals = new ConcurrentHashMap<>();
    private final SnailAiTraceService traceService;

    /**
     * 为一次员工对话创建不可预测 SID，并把员工令牌和权限仅保存在服务端会话对象中。
     * 超过并发上限时拒绝创建，避免每轮会话无界占用线程和内存。
     */
    public SnailAiChatSession open(Long userId, String nickname, String bearerToken,
                                   String clientId, java.util.Set<String> permissions, String traceId) {
        purgeExpired();
        if (sessions.size() >= MAX_ACTIVE_SESSIONS) {
            throw new IllegalStateException("当前智能体并发会话较多，请稍后重试");
        }
        // SID 只作为查找服务端上下文的随机索引，不编码员工 ID、令牌或权限。
        String sid = UUID.randomUUID().toString();
        SnailAiChatSession session = new SnailAiChatSession(sid, userId, nickname, bearerToken,
            clientId, permissions, Instant.now().plus(SESSION_TTL), traceId, traceService);
        sessions.put(sid, session);
        return session;
    }

    /** 查找未过期会话；过期项立即关闭并撤销其仍在等待的审批。 */
    public SnailAiChatSession get(String sid) {
        if (sid == null) {
            return null;
        }
        SnailAiChatSession session = sessions.get(sid);
        if (session != null && session.getExpiresAt().isAfter(Instant.now())) {
            return session;
        }
        if (session != null) {
            close(sid);
        }
        return null;
    }

    /**
     * 暂停工具线程并向本人对话流发布待确认摘要，最多等待两分钟。
     * 超时、中断、会话关闭或异常均返回未批准；无论结果如何都删除一次性审批项。
     */
    public boolean awaitApproval(SnailAiChatSession session, String toolName, String description,
                                 String arguments) {
        // 一次性审批 ID 与员工、当前会话和 CompletableFuture 只保存在服务端 Map 中。
        String approvalId = UUID.randomUUID().toString().replace("-", "")
            + UUID.randomUUID().toString().replace("-", "");
        CompletableFuture<Boolean> decision = new CompletableFuture<>();
        approvals.put(approvalId, new PendingApproval(session.getUserId(), session, decision));

        session.publishApproval(Map.of(
            "type", "approval_required",
            "approvalId", approvalId,
            "toolName", toolName,
            "description", description == null ? "" : description,
            "arguments", arguments == null ? "" : arguments
        ));

        try {
            // 只在本人完成页面确认后返回 true；等待期间工具尚未调用。
            Boolean approved = decision.get(APPROVAL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(approved);
        } catch (TimeoutException e) {
            log.info("SnailAI MCP approval expired userId={}, toolName={}", session.getUserId(), toolName);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            // 保证任何结果都销毁待审批能力，阻止旧审批 ID 被重复使用。
            approvals.remove(approvalId);
        }
    }

    /** 当前登录员工必须与审批登记员工相同；CompletableFuture 只允许首个决定成功。 */
    public boolean resolveApproval(String approvalId, Long userId, boolean approved) {
        PendingApproval pending = approvals.get(approvalId);
        if (pending == null || !pending.userId().equals(userId)) {
            return false;
        }
        boolean completed = pending.decision().complete(approved);
        if (completed) {
            log.info("SnailAI MCP approval userId={}, sid={}, approved={}",
                userId, pending.session().getSid(), approved);
        }
        return completed;
    }

    /** 结束会话并对尚未决策的工具审批发送拒绝结果，确保写操作停止。 */
    public void close(String sid) {
        SnailAiChatSession session = sessions.remove(sid);
        if (session == null) {
            return;
        }
        approvals.entrySet().removeIf(entry -> {
            PendingApproval pending = entry.getValue();
            if (pending.session() != session) {
                return false;
            }
            pending.decision().complete(false);
            return true;
        });
        session.finish();
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        sessions.forEach((sid, session) -> {
            if (!session.getExpiresAt().isAfter(now)) {
                close(sid);
            }
        });
    }

    private record PendingApproval(Long userId, SnailAiChatSession session,
                                   CompletableFuture<Boolean> decision) {
    }
}
