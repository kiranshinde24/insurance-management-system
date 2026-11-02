package com.insurance.service;

import com.insurance.dto.AuthDTO.*;
import com.insurance.exception.ConflictException;
import com.insurance.exception.UnauthorizedException;
import com.insurance.model.Agent;
import com.insurance.model.Customer;
import com.insurance.model.RefreshToken;
import com.insurance.repository.AgentRepository;
import com.insurance.repository.CustomerRepository;
import com.insurance.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private CustomerRepository customerRepo;
    @Mock private AgentRepository agentRepo;
    @Mock private JwtUtil jwtUtil;
    @Mock private PasswordEncoder encoder;
    @Mock private RefreshTokenService refreshTokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(customerRepo, agentRepo, jwtUtil, encoder, refreshTokenService);
    }

    private RefreshToken sampleRefreshToken(Integer userId, String role) {
        return RefreshToken.builder()
                .id(1)
                .token("refresh-token-value")
                .userId(userId)
                .role(role)
                .expiryDate(Instant.now().plusSeconds(60))
                .revoked(false)
                .build();
    }

    @Test
    @DisplayName("Logs in the admin using the hardcoded admin credentials")
    void login_admin_returnsAdminResponse() {
        LoginRequest req = new LoginRequest("admin", "admin@123");
        when(refreshTokenService.create(1000, "admin")).thenReturn(sampleRefreshToken(1000, "admin"));
        when(jwtUtil.generateToken("admin", "admin", 1000)).thenReturn("jwt-token");

        LoginResponse response = authService.login(req);

        assertThat(response.getRole()).isEqualTo("admin");
        assertThat(response.getId()).isEqualTo(1000);
        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token-value");
        verifyNoInteractions(agentRepo, customerRepo);
    }

    @Test
    @DisplayName("Admin username check is case-insensitive")
    void login_adminUsernameDifferentCase_stillMatches() {
        LoginRequest req = new LoginRequest("ADMIN", "admin@123");
        when(refreshTokenService.create(1000, "admin")).thenReturn(sampleRefreshToken(1000, "admin"));
        when(jwtUtil.generateToken(anyString(), eq("admin"), eq(1000))).thenReturn("jwt-token");

        LoginResponse response = authService.login(req);

        assertThat(response.getRole()).isEqualTo("admin");
    }

    @Test
    @DisplayName("Logs in an agent with a matching username/password")
    void login_agent_returnsAgentResponse() {
        LoginRequest req = new LoginRequest("agent1", "password");
        Agent agent = Agent.builder().id(5).name("Agent Smith").username("agent1").password("hashed").build();
        when(agentRepo.findByUsername("agent1")).thenReturn(Optional.of(agent));
        when(encoder.matches("password", "hashed")).thenReturn(true);
        when(refreshTokenService.create(5, "agent")).thenReturn(sampleRefreshToken(5, "agent"));
        when(jwtUtil.generateToken("agent1", "agent", 5)).thenReturn("jwt-token");

        LoginResponse response = authService.login(req);

        assertThat(response.getRole()).isEqualTo("agent");
        assertThat(response.getId()).isEqualTo(5);
        assertThat(response.getName()).isEqualTo("Agent Smith");
    }

    @Test
    @DisplayName("Logs in a customer with a matching username/password")
    void login_customer_returnsCustomerResponse() {
        LoginRequest req = new LoginRequest("cust1", "password");
        Customer customer = Customer.builder().id(7).name("Jane Doe").username("cust1").password("hashed").build();
        when(agentRepo.findByUsername("cust1")).thenReturn(Optional.empty());
        when(customerRepo.findByUsername("cust1")).thenReturn(Optional.of(customer));
        when(encoder.matches("password", "hashed")).thenReturn(true);
        when(refreshTokenService.create(7, "customer")).thenReturn(sampleRefreshToken(7, "customer"));
        when(jwtUtil.generateToken("cust1", "customer", 7)).thenReturn("jwt-token");

        LoginResponse response = authService.login(req);

        assertThat(response.getRole()).isEqualTo("customer");
        assertThat(response.getId()).isEqualTo(7);
    }

    @Test
    @DisplayName("Rejects login when the username doesn't match anyone")
    void login_unknownUsername_throwsUnauthorized() {
        LoginRequest req = new LoginRequest("ghost", "whatever");
        when(agentRepo.findByUsername("ghost")).thenReturn(Optional.empty());
        when(customerRepo.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid username or password");
    }

    @Test
    @DisplayName("Rejects login when the password doesn't match")
    void login_wrongPassword_throwsUnauthorized() {
        LoginRequest req = new LoginRequest("cust1", "wrong");
        Customer customer = Customer.builder().id(7).name("Jane Doe").username("cust1").password("hashed").build();
        when(agentRepo.findByUsername("cust1")).thenReturn(Optional.empty());
        when(customerRepo.findByUsername("cust1")).thenReturn(Optional.of(customer));
        when(encoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("Registers a new customer when the username and PAN are both free")
    void register_success_createsCustomerAndReturnsToken() {
        RegisterRequest req = new RegisterRequest();
        req.setFullname("New Customer");
        req.setEmail("new@example.com");
        req.setUsername("newuser");
        req.setPassword("secret1");
        req.setPhone("9999999999");
        req.setCity("Pune");
        req.setDob("1995-05-05");
        req.setPan("ABCDE1234F");
        req.setAgentId(1);

        when(customerRepo.existsByUsername("newuser")).thenReturn(false);
        when(agentRepo.existsByUsername("newuser")).thenReturn(false);
        when(customerRepo.existsByPan("ABCDE1234F")).thenReturn(false);
        when(encoder.encode("secret1")).thenReturn("hashed-secret");
        when(customerRepo.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(42);
            return c;
        });
        when(refreshTokenService.create(42, "customer")).thenReturn(sampleRefreshToken(42, "customer"));
        when(jwtUtil.generateToken("newuser", "customer", 42)).thenReturn("jwt-token");

        LoginResponse response = authService.register(req);

        assertThat(response.getId()).isEqualTo(42);
        assertThat(response.getRole()).isEqualTo("customer");
        assertThat(response.getUsername()).isEqualTo("newuser");

        verify(customerRepo).save(argThat(c ->
                c.getPassword().equals("hashed-secret") && c.getPan().equals("ABCDE1234F")));
    }

    @Test
    @DisplayName("Rejects registration when the username is already used by a customer")
    void register_usernameTakenByCustomer_throwsConflict() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("taken");
        when(customerRepo.existsByUsername("taken")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Username already exists");

        verify(customerRepo, never()).save(any());
    }

    @Test
    @DisplayName("Rejects registration when the username is already used by an agent")
    void register_usernameTakenByAgent_throwsConflict() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("agentname");
        when(customerRepo.existsByUsername("agentname")).thenReturn(false);
        when(agentRepo.existsByUsername("agentname")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ConflictException.class);

        verify(customerRepo, never()).save(any());
    }

    @Test
    @DisplayName("Rejects registration when the PAN is already registered")
    void register_panAlreadyRegistered_throwsConflict() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("newuser2");
        req.setPan("abcde1234f");
        when(customerRepo.existsByUsername("newuser2")).thenReturn(false);
        when(agentRepo.existsByUsername("newuser2")).thenReturn(false);
        when(customerRepo.existsByPan("ABCDE1234F")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("PAN already registered");
    }

    @Test
    @DisplayName("Refresh rotates the token and returns a new access token for a customer")
    void refresh_customer_returnsNewTokenPair() {
        RefreshRequest req = new RefreshRequest("old-refresh-token");
        Customer customer = Customer.builder().id(7).name("Jane Doe").username("cust1").build();
        RefreshToken rotated = sampleRefreshToken(7, "customer");
        when(refreshTokenService.verifyAndRotate("old-refresh-token")).thenReturn(rotated);
        when(customerRepo.findById(7)).thenReturn(Optional.of(customer));
        when(jwtUtil.generateToken("cust1", "customer", 7)).thenReturn("new-jwt-token");

        LoginResponse response = authService.refresh(req);

        assertThat(response.getToken()).isEqualTo("new-jwt-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token-value");
        assertThat(response.getUsername()).isEqualTo("cust1");
    }

    @Test
    @DisplayName("Refresh fails cleanly if the customer account behind the token no longer exists")
    void refresh_customerAccountDeleted_throwsUnauthorized() {
        RefreshRequest req = new RefreshRequest("old-refresh-token");
        RefreshToken rotated = sampleRefreshToken(99, "customer");
        when(refreshTokenService.verifyAndRotate("old-refresh-token")).thenReturn(rotated);
        when(customerRepo.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(req))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Account no longer exists");
    }

    @Test
    @DisplayName("Logout revokes the refresh token")
    void logout_revokesRefreshToken() {
        RefreshRequest req = new RefreshRequest("token-to-kill");

        authService.logout(req);

        verify(refreshTokenService).revoke("token-to-kill");
    }
}
