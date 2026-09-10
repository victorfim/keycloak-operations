package io.github.keycloakmcp.service.change;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.ClientRepresentation;

import io.github.keycloakmcp.adapter.keycloak.StableAdminApiAdapter;
import io.github.keycloakmcp.domain.change.ChangeStatus;
import io.github.keycloakmcp.domain.error.ErrorCode;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.persistence.entity.ChangeRecordEntity;
import io.github.keycloakmcp.persistence.repository.ChangeRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@QuarkusTest
@TestProfile(WriteEnabledTestProfile.class)
class ChangeManagementServiceTest {

    private static final String TARGET_A = "lab-keycloak-a";
    private static final String TARGET_B = "lab-keycloak-b";
    private static final String REALM = "master";
    private static final String CLIENT = "account";

    @Inject
    ChangeManagementService changeManagementService;

    @Inject
    ChangeRepository changeRepository;

    @InjectMock
    StableAdminApiAdapter adminApi;

    private final AtomicReference<ClientRepresentation> liveClient = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        liveClient.set(sampleClient("Account", "Account console"));
        when(adminApi.findClientByClientId(any(), eq(REALM), eq(CLIENT)))
                .thenAnswer(inv -> copy(liveClient.get()));
        doAnswer(inv -> {
            ClientRepresentation updated = inv.getArgument(2);
            liveClient.set(copy(updated));
            return null;
        }).when(adminApi).updateClient(any(), eq(REALM), any());
    }

    @Test
    void planApproveApplyVerifyHappyPathOnDev() {
        var planned = changeManagementService.planClientUpdate(
                TARGET_A, REALM, CLIENT, Map.of("name", "Account Updated"), "planner", null);

        assertThat(planned.status()).isEqualTo(ChangeStatus.APPROVED); // DEV + LOW
        assertThat(planned.requiresApproval()).isFalse();
        assertThat(planned.diff()).isNotEmpty();
        assertThat(planned.planFingerprint()).isNotBlank();

        var applied = changeManagementService.apply(planned.changeId(), "applier");
        assertThat(applied.status()).isEqualTo(ChangeStatus.VERIFIED);
        assertThat(liveClient.get().getName()).isEqualTo("Account Updated");
        assertThat(applied.verificationStatus()).isEqualTo("VERIFIED");
    }

    @Test
    void applyWithoutApprovalDeniedOnPrd() {
        var planned = changeManagementService.planClientUpdate(
                TARGET_B, REALM, CLIENT, Map.of("description", "prd-desc"), "planner", null);
        assertThat(planned.status()).isEqualTo(ChangeStatus.WAITING_APPROVAL);
        assertThat(planned.requiresApproval()).isTrue();

        assertThatThrownBy(() -> changeManagementService.apply(planned.changeId(), "applier"))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> assertThat(((McpException) ex).getCode()).isEqualTo(ErrorCode.CHANGE_NOT_APPROVED));
        verify(adminApi, never()).updateClient(any(), any(), any());
    }

    @Test
    void approveThenApplyOnPrd() {
        var planned = changeManagementService.planClientUpdate(
                TARGET_B, REALM, CLIENT, Map.of("name", "Prd Name"), "planner", null);
        var approved = changeManagementService.approve(planned.changeId(), "approver-1");
        assertThat(approved.status()).isEqualTo(ChangeStatus.APPROVED);
        assertThat(approved.approvalFingerprint()).isEqualTo(approved.planFingerprint());

        var applied = changeManagementService.apply(planned.changeId(), "applier");
        assertThat(applied.status()).isEqualTo(ChangeStatus.VERIFIED);
    }

    @Test
    @Transactional
    void modifiedPlanInvalidatesApproval() {
        var planned = changeManagementService.planClientUpdate(
                TARGET_B, REALM, CLIENT, Map.of("name", "X"), "planner", null);
        changeManagementService.approve(planned.changeId(), "approver");
        ChangeRecordEntity entity = changeRepository.findById(planned.changeId());
        entity.planFingerprint = entity.planFingerprint + "-tampered";

        assertThatThrownBy(() -> changeManagementService.apply(planned.changeId(), "applier"))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> assertThat(((McpException) ex).getCode()).isEqualTo(ErrorCode.APPROVAL_INVALID));
    }

    @Test
    void rejectPreventsApply() {
        var planned = changeManagementService.planClientUpdate(
                TARGET_B, REALM, CLIENT, Map.of("name", "RejectMe"), "planner", null);
        changeManagementService.reject(planned.changeId(), "rejector", "nope");
        assertThatThrownBy(() -> changeManagementService.apply(planned.changeId(), "applier"))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> assertThat(((McpException) ex).getCode()).isEqualTo(ErrorCode.POLICY_DENIED));
    }

    @Test
    void staleBaselineRequiresReplan() {
        var planned = changeManagementService.planClientUpdate(
                TARGET_A, REALM, CLIENT, Map.of("name", "Stale"), "planner", null);
        ClientRepresentation mutated = copy(liveClient.get());
        mutated.setName("Externally Changed");
        liveClient.set(mutated);

        assertThatThrownBy(() -> changeManagementService.apply(planned.changeId(), "applier"))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> {
                    assertThat(((McpException) ex).getCode()).isEqualTo(ErrorCode.CHANGE_CONFLICT);
                    assertThat(ex.getMessage()).contains("REPLAN_REQUIRED");
                });
    }

    @Test
    void idempotentPlanReturnsSameChange() {
        String key = "idem-" + System.nanoTime();
        var first = changeManagementService.planClientUpdate(
                TARGET_A, REALM, CLIENT, Map.of("description", "idem"), "a", key);
        var second = changeManagementService.planClientUpdate(
                TARGET_A, REALM, CLIENT, Map.of("description", "idem"), "a", key);
        assertThat(second.changeId()).isEqualTo(first.changeId());
    }

    @Test
    void repeatedApplyIsIdempotent() {
        var planned = changeManagementService.planClientUpdate(
                TARGET_A, REALM, CLIENT, Map.of("name", "Once"), "planner", null);
        var first = changeManagementService.apply(planned.changeId(), "a");
        var second = changeManagementService.apply(planned.changeId(), "a");
        assertThat(first.status()).isEqualTo(ChangeStatus.VERIFIED);
        assertThat(second.status()).isEqualTo(ChangeStatus.VERIFIED);
        assertThat(second.changeId()).isEqualTo(first.changeId());
    }

    @Test
    void targetIsolationListFilter() {
        var a = changeManagementService.planClientUpdate(
                TARGET_A, REALM, CLIENT, Map.of("name", "IsoA"), "a", null);
        changeManagementService.planClientUpdate(
                TARGET_B, REALM, CLIENT, Map.of("name", "IsoB"), "b", null);

        var onlyA = changeManagementService.listChanges(Optional.of(TARGET_A), Optional.empty(), 0, 50);
        assertThat(onlyA.items()).allMatch(c -> TARGET_A.equals(c.targetId()));
        assertThat(onlyA.items().stream().map(c -> c.changeId())).contains(a.changeId());
    }

    @Test
    void secretNeverStoredInPlan() {
        assertThatThrownBy(() -> changeManagementService.planClientUpdate(
                        TARGET_A, REALM, CLIENT, Map.of("clientSecret", "leak"), "a", null))
                .isInstanceOf(McpException.class);
    }

    @Test
    @Transactional
    void waitingApprovalExpiresOnGet() {
        var planned = changeManagementService.planClientUpdate(
                TARGET_B, REALM, CLIENT, Map.of("name", "ExpireMe"), "planner", null);
        backdateForExpiration(planned.changeId());

        var fetched = changeManagementService.getChange(planned.changeId());
        assertThat(fetched.status()).isEqualTo(ChangeStatus.EXPIRED);
    }

    @Test
    @Transactional
    void applyOnExpiredChangeDenied() {
        var planned = changeManagementService.planClientUpdate(
                TARGET_B, REALM, CLIENT, Map.of("name", "ExpiredApply"), "planner", null);
        backdateForExpiration(planned.changeId());

        assertThatThrownBy(() -> changeManagementService.apply(planned.changeId(), "applier"))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> assertThat(((McpException) ex).getCode()).isEqualTo(ErrorCode.CHANGE_EXPIRED));
        verify(adminApi, never()).updateClient(any(), any(), any());
    }

    @Test
    @Transactional
    void approveOnExpiredChangeDenied() {
        var planned = changeManagementService.planClientUpdate(
                TARGET_B, REALM, CLIENT, Map.of("name", "ExpiredApprove"), "planner", null);
        backdateForExpiration(planned.changeId());

        assertThatThrownBy(() -> changeManagementService.approve(planned.changeId(), "approver"))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> assertThat(((McpException) ex).getCode()).isEqualTo(ErrorCode.CHANGE_EXPIRED));
    }

    @Test
    void manualExpireWaitingApproval() {
        var planned = changeManagementService.planClientUpdate(
                TARGET_B, REALM, CLIENT, Map.of("name", "ManualExpire"), "planner", null);
        assertThat(planned.status()).isEqualTo(ChangeStatus.WAITING_APPROVAL);

        var expired = changeManagementService.expire(planned.changeId(), "operator-1");
        assertThat(expired.status()).isEqualTo(ChangeStatus.EXPIRED);
        assertThat(expired.resultMessage()).contains("operator-1");

        var again = changeManagementService.expire(planned.changeId(), "operator-2");
        assertThat(again.status()).isEqualTo(ChangeStatus.EXPIRED);
    }

    @Test
    @Transactional
    void scheduledBatchExpiresStaleRecords() {
        var planned = changeManagementService.planClientUpdate(
                TARGET_B, REALM, CLIENT, Map.of("name", "BatchExpire"), "planner", null);
        backdateForExpiration(planned.changeId());

        int expired = changeManagementService.expireStaleChanges();
        assertThat(expired).isGreaterThanOrEqualTo(1);
        assertThat(changeRepository.findById(planned.changeId()).status)
                .isEqualTo(ChangeStatus.EXPIRED.name());
    }

    @Test
    void verificationFailureWhenReadBackMismatches() {
        var planned = changeManagementService.planClientUpdate(
                TARGET_A, REALM, CLIENT, Map.of("name", "ShouldVerify"), "planner", null);
        doAnswer(inv -> null).when(adminApi).updateClient(any(), eq(REALM), any());

        assertThatThrownBy(() -> changeManagementService.apply(planned.changeId(), "applier"))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> assertThat(((McpException) ex).getCode()).isEqualTo(ErrorCode.VERIFICATION_FAILED));
    }

    private void backdateForExpiration(String changeId) {
        ChangeRecordEntity entity = changeRepository.findById(changeId);
        entity.createdAt = Instant.now().minus(Duration.ofDays(8));
        entity.updatedAt = entity.createdAt;
    }

    private static ClientRepresentation sampleClient(String name, String description) {
        ClientRepresentation rep = new ClientRepresentation();
        rep.setId("client-uuid-1");
        rep.setClientId(CLIENT);
        rep.setName(name);
        rep.setDescription(description);
        rep.setAttributes(new HashMap<>());
        return rep;
    }

    private static ClientRepresentation copy(ClientRepresentation source) {
        ClientRepresentation copy = new ClientRepresentation();
        copy.setId(source.getId());
        copy.setClientId(source.getClientId());
        copy.setName(source.getName());
        copy.setDescription(source.getDescription());
        copy.setAttributes(source.getAttributes() == null ? new HashMap<>() : new HashMap<>(source.getAttributes()));
        copy.setSecret(null);
        return copy;
    }
}
