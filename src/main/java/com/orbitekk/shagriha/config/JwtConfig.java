package com.orbitekk.shagriha.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.*;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

@Configuration
public class JwtConfig {
    @Bean
    RSAKey rsaKey() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate()).keyID(UUID.randomUUID().toString()).build();
    }
    @Bean JwtDecoder jwtDecoder(RSAKey key) throws Exception {
        return NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
    }
    @Bean JwtEncoder jwtEncoder(RSAKey key) {
        JWKSource<SecurityContext> source = new ImmutableJWKSet<>(new JWKSet(key));
        return new NimbusJwtEncoder(source);
    }
}
