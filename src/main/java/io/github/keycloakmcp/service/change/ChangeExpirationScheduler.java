package io.github.keycloakmcp.service.change;

import org.jboss.logging.Logger;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Periodically expires stale change records. Uses in-process scheduling; add distributed
 * locking before enabling in multi-replica production deployments.
 */
@ApplicationScoped
public class ChangeExpirationScheduler {

    private static final Logger LOG = Logger.getLogger(ChangeExpirationScheduler.class);

    private final ChangeManagementService changeManagementService;

    @Inject
    public ChangeExpirationScheduler(ChangeManagementService changeManagementService) {
        this.changeManagementService = changeManagementService;
    }

    @Scheduled(every = "1h", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void runScheduledExpiration() {
        int expired = changeManagementService.expireStaleChanges();
        if (expired > 0) {
            LOG.infof("Expired %d stale change record(s)", expired);
        } else {
            LOG.debug("No stale change records to expire");
        }
    }
}
