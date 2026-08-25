package com.orbitekk.shagriha.property;

import com.orbitekk.shagriha.common.ApiException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.Map;

@Component
public class PropertyReader {
    private static final String SELECT = """
        SELECT p.*, l.address_line1, l.address_line2, l.city, l.state_name, l.state_code,
               l.country_name, l.country_code, l.postal_code, l.formatted_address, l.mapbox_feature_id,
               ST_X(l.coordinates::geometry) longitude, ST_Y(l.coordinates::geometry) latitude,
               up.id manager_profile_id, up.name manager_name, up.phone_number manager_phone,
               up.image_url manager_image, u.email manager_email
        FROM properties p
        JOIN locations l ON l.id=p.location_id
        JOIN users u ON u.id=p.manager_user_id
        JOIN user_profiles up ON up.user_id=u.id
        """;
    private final JdbcClient jdbc;

    public PropertyReader(JdbcClient jdbc) { this.jdbc = jdbc; }

    public PropertyView get(long id) {
        return jdbc.sql(SELECT + " WHERE p.id=:id").param("id", id)
                .query(this::map).optional().orElseThrow(() -> ApiException.notFound("Property not found"));
    }

    public List<PropertyView> list() {
        return jdbc.sql(SELECT + " WHERE p.status='PUBLISHED' ORDER BY p.posted_at DESC")
                .query(this::map).list();
    }

    public List<PropertyView> byIds(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        List<PropertyView> rows = jdbc.sql(SELECT + " WHERE p.id IN (:ids)")
                .param("ids", ids).query(this::map).list();
        Map<Long, PropertyView> byId = rows.stream().collect(java.util.stream.Collectors.toMap(PropertyView::id, p -> p));
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    public List<PropertyView> managedBy(UUID managerId) {
        return jdbc.sql(SELECT + " WHERE p.manager_user_id=:managerId ORDER BY p.posted_at DESC")
                .param("managerId", managerId).query(this::map).list();
    }

    public boolean isManagedBy(long propertyId, UUID managerId) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM properties WHERE id=:propertyId AND manager_user_id=:managerId)")
                .param("propertyId", propertyId).param("managerId", managerId)
                .query(Boolean.class).single();
    }

    public List<PropertyView> favorites(java.util.UUID tenantId) {
        return jdbc.sql(SELECT + " JOIN tenant_favorites tf ON tf.property_id=p.id WHERE tf.tenant_user_id=:tenantId ORDER BY tf.created_at DESC")
                .param("tenantId", tenantId).query(this::map).list();
    }

    public List<PropertyView> currentResidences(java.util.UUID tenantId) {
        return jdbc.sql(SELECT.replace("SELECT p.*", "SELECT DISTINCT p.*") + " JOIN leases le ON le.property_id=p.id WHERE le.tenant_user_id=:tenantId AND CURRENT_DATE BETWEEN le.start_date AND le.end_date ORDER BY p.posted_at DESC")
                .param("tenantId", tenantId).query(this::map).list();
    }

    private PropertyView map(ResultSet rs, int rowNum) throws SQLException {
        long id = rs.getLong("id");
        List<String> photos = jdbc.sql("SELECT url FROM property_photos WHERE property_id=:id ORDER BY display_order")
                .param("id", id).query(String.class).list();
        List<String> amenities = jdbc.sql("SELECT amenity FROM property_amenities WHERE property_id=:id ORDER BY amenity")
                .param("id", id).query(String.class).list();
        List<String> highlights = jdbc.sql("SELECT highlight FROM property_highlights WHERE property_id=:id ORDER BY highlight")
                .param("id", id).query(String.class).list();
        var location = new PropertyView.LocationView(rs.getLong("location_id"), rs.getString("address_line1"),
                rs.getString("address_line1"), rs.getString("address_line2"), rs.getString("city"),
                rs.getString("state_name"), rs.getString("state_name"), rs.getString("state_code"),
                rs.getString("country_name"), rs.getString("country_name"), rs.getString("country_code"),
                rs.getString("postal_code"), rs.getString("formatted_address"), rs.getString("mapbox_feature_id"),
                new PropertyView.Coordinates(rs.getDouble("longitude"), rs.getDouble("latitude")));
        var manager = new PropertyView.ManagerView(rs.getLong("manager_profile_id"),
                rs.getObject("manager_user_id", java.util.UUID.class), rs.getString("manager_name"),
                rs.getString("manager_email"), rs.getString("manager_phone"), rs.getString("manager_image"));
        return new PropertyView(id, rs.getString("name"), rs.getString("description"),
                rs.getBigDecimal("price_per_month"), rs.getBigDecimal("security_deposit"),
                rs.getBigDecimal("application_fee"), photos, amenities, highlights,
                rs.getBoolean("pets_allowed"), rs.getBoolean("parking_included"),
                rs.getObject("pet_count", Integer.class), rs.getBigDecimal("pet_fee"), rs.getBigDecimal("parking_fee"),
                rs.getBoolean("smoking_included"), rs.getString("stay_type"), rs.getString("bath_type"),
                rs.getString("gender_preference"), rs.getInt("beds"),
                rs.getInt("baths"), rs.getInt("square_feet"), rs.getString("property_type"),
                rs.getTimestamp("posted_at").toInstant(), 0, 0, rs.getLong("location_id"),
                rs.getObject("manager_user_id", java.util.UUID.class), location, manager);
    }
}
