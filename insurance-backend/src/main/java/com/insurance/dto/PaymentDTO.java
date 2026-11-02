package com.insurance.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentDTO {
    private Integer id;

    @NotBlank(message = "Payment date is required")
    private String date;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotBlank(message = "Payment mode is required")
    private String mode;

    @NotNull(message = "Policy is required")
    private Integer polId;

    private String policyNumber;
    private String customerName;
}
