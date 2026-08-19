package com.orbitekk.shagriha.auth;

import com.orbitekk.shagriha.user.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final UserRepository users; private final PasswordEncoder passwords;
    private final AuthenticationManager authentication; private final TokenService tokens; private final JdbcClient jdbc;
    public AuthController(UserRepository users, PasswordEncoder passwords, AuthenticationManager authentication,
                          TokenService tokens, JdbcClient jdbc) {
        this.users = users; this.passwords = passwords; this.authentication = authentication; this.tokens = tokens; this.jdbc = jdbc;
    }
    @PostMapping("/signup") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public AuthResponse signup(@Valid @RequestBody SignupRequest request) {
        if (!request.password().equals(request.confirmPassword())) throw new IllegalArgumentException("Passwords do not match");
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email) || users.existsByUsernameIgnoreCase(email)) {
            throw new IllegalArgumentException("Email is already registered");
        }
        AppUser user = users.saveAndFlush(new AppUser(UUID.randomUUID(), email, email,
                passwords.encode(request.password()), UserRole.MANAGER));
        jdbc.sql("INSERT INTO tenant_profiles (user_id, name) VALUES (:userId, :name)")
                .param("userId", user.getId()).param("name", email).update();
        jdbc.sql("INSERT INTO manager_profiles (user_id, name) VALUES (:userId, :name)")
                .param("userId", user.getId()).param("name", email).update();
        return response(user);
    }
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        authentication.authenticate(new UsernamePasswordAuthenticationToken(request.login(), request.password()));
        AppUser user = users.findByUsernameIgnoreCaseOrEmailIgnoreCase(request.login(), request.login()).orElseThrow();
        return response(user);
    }
    @GetMapping("/me")
    public Map<String, Object> me(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt) {
        UUID id = UUID.fromString(jwt.getSubject());
        AppUser user = users.findById(id).orElseThrow();
        String table = "manager_profiles";
        Map<String, Object> profile = jdbc.sql("SELECT p.id, p.user_id AS \"userId\", p.name, u.email, p.phone_number AS \"phoneNumber\", p.image_url AS image FROM " + table + " p JOIN users u ON u.id=p.user_id WHERE p.user_id=:id")
                .param("id", id).query().singleRow();
        return Map.of("authInfo", Map.of("userId", id, "username", user.getUsername()),
                "userInfo", profile, "userRole", user.getRole().name().toLowerCase());
    }
    @PostMapping("/enable-manager") @Transactional
    public AuthResponse enableManager(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestBody EnableManagerRequest request) {
        if (!request.authorizedToList()) {
            throw new IllegalArgumentException("Authorization to list or manage properties is required");
        }

        UUID id = UUID.fromString(jwt.getSubject());
        AppUser user = users.findById(id).orElseThrow();
        Integer managerProfiles = jdbc.sql("SELECT count(*) FROM manager_profiles WHERE user_id=:id")
                .param("id", id).query(Integer.class).single();
        if (managerProfiles == 0) {
            String name = jdbc.sql("SELECT name FROM tenant_profiles WHERE user_id=:id")
                    .param("id", id).query(String.class).optional().orElse(user.getUsername());
            jdbc.sql("INSERT INTO manager_profiles (user_id, name) VALUES (:id, :name)")
                    .param("id", id).param("name", name).update();
        }
        user.enableManagerRole();
        users.save(user);
        return response(user);
    }
    private AuthResponse response(AppUser user) { return new AuthResponse(tokens.issue(user), user.getId(), user.getUsername(), user.getRole()); }
    public record SignupRequest(@Email @NotBlank @Size(max=80) String email,
            @Size(min=10,max=100) String password, @NotBlank String confirmPassword) {}
    public record LoginRequest(@NotBlank String login, @NotBlank String password) {}
    public record EnableManagerRequest(@AssertTrue boolean authorizedToList) {}
    public record AuthResponse(TokenService.AccessToken token, UUID userId, String username, UserRole role) {}
}
