package com.orbitekk.shagriha.lease;

import com.orbitekk.shagriha.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaseControllerTests {
    @Test
    void managerCanChooseRenterOrManagedLeaseView() {
        Jwt manager = jwt("ROLE_MANAGER");

        assertTrue(LeaseController.managerView(manager, null));
        assertTrue(LeaseController.managerView(manager, "manager"));
        assertFalse(LeaseController.managerView(manager, "tenant"));
    }

    @Test
    void tenantCannotRequestManagedLeaseView() {
        assertThrows(ApiException.class,
                () -> LeaseController.managerView(jwt("ROLE_TENANT"), "manager"));
    }

    private static Jwt jwt(String role) {
        return Jwt.withTokenValue("token").header("alg", "none").subject("subject")
                .claim("roles", List.of(role)).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60)).build();
    }
}
