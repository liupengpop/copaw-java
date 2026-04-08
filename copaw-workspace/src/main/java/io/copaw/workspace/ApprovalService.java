package io.copaw.workspace;

import io.copaw.core.security.GuardFinding;
import io.copaw.core.security.ToolGuardResult;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Manages pending user approvals for tool calls.
 */
public class ApprovalService {

    private final ConsolePushMessageStore pushMessageStore;
    private final Map<String, PendingApprovalState> pendingApprovals = new ConcurrentHashMap<>();

    public ApprovalService(ConsolePushMessageStore pushMessageStore) {
        this.pushMessageStore = pushMessageStore;
    }

    public PendingApprovalTicket requestApproval(RuntimeToolContext context,
                                                 String toolName,
                                                 Map<String, Object> params,
                                                 ToolGuardResult guardResult,
                                                 Duration timeout) {
        Instant now = Instant.now();
        PendingApproval approval = new PendingApproval(
                UUID.randomUUID().toString(),
                context.agentId(),
                context.chatId(),
                context.sessionId(),
                context.userId(),
                context.channel(),
                toolName,
                params != null ? params : Map.of(),
                guardResult != null ? List.copyOf(guardResult.getFindings()) : List.of(),
                now,
                now.plus(timeout)
        );

        PendingApprovalState state = new PendingApprovalState(approval);
        pendingApprovals.put(approval.id(), state);
        pushMessageStore.publish(
                approval.sessionId(),
                approval.chatId(),
                "tool_approval_required",
                Map.of(
                        "approval", approval,
                        "tool_name", toolName,
                        "params", approval.params(),
                        "findings", approval.findings(),
                        "expires_at", approval.expiresAt().toString()
                )
        );
        return new PendingApprovalTicket(approval, state.decision());
    }

    public boolean awaitDecision(PendingApprovalTicket ticket, Duration timeout) {
        try {
            return ticket.decision().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            resolveTimeout(ticket.approval(), "approval wait interrupted");
            return false;
        } catch (TimeoutException e) {
            resolveTimeout(ticket.approval(), "approval timed out");
            return false;
        } catch (ExecutionException e) {
            resolveTimeout(ticket.approval(), "approval failed: " + e.getCause().getMessage());
            return false;
        } finally {
            pendingApprovals.remove(ticket.approval().id());
        }
    }

    public boolean resolve(String approvalId, boolean approved, String responder, String note) {
        PendingApprovalState state = pendingApprovals.remove(approvalId);
        if (state == null) {
            return false;
        }
        state.decision().complete(approved);
        pushMessageStore.publish(
                state.approval().sessionId(),
                state.approval().chatId(),
                "tool_approval_resolved",
                Map.of(
                        "approval_id", approvalId,
                        "approved", approved,
                        "responder", responder != null ? responder : "user",
                        "note", note != null ? note : "",
                        "tool_name", state.approval().toolName()
                )
        );
        return true;
    }

    public void publishSessionMessage(RuntimeToolContext context,
                                      String eventType,
                                      Map<String, Object> payload) {
        if (context == null) {
            return;
        }
        pushMessageStore.publish(
                context.sessionId(),
                context.chatId(),
                eventType,
                payload != null ? payload : Map.of()
        );
    }

    public List<PendingApproval> listPending(String sessionId) {
        return pendingApprovals.values().stream()
                .map(PendingApprovalState::approval)
                .filter(approval -> sessionId == null || sessionId.isBlank()
                        || approval.sessionId().equals(sessionId))
                .sorted(Comparator.comparing(PendingApproval::createdAt))
                .toList();
    }

    public void cancelAll(String reason) {
        List<String> ids = new ArrayList<>(pendingApprovals.keySet());
        for (String approvalId : ids) {
            PendingApprovalState state = pendingApprovals.remove(approvalId);
            if (state == null) {
                continue;
            }
            state.decision().complete(false);
            pushMessageStore.publish(
                    state.approval().sessionId(),
                    state.approval().chatId(),
                    "tool_approval_cancelled",
                    Map.of(
                            "approval_id", approvalId,
                            "tool_name", state.approval().toolName(),
                            "reason", reason != null ? reason : "cancelled"
                    )
            );
        }
    }

    private void resolveTimeout(PendingApproval approval, String reason) {
        pushMessageStore.publish(
                approval.sessionId(),
                approval.chatId(),
                "tool_approval_timeout",
                Map.of(
                        "approval_id", approval.id(),
                        "tool_name", approval.toolName(),
                        "reason", reason
                )
        );
    }

    private record PendingApprovalState(PendingApproval approval, CompletableFuture<Boolean> decision) {
        private PendingApprovalState(PendingApproval approval) {
            this(approval, new CompletableFuture<>());
        }
    }

    public record PendingApproval(
            String id,
            String agentId,
            String chatId,
            String sessionId,
            String userId,
            String channel,
            String toolName,
            Map<String, Object> params,
            List<GuardFinding> findings,
            Instant createdAt,
            Instant expiresAt
    ) {}

    record PendingApprovalTicket(PendingApproval approval, CompletableFuture<Boolean> decision) {}
}
