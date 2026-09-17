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
    private final LeaseDocumentService documents;
    public LeaseController(LeaseService leases, LeaseDocumentService documents) { this.leases = leases; this.documents = documents; }

    @GetMapping List<LeaseView> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String view) {
        return leases.list(subject(jwt), managerView(jwt, view));
    }
    @GetMapping("/{id}/payments") List<LeaseService.PaymentView> payments(@AuthenticationPrincipal Jwt jwt,
            @PathVariable long id, @RequestParam(required = false) String view) {
        return leases.payments(id, subject(jwt), managerView(jwt, view));
    }
    @GetMapping("/{id}/document") org.springframework.http.ResponseEntity<org.springframework.core.io.ByteArrayResource> document(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return LeaseDocumentController.response(documents.downloadForLease(id, subject(jwt)));
    }
    private static UUID subject(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    static boolean managerView(Jwt jwt, String view) {
        return "manager".equalsIgnoreCase(view);
    }
}
