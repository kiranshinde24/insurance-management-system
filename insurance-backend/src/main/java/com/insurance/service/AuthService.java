package com.insurance.service;

import com.insurance.dto.AuthDTO.*;
import com.insurance.exception.ConflictException;
import com.insurance.exception.UnauthorizedException;
import com.insurance.model.*;
import com.insurance.repository.*;
import com.insurance.security.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Slf4j
@Service
public class AuthService {

    private final CustomerRepository customerRepo;
    private final AgentRepository agentRepo;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder encoder;
    private final RefreshTokenService refreshTokenService;

    // Admin credentials stored as constants (can be moved to DB)
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin@123";
    private static final Integer ADMIN_ID = 1000;

    public AuthService(CustomerRepository customerRepo,
                       AgentRepository agentRepo,
                       JwtUtil jwtUtil,
                       PasswordEncoder encoder,
                       RefreshTokenService refreshTokenService) {
        this.customerRepo = customerRepo;
        this.agentRepo = agentRepo;
        this.jwtUtil = jwtUtil;
        this.encoder = encoder;
        this.refreshTokenService = refreshTokenService;
    }

    public LoginResponse login(LoginRequest req) {
        // 1. Check admin
        if (ADMIN_USERNAME.equalsIgnoreCase(req.getUsername()) &&
            ADMIN_PASSWORD.equals(req.getPassword())) {
            log.info("Login success: username={}, role=admin", req.getUsername());
            return buildAuthResponse("admin", ADMIN_ID, "Admin", ADMIN_USERNAME);
        }

        // 2. Check agent
        Optional<Agent> agentOpt = agentRepo.findByUsername(req.getUsername());
        if (agentOpt.isPresent()) {
            Agent agent = agentOpt.get();
            if (encoder.matches(req.getPassword(), agent.getPassword())) {
                log.info("Login success: username={}, role=agent, id={}", req.getUsername(), agent.getId());
                return buildAuthResponse("agent", agent.getId(), agent.getName(), agent.getUsername());
            }
        }

        // 3. Check customer
        Optional<Customer> custOpt = customerRepo.findByUsername(req.getUsername());
        if (custOpt.isPresent()) {
            Customer customer = custOpt.get();
            if (encoder.matches(req.getPassword(), customer.getPassword())) {
                log.info("Login success: username={}, role=customer, id={}", req.getUsername(), customer.getId());
                return buildAuthResponse("customer", customer.getId(), customer.getName(), customer.getUsername());
            }
        }

        // Never log the submitted password here.
        log.warn("Login failed: username={}", req.getUsername());
        throw new UnauthorizedException("Invalid username or password");
    }

    @Transactional
    public LoginResponse register(RegisterRequest req) {
        // Validate uniqueness
        if (customerRepo.existsByUsername(req.getUsername())) {
            throw new ConflictException("Username already exists");
        }
        if (agentRepo.existsByUsername(req.getUsername())) {
            throw new ConflictException("Username already exists");
        }
        if (req.getPan() != null && !req.getPan().isEmpty() &&
            customerRepo.existsByPan(req.getPan().toUpperCase())) {
            throw new ConflictException("PAN already registered");
        }

        Customer customer = Customer.builder()
                .name(req.getFullname())
                .dob(req.getDob() != null ? LocalDate.parse(req.getDob()) : null)
                .pan(req.getPan() != null && !req.getPan().isEmpty() ? req.getPan().toUpperCase() : null)
                .phone(req.getPhone())
                .email(req.getEmail())
                .city(req.getCity())
                .agentId(req.getAgentId())
                .username(req.getUsername())
                .password(encoder.encode(req.getPassword()))
                .build();

        customer = customerRepo.save(customer);
        log.info("New customer registered: username={}, id={}", customer.getUsername(), customer.getId());

        return buildAuthResponse("customer", customer.getId(), customer.getName(), customer.getUsername());
    }

    /**
     * Exchanges a valid, unexpired refresh token for a brand-new access token. The refresh token
     * itself is rotated (old one destroyed, new one returned) so it can't be replayed.
     */
    @Transactional
    public LoginResponse refresh(RefreshRequest req) {
        RefreshToken rotated = refreshTokenService.verifyAndRotate(req.getRefreshToken());
        Integer userId = rotated.getUserId();
        String role = rotated.getRole();
        log.debug("Access token refreshed: userId={}, role={}", userId, role);

        String username;
        String name;

        if ("admin".equals(role)) {
            username = ADMIN_USERNAME;
            name = "Admin";
        } else if ("agent".equals(role)) {
            Agent agent = agentRepo.findById(userId)
                    .orElseThrow(() -> new UnauthorizedException("Account no longer exists, please log in again"));
            username = agent.getUsername();
            name = agent.getName();
        } else if ("customer".equals(role)) {
            Customer customer = customerRepo.findById(userId)
                    .orElseThrow(() -> new UnauthorizedException("Account no longer exists, please log in again"));
            username = customer.getUsername();
            name = customer.getName();
        } else {
            throw new UnauthorizedException("Invalid refresh token");
        }

        return LoginResponse.builder()
                .token(jwtUtil.generateToken(username, role, userId))
                .refreshToken(rotated.getToken())
                .role(role)
                .id(userId)
                .name(name)
                .username(username)
                .build();
    }

    /** Revokes the refresh token so it (and any future rotation of it) can no longer be used. */
    @Transactional
    public void logout(RefreshRequest req) {
        refreshTokenService.revoke(req.getRefreshToken());
        log.info("User logged out (refresh token revoked)");
    }

    private LoginResponse buildAuthResponse(String role, Integer id, String name, String username) {
        RefreshToken refreshToken = refreshTokenService.create(id, role);
        return LoginResponse.builder()
                .token(jwtUtil.generateToken(username, role, id))
                .refreshToken(refreshToken.getToken())
                .role(role)
                .id(id)
                .name(name)
                .username(username)
                .build();
    }
}
