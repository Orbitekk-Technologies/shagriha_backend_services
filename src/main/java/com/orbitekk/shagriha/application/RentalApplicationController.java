package com.orbitekk.shagriha.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/applications")
public class RentalApplicationController {
    private final RentalApplicationService applications;
    public RentalApplicationController(RentalApplicationService applications) { this.applications = applications; }

    @GetMapping List<RentalApplicationService.ApplicationView> list(@AuthenticationPrincipal Jwt jwt) {
        return applications.list(subject(jwt), isManager(jwt));
    }
    @PostMapping RentalApplicationService.ApplicationView create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RentalApplicationService.CreateRequest request) {
        if (isManager(jwt)) throw com.orbitekk.shagriha.common.ApiException.forbidden("Only tenants can apply for properties");
        return applications.create(subject(jwt), request);
    }
    @PutMapping("/{id}/status") RentalApplicationService.ApplicationView status(@AuthenticationPrincipal Jwt jwt,
            @PathVariable long id, @Valid @RequestBody StatusRequest request) {
        if (!isManager(jwt)) throw com.orbitekk.shagriha.common.ApiException.forbidden("Only managers can update applications");
        return applications.updateStatus(subject(jwt), id, request.status());
    }
    private static UUID subject(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    private static boolean isManager(Jwt jwt) { return jwt.getClaimAsStringList("roles").contains("ROLE_MANAGER"); }
    public record StatusRequest(@NotBlank String status) {}
}
