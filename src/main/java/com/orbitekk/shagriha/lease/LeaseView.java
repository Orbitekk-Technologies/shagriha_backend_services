package com.orbitekk.shagriha.lease;

import com.orbitekk.shagriha.property.PropertyView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LeaseView(long id, LocalDate startDate, LocalDate endDate, BigDecimal rent,
                        BigDecimal deposit, long propertyId, UUID tenantUserId,
                        PropertyView property, TenantSummary tenant, String status) {
    public record TenantSummary(long id, UUID userId, String name, String email,
                                String phoneNumber, String image) {}
}
