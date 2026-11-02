package com.insurance.service;

import com.insurance.exception.UnauthorizedException;
import com.insurance.model.RefreshToken;
import com.insurance.repository.RefreshTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repo;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpirationMs;

    public RefreshTokenService(RefreshTokenRepository repo) {
        this.repo = repo;
    }

    /**
     * Issues a brand-new refresh token for this user/role, replacing any previous ones so a user
     * only ever has one valid refresh token at a time (one active session per login).
     */
    @Transactional
    public RefreshToken create(Integer userId, String role) {
        repo.deleteAllForUser(userId, role);
        RefreshToken rt = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .userId(userId)
                .role(role)
                .expiryDate(Instant.now().plusMillis(refreshExpirationMs))
                .revoked(false)
                .build();
        return repo.save(rt);
    }

    /**
     * Validates the given refresh token and, if it's still valid, rotates it: the old token is
     * deleted and a brand-new one is issued. Rotation means a leaked/stolen refresh token can only
     * ever be used once before it stops working.
     */
    @Transactional
    public RefreshToken verifyAndRotate(String token) {
        RefreshToken rt = repo.findByToken(token)
                .orElseThrow(() -> {
                    log.warn("Refresh token rejected: not found");
                    return new UnauthorizedException("Invalid refresh token, please log in again");
                });

        if (rt.isRevoked() || rt.getExpiryDate().isBefore(Instant.now())) {
            repo.deleteByToken(token);
            log.warn("Refresh token rejected: userId={}, role={}, revoked={}, expired={}",
                    rt.getUserId(), rt.getRole(), rt.isRevoked(), rt.getExpiryDate().isBefore(Instant.now()));
            throw new UnauthorizedException("Session expired, please log in again");
        }

        repo.deleteByToken(token);
        return create(rt.getUserId(), rt.getRole());
    }

    @Transactional
    public void revoke(String token) {
        repo.deleteByToken(token);
    }
}
