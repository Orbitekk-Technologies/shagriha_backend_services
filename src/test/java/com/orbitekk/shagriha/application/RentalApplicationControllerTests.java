package com.orbitekk.shagriha.application;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RentalApplicationControllerTests {
    @Test
    void userCanChoosePersonalOrListingApplicationView() {
        Jwt user = jwt("ROLE_USER");

        assertFalse(RentalApplicationController.managerView(user, null));
        assertTrue(RentalApplicationController.managerView(user, "manager"));
        assertFalse(RentalApplicationController.managerView(user, "tenant"));
    }

    private static Jwt jwt(String role) {
        return Jwt.withTokenValue("token").header("alg", "none").subject("subject")
                .claim("roles", List.of(role)).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60)).build();
    }
}
