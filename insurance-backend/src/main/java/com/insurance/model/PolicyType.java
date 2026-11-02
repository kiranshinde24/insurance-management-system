package com.insurance.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "Policy_Type")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Policy_Type_ID")
    private Integer id;

    @NotBlank(message = "Type name is required")
    @Size(max = 50, message = "Type name must be at most 50 characters")
    @Column(name = "Type_Name", nullable = false, length = 50)
    private String name;

    @Size(max = 200, message = "Description must be at most 200 characters")
    @Column(name = "Description", length = 200)
    private String desc;

    // Base premium rate charged per 1000 of sum assured, per year (e.g. 1.20 = ₹1.20 per ₹1000/year)
    @NotNull(message = "Base rate is required")
    @Positive(message = "Base rate must be greater than zero")
    @Column(name = "Base_Rate", precision = 6, scale = 2, columnDefinition = "DECIMAL(6,2) DEFAULT 1.00")
    @Builder.Default
    private BigDecimal baseRate = new BigDecimal("1.00");

    // Multiplier applied on top of the base rate for inherently riskier policy types (e.g. 1.5 for health, 1.0 for term)
    @NotNull(message = "Risk factor is required")
    @Positive(message = "Risk factor must be greater than zero")
    @Column(name = "Risk_Factor", precision = 4, scale = 2, columnDefinition = "DECIMAL(4,2) DEFAULT 1.00")
    @Builder.Default
    private BigDecimal riskFactor = new BigDecimal("1.00");
}
