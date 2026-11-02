package com.insurance.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerDTO {
    private Integer id;

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    private String dob;

    @Pattern(regexp = "^$|^[A-Z]{5}[0-9]{4}[A-Z]$", message = "PAN must be a valid 10-character PAN (e.g. ABCDE1234F)")
    private String pan;

    @Pattern(regexp = "^$|^[0-9+\\-\\s]{7,15}$", message = "Phone number is invalid")
    private String phone;

    @Email(message = "Email must be a valid email address")
    private String email;

    private String city;
    private Integer agentId;
}
