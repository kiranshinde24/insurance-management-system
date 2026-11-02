package com.insurance.service;

import com.insurance.exception.UnauthorizedException;
import com.insurance.model.RefreshToken;
import com.insurance.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock private RefreshTokenRepository repo;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new RefreshTokenService(repo);
        // @Value-injected field isn't populated outside a Spring context, so set it manually
        Field field = RefreshTokenService.class.getDeclaredField("refreshExpirationMs");
        field.setAccessible(true);
        field.set(service, 604800000L); // 7 days
    }

    @Test
    @DisplayName("create clears any previous token for the user and issues a fresh one")
    void create_deletesPreviousTokensAndSavesNew() {
        when(repo.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        RefreshToken token = service.create(5, "customer");

        verify(repo).deleteAllForUser(5, "customer");
        assertThat(token.getUserId()).isEqualTo(5);
        assertThat(token.getRole()).isEqualTo("customer");
        assertThat(token.isRevoked()).isFalse();
        assertThat(token.getToken()).isNotBlank();
        assertThat(token.getExpiryDate()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("verifyAndRotate throws for a token that doesn't exist")
    void verifyAndRotate_unknownToken_throwsUnauthorized() {
        when(repo.findByToken("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyAndRotate("nope"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    @Test
    @DisplayName("verifyAndRotate rejects and deletes an expired token")
    void verifyAndRotate_expiredToken_throwsAndDeletes() {
        RefreshToken expired = RefreshToken.builder()
                .id(1).token("expired-token").userId(5).role("customer")
                .expiryDate(Instant.now().minusSeconds(60)).revoked(false).build();
        when(repo.findByToken("expired-token")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.verifyAndRotate("expired-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Session expired");

        verify(repo).deleteByToken("expired-token");
    }

    @Test
    @DisplayName("verifyAndRotate rejects a revoked token")
    void verifyAndRotate_revokedToken_throws() {
        RefreshToken revoked = RefreshToken.builder()
                .id(1).token("revoked-token").userId(5).role("customer")
                .expiryDate(Instant.now().plusSeconds(60)).revoked(true).build();
        when(repo.findByToken("revoked-token")).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.verifyAndRotate("revoked-token"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("verifyAndRotate deletes the old token and issues a brand-new one")
    void verifyAndRotate_validToken_rotatesToken() {
        RefreshToken valid = RefreshToken.builder()
                .id(1).token("old-token").userId(5).role("customer")
                .expiryDate(Instant.now().plusSeconds(60)).revoked(false).build();
        when(repo.findByToken("old-token")).thenReturn(Optional.of(valid));
        when(repo.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        RefreshToken rotated = service.verifyAndRotate("old-token");

        verify(repo).deleteByToken("old-token");
        verify(repo).deleteAllForUser(5, "customer");
        assertThat(rotated.getToken()).isNotEqualTo("old-token");
        assertThat(rotated.getUserId()).isEqualTo(5);
    }

    @Test
    @DisplayName("revoke simply deletes the given token")
    void revoke_deletesToken() {
        service.revoke("some-token");

        verify(repo).deleteByToken("some-token");
    }
}
