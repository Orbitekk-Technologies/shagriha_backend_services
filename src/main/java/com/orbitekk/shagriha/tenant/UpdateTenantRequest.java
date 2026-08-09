package com.orbitekk.shagriha.tenant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateTenantRequest(
        @Size(min=1, max=160) String name,
        @Email @Size(max=255) String email,
        @Size(max=40) String phoneNumber,
        @Size(max=2048) String image) {}
