package com.insurance.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

public class DashboardDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AdminDashboard {
        private long totalCustomers;
        private long totalPolicies;
        private long totalClaims;
        private long totalAgents;
        private BigDecimal totalCollection;
        private long activePolicies;
        private long pendingClaims;
        private List<PolicyDTO> recentPolicies;

        // Chart data — trailing 6 months, oldest first
        private List<MonthPoint> policiesSoldTrend;
        private List<MonthPoint> revenueTrend;
        private List<MonthPoint> claimsTrend;
    }

    // One data point on a monthly trend chart
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthPoint {
        private String label;   // e.g. "Jan 2026"
        private long count;     // e.g. policies sold / claims filed that month
        private BigDecimal amount; // e.g. revenue collected that month
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CustomerDashboard {
        private long totalPolicies;
        private long activePolicies;
        private long pendingClaims;
        private long pastClaims;
        private long agentCount;
        private BigDecimal totalPayments;
        private String lastPayment;
        private List<PolicyDTO> recentPolicies;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AgentDashboard {
        private long customerCount;
        private long policyCount;
        private BigDecimal commissionTotal;
        private long activePolicies;
        private long pendingClaims;
        private long policiesWithPayments;
        private List<PolicyDTO> recentPolicies;
    }
}
