package com.orbitekk.shagriha.manager;

import com.orbitekk.shagriha.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManagerControllerTests {
    @Test
    void legacyUserIdRouteRejectsAnotherManager() {
        UUID authenticated = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none")
                .subject(authenticated.toString()).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60)).build();

        ApiException error = assertThrows(ApiException.class,
                () -> ManagerController.authorize(jwt, UUID.randomUUID()));

        assertEquals(HttpStatus.FORBIDDEN, error.status());
    }
}
