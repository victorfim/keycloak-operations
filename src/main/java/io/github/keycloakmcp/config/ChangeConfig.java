package io.github.keycloakmcp.config;

import java.time.Duration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Controlled change lifecycle settings (milestone 0.8).
 */
@ConfigMapping(prefix = "change")
public interface ChangeConfig {

    Expiration expiration();

    interface Expiration {
        /** When false, TTL enforcement and the expiration scheduler are disabled. */
        @WithDefault("true")
        boolean enabled();

        /**
         * Maximum age for {@code WAITING_APPROVAL}/{@code PLANNED} (from {@code createdAt})
         * and {@code APPROVED} (from {@code approvedAt}, falling back to {@code createdAt}).
         */
        @WithName("after")
        @WithDefault("7d")
        Duration expireAfter();
    }
}
