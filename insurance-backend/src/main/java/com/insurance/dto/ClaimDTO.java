package com.insurance.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimDTO {
    private Integer id;

    @NotBlank(message = "Claim date is required")
    private String date;

    @NotNull(message = "Claim amount is required")
    @Positive(message = "Claim amount must be greater than zero")
    private BigDecimal amount;

    private BigDecimal approvedAmount;
    private String status;

    @NotBlank(message = "Description is required")
    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;

    private String attachment;    // base64 encoded file
    private String attachmentName;
    private String attachmentType;
    private String attachmentUrl;   // set when the file is stored on disk instead of as base64

    @NotNull(message = "Policy is required")
    private Integer polId;

    private String policyNumber;
    private String customerName;
    private String agentName;
    private String policyType;
}
