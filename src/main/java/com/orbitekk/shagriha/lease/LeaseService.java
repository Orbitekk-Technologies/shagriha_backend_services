package com.orbitekk.shagriha.lease;

import com.orbitekk.shagriha.common.ApiException;
import com.orbitekk.shagriha.property.PropertyReader;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class LeaseService {
    private final JdbcClient jdbc;
    private final PropertyReader properties;
    public LeaseService(JdbcClient jdbc, PropertyReader properties) { this.jdbc = jdbc; this.properties = properties; }

    public List<LeaseView> list(UUID userId, boolean manager) {
        String condition = manager ? "p.manager_user_id=:userId" : "le.tenant_user_id=:userId";
        return jdbc.sql("SELECT le.* FROM leases le JOIN properties p ON p.id=le.property_id WHERE " + condition + " ORDER BY le.start_date DESC")
                .param("userId", userId).query((rs, n) -> view(rs.getLong("id"), rs.getLong("property_id"),
                        rs.getObject("tenant_user_id", UUID.class), rs.getDate("start_date").toLocalDate(),
                        rs.getDate("end_date").toLocalDate(), rs.getBigDecimal("rent"), rs.getBigDecimal("deposit"))).list();
    }

    public Optional<LeaseView> forApplication(long applicationId) {
        return jdbc.sql("SELECT * FROM leases WHERE application_id=:id").param("id", applicationId)
                .query((rs, n) -> view(rs.getLong("id"), rs.getLong("property_id"),
                        rs.getObject("tenant_user_id", UUID.class), rs.getDate("start_date").toLocalDate(),
                        rs.getDate("end_date").toLocalDate(), rs.getBigDecimal("rent"), rs.getBigDecimal("deposit"))).optional();
    }

    public List<LeaseView> forProperty(long propertyId, UUID managerId) {
        boolean owned = jdbc.sql("SELECT EXISTS(SELECT 1 FROM properties WHERE id=:propertyId AND manager_user_id=:managerId)")
                .param("propertyId", propertyId).param("managerId", managerId).query(Boolean.class).single();
        if (!owned) throw ApiException.notFound("Property not found");
        return jdbc.sql("SELECT * FROM leases WHERE property_id=:propertyId ORDER BY start_date DESC")
                .param("propertyId", propertyId).query((rs, n) -> view(rs.getLong("id"), rs.getLong("property_id"),
                        rs.getObject("tenant_user_id", UUID.class), rs.getDate("start_date").toLocalDate(),
                        rs.getDate("end_date").toLocalDate(), rs.getBigDecimal("rent"), rs.getBigDecimal("deposit"))).list();
    }

    public List<PaymentView> payments(long leaseId, UUID userId, boolean manager) {
        boolean allowed = jdbc.sql("SELECT EXISTS(SELECT 1 FROM leases le JOIN properties p ON p.id=le.property_id WHERE le.id=:leaseId AND " +
                        (manager ? "p.manager_user_id=:userId" : "le.tenant_user_id=:userId") + ")")
                .param("leaseId", leaseId).param("userId", userId).query(Boolean.class).single();
        if (!allowed) throw ApiException.notFound("Lease not found");
        return jdbc.sql("SELECT * FROM payments WHERE lease_id=:id ORDER BY due_date DESC").param("id", leaseId)
                .query((rs, n) -> new PaymentView(rs.getLong("id"), rs.getBigDecimal("amount_due"),
                        rs.getBigDecimal("amount_paid"), rs.getDate("due_date").toLocalDate(),
                        rs.getTimestamp("payment_date") == null ? null : rs.getTimestamp("payment_date").toInstant(),
                        display(rs.getString("status")), rs.getLong("lease_id"))).list();
    }

    private LeaseView view(long id, long propertyId, UUID tenantId, LocalDate start, LocalDate end,
                           BigDecimal rent, BigDecimal deposit) {
        var tenant = jdbc.sql("SELECT tp.id,tp.user_id,tp.name,u.email,tp.phone_number,tp.image_url FROM tenant_profiles tp JOIN users u ON u.id=tp.user_id WHERE tp.user_id=:id")
                .param("id", tenantId).query((rs, n) -> new LeaseView.TenantSummary(rs.getLong("id"),
                        rs.getObject("user_id", UUID.class), rs.getString("name"), rs.getString("email"),
                        rs.getString("phone_number"), rs.getString("image_url"))).single();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String status = today.isBefore(start) ? "Upcoming" : today.isAfter(end) ? "Expired" : "Active";
        return new LeaseView(id, start, end, rent, deposit, propertyId, tenantId, properties.get(propertyId), tenant, status);
    }

    private static String display(String value) {
        String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1).replace(" ", "");
    }

    public record PaymentView(long id, BigDecimal amountDue, BigDecimal amountPaid, LocalDate dueDate,
                              Instant paymentDate, String paymentStatus, long leaseId) {}
}
