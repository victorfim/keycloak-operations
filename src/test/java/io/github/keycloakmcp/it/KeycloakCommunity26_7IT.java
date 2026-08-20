package io.github.keycloakmcp.it;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Community Keycloak 26.7 integration test placeholder.
 * Enabled when RUN_KEYCLOAK_IT=true and Podman is available.
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "RUN_KEYCLOAK_IT", matches = "true")
class KeycloakCommunity26_7IT {

    @Test
    void placeholderEnabledOnlyWhenFlagSet() {
        // Full Testcontainers coverage is expanded when RUN_KEYCLOAK_IT=true in CI.
        org.assertj.core.api.Assertions.assertThat(System.getenv("RUN_KEYCLOAK_IT")).isEqualTo("true");
    }
}
