package io.github.keycloakmcp.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.github.keycloakmcp.domain.platform.PageResult;
import io.github.keycloakmcp.persistence.entity.ChangeRecordEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ChangeRepository implements PanacheRepositoryBase<ChangeRecordEntity, String> {

    public PageResult<ChangeRecordEntity> list(
            Optional<String> targetId,
            Optional<String> status,
            int page,
            int size) {
        int p = PageResult.clampPage(page);
        int s = PageResult.clampSize(size);

        StringBuilder jpql = new StringBuilder("1 = 1");
        java.util.ArrayList<Object> params = new java.util.ArrayList<>();
        int idx = 1;
        if (targetId.isPresent() && !targetId.get().isBlank()) {
            jpql.append(" and targetId = ?").append(idx++);
            params.add(targetId.get().trim());
        }
        if (status.isPresent() && !status.get().isBlank()) {
            jpql.append(" and status = ?").append(idx);
            params.add(status.get().trim().toUpperCase());
        }

        var query = params.isEmpty()
                ? find(jpql.toString(), Sort.by("createdAt").descending())
                : find(jpql.toString(), Sort.by("createdAt").descending(), params.toArray());
        long total = query.count();
        List<ChangeRecordEntity> items = query.page(Page.of(p, s)).list();
        return new PageResult<>(items, p, s, total);
    }

    public Optional<ChangeRecordEntity> findByIdempotency(String targetId, String idempotencyKey) {
        if (targetId == null || idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return find("targetId = ?1 and idempotencyKey = ?2", targetId, idempotencyKey).firstResultOptional();
    }

    /**
     * Returns change records that may have exceeded their TTL (scheduler batch).
     * Cutoff is {@code now - expireAfter}.
     */
    public List<ChangeRecordEntity> findExpirableBefore(Instant cutoff) {
        return find(
                        "(status in ?1 and createdAt < ?2) or (status = ?3 and coalesce(approvedAt, createdAt) < ?4)",
                        List.of("WAITING_APPROVAL", "PLANNED"),
                        cutoff,
                        "APPROVED",
                        cutoff)
                .list();
    }
}
