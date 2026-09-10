package io.github.keycloakmcp.service.change;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.keycloak.representations.idm.ClientRepresentation;

import io.github.keycloakmcp.adapter.keycloak.StableAdminApiAdapter;
import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.domain.change.ChangeOperation;
import io.github.keycloakmcp.domain.change.ChangePolicyDecision;
import io.github.keycloakmcp.domain.change.ChangeRecord;
import io.github.keycloakmcp.domain.change.ChangeResourceType;
import io.github.keycloakmcp.domain.change.ChangeRisk;
import io.github.keycloakmcp.domain.change.ChangeStatus;
import io.github.keycloakmcp.domain.change.ChangeVerificationResult;
import io.github.keycloakmcp.domain.change.ChangeOperationType;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.domain.platform.AuditSource;
import io.github.keycloakmcp.domain.platform.PageResult;
import io.github.keycloakmcp.persistence.entity.ChangeRecordEntity;
import io.github.keycloakmcp.persistence.mapper.ChangePersistenceMapper;
import io.github.keycloakmcp.persistence.repository.ChangeRepository;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.service.change.ChangePolicyEvaluator.PolicyResult;
import io.github.keycloakmcp.service.change.ClientConfigChangeSupport.PlannedClientChange;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ChangeManagementService {

    private final TargetResolver targetResolver;
    private final TargetAuthorizationService targetAuthorization;
    private final StableAdminApiAdapter adminApi;
    private final ClientConfigChangeSupport clientConfigChangeSupport;
    private final ChangeRiskClassifier riskClassifier;
    private final ChangePolicyEvaluator policyEvaluator;
    private final ChangePlanFingerprinter fingerprinter;
    private final ChangeRepository changeRepository;
    private final ChangePersistenceMapper mapper;
    private final SensitiveDataFilter sensitiveDataFilter;
    private final AuditService auditService;
    private final ChangeExpirationService expirationService;

    @Inject
    public ChangeManagementService(
            TargetResolver targetResolver,
            TargetAuthorizationService targetAuthorization,
            StableAdminApiAdapter adminApi,
            ClientConfigChangeSupport clientConfigChangeSupport,
            ChangeRiskClassifier riskClassifier,
            ChangePolicyEvaluator policyEvaluator,
            ChangePlanFingerprinter fingerprinter,
            ChangeRepository changeRepository,
            ChangePersistenceMapper mapper,
            SensitiveDataFilter sensitiveDataFilter,
            AuditService auditService,
            ChangeExpirationService expirationService) {
        this.targetResolver = targetResolver;
        this.targetAuthorization = targetAuthorization;
        this.adminApi = adminApi;
        this.clientConfigChangeSupport = clientConfigChangeSupport;
        this.riskClassifier = riskClassifier;
        this.policyEvaluator = policyEvaluator;
        this.fingerprinter = fingerprinter;
        this.changeRepository = changeRepository;
        this.mapper = mapper;
        this.sensitiveDataFilter = sensitiveDataFilter;
        this.auditService = auditService;
        this.expirationService = expirationService;
    }

    @Transactional
    public ChangeRecord planClientUpdate(
            String targetId,
            String realm,
            String clientId,
            Map<String, Object> desiredState,
            String actor,
            String idempotencyKey) {
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            Target target = resolve(targetId, TargetPermission.PLAN);
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                Optional<ChangeRecordEntity> existing =
                        changeRepository.findByIdempotency(target.id().value(), idempotencyKey.trim());
                if (existing.isPresent()) {
                    success = true;
                    return mapper.toDomain(existing.get());
                }
            }

            ClientRepresentation current = adminApi.findClientByClientId(target, realm, clientId);
            PlannedClientChange planned = clientConfigChangeSupport.plan(current, desiredState);
            ChangeRisk risk = riskClassifier.classify(planned.operations());
            PolicyResult policy = policyEvaluator.evaluate(
                    target.environment(), ChangeOperationType.UPDATE, risk, false);
            if (policy.decision() == ChangePolicyDecision.DENY) {
                throw McpException.policyDenied(policy.reason());
            }

            String planFingerprint = fingerprinter.fingerprintPlan(
                    target.id().value(),
                    realm,
                    ChangeResourceType.CLIENT.name(),
                    clientId,
                    ChangeOperationType.UPDATE.name(),
                    planned.operations());
            String baselineFingerprint = fingerprinter.fingerprintBaseline(planned.baselineState());

            Instant now = Instant.now();
            ChangeRecordEntity entity = new ChangeRecordEntity();
            entity.id = UUID.randomUUID().toString();
            entity.targetId = target.id().value();
            entity.environment = target.environment().name();
            entity.resourceType = ChangeResourceType.CLIENT.name();
            entity.resourceId = clientId;
            entity.realm = realm;
            entity.operation = ChangeOperationType.UPDATE.name();
            entity.risk = risk.name();
            entity.policyDecision = policy.decision().name();
            entity.policyReason = policy.reason();
            entity.requiresApproval = policy.requiresApproval();
            entity.status = policy.requiresApproval()
                    ? ChangeStatus.WAITING_APPROVAL.name()
                    : ChangeStatus.APPROVED.name();
            entity.planFingerprint = planFingerprint;
            entity.baselineFingerprint = baselineFingerprint;
            if (!policy.requiresApproval()) {
                entity.approvalFingerprint = planFingerprint;
                entity.approvedBy = "POLICY_AUTO";
                entity.approvedAt = now;
            }
            entity.desiredState = planned.desiredState();
            entity.baselineState = planned.baselineState();
            entity.diffJson = mapper.fromDiff(planned.diff());
            entity.operationsJson = mapper.fromOperations(planned.operations());
            entity.actor = actor;
            entity.idempotencyKey = idempotencyKey == null || idempotencyKey.isBlank()
                    ? null
                    : idempotencyKey.trim();
            entity.createdAt = now;
            entity.updatedAt = now;
            changeRepository.persist(entity);

            auditChange("change.plan", entity, true, Map.of(
                    "risk", risk.name(),
                    "policy", policy.decision().name(),
                    "planFingerprint", planFingerprint));
            success = true;
            return mapper.toDomain(entity);
        } finally {
            auditService.logToolInvocation(
                    "ChangeManagementService.planClientUpdate",
                    targetId,
                    realm,
                    System.currentTimeMillis() - start,
                    success);
        }
    }

    public ChangeRecord getChange(String changeId) {
        ChangeRecordEntity entity = requireEntity(changeId);
        // READ on the owning target
        resolve(entity.targetId, TargetPermission.READ);
        maybeExpire(entity, "Expired: approval window elapsed");
        return sensitiveDataFilter.redact(mapper.toDomain(entity));
    }

    public PageResult<ChangeRecord> listChanges(
            Optional<String> targetId, Optional<String> status, int page, int size) {
        if (targetId.isPresent() && !targetId.get().isBlank()) {
            resolve(targetId.get(), TargetPermission.READ);
        }
        PageResult<ChangeRecordEntity> pageResult = changeRepository.list(targetId, status, page, size);
        List<ChangeRecord> items = pageResult.items().stream()
                .map(mapper::toDomain)
                .map(sensitiveDataFilter::redact)
                .toList();
        return new PageResult<>(items, pageResult.page(), pageResult.size(), pageResult.total());
    }

    @Transactional
    public ChangeRecord approve(String changeId, String approver) {
        ChangeRecordEntity entity = requireActiveEntity(changeId);
        resolve(entity.targetId, TargetPermission.WRITE);
        ChangeStatus status = ChangeStatus.valueOf(entity.status);
        if (status == ChangeStatus.APPROVED) {
            return mapper.toDomain(entity);
        }
        if (status == ChangeStatus.REJECTED) {
            throw McpException.approvalInvalid("rejected change cannot be approved: " + changeId);
        }
        if (status == ChangeStatus.APPLIED || status == ChangeStatus.VERIFIED || status == ChangeStatus.APPLYING) {
            throw McpException.changeAlreadyApplied(changeId);
        }
        if (status != ChangeStatus.WAITING_APPROVAL && status != ChangeStatus.PLANNED) {
            throw McpException.approvalInvalid("change is not waiting for approval: " + status);
        }
        if (entity.planFingerprint == null || entity.planFingerprint.isBlank()) {
            throw McpException.approvalInvalid("change has no plan fingerprint");
        }
        entity.status = ChangeStatus.APPROVED.name();
        entity.approvedBy = approver == null || approver.isBlank() ? "unknown" : approver.trim();
        entity.approvedAt = Instant.now();
        entity.approvalFingerprint = entity.planFingerprint;
        entity.updatedAt = Instant.now();
        auditChange("change.approve", entity, true, Map.of("approvedBy", entity.approvedBy));
        return mapper.toDomain(entity);
    }

    @Transactional
    public ChangeRecord reject(String changeId, String rejector, String reason) {
        ChangeRecordEntity entity = requireActiveEntity(changeId);
        resolve(entity.targetId, TargetPermission.WRITE);
        ChangeStatus status = ChangeStatus.valueOf(entity.status);
        if (status == ChangeStatus.APPLIED || status == ChangeStatus.VERIFIED || status == ChangeStatus.APPLYING) {
            throw McpException.changeAlreadyApplied(changeId);
        }
        if (status == ChangeStatus.REJECTED) {
            return mapper.toDomain(entity);
        }
        entity.status = ChangeStatus.REJECTED.name();
        entity.rejectedBy = rejector == null || rejector.isBlank() ? "unknown" : rejector.trim();
        entity.rejectedAt = Instant.now();
        entity.rejectionReason = reason;
        entity.updatedAt = Instant.now();
        auditChange("change.reject", entity, true, Map.of("rejectedBy", entity.rejectedBy));
        return mapper.toDomain(entity);
    }

    @Transactional
    public ChangeRecord apply(String changeId, String actor) {
        long start = System.currentTimeMillis();
        boolean success = false;
        ChangeRecordEntity entity = requireActiveEntity(changeId);
        try {
            Target target = resolve(entity.targetId, TargetPermission.WRITE);
            ChangeStatus status = ChangeStatus.valueOf(entity.status);

            if (status == ChangeStatus.VERIFIED || status == ChangeStatus.APPLIED) {
                success = true;
                return mapper.toDomain(entity);
            }
            if (status == ChangeStatus.REJECTED) {
                throw McpException.policyDenied("rejected change cannot be applied");
            }
            if (entity.policyDecision != null
                    && ChangePolicyDecision.valueOf(entity.policyDecision) == ChangePolicyDecision.DENY) {
                throw McpException.policyDenied(entity.policyReason);
            }
            if (entity.requiresApproval) {
                if (status != ChangeStatus.APPROVED) {
                    throw McpException.changeNotApproved(changeId);
                }
                if (entity.approvalFingerprint == null
                        || !entity.approvalFingerprint.equals(entity.planFingerprint)) {
                    throw McpException.approvalInvalid(
                            "approval fingerprint does not match plan fingerprint (replan required)");
                }
            } else if (status != ChangeStatus.APPROVED && status != ChangeStatus.PLANNED) {
                throw McpException.changeNotApproved(changeId);
            }

            entity.status = ChangeStatus.APPLYING.name();
            entity.updatedAt = Instant.now();

            if (!ChangeResourceType.CLIENT.name().equals(entity.resourceType)) {
                throw McpException.writeNotSupported("resource type not supported in 0.8: " + entity.resourceType);
            }

            ClientRepresentation current =
                    adminApi.findClientByClientId(target, entity.realm, entity.resourceId);
            Map<String, Object> liveBaseline = clientConfigChangeSupport.extractBaseline(current);
            String liveBaselineFingerprint = fingerprinter.fingerprintBaseline(liveBaseline);
            if (entity.baselineFingerprint != null
                    && !entity.baselineFingerprint.equals(liveBaselineFingerprint)) {
                entity.status = ChangeStatus.FAILED.name();
                entity.resultMessage = "REPLAN_REQUIRED: target resource changed since planning";
                entity.updatedAt = Instant.now();
                auditChange("change.apply.conflict", entity, false, Map.of("reason", "REPLAN_REQUIRED"));
                throw McpException.changeConflict(
                        "REPLAN_REQUIRED: resource changed since plan was created for change " + changeId);
            }

            List<ChangeOperation> operations = mapper.toDomain(entity).operations();
            clientConfigChangeSupport.applyToRepresentation(current, operations);
            // Never send secret fields back even if present on the representation.
            current.setSecret(null);
            adminApi.updateClient(target, entity.realm, current);

            entity.appliedAt = Instant.now();
            entity.status = ChangeStatus.APPLIED.name();
            entity.resultMessage = "Applied by " + (actor == null ? "unknown" : actor);
            entity.updatedAt = Instant.now();

            ChangeVerificationResult verification = verifyEntity(entity, target);
            if (verification.verified()) {
                entity.status = ChangeStatus.VERIFIED.name();
            } else {
                entity.status = ChangeStatus.FAILED.name();
                entity.resultMessage = verification.message();
            }
            success = verification.verified();
            auditChange("change.apply", entity, success, Map.of(
                    "verification", entity.verificationStatus == null ? "" : entity.verificationStatus));
            if (!verification.verified()) {
                throw McpException.verificationFailed(verification.message());
            }
            return mapper.toDomain(entity);
        } finally {
            auditService.logToolInvocation(
                    "ChangeManagementService.apply",
                    entity.targetId,
                    entity.realm,
                    System.currentTimeMillis() - start,
                    success);
        }
    }

    @Transactional
    public ChangeRecord verify(String changeId) {
        ChangeRecordEntity entity = requireActiveEntity(changeId);
        Target target = resolve(entity.targetId, TargetPermission.READ);
        if (entity.status.equals(ChangeStatus.REJECTED.name())) {
            throw McpException.invalidArgument("cannot verify rejected change");
        }
        ChangeVerificationResult result = verifyEntity(entity, target);
        if (result.verified()
                && (entity.status.equals(ChangeStatus.APPLIED.name())
                        || entity.status.equals(ChangeStatus.VERIFIED.name()))) {
            entity.status = ChangeStatus.VERIFIED.name();
        } else if (!result.verified()
                && (entity.status.equals(ChangeStatus.APPLIED.name())
                        || entity.status.equals(ChangeStatus.VERIFIED.name())
                        || entity.status.equals(ChangeStatus.FAILED.name()))) {
            entity.status = ChangeStatus.FAILED.name();
        }
        entity.updatedAt = Instant.now();
        auditChange("change.verify", entity, result.verified(), Map.of(
                "verification", result.status()));
        return mapper.toDomain(entity);
    }

    @Transactional
    public ChangeRecord expire(String changeId, String actor) {
        ChangeRecordEntity entity = requireEntity(changeId);
        resolve(entity.targetId, TargetPermission.WRITE);
        ChangeStatus status = ChangeStatus.valueOf(entity.status);
        if (status == ChangeStatus.EXPIRED) {
            return mapper.toDomain(entity);
        }
        if (!ChangeExpirationService.isExpirable(status)) {
            throw McpException.invalidArgument("cannot expire change in status: " + status);
        }
        expirationService.forceExpire(
                entity,
                "Expired by " + (actor == null || actor.isBlank() ? "unknown" : actor.trim()));
        auditChange("change.expire", entity, true, Map.of(
                "actor", actor == null || actor.isBlank() ? "unknown" : actor.trim(),
                "manual", true));
        return mapper.toDomain(entity);
    }

    /**
     * Batch-expire stale records (scheduler). Audits each transition.
     *
     * @return number of records transitioned to EXPIRED
     */
    @Transactional
    public int expireStaleChanges() {
        if (!expirationService.isEnabled()) {
            return 0;
        }
        java.time.Instant cutoff = java.time.Instant.now().minus(expirationService.expireAfter());
        List<ChangeRecordEntity> candidates = changeRepository.findExpirableBefore(cutoff);
        int expired = 0;
        for (ChangeRecordEntity entity : candidates) {
            if (expirationService.expireIfNeeded(entity, "Expired by scheduled TTL enforcement")) {
                auditChange("change.expire", entity, true, Map.of("reason", "TTL", "manual", false));
                expired++;
            }
        }
        return expired;
    }

    private ChangeVerificationResult verifyEntity(ChangeRecordEntity entity, Target target) {
        ClientRepresentation actual =
                adminApi.findClientByClientId(target, entity.realm, entity.resourceId);
        var mismatches = clientConfigChangeSupport.compareDesired(actual, entity.desiredState);
        ChangeVerificationResult result = mismatches.isEmpty()
                ? ChangeVerificationResult.verified("Desired state confirmed by read-back")
                : ChangeVerificationResult.failed("Desired state mismatch after read-back", mismatches);
        entity.verificationStatus = result.status();
        entity.verificationMessage = result.message();
        entity.verificationJson = mapper.fromDiff(result.mismatches());
        return result;
    }

    private ChangeRecordEntity requireEntity(String changeId) {
        if (changeId == null || changeId.isBlank()) {
            throw McpException.invalidArgument("changeId must not be blank");
        }
        return changeRepository.findByIdOptional(changeId.trim())
                .orElseThrow(() -> McpException.changeNotFound(changeId));
    }

    private ChangeRecordEntity requireActiveEntity(String changeId) {
        ChangeRecordEntity entity = requireEntity(changeId);
        maybeExpire(entity, "Expired: approval window elapsed");
        if (ChangeStatus.EXPIRED.name().equals(entity.status)) {
            throw McpException.changeExpired(changeId);
        }
        return entity;
    }

    private void maybeExpire(ChangeRecordEntity entity, String reason) {
        if (expirationService.expireIfNeeded(entity, reason)) {
            auditChange("change.expire", entity, true, Map.of("reason", "TTL", "manual", false));
        }
    }

    private Target resolve(String targetId, TargetPermission permission) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, permission);
        return target;
    }

    private void auditChange(String operation, ChangeRecordEntity entity, boolean success, Map<String, Object> extra) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("changeId", entity.id);
        metadata.put("resourceType", entity.resourceType);
        metadata.put("resourceId", entity.resourceId);
        metadata.put("status", entity.status);
        metadata.put("risk", entity.risk);
        metadata.put("policyDecision", entity.policyDecision);
        metadata.put("realm", entity.realm);
        if (extra != null) {
            metadata.putAll(extra);
        }
        auditService.record(
                AuditSource.SYSTEM,
                operation,
                entity.targetId,
                operation,
                success ? "SUCCESS" : "FAILURE",
                0L,
                sensitiveDataFilter.redact(metadata));
    }
}
