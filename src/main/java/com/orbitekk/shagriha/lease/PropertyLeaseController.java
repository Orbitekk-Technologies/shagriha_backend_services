package com.orbitekk.shagriha.lease;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/properties/{propertyId}/leases")
public class PropertyLeaseController {
    private final LeaseService leases;
    public PropertyLeaseController(LeaseService leases) { this.leases = leases; }

    @GetMapping @PreAuthorize("hasRole('MANAGER')")
    List<LeaseView> list(@AuthenticationPrincipal Jwt jwt, @PathVariable long propertyId) {
        return leases.forProperty(propertyId, UUID.fromString(jwt.getSubject()));
    }
}
