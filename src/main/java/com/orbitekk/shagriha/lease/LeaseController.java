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

    @GetMapping List<LeaseView> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String view) {
        return leases.list(subject(jwt), managerView(jwt, view));
    }
    @GetMapping("/{id}/payments") List<LeaseService.PaymentView> payments(@AuthenticationPrincipal Jwt jwt,
            @PathVariable long id, @RequestParam(required = false) String view) {
        return leases.payments(id, subject(jwt), managerView(jwt, view));
    }
    private static UUID subject(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    private static boolean isManager(Jwt jwt) { return jwt.getClaimAsStringList("roles").contains("ROLE_MANAGER"); }
    static boolean managerView(Jwt jwt, String view) {
        if ("manager".equalsIgnoreCase(view) && !isManager(jwt)) {
            throw com.orbitekk.shagriha.common.ApiException.forbidden("Only managers can view managed leases");
        }
        return isManager(jwt) && !"tenant".equalsIgnoreCase(view);
    }
}
