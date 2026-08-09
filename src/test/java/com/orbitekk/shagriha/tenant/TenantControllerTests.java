package com.orbitekk.shagriha.tenant;

import com.orbitekk.shagriha.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TenantControllerTests {
    @Test
    void legacyUserIdRouteRejectsAnotherTenant() {
        UUID authenticated = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none")
                .subject(authenticated.toString()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        ApiException error = assertThrows(ApiException.class,
                () -> TenantController.authorize(jwt, UUID.randomUUID()));

        assertEquals(HttpStatus.FORBIDDEN, error.status());
    }
}
