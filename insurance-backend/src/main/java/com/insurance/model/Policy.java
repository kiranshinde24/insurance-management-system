package com.insurance.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "Policy")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Policy_ID")
    private Integer id;

    @Column(name = "Policy_Number", unique = true, length = 20)
    private String polno;

    @Column(name = "Start_Date", nullable = false)
    @NotNull
    private LocalDate start;

    @Column(name = "End_Date", nullable = false)
    @NotNull
    private LocalDate end;

    @Column(name = "Status", nullable = false, length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'Active'")
    private String status;

    @Column(name = "Premium_Amount", nullable = false, precision = 10, scale = 2)
    @NotNull
    private BigDecimal premium;

    // The insured amount the premium is calculated against
    @Column(name = "Sum_Assured", precision = 12, scale = 2)
    private BigDecimal sumAssured;

    @Column(name = "Customer_ID")
    private Integer custId;

    @Column(name = "Policy_Type_ID")
    private Integer typeId;

    @Column(name = "Agent_ID")
    private Integer agentId;
}
