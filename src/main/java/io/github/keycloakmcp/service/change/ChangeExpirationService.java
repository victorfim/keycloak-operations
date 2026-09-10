package io.github.keycloakmcp.service.change;

import java.time.Duration;
import java.time.Instant;
import io.github.keycloakmcp.config.ChangeConfig;
import io.github.keycloakmcp.domain.change.ChangeStatus;
import io.github.keycloakmcp.persistence.entity.ChangeRecordEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * TTL enforcement for change records in {@code WAITING_APPROVAL}, {@code PLANNED}, or {@code APPROVED}.
 */
@ApplicationScoped
public class ChangeExpirationService {

    private final ChangeConfig changeConfig;

    @Inject
    public ChangeExpirationService(ChangeConfig changeConfig) {
        this.changeConfig = changeConfig;
    }

    public boolean isEnabled() {
        return changeConfig.expiration().enabled();
    }

    public Duration expireAfter() {
        return changeConfig.expiration().expireAfter();
    }

    public static boolean isExpirable(ChangeStatus status) {
        return status == ChangeStatus.WAITING_APPROVAL
                || status == ChangeStatus.PLANNED
                || status == ChangeStatus.APPROVED;
    }

    public static Instant referenceInstant(ChangeRecordEntity entity) {
        if (ChangeStatus.APPROVED.name().equals(entity.status) && entity.approvedAt != null) {
            return entity.approvedAt;
        }
        return entity.createdAt;
    }

    public boolean isPastDeadline(ChangeRecordEntity entity, Instant now) {
        if (entity.createdAt == null) {
            return false;
        }
        Instant reference = referenceInstant(entity);
        return reference.plus(expireAfter()).isBefore(now) || reference.plus(expireAfter()).equals(now);
    }

    /**
     * Transitions the entity to {@link ChangeStatus#EXPIRED} when its TTL has elapsed.
     *
     * @return {@code true} when the record was transitioned to EXPIRED
     */
    @Transactional
    public boolean expireIfNeeded(ChangeRecordEntity entity, String reason) {
        if (!isEnabled()) {
            return false;
        }
        ChangeStatus status = ChangeStatus.valueOf(entity.status);
        if (status == ChangeStatus.EXPIRED || !isExpirable(status)) {
            return false;
        }
        if (!isPastDeadline(entity, Instant.now())) {
            return false;
        }
        entity.status = ChangeStatus.EXPIRED.name();
        entity.resultMessage = reason == null || reason.isBlank()
                ? "Expired: approval window elapsed (" + expireAfter() + ")"
                : reason;
        entity.updatedAt = Instant.now();
        return true;
    }

    @Transactional
    public boolean forceExpire(ChangeRecordEntity entity, String reason) {
        ChangeStatus status = ChangeStatus.valueOf(entity.status);
        if (status == ChangeStatus.EXPIRED) {
            return false;
        }
        if (!isExpirable(status)) {
            return false;
        }
        entity.status = ChangeStatus.EXPIRED.name();
        entity.resultMessage = reason == null || reason.isBlank() ? "Expired by operator" : reason;
        entity.updatedAt = Instant.now();
        return true;
    }
}
