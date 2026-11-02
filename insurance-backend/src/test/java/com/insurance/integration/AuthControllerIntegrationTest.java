package com.insurance.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end test of the registration/login/refresh flow: real Spring context, real Spring
 * Security filter chain, real BCrypt password hashing, real JWT issuance — only the database
 * is swapped for an in-memory H2 instance (see application-test.properties).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private Map<String, Object> registerPayload(String username) {
        return Map.of(
                "fullname", "Integration Test User",
                "email", username + "@example.com",
                "username", username,
                "password", "password123",
                "phone", "9876543210",
                "city", "Mumbai",
                "dob", "1995-06-15",
                "pan", "",
                "agentId", 1
        );
    }

    @Test
    @DisplayName("A new customer can register and immediately receive a usable token pair")
    void register_newCustomer_returnsTokenAndCanLoginAfterwards() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(registerPayload("newcustomer1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("customer"))
                .andExpect(jsonPath("$.username").value("newcustomer1"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());

        // The same credentials should now work through the normal login endpoint
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "newcustomer1", "password", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("customer"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("Registering the same username twice is rejected with 409 Conflict")
    void register_duplicateUsername_returnsConflict() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(registerPayload("dupeuser"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(registerPayload("dupeuser"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username already exists"));
    }

    @Test
    @DisplayName("Registration with an invalid email fails validation with 400")
    void register_invalidEmail_returnsBadRequestWithFieldErrors() throws Exception {
        Map<String, Object> payload = new java.util.HashMap<>(registerPayload("baduser"));
        payload.put("email", "not-an-email");

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    @DisplayName("Login with a wrong password returns 401 Unauthorized")
    void login_wrongPassword_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(registerPayload("wrongpwuser"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "wrongpwuser", "password", "totally-wrong"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("The hardcoded admin account can log in through the same endpoint")
    void login_admin_returnsAdminRole() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "admin", "password", "admin@123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("admin"))
                .andExpect(jsonPath("$.id").value(1000));
    }

    @Test
    @DisplayName("A refresh token issued at registration can be exchanged for a new access token")
    void refresh_afterRegister_issuesNewAccessToken() throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(registerPayload("refreshuser"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String refreshToken = objectMapper.readTree(response).get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").value(org.hamcrest.Matchers.not(refreshToken)));
    }

    @Test
    @DisplayName("A garbage refresh token is rejected with 401")
    void refresh_invalidToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", "not-a-real-token"))))
                .andExpect(status().isUnauthorized());
    }
}
