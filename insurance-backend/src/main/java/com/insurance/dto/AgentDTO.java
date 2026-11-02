package com.insurance.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentDTO {
    private Integer id;

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    @NotBlank(message = "Username is required")
    @Size(min = 4, max = 50, message = "Username must be between 4 and 50 characters")
    private String username;

    @Pattern(regexp = "^$|^[0-9+\\-\\s]{7,15}$", message = "Phone number is invalid")
    private String phone;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    private String email;

    @NotNull(message = "Commission is required")
    @DecimalMin(value = "0.0", message = "Commission cannot be negative")
    @DecimalMax(value = "100.0", message = "Commission cannot exceed 100")
    private Double comm;

    // Only required on create; optional on update (existing password kept if blank) — enforced in AgentService
    private String password;
}
