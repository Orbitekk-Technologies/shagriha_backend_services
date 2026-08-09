package com.orbitekk.shagriha.tenant;

import com.orbitekk.shagriha.common.ApiException;
import com.orbitekk.shagriha.property.PropertyView;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tenants")
public class TenantController {
    private final TenantService tenants;
    public TenantController(TenantService tenants) { this.tenants = tenants; }

    @GetMapping("/me") TenantService.TenantView me(@AuthenticationPrincipal Jwt jwt) { return tenants.get(subject(jwt)); }
    @PatchMapping("/me") TenantService.TenantView updateMe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateTenantRequest request) { return tenants.update(subject(jwt), request); }
    @PutMapping("/me") TenantService.TenantView replaceMe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateTenantRequest request) { return tenants.update(subject(jwt), request); }
    @GetMapping("/me/favorites") List<PropertyView> favorites(@AuthenticationPrincipal Jwt jwt) { return tenants.favorites(subject(jwt)); }
    @PostMapping("/me/favorites/{propertyId}") TenantService.TenantView addFavorite(@AuthenticationPrincipal Jwt jwt, @PathVariable long propertyId) { return tenants.addFavorite(subject(jwt), propertyId); }
    @DeleteMapping("/me/favorites/{propertyId}") TenantService.TenantView removeFavorite(@AuthenticationPrincipal Jwt jwt, @PathVariable long propertyId) { return tenants.removeFavorite(subject(jwt), propertyId); }
    @GetMapping("/me/residences") List<PropertyView> residences(@AuthenticationPrincipal Jwt jwt) { return tenants.residences(subject(jwt)); }

    @GetMapping("/{userId}") TenantService.TenantView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) { authorize(jwt, userId); return tenants.get(userId); }
    @PutMapping("/{userId}") TenantService.TenantView update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId, @Valid @RequestBody UpdateTenantRequest request) { authorize(jwt, userId); return tenants.update(userId, request); }
    @GetMapping("/{userId}/current-residences") List<PropertyView> residences(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) { authorize(jwt, userId); return tenants.residences(userId); }
    @PostMapping("/{userId}/favorites/{propertyId}") TenantService.TenantView addFavorite(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId, @PathVariable long propertyId) { authorize(jwt, userId); return tenants.addFavorite(userId, propertyId); }
    @DeleteMapping("/{userId}/favorites/{propertyId}") TenantService.TenantView removeFavorite(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId, @PathVariable long propertyId) { authorize(jwt, userId); return tenants.removeFavorite(userId, propertyId); }

    private static UUID subject(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    static void authorize(Jwt jwt, UUID requested) { if (!subject(jwt).equals(requested)) throw ApiException.forbidden("You cannot access another tenant's account"); }
}
