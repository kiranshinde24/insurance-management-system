package com.insurance.service;

import com.insurance.dto.PolicyDTO;
import com.insurance.exception.BadRequestException;
import com.insurance.exception.ForbiddenException;
import com.insurance.exception.ResourceNotFoundException;
import com.insurance.model.*;
import com.insurance.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PolicyService {

    private final PolicyRepository repo;
    private final CustomerRepository customerRepo;
    private final AgentRepository agentRepo;
    private final PolicyTypeRepository typeRepo;
    private final PremiumCalculationService premiumCalculationService;
    private final EmailService emailService;
    private final PdfService pdfService;

    public PolicyService(PolicyRepository repo,
                         CustomerRepository customerRepo,
                         AgentRepository agentRepo,
                         PolicyTypeRepository typeRepo,
                         PremiumCalculationService premiumCalculationService,
                         EmailService emailService,
                         PdfService pdfService) {
        this.repo = repo;
        this.customerRepo = customerRepo;
        this.agentRepo = agentRepo;
        this.typeRepo = typeRepo;
        this.premiumCalculationService = premiumCalculationService;
        this.emailService = emailService;
        this.pdfService = pdfService;
    }

    /**
     * Generates the policy certificate PDF. If requesterCustId is non-null, enforces that the
     * requester actually owns the policy (used by the customer-facing download endpoint).
     */
    public byte[] generateCertificate(Integer policyId, Integer requesterCustId) {
        Policy policy = findById(policyId);
        if (requesterCustId != null && !requesterCustId.equals(policy.getCustId())) {
            throw new ForbiddenException("You are not authorized to view this policy's certificate");
        }
        Customer customer = customerRepo.findById(policy.getCustId()).orElse(null);
        PolicyType type = policy.getTypeId() != null ? typeRepo.findById(policy.getTypeId()).orElse(null) : null;
        Agent agent = policy.getAgentId() != null ? agentRepo.findById(policy.getAgentId()).orElse(null) : null;
        return pdfService.generatePolicyCertificate(policy, customer, type, agent);
    }

    public List<PolicyDTO> getAll() {
        return repo.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<PolicyDTO> getByCustomerId(Integer custId) {
        return repo.findByCustId(custId).stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<PolicyDTO> getByAgentId(Integer agentId) {
        return repo.findByAgentId(agentId).stream().map(this::toDTO).collect(Collectors.toList());
    }

    public PolicyDTO getById(Integer id) {
        return toDTO(findById(id));
    }

    @Transactional
    public PolicyDTO create(PolicyDTO dto) {
        validate(dto);
        LocalDate start = LocalDate.parse(dto.getStart());
        LocalDate end = LocalDate.parse(dto.getEnd());
        BigDecimal premium = resolvePremium(dto, start, end);

        Policy policy = Policy.builder()
                .polno(dto.getPolno() != null ? dto.getPolno() : generatePolno())
                .start(start)
                .end(end)
                .status("Active")
                .premium(premium)
                .sumAssured(dto.getSumAssured())
                .custId(dto.getCustId())
                .typeId(dto.getTypeId())
                .agentId(dto.getAgentId())
                .build();
        Policy saved = repo.save(policy);
        log.info("Policy created: id={}, polno={}, custId={}, premium={}",
                saved.getId(), saved.getPolno(), saved.getCustId(), saved.getPremium());

        customerRepo.findById(saved.getCustId()).ifPresent(c ->
                emailService.sendPolicyIssued(c.getEmail(), c.getName(), saved.getPolno(), saved.getPremium()));

        return toDTO(saved);
    }

    @Transactional
    public PolicyDTO update(Integer id, PolicyDTO dto) {
        Policy policy = findById(id);
        validate(dto);
        LocalDate start = LocalDate.parse(dto.getStart());
        LocalDate end = LocalDate.parse(dto.getEnd());

        policy.setPolno(dto.getPolno());
        policy.setStart(start);
        policy.setEnd(end);
        policy.setSumAssured(dto.getSumAssured());
        policy.setPremium(resolvePremium(dto, start, end));
        policy.setCustId(dto.getCustId());
        policy.setTypeId(dto.getTypeId());
        policy.setAgentId(dto.getAgentId());
        // Recalculate status based on end date
        policy.setStatus(computeStatus(policy.getEnd()));
        Policy saved = repo.save(policy);
        log.info("Policy updated: id={}, polno={}", saved.getId(), saved.getPolno());
        return toDTO(saved);
    }

    /**
     * Returns the premium to store: if the caller explicitly passed a positive premium
     * AND didn't ask for auto-calculation, that value is trusted (manual override, e.g. an
     * admin correcting a figure). Otherwise the premium engine computes it from sum assured,
     * policy type, and customer age/risk.
     */
    private BigDecimal resolvePremium(PolicyDTO dto, LocalDate start, LocalDate end) {
        boolean wantsAuto = Boolean.TRUE.equals(dto.getAutoCalculatePremium()) || dto.getPremium() == null;
        if (!wantsAuto && dto.getPremium().compareTo(BigDecimal.ZERO) > 0) {
            return dto.getPremium();
        }
        return calculatePremium(dto.getSumAssured(), dto.getTypeId(), dto.getCustId(), start, end);
    }

    /** Public so the controller can expose a "preview premium before saving" endpoint. */
    public BigDecimal calculatePremium(BigDecimal sumAssured, Integer typeId, Integer custId,
                                        LocalDate start, LocalDate end) {
        PolicyType type = typeRepo.findById(typeId)
                .orElseThrow(() -> new ResourceNotFoundException("Policy type not found"));
        LocalDate dob = customerRepo.findById(custId).map(Customer::getDob).orElse(null);
        return premiumCalculationService.calculate(sumAssured, type, dob, start, end);
    }

    @Transactional
    public void delete(Integer id) {
        repo.deleteById(id);
        log.info("Policy deleted: id={}", id);
    }

    public String generatePolno() {
        String max = repo.findMaxPolno();
        int next = 1;
        if (max != null && max.startsWith("POL-")) {
            try { next = Integer.parseInt(max.substring(4)) + 1; }
            catch (NumberFormatException ignored) {}
        }
        return String.format("POL-%03d", next);
    }

    private void validate(PolicyDTO dto) {
        boolean wantsAuto = Boolean.TRUE.equals(dto.getAutoCalculatePremium()) || dto.getPremium() == null;
        if (wantsAuto) {
            if (dto.getSumAssured() == null || dto.getSumAssured().compareTo(BigDecimal.ZERO) <= 0)
                throw new BadRequestException("Sum assured must be greater than zero to auto-calculate premium");
        } else if (dto.getPremium().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Premium must be greater than zero");
        }
        if (dto.getCustId() == null)
            throw new BadRequestException("Customer is required");
        if (dto.getTypeId() == null)
            throw new BadRequestException("Policy type is required");
        if (dto.getStart() == null || dto.getEnd() == null)
            throw new BadRequestException("Start and end dates are required");
    }

    private String computeStatus(LocalDate endDate) {
        return endDate.isBefore(LocalDate.now()) ? "Expired" : "Active";
    }

    private Policy findById(Integer id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found"));
    }

    public PolicyDTO toDTO(Policy p) {
        String customerName = customerRepo.findById(p.getCustId())
                .map(Customer::getName).orElse("-");
        String typeName = p.getTypeId() != null ?
                typeRepo.findById(p.getTypeId()).map(PolicyType::getName).orElse("-") : "-";
        String agentName = p.getAgentId() != null ?
                agentRepo.findById(p.getAgentId()).map(Agent::getName).orElse("-") : "-";

        // Compute runtime status based on end date
        String status = p.getEnd() != null ? computeStatus(p.getEnd()) : "Active";

        return PolicyDTO.builder()
                .id(p.getId())
                .polno(p.getPolno())
                .start(p.getStart() != null ? p.getStart().toString() : null)
                .end(p.getEnd() != null ? p.getEnd().toString() : null)
                .status(status)
                .premium(p.getPremium())
                .sumAssured(p.getSumAssured())
                .custId(p.getCustId())
                .customerName(customerName)
                .typeId(p.getTypeId())
                .typeName(typeName)
                .agentId(p.getAgentId())
                .agentName(agentName)
                .build();
    }
}
