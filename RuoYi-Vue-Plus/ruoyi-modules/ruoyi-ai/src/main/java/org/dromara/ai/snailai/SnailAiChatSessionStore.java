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

    public SnailAiChatSession open(Long userId, String nickname, String bearerToken,
                                   String clientId, java.util.Set<String> permissions, String traceId) {
        purgeExpired();
        if (sessions.size() >= MAX_ACTIVE_SESSIONS) {
            throw new IllegalStateException("当前智能体并发会话较多，请稍后重试");
        }
        String sid = UUID.randomUUID().toString();
        SnailAiChatSession session = new SnailAiChatSession(sid, userId, nickname, bearerToken,
            clientId, permissions, Instant.now().plus(SESSION_TTL), traceId, traceService);
        sessions.put(sid, session);
        return session;
    }

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

    public boolean awaitApproval(SnailAiChatSession session, String toolName, String description,
                                 String arguments) {
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
            approvals.remove(approvalId);
        }
    }

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
