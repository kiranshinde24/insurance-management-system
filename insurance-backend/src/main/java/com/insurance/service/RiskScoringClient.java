package com.insurance.service;

import com.insurance.dto.ClaimRiskDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Talks to the Python FastAPI fraud/risk-scoring microservice (see /insurance-ai
 * at the project root: data generation, model training, and the FastAPI service
 * that serves it). Spring Boot never trains or holds the model itself -- it just
 * sends raw claim/policy/customer facts and gets back a risk score + reasons.
 */
@Slf4j
@Service
public class RiskScoringClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final boolean enabled;

    public RiskScoringClient(RestTemplate restTemplate,
                              @Value("${ai.risk-service.url}") String baseUrl,
                              @Value("${ai.risk-service.enabled:true}") boolean enabled) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.enabled = enabled;
    }

    @SuppressWarnings("unchecked")
    public ClaimRiskDTO scoreClaim(BigDecimal claimAmount, LocalDate claimDate, String description,
                                    LocalDate policyStart, LocalDate policyEnd, BigDecimal sumAssured,
                                    BigDecimal annualPremium, BigDecimal policyRiskFactor, String policyType,
                                    LocalDate customerDob, int numPriorClaims, Integer daysSinceLastClaim) {
        if (!enabled) {
            return ClaimRiskDTO.builder().available(false)
                    .message("AI risk scoring is disabled").build();
        }

        Map<String, Object> body = new HashMap<>();
        body.put("claim_amount", claimAmount);
        body.put("claim_date", claimDate.toString());
        body.put("description", description == null ? "" : description);
        body.put("policy_start", policyStart.toString());
        body.put("policy_end", policyEnd.toString());
        body.put("sum_assured", sumAssured);
        body.put("annual_premium", annualPremium);
        body.put("policy_risk_factor", policyRiskFactor);
        body.put("policy_type", policyType == null ? "Term Life" : policyType);
        body.put("customer_dob", customerDob == null ? null : customerDob.toString());
        body.put("num_prior_claims", numPriorClaims);
        body.put("days_since_last_claim", daysSinceLastClaim);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            Map<String, Object> response = restTemplate.postForObject(
                    baseUrl + "/predict", request, Map.class);
            if (response == null) {
                return unavailable("AI service returned an empty response");
            }
            List<Map<String, String>> rawFactors = (List<Map<String, String>>) response.get("top_factors");
            List<ClaimRiskDTO.RiskFactor> factors = rawFactors.stream()
                    .map(f -> ClaimRiskDTO.RiskFactor.builder()
                            .feature(f.get("feature"))
                            .detail(f.get("detail"))
                            .build())
                    .toList();

            return ClaimRiskDTO.builder()
                    .riskScore(((Number) response.get("risk_score")).doubleValue())
                    .riskLevel((String) response.get("risk_level"))
                    .topFactors(factors)
                    .available(true)
                    .build();
        } catch (RestClientException ex) {
            log.warn("AI risk-scoring service unreachable: {}", ex.getMessage());
            return unavailable("AI risk-scoring service is currently unavailable");
        }
    }

    private ClaimRiskDTO unavailable(String message) {
        return ClaimRiskDTO.builder().available(false).message(message).build();
    }
}
