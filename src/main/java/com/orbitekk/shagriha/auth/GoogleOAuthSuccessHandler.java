package com.orbitekk.shagriha.auth;

import com.orbitekk.shagriha.user.AppUser;
import com.orbitekk.shagriha.user.AuthProvider;
import com.orbitekk.shagriha.user.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.UUID;

@Component
public class GoogleOAuthSuccessHandler implements AuthenticationSuccessHandler {
    private final UserRepository users;
    private final TokenService tokens;
    private final JdbcClient jdbc;
    private final String frontendUrl;

    public GoogleOAuthSuccessHandler(UserRepository users, TokenService tokens, JdbcClient jdbc,
            @Value("${app.frontend-url}") String frontendUrl) {
        this.users = users; this.tokens = tokens; this.jdbc = jdbc; this.frontendUrl = frontendUrl;
    }

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        OAuth2User principal = (OAuth2User) authentication.getPrincipal();
        String subject = principal.getAttribute("sub");
        String email = principal.getAttribute("email");
        String name = principal.getAttribute("name");
        if (subject == null || email == null) throw new ServletException("Google account is missing required identity claims");

        AppUser user = users.findByProviderAndProviderSubject(AuthProvider.GOOGLE, subject)
                .orElseGet(() -> createUser(subject, email, name));
        TokenService.AccessToken token = tokens.issue(user);
        String fragment = UriComponentsBuilder.newInstance()
                .queryParam("access_token", token.accessToken())
                .queryParam("user_id", user.getId())
                .queryParam("username", user.getUsername()).build().encode().toUriString().substring(1);
        response.sendRedirect(frontendUrl + "/oauth/callback#" + fragment);
    }

    private AppUser createUser(String subject, String email, String displayName) {
        AppUser existing = users.findByUsernameIgnoreCaseOrEmailIgnoreCase(email, email).orElse(null);
        if (existing != null) return existing;
        String base = email.substring(0, email.indexOf('@')).replaceAll("[^A-Za-z0-9._-]", "");
        if (base.isBlank()) base = "google-user";
        String username = base;
        for (int suffix = 1; users.existsByUsernameIgnoreCase(username); suffix++) username = base + suffix;
        AppUser user = users.saveAndFlush(AppUser.google(UUID.randomUUID(), username, email.toLowerCase(), subject));
        String profileName = displayName == null || displayName.isBlank() ? username : displayName;
        jdbc.sql("INSERT INTO tenant_profiles (user_id, name) VALUES (:id, :name)")
                .param("id", user.getId()).param("name", profileName).update();
        jdbc.sql("INSERT INTO manager_profiles (user_id, name) VALUES (:id, :name)")
                .param("id", user.getId()).param("name", profileName).update();
        return user;
    }
}
