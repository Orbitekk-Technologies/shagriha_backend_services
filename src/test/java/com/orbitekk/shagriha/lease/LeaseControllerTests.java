package com.orbitekk.shagriha.lease;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaseControllerTests {
    @Test
    void userCanChoosePersonalOrManagedLeaseView() {
        Jwt user = jwt("ROLE_USER");

        assertFalse(LeaseController.managerView(user, null));
        assertTrue(LeaseController.managerView(user, "manager"));
        assertFalse(LeaseController.managerView(user, "tenant"));
    }

    private static Jwt jwt(String role) {
        return Jwt.withTokenValue("token").header("alg", "none").subject("subject")
                .claim("roles", List.of(role)).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60)).build();
    }
}
