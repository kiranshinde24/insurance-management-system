package com.insurance.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "Payment")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Payment_ID")
    private Integer id;

    @Column(name = "Payment_Date", nullable = false)
    private LocalDate date;

    @Column(name = "Amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "Payment_Mode", nullable = false, length = 20)
    private String mode;

    @Column(name = "Policy_ID")
    private Integer polId;
}
