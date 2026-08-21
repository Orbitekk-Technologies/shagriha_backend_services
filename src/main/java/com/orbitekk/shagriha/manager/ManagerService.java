package com.orbitekk.shagriha.manager;

import com.orbitekk.shagriha.common.ApiException;
import com.orbitekk.shagriha.property.PropertyReader;
import com.orbitekk.shagriha.property.PropertyView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ManagerService {
    private final JdbcClient jdbc;
    private final PropertyReader properties;

    public ManagerService(JdbcClient jdbc, PropertyReader properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    public ManagerView get(UUID userId) {
        ManagerRow row = row(userId);
        return new ManagerView(row.id(), row.userId(), row.name(), row.email(), row.phoneNumber(), row.image());
    }

    @Transactional
    public ManagerView update(UUID userId, UpdateManagerRequest request) {
        row(userId);
        if (request.email() != null) {
            int changed = jdbc.sql("UPDATE users SET email=:email, username=:email, updated_at=now() WHERE id=:id AND NOT EXISTS (SELECT 1 FROM users WHERE (lower(email)=lower(:email) OR lower(username)=lower(:email)) AND id<>:id)")
                    .param("email", request.email().trim().toLowerCase()).param("id", userId).update();
            if (changed == 0) throw ApiException.conflict("Email is already registered");
        }
        jdbc.sql("UPDATE user_profiles SET name=COALESCE(:name,name), phone_number=COALESCE(:phone,phone_number), image_url=COALESCE(:image,image_url) WHERE user_id=:id")
                .param("name", trim(request.name())).param("phone", trim(request.phoneNumber()))
                .param("image", trim(request.image())).param("id", userId).update();
        return get(userId);
    }

    public List<PropertyView> properties(UUID userId) {
        row(userId);
        return properties.managedBy(userId);
    }

    private ManagerRow row(UUID userId) {
        return jdbc.sql("SELECT p.id,p.user_id,p.name,u.email,p.phone_number,p.image_url FROM user_profiles p JOIN users u ON u.id=p.user_id WHERE p.user_id=:id")
                .param("id", userId).query((rs, n) -> new ManagerRow(rs.getLong("id"),
                        rs.getObject("user_id", UUID.class), rs.getString("name"), rs.getString("email"),
                        rs.getString("phone_number"), rs.getString("image_url")))
                .optional().orElseThrow(() -> ApiException.notFound("Manager not found"));
    }

    private static String trim(String value) { return value == null ? null : value.trim(); }
    private record ManagerRow(long id, UUID userId, String name, String email, String phoneNumber, String image) {}
    public record ManagerView(long id, UUID userId, String name, String email, String phoneNumber, String image) {}
}
