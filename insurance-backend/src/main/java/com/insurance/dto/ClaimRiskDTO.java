package com.insurance.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimRiskDTO {
    private Double riskScore;      // 0.0 - 1.0
    private String riskLevel;      // Low / Medium / High
    private List<RiskFactor> topFactors;
    private boolean available;     // false if the AI service was unreachable
    private String message;        // explanation when unavailable

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RiskFactor {
        private String feature;
        private String detail;
    }
}
