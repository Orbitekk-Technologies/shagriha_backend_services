package com.orbitekk.shagriha.tenant;

import com.orbitekk.shagriha.common.ApiException;
import com.orbitekk.shagriha.property.PropertyReader;
import com.orbitekk.shagriha.property.PropertyView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TenantService {
    private final JdbcClient jdbc;
    private final PropertyReader properties;

    public TenantService(JdbcClient jdbc, PropertyReader properties) { this.jdbc = jdbc; this.properties = properties; }

    public TenantView get(UUID userId) {
        TenantRow row = row(userId);
        return new TenantView(row.id(), row.userId(), row.name(), row.email(), row.phoneNumber(), row.image(), properties.favorites(userId));
    }

    @Transactional
    public TenantView update(UUID userId, UpdateTenantRequest request) {
        if (request.email() != null) {
            int changed = jdbc.sql("UPDATE users SET email=:email, updated_at=now() WHERE id=:id AND NOT EXISTS (SELECT 1 FROM users WHERE lower(email)=lower(:email) AND id<>:id)")
                    .param("email", request.email().trim().toLowerCase()).param("id", userId).update();
            if (changed == 0) throw ApiException.conflict("Email is already registered");
        }
        jdbc.sql("UPDATE tenant_profiles SET name=COALESCE(:name,name), phone_number=COALESCE(:phone,phone_number), image_url=COALESCE(:image,image_url) WHERE user_id=:id")
                .param("name", trim(request.name())).param("phone", trim(request.phoneNumber()))
                .param("image", trim(request.image())).param("id", userId).update();
        return get(userId);
    }

    @Transactional
    public TenantView addFavorite(UUID userId, long propertyId) {
        if (!jdbc.sql("SELECT EXISTS(SELECT 1 FROM properties WHERE id=:id)").param("id", propertyId).query(Boolean.class).single())
            throw ApiException.notFound("Property not found");
        jdbc.sql("INSERT INTO tenant_favorites(tenant_user_id,property_id) VALUES(:tenantId,:propertyId) ON CONFLICT DO NOTHING")
                .param("tenantId", userId).param("propertyId", propertyId).update();
        return get(userId);
    }

    @Transactional
    public TenantView removeFavorite(UUID userId, long propertyId) {
        jdbc.sql("DELETE FROM tenant_favorites WHERE tenant_user_id=:tenantId AND property_id=:propertyId")
                .param("tenantId", userId).param("propertyId", propertyId).update();
        return get(userId);
    }

    public List<PropertyView> favorites(UUID userId) { row(userId); return properties.favorites(userId); }
    public List<PropertyView> residences(UUID userId) { row(userId); return properties.currentResidences(userId); }

    private TenantRow row(UUID userId) {
        return jdbc.sql("SELECT tp.id,tp.user_id,tp.name,u.email,tp.phone_number,tp.image_url FROM tenant_profiles tp JOIN users u ON u.id=tp.user_id WHERE tp.user_id=:id")
                .param("id", userId).query((rs, n) -> new TenantRow(rs.getLong("id"), rs.getObject("user_id", UUID.class),
                        rs.getString("name"), rs.getString("email"), rs.getString("phone_number"), rs.getString("image_url")))
                .optional().orElseThrow(() -> ApiException.notFound("Tenant not found"));
    }

    private static String trim(String value) { return value == null ? null : value.trim(); }
    private record TenantRow(long id, UUID userId, String name, String email, String phoneNumber, String image) {}
    public record TenantView(long id, UUID userId, String name, String email, String phoneNumber, String image,
                             List<PropertyView> favorites) {}
}
