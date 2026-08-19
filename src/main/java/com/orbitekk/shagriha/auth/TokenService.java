package com.orbitekk.shagriha.auth;

import com.orbitekk.shagriha.user.AppUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final String issuer;
    private final Duration ttl;
    public TokenService(JwtEncoder encoder, @Value("${app.jwt.issuer}") String issuer,
                        @Value("${app.jwt.access-token-ttl}") Duration ttl) {
        this.encoder = encoder; this.issuer = issuer; this.ttl = ttl;
    }
    public AccessToken issue(AppUser user) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(issuer).issuedAt(now).expiresAt(now.plus(ttl))
                .subject(user.getId().toString()).claim("username", user.getUsername())
                .claim("email", user.getEmail()).claim("roles", List.of("ROLE_MANAGER", "ROLE_TENANT")).build();
        var headers = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
        return new AccessToken(token, "Bearer", ttl.toSeconds());
    }
    public record AccessToken(String accessToken, String tokenType, long expiresIn) {}
}
