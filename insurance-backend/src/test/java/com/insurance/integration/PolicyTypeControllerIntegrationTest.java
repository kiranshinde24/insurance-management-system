package com.insurance.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.security.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Exercises PolicyTypeController through the real security filter chain: requests are only
 * accepted with a valid, real JWT (produced by the real JwtUtil bean), and persistence goes
 * through the real JPA layer against the in-memory H2 database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PolicyTypeControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtUtil jwtUtil;

    private String bearerToken(String role) {
        return "Bearer " + jwtUtil.generateToken("test-user", role, 1);
    }

    // Note: SecurityConfig doesn't configure httpBasic()/formLogin(), so Spring Security has no
    // AuthenticationEntryPoint registered and falls back to Http403ForbiddenEntryPoint for
    // unauthenticated requests — hence 403 here rather than the more conventional 401.

    @Test
    @DisplayName("Requests without a JWT are rejected with 403")
    void getAll_noAuthHeader_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/policy-types"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Requests with a garbage token are rejected with 403")
    void getAll_invalidToken_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/policy-types")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-jwt"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Any authenticated role can create and then list policy types")
    void create_thenGetAll_returnsCreatedType() throws Exception {
        Map<String, Object> payload = Map.of(
                "name", "Term Life " + System.nanoTime(),
                "desc", "Basic term life cover",
                "baseRate", 1.5,
                "riskFactor", 1.0
        );

        String createResponse = mockMvc.perform(post("/api/policy-types")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("admin"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        int createdId = objectMapper.readTree(createResponse).get("id").asInt();

        mockMvc.perform(get("/api/policy-types")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("customer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + createdId + ")]").exists());
    }

    @Test
    @DisplayName("Creating a policy type with a blank name fails validation")
    void create_blankName_returnsBadRequest() throws Exception {
        Map<String, Object> payload = Map.of(
                "name", "",
                "baseRate", 1.0,
                "riskFactor", 1.0
        );

        mockMvc.perform(post("/api/policy-types")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("admin"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Fetching an unknown policy type id returns 404 with a clear message")
    void getById_unknown_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/policy-types/999999")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("admin")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Policy type not found"));
    }

    @Test
    @DisplayName("A policy type can be created and then deleted while unused")
    void create_thenDelete_succeeds() throws Exception {
        Map<String, Object> payload = Map.of(
                "name", "Temp Type " + System.nanoTime(),
                "baseRate", 1.0,
                "riskFactor", 1.0
        );

        String createResponse = mockMvc.perform(post("/api/policy-types")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("admin"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int createdId = objectMapper.readTree(createResponse).get("id").asInt();

        mockMvc.perform(delete("/api/policy-types/" + createdId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Deleted successfully"));

        mockMvc.perform(get("/api/policy-types/" + createdId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("admin")))
                .andExpect(status().isNotFound());
    }
}
