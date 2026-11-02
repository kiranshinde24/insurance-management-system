package com.insurance.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyDTO {
    private Integer id;
    private String polno;

    @NotBlank(message = "Start date is required")
    private String start;

    @NotBlank(message = "End date is required")
    private String end;

    private String status;

    @Positive(message = "Premium must be greater than zero")
    private BigDecimal premium;

    // Optional: required only when auto-calculating premium (enforced in PolicyService, since it's
    // conditional on autoCalculatePremium/premium being absent — not expressible as a simple annotation)
    @Positive(message = "Sum assured must be greater than zero")
    private BigDecimal sumAssured;

    // If true (or premium is null), the backend calculates the premium instead of trusting the client value
    private Boolean autoCalculatePremium;

    @NotNull(message = "Customer is required")
    private Integer custId;

    private String customerName;

    @NotNull(message = "Policy type is required")
    private Integer typeId;

    private String typeName;
    private Integer agentId;
    private String agentName;
}
