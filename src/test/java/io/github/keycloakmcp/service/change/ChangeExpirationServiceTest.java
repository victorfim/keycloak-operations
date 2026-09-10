package io.github.keycloakmcp.service.change;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.domain.change.ChangeStatus;
import io.github.keycloakmcp.persistence.entity.ChangeRecordEntity;

class ChangeExpirationServiceTest {

    @Test
    void isExpirableIncludesWaitingApprovalPlannedAndApproved() {
        assertThat(ChangeExpirationService.isExpirable(ChangeStatus.WAITING_APPROVAL)).isTrue();
        assertThat(ChangeExpirationService.isExpirable(ChangeStatus.PLANNED)).isTrue();
        assertThat(ChangeExpirationService.isExpirable(ChangeStatus.APPROVED)).isTrue();
        assertThat(ChangeExpirationService.isExpirable(ChangeStatus.APPLIED)).isFalse();
        assertThat(ChangeExpirationService.isExpirable(ChangeStatus.EXPIRED)).isFalse();
    }

    @Test
    void referenceInstantPrefersApprovedAtForApprovedStatus() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Instant approved = Instant.parse("2026-01-05T00:00:00Z");
        ChangeRecordEntity entity = new ChangeRecordEntity();
        entity.status = ChangeStatus.APPROVED.name();
        entity.createdAt = created;
        entity.approvedAt = approved;
        assertThat(ChangeExpirationService.referenceInstant(entity)).isEqualTo(approved);
    }

    @Test
    void referenceInstantFallsBackToCreatedAtWhenApprovedAtMissing() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        ChangeRecordEntity entity = new ChangeRecordEntity();
        entity.status = ChangeStatus.APPROVED.name();
        entity.createdAt = created;
        assertThat(ChangeExpirationService.referenceInstant(entity)).isEqualTo(created);
    }
}
