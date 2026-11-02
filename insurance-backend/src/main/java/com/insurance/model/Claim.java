package com.insurance.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "Claim")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Claim_ID")
    private Integer id;

    @Column(name = "Claim_Date", nullable = false)
    private LocalDate date;

    @Column(name = "Claim_Amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "Claim_Status", nullable = false, length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'Pending'")
    private String status;

    @Column(name = "Approved_Amount", precision = 10, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "Description", length = 500)
    private String description;

    // Attachment stored as Base64 string (filename|mimetype|base64data)
    @Column(name = "Attachment", columnDefinition = "LONGTEXT")
    private String attachment;

    @Column(name = "Policy_ID")
    private Integer polId;
}
