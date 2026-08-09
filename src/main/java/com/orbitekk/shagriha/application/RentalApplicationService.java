package com.orbitekk.shagriha.application;

import com.orbitekk.shagriha.common.ApiException;
import com.orbitekk.shagriha.lease.LeaseService;
import com.orbitekk.shagriha.lease.LeaseView;
import com.orbitekk.shagriha.property.PropertyReader;
import com.orbitekk.shagriha.property.PropertyView;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class RentalApplicationService {
    private final JdbcClient jdbc;
    private final PropertyReader properties;
    private final LeaseService leases;
    public RentalApplicationService(JdbcClient jdbc, PropertyReader properties, LeaseService leases) {
        this.jdbc = jdbc; this.properties = properties; this.leases = leases;
    }

    public List<ApplicationView> list(UUID userId, boolean manager) {
        String condition = manager ? "p.manager_user_id=:userId" : "a.tenant_user_id=:userId";
        return jdbc.sql("SELECT a.* FROM applications a JOIN properties p ON p.id=a.property_id WHERE " + condition + " ORDER BY a.applied_at DESC")
                .param("userId", userId).query((rs, n) -> view(rs.getLong("id"), rs.getLong("property_id"),
                        rs.getObject("tenant_user_id", UUID.class), rs.getString("name"), rs.getString("email"),
                        rs.getString("phone_number"), rs.getString("message"), rs.getString("status"),
                        rs.getTimestamp("applied_at").toInstant())).list();
    }

    @Transactional
    public ApplicationView create(UUID tenantId, CreateRequest request) {
        PropertyView property = properties.get(request.propertyId());
        if (!jdbc.sql("SELECT status='PUBLISHED' FROM properties WHERE id=:id").param("id", property.id()).query(Boolean.class).single())
            throw ApiException.conflict("Property is not accepting applications");
        boolean exists = jdbc.sql("SELECT EXISTS(SELECT 1 FROM applications WHERE property_id=:propertyId AND tenant_user_id=:tenantId)")
                .param("propertyId", request.propertyId()).param("tenantId", tenantId).query(Boolean.class).single();
        if (exists) throw ApiException.conflict("You have already applied for this property");
        long id = jdbc.sql("INSERT INTO applications(property_id,tenant_user_id,name,email,phone_number,message) VALUES(:propertyId,:tenantId,:name,:email,:phone,:message) RETURNING id")
                .param("propertyId", request.propertyId()).param("tenantId", tenantId).param("name", request.name().trim())
                .param("email", request.email().trim().toLowerCase()).param("phone", request.phoneNumber().trim())
                .param("message", request.message()).query(Long.class).single();
        return getRaw(id);
    }

    @Transactional
    public ApplicationView updateStatus(UUID managerId, long id, String requestedStatus) {
        String status = normalizeStatus(requestedStatus);
        var owners = jdbc.sql("SELECT p.manager_user_id,a.status,a.property_id,a.tenant_user_id,p.price_per_month,p.security_deposit FROM applications a JOIN properties p ON p.id=a.property_id WHERE a.id=:id")
                .param("id", id).query().listOfRows();
        if (owners.isEmpty()) throw ApiException.notFound("Application not found");
        var owner = owners.getFirst();
        if (!managerId.equals(owner.get("manager_user_id"))) throw ApiException.forbidden("You cannot manage this application");
        if ("APPROVED".equals(status) && !"APPROVED".equals(owner.get("status"))) {
            LocalDate start = LocalDate.now(ZoneOffset.UTC);
            jdbc.sql("INSERT INTO leases(property_id,tenant_user_id,application_id,start_date,end_date,rent,deposit) VALUES(:propertyId,:tenantId,:applicationId,:start,:end,:rent,:deposit)")
                    .param("propertyId", owner.get("property_id")).param("tenantId", owner.get("tenant_user_id"))
                    .param("applicationId", id).param("start", start).param("end", start.plusYears(1))
                    .param("rent", owner.get("price_per_month")).param("deposit", owner.get("security_deposit")).update();
        }
        if (!"APPROVED".equals(status) && "APPROVED".equals(owner.get("status")))
            throw ApiException.conflict("An approved application with a lease cannot be moved backwards");
        jdbc.sql("UPDATE applications SET status=:status WHERE id=:id").param("status", status).param("id", id).update();
        return getRaw(id);
    }

    private ApplicationView getRaw(long id) {
        return jdbc.sql("SELECT * FROM applications WHERE id=:id").param("id", id)
                .query((rs, n) -> view(rs.getLong("id"), rs.getLong("property_id"),
                        rs.getObject("tenant_user_id", UUID.class), rs.getString("name"), rs.getString("email"),
                        rs.getString("phone_number"), rs.getString("message"), rs.getString("status"),
                        rs.getTimestamp("applied_at").toInstant())).single();
    }

    private ApplicationView view(long id, long propertyId, UUID tenantId, String name, String email,
                                 String phone, String message, String status, Instant appliedAt) {
        LeaseView.TenantSummary tenant = jdbc.sql("SELECT tp.id,tp.user_id,tp.name,u.email,tp.phone_number,tp.image_url FROM tenant_profiles tp JOIN users u ON u.id=tp.user_id WHERE tp.user_id=:id")
                .param("id", tenantId).query((rs, n) -> new LeaseView.TenantSummary(rs.getLong("id"),
                        rs.getObject("user_id", UUID.class), rs.getString("name"), rs.getString("email"),
                        rs.getString("phone_number"), rs.getString("image_url"))).single();
        LeaseView lease = leases.forApplication(id).orElse(null);
        return new ApplicationView(id, appliedAt, display(status), propertyId, tenantId, name, email, phone,
                message, lease == null ? null : lease.id(), properties.get(propertyId), tenant, lease);
    }

    private static String display(String value) { String lower=value.toLowerCase(Locale.ROOT); return Character.toUpperCase(lower.charAt(0))+lower.substring(1); }
    static String normalizeStatus(String value) {
        String status = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PENDING", "APPROVED", "DENIED").contains(status)) throw new IllegalArgumentException("Invalid application status");
        return status;
    }

    public record CreateRequest(@NotNull @Positive Long propertyId, @NotBlank @Size(max=160) String name,
                                @NotBlank @Email @Size(max=255) String email,
                                @NotBlank @Size(max=40) String phoneNumber, @Size(max=4000) String message) {}
    public record ApplicationView(long id, Instant applicationDate, String status, long propertyId,
                                  UUID tenantUserId, String name, String email, String phoneNumber,
                                  String message, Long leaseId, PropertyView property,
                                  LeaseView.TenantSummary tenant, LeaseView lease) {}
}
