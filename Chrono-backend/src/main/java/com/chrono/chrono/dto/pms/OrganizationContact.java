package com.chrono.chrono.dto.pms;

import jakarta.validation.constraints.*;

public record OrganizationContact(
        @Size(max = 36) String id,
        Long linkedGuestId,
        @NotBlank @Size(max = 180) String name,
        @Size(max = 100) String role,
        @Email @Size(max = 190) String email,
        @Size(max = 60) String phone,
        boolean primaryContact
) { }
