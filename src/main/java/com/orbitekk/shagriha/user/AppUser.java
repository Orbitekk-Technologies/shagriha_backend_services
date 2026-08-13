package com.orbitekk.shagriha.user;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class AppUser {
    @Id private UUID id;
    @Column(nullable = false, unique = true) private String username;
    @Column(nullable = false, unique = true) private String email;
    @Column(name = "password_hash") private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private UserRole role;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AuthProvider provider;
    @Column(name = "provider_subject") private String providerSubject;
    @Column(nullable = false) private boolean enabled = true;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false) private Instant updatedAt;

    protected AppUser() {}
    public AppUser(UUID id, String username, String email, String passwordHash, UserRole role) {
        this.id = id; this.username = username; this.email = email; this.passwordHash = passwordHash;
        this.role = role; this.provider = AuthProvider.LOCAL;
    }
    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public UserRole getRole() { return role; }
    public void enableManagerRole() { this.role = UserRole.MANAGER; }
    public AuthProvider getProvider() { return provider; }
    public boolean isEnabled() { return enabled; }
}
