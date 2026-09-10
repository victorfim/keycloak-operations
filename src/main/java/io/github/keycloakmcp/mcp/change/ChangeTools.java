package io.github.keycloakmcp.mcp.change;

import java.util.Map;
import java.util.Optional;

import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.domain.change.ChangeRecord;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.domain.platform.PageResult;
import io.github.keycloakmcp.observability.McpMetrics;
import io.github.keycloakmcp.security.ToolAuthorization;
import io.github.keycloakmcp.service.change.ChangeManagementService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.inject.Inject;

public class ChangeTools {

    private static final String TARGET_ID_HINT =
            "Identifies a previously registered Keycloak/RHBK environment. "
                    + "Use keycloak_list_targets when the target is unknown.";

    @Inject
    ChangeManagementService changeManagementService;

    @Inject
    AuditService auditService;

    @Inject
    McpMetrics metrics;

    @Inject
    ToolAuthorization toolAuthorization;

    @Tool(
            name = "keycloak_plan_client_update",
            description = "Plan a controlled non-sensitive client configuration update "
                    + "(name, description, pkceCodeChallengeMethod). Does not apply the change.")
    public ChangeRecord keycloakPlanClientUpdate(
            @ToolArg(description = TARGET_ID_HINT) String targetId,
            @ToolArg(description = "Realm name") String realm,
            @ToolArg(description = "OAuth/OIDC clientId") String clientId,
            @ToolArg(description = "Desired non-sensitive properties as a map") Map<String, Object> desiredState,
            @ToolArg(description = "Optional actor identity", required = false) String actor,
            @ToolArg(description = "Optional idempotency key", required = false) String idempotencyKey) {
        return invoke(
                "keycloak_plan_client_update",
                targetId,
                realm,
                () -> changeManagementService.planClientUpdate(
                        targetId, realm, clientId, desiredState, actor, idempotencyKey));
    }

    @Tool(name = "keycloak_get_change", description = "Get a change lifecycle record by changeId")
    public ChangeRecord keycloakGetChange(@ToolArg(description = "Change identifier") String changeId) {
        return invoke("keycloak_get_change", null, null, () -> changeManagementService.getChange(changeId));
    }

    @Tool(name = "keycloak_list_changes", description = "List change lifecycle records")
    public PageResult<ChangeRecord> keycloakListChanges(
            @ToolArg(description = TARGET_ID_HINT, required = false) String targetId,
            @ToolArg(description = "Optional status filter", required = false) String status,
            @ToolArg(description = "Page number", required = false) Integer page,
            @ToolArg(description = "Page size", required = false) Integer size) {
        return invoke(
                "keycloak_list_changes",
                targetId,
                null,
                () -> changeManagementService.listChanges(
                        Optional.ofNullable(targetId),
                        Optional.ofNullable(status),
                        page == null ? 0 : page,
                        size == null ? 20 : size));
    }

    @Tool(name = "keycloak_approve_change", description = "Approve a planned change (bound to plan fingerprint)")
    public ChangeRecord keycloakApproveChange(
            @ToolArg(description = "Change identifier") String changeId,
            @ToolArg(description = "Approver identity", required = false) String approver) {
        return invoke(
                "keycloak_approve_change",
                null,
                null,
                () -> changeManagementService.approve(changeId, approver));
    }

    @Tool(name = "keycloak_reject_change", description = "Reject a planned change")
    public ChangeRecord keycloakRejectChange(
            @ToolArg(description = "Change identifier") String changeId,
            @ToolArg(description = "Rejector identity", required = false) String rejector,
            @ToolArg(description = "Rejection reason", required = false) String reason) {
        return invoke(
                "keycloak_reject_change",
                null,
                null,
                () -> changeManagementService.reject(changeId, rejector, reason));
    }

    @Tool(
            name = "keycloak_apply_change",
            description = "Apply an approved change plan (requires mcp.read-only=false and WRITE permission)")
    public ChangeRecord keycloakApplyChange(
            @ToolArg(description = "Change identifier") String changeId,
            @ToolArg(description = "Actor identity", required = false) String actor) {
        return invoke(
                "keycloak_apply_change",
                null,
                null,
                () -> changeManagementService.apply(changeId, actor));
    }

    @Tool(name = "keycloak_verify_change", description = "Re-run read-back verification for a change")
    public ChangeRecord keycloakVerifyChange(@ToolArg(description = "Change identifier") String changeId) {
        return invoke("keycloak_verify_change", null, null, () -> changeManagementService.verify(changeId));
    }

    @Tool(
            name = "keycloak_expire_change",
            description = "Expire a planned or approved change that is no longer actionable")
    public ChangeRecord keycloakExpireChange(
            @ToolArg(description = "Change identifier") String changeId,
            @ToolArg(description = "Actor identity", required = false) String actor) {
        return invoke(
                "keycloak_expire_change",
                null,
                null,
                () -> changeManagementService.expire(changeId, actor));
    }

    private <T> T invoke(String toolName, String targetId, String realm, java.util.concurrent.Callable<T> action) {
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            toolAuthorization.assertReadOnlyOperation(toolName);
            T result = action.call();
            success = true;
            return result;
        } catch (McpException e) {
            throw new ToolCallException(e.getError().code() + ": " + e.getMessage());
        } catch (ToolCallException e) {
            throw e;
        } catch (Exception e) {
            throw new ToolCallException("INTERNAL_ERROR: " + e.getMessage());
        } finally {
            long duration = System.currentTimeMillis() - start;
            metrics.recordToolInvocation(toolName, duration, success);
            auditService.logToolInvocation(toolName, targetId, realm, duration, success);
        }
    }
}
