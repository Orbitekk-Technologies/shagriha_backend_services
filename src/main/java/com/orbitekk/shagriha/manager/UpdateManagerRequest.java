package com.orbitekk.shagriha.manager;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record UpdateManagerRequest(
        @Size(min = 1, max = 160) String name,
        @Email @Size(max = 255) String email,
        @Pattern(regexp="\\d{10}", message="phoneNumber must contain exactly 10 digits") String phoneNumber,
        @Size(max = 4000) String image) {}
