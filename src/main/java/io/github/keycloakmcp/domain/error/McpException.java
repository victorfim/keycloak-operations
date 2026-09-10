package io.github.keycloakmcp.domain.error;

import java.util.Map;
import java.util.Objects;

public class McpException extends RuntimeException {

    private final McpError error;

    public McpException(McpError error) {
        super(Objects.requireNonNull(error, "error").message());
        this.error = error;
    }

    public McpException(McpError error, Throwable cause) {
        super(Objects.requireNonNull(error, "error").message(), cause);
        this.error = error;
    }

    public McpError getError() {
        return error;
    }

    public ErrorCode getCode() {
        return error.code();
    }

    public static McpException forNotFound(ErrorCode code, String resourceType, String identifier) {
        return new McpException(McpError.notFound(code, resourceType, identifier));
    }

    public static McpException realmNotFound(String realm) {
        return forNotFound(ErrorCode.REALM_NOT_FOUND, "realm", realm);
    }

    public static McpException clientNotFound(String clientId) {
        return forNotFound(ErrorCode.CLIENT_NOT_FOUND, "client", clientId);
    }

    public static McpException userNotFound(String userId) {
        return forNotFound(ErrorCode.USER_NOT_FOUND, "user", userId);
    }

    public static McpException groupNotFound(String groupId) {
        return forNotFound(ErrorCode.GROUP_NOT_FOUND, "group", groupId);
    }

    public static McpException roleNotFound(String roleName) {
        return forNotFound(ErrorCode.ROLE_NOT_FOUND, "role", roleName);
    }

    public static McpException keycloakUnavailable(String message, Throwable cause) {
        return new McpException(McpError.unavailable(ErrorCode.KEYCLOAK_UNAVAILABLE, message), cause);
    }

    public static McpException authenticationFailed(String message) {
        return new McpException(McpError.of(ErrorCode.AUTHENTICATION_FAILED, message));
    }

    public static McpException authenticationFailed(String message, Throwable cause) {
        return new McpException(McpError.of(ErrorCode.AUTHENTICATION_FAILED, message), cause);
    }

    public static McpException authorizationFailed(String message) {
        return new McpException(McpError.of(ErrorCode.AUTHORIZATION_FAILED, message));
    }

    public static McpException invalidArgument(String message) {
        return new McpException(McpError.invalidArgument(message));
    }

    public static McpException unsupportedCapability(String message) {
        return new McpException(McpError.unsupported(message));
    }

    public static McpException assessmentFailed(String message) {
        return new McpException(McpError.of(ErrorCode.ASSESSMENT_FAILED, message));
    }

    public static McpException assessmentFailed(String message, Throwable cause) {
        return new McpException(McpError.of(ErrorCode.ASSESSMENT_FAILED, message), cause);
    }

    public static McpException evidenceCollectionFailed(String message, Throwable cause) {
        return new McpException(McpError.of(ErrorCode.EVIDENCE_COLLECTION_FAILED, message), cause);
    }

    public static McpException targetNotFound(String id) {
        return forNotFound(ErrorCode.TARGET_NOT_FOUND, "target", id == null ? "" : id);
    }

    public static McpException targetDisabled(String id) {
        return new McpException(McpError.of(
                ErrorCode.TARGET_DISABLED,
                "target disabled: " + (id == null ? "" : id),
                Map.of("resourceType", "target", "identifier", id == null ? "" : id)));
    }

    public static McpException targetUnauthorized(String id) {
        return new McpException(McpError.of(
                ErrorCode.TARGET_NOT_AUTHORIZED,
                "not authorized for target: " + (id == null ? "" : id),
                Map.of("resourceType", "target", "identifier", id == null ? "" : id)));
    }

    public static McpException targetUnavailable(String id, String message) {
        return new McpException(McpError.of(
                ErrorCode.TARGET_UNAVAILABLE,
                message == null || message.isBlank()
                        ? "target unavailable: " + (id == null ? "" : id)
                        : message,
                Map.of("resourceType", "target", "identifier", id == null ? "" : id)));
    }

    public static McpException internal(String message) {
        return new McpException(McpError.internal(message));
    }

    public static McpException internal(String message, Throwable cause) {
        return new McpException(McpError.internal(message), cause);
    }

    public static McpException of(ErrorCode code, String message) {
        return new McpException(McpError.of(code, message));
    }

    public static McpException of(ErrorCode code, String message, Map<String, Object> details) {
        return new McpException(McpError.of(code, message, details));
    }

    public static McpException changeNotFound(String changeId) {
        return forNotFound(ErrorCode.CHANGE_NOT_FOUND, "change", changeId == null ? "" : changeId);
    }

    public static McpException changeNotApproved(String changeId) {
        return of(ErrorCode.CHANGE_NOT_APPROVED, "change is not approved: " + changeId,
                Map.of("changeId", changeId == null ? "" : changeId));
    }

    public static McpException changeConflict(String message) {
        return of(ErrorCode.CHANGE_CONFLICT, message);
    }

    public static McpException changeAlreadyApplied(String changeId) {
        return of(ErrorCode.CHANGE_ALREADY_APPLIED, "change already applied: " + changeId,
                Map.of("changeId", changeId == null ? "" : changeId));
    }

    public static McpException changeExpired(String changeId) {
        return of(ErrorCode.CHANGE_EXPIRED, "change expired: " + changeId,
                Map.of("changeId", changeId == null ? "" : changeId));
    }

    public static McpException policyDenied(String message) {
        return of(ErrorCode.POLICY_DENIED, message);
    }

    public static McpException approvalRequired(String changeId) {
        return of(ErrorCode.APPROVAL_REQUIRED, "approval required for change: " + changeId,
                Map.of("changeId", changeId == null ? "" : changeId));
    }

    public static McpException approvalInvalid(String message) {
        return of(ErrorCode.APPROVAL_INVALID, message);
    }

    public static McpException verificationFailed(String message) {
        return of(ErrorCode.VERIFICATION_FAILED, message);
    }

    public static McpException writeNotSupported(String message) {
        return of(ErrorCode.WRITE_NOT_SUPPORTED, message);
    }
}
