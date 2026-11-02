package com.insurance.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A refresh token issued at login/register and exchanged (via POST /api/auth/refresh) for a new
 * short-lived access token. Stored in the DB (rather than as a self-contained JWT) so it can be
 * revoked immediately on logout, instead of remaining valid until it naturally expires.
 */
@Entity
@Table(name = "Refresh_Token")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Token_ID")
    private Integer id;

    @Column(name = "Token", nullable = false, unique = true, length = 255)
    private String token;

    // Same convention as the "userId" claim in the access token: admin=1000, else Customer_ID / Agent_ID
    @Column(name = "User_ID", nullable = false)
    private Integer userId;

    @Column(name = "Role", nullable = false, length = 20)
    private String role;

    @Column(name = "Expiry_Date", nullable = false)
    private Instant expiryDate;

    @Column(name = "Revoked", nullable = false)
    @Builder.Default
    private boolean revoked = false;
}
