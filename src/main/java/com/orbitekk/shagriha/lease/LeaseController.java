package com.orbitekk.shagriha.lease;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/leases")
public class LeaseController {
    private final LeaseService leases;
    public LeaseController(LeaseService leases) { this.leases = leases; }

    @GetMapping List<LeaseView> list(@AuthenticationPrincipal Jwt jwt) { return leases.list(subject(jwt), isManager(jwt)); }
    @GetMapping("/{id}/payments") List<LeaseService.PaymentView> payments(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return leases.payments(id, subject(jwt), isManager(jwt));
    }
    private static UUID subject(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    private static boolean isManager(Jwt jwt) { return jwt.getClaimAsStringList("roles").contains("ROLE_MANAGER"); }
}
