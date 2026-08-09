package com.orbitekk.shagriha.manager;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateManagerRequest(
        @Size(min = 1, max = 160) String name,
        @Email @Size(max = 255) String email,
        @Size(max = 40) String phoneNumber,
        @Size(max = 4000) String image) {}
