package com.orbitekk.shagriha.manager;

import com.orbitekk.shagriha.common.ApiException;
import com.orbitekk.shagriha.property.PropertyView;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/managers")
public class ManagerController {
    private final ManagerService managers;
    public ManagerController(ManagerService managers) { this.managers = managers; }

    @GetMapping("/me") ManagerService.ManagerView me(@AuthenticationPrincipal Jwt jwt) { return managers.get(subject(jwt)); }
    @PatchMapping("/me") ManagerService.ManagerView patchMe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateManagerRequest request) { return managers.update(subject(jwt), request); }
    @PutMapping("/me") ManagerService.ManagerView updateMe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateManagerRequest request) { return managers.update(subject(jwt), request); }
    @GetMapping("/me/properties") List<PropertyView> myProperties(@AuthenticationPrincipal Jwt jwt) { return managers.properties(subject(jwt)); }

    @GetMapping("/{userId}") ManagerService.ManagerView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) { authorize(jwt, userId); return managers.get(userId); }
    @PutMapping("/{userId}") ManagerService.ManagerView update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId, @Valid @RequestBody UpdateManagerRequest request) { authorize(jwt, userId); return managers.update(userId, request); }
    @GetMapping("/{userId}/properties") List<PropertyView> properties(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) { authorize(jwt, userId); return managers.properties(userId); }

    private static UUID subject(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    static void authorize(Jwt jwt, UUID requested) {
        if (!subject(jwt).equals(requested)) throw ApiException.forbidden("You cannot access another manager's account");
    }
}
