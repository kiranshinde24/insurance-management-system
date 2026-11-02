package com.insurance.service;

import com.insurance.dto.DashboardDTO.*;
import com.insurance.dto.PolicyDTO;
import com.insurance.model.*;
import com.insurance.repository.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final CustomerRepository customerRepo;
    private final AgentRepository agentRepo;
    private final PolicyRepository policyRepo;
    private final PaymentRepository paymentRepo;
    private final ClaimRepository claimRepo;
    private final PolicyService policyService;

    public DashboardService(CustomerRepository customerRepo,
                            AgentRepository agentRepo,
                            PolicyRepository policyRepo,
                            PaymentRepository paymentRepo,
                            ClaimRepository claimRepo,
                            PolicyService policyService) {
        this.customerRepo = customerRepo;
        this.agentRepo = agentRepo;
        this.policyRepo = policyRepo;
        this.paymentRepo = paymentRepo;
        this.claimRepo = claimRepo;
        this.policyService = policyService;
    }

    public AdminDashboard getAdminDashboard() {
        List<Policy> policies = policyRepo.findAll();
        List<Payment> payments = paymentRepo.findAll();
        List<Claim> claims = claimRepo.findAll();

        BigDecimal totalCollection = payments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long activePolicies = policies.stream()
                .filter(p -> "Active".equals(p.getStatus())).count();
        long pendingClaims = claims.stream()
                .filter(c -> "Pending".equals(c.getStatus())).count();

        List<PolicyDTO> recentPolicies = policies.stream()
                .sorted(Comparator.comparing(Policy::getId).reversed())
                .limit(5)
                .map(policyService::toDTO)
                .collect(Collectors.toList());

        return AdminDashboard.builder()
                .totalCustomers(customerRepo.count())
                .totalPolicies(policies.size())
                .totalClaims(claims.size())
                .totalAgents(agentRepo.count())
                .totalCollection(totalCollection)
                .activePolicies(activePolicies)
                .pendingClaims(pendingClaims)
                .recentPolicies(recentPolicies)
                .policiesSoldTrend(buildCountTrend(policies.stream().map(Policy::getStart).collect(Collectors.toList())))
                .revenueTrend(buildAmountTrend(payments))
                .claimsTrend(buildCountTrend(claims.stream().map(Claim::getDate).collect(Collectors.toList())))
                .build();
    }

    // Last 6 calendar months (oldest first, current month last), used as the x-axis for all trend charts
    private List<YearMonth> lastSixMonths() {
        YearMonth current = YearMonth.now();
        List<YearMonth> months = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            months.add(current.minusMonths(i));
        }
        return months;
    }

    private String monthLabel(YearMonth ym) {
        return ym.format(DateTimeFormatter.ofPattern("MMM yyyy"));
    }

    // Counts how many dates (policy start dates, claim dates) fall in each of the last 6 months
    private List<MonthPoint> buildCountTrend(List<java.time.LocalDate> dates) {
        Map<YearMonth, Long> counts = dates.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(YearMonth::from, Collectors.counting()));

        return lastSixMonths().stream()
                .map(ym -> MonthPoint.builder()
                        .label(monthLabel(ym))
                        .count(counts.getOrDefault(ym, 0L))
                        .build())
                .collect(Collectors.toList());
    }

    // Sums payment amounts collected in each of the last 6 months
    private List<MonthPoint> buildAmountTrend(List<Payment> payments) {
        Map<YearMonth, BigDecimal> sums = payments.stream()
                .filter(p -> p.getDate() != null)
                .collect(Collectors.groupingBy(
                        p -> YearMonth.from(p.getDate()),
                        Collectors.reducing(BigDecimal.ZERO, Payment::getAmount, BigDecimal::add)));

        return lastSixMonths().stream()
                .map(ym -> MonthPoint.builder()
                        .label(monthLabel(ym))
                        .amount(sums.getOrDefault(ym, BigDecimal.ZERO))
                        .build())
                .collect(Collectors.toList());
    }

    public CustomerDashboard getCustomerDashboard(Integer custId) {
        List<Policy> policies = policyRepo.findByCustId(custId);
        List<Integer> policyIds = policies.stream().map(Policy::getId).collect(Collectors.toList());

        List<Payment> payments = policyIds.isEmpty() ? List.of() :
                paymentRepo.findByPolIdIn(policyIds);
        List<Claim> claims = policyIds.isEmpty() ? List.of() :
                claimRepo.findByPolIdIn(policyIds);

        long activePolicies = policies.stream()
                .filter(p -> "Active".equals(p.getStatus())).count();
        long pendingClaims = claims.stream()
                .filter(c -> "Pending".equals(c.getStatus())).count();

        Set<Integer> uniqueAgentIds = policies.stream()
                .filter(p -> p.getAgentId() != null)
                .map(Policy::getAgentId)
                .collect(Collectors.toSet());

        BigDecimal totalPayments = payments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String lastPayment = payments.stream()
                .max(Comparator.comparing(Payment::getDate))
                .map(p -> p.getDate().toString())
                .orElse(null);

        List<PolicyDTO> recentPolicies = policies.stream()
                .sorted(Comparator.comparing(Policy::getId).reversed())
                .limit(5)
                .map(policyService::toDTO)
                .collect(Collectors.toList());

        return CustomerDashboard.builder()
                .totalPolicies(policies.size())
                .activePolicies(activePolicies)
                .pendingClaims(pendingClaims)
                .pastClaims(claims.size())
                .agentCount(uniqueAgentIds.size())
                .totalPayments(totalPayments)
                .lastPayment(lastPayment)
                .recentPolicies(recentPolicies)
                .build();
    }

    public AgentDashboard getAgentDashboard(Integer agentId) {
        List<Policy> policies = policyRepo.findByAgentId(agentId);
        List<Integer> policyIds = policies.stream().map(Policy::getId).collect(Collectors.toList());

        List<Payment> payments = policyIds.isEmpty() ? List.of() :
                paymentRepo.findByPolIdIn(policyIds);
        List<Claim> claims = policyIds.isEmpty() ? List.of() :
                claimRepo.findByPolIdIn(policyIds);

        Agent agent = agentRepo.findById(agentId).orElse(null);
        double commPct = agent != null && agent.getComm() != null ? agent.getComm() : 0;

        BigDecimal totalCommission = policies.stream()
                .map(p -> p.getPremium()
                        .multiply(BigDecimal.valueOf(commPct))
                        .divide(BigDecimal.valueOf(100)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long activePolicies = policies.stream()
                .filter(p -> "Active".equals(p.getStatus())).count();
        long pendingClaims = claims.stream()
                .filter(c -> "Pending".equals(c.getStatus())).count();

        Set<Integer> uniqueCustomers = policies.stream()
                .filter(p -> p.getCustId() != null)
                .map(Policy::getCustId)
                .collect(Collectors.toSet());

        long policiesWithPayments = policies.stream()
                .filter(pol -> payments.stream()
                        .anyMatch(pay -> pay.getPolId().equals(pol.getId())))
                .count();

        List<PolicyDTO> recentPolicies = policies.stream()
                .sorted(Comparator.comparing(Policy::getId).reversed())
                .limit(5)
                .map(policyService::toDTO)
                .collect(Collectors.toList());

        return AgentDashboard.builder()
                .customerCount(uniqueCustomers.size())
                .policyCount(policies.size())
                .commissionTotal(totalCommission)
                .activePolicies(activePolicies)
                .pendingClaims(pendingClaims)
                .policiesWithPayments(policiesWithPayments)
                .recentPolicies(recentPolicies)
                .build();
    }
}
