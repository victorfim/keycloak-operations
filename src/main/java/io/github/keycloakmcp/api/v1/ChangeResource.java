package io.github.keycloakmcp.api.v1;

import java.util.Map;
import java.util.Optional;

import io.github.keycloakmcp.domain.change.ChangeRecord;
import io.github.keycloakmcp.domain.platform.PageResult;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.service.change.ChangeManagementService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/changes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ChangeResource {

    @Inject
    ChangeManagementService changeManagementService;

    @Inject
    SensitiveDataFilter sensitiveDataFilter;

    @GET
    public PageResult<ChangeRecord> list(
            @QueryParam("targetId") String targetId,
            @QueryParam("status") String status,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        return sensitiveDataFilter.redact(changeManagementService.listChanges(
                Optional.ofNullable(targetId),
                Optional.ofNullable(status),
                page,
                size));
    }

    @GET
    @Path("/{changeId}")
    public ChangeRecord get(@PathParam("changeId") String changeId) {
        return sensitiveDataFilter.redact(changeManagementService.getChange(changeId));
    }

    @POST
    @Path("/{changeId}/approve")
    public ChangeRecord approve(@PathParam("changeId") String changeId, Map<String, Object> body) {
        String approver = body == null ? null : stringVal(body.get("approver"));
        return sensitiveDataFilter.redact(changeManagementService.approve(changeId, approver));
    }

    @POST
    @Path("/{changeId}/reject")
    public ChangeRecord reject(@PathParam("changeId") String changeId, Map<String, Object> body) {
        String rejector = body == null ? null : stringVal(body.get("rejector"));
        String reason = body == null ? null : stringVal(body.get("reason"));
        return sensitiveDataFilter.redact(changeManagementService.reject(changeId, rejector, reason));
    }

    @POST
    @Path("/{changeId}/apply")
    public ChangeRecord apply(@PathParam("changeId") String changeId, Map<String, Object> body) {
        String actor = body == null ? null : stringVal(body.get("actor"));
        return sensitiveDataFilter.redact(changeManagementService.apply(changeId, actor));
    }

    @POST
    @Path("/{changeId}/verify")
    public ChangeRecord verify(@PathParam("changeId") String changeId) {
        return sensitiveDataFilter.redact(changeManagementService.verify(changeId));
    }

    @POST
    @Path("/{changeId}/expire")
    public ChangeRecord expire(@PathParam("changeId") String changeId, Map<String, Object> body) {
        String actor = body == null ? null : stringVal(body.get("actor"));
        return sensitiveDataFilter.redact(changeManagementService.expire(changeId, actor));
    }

    @POST
    @Path("/plan/client-update")
    public ChangeRecord planClientUpdate(Map<String, Object> body) {
        if (body == null) {
            body = Map.of();
        }
        String targetId = stringVal(body.get("targetId"));
        String realm = stringVal(body.get("realm"));
        String clientId = stringVal(body.get("clientId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> desiredState = body.get("desiredState") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        String actor = stringVal(body.get("actor"));
        String idempotencyKey = stringVal(body.get("idempotencyKey"));
        return sensitiveDataFilter.redact(changeManagementService.planClientUpdate(
                targetId, realm, clientId, desiredState, actor, idempotencyKey));
    }

    private static String stringVal(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
