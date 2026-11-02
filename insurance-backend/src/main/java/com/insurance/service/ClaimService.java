package com.insurance.service;

import com.insurance.dto.ClaimDTO;
import com.insurance.dto.ClaimRiskDTO;
import com.insurance.exception.BadRequestException;
import com.insurance.exception.ResourceNotFoundException;
import com.insurance.model.*;
import com.insurance.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ClaimService {

    private final ClaimRepository repo;
    private final PolicyRepository policyRepo;
    private final CustomerRepository customerRepo;
    private final AgentRepository agentRepo;
    private final PolicyTypeRepository typeRepo;
    private final EmailService emailService;
    private final FileStorageService fileStorageService;
    private final RiskScoringClient riskScoringClient;

    public ClaimService(ClaimRepository repo,
                        PolicyRepository policyRepo,
                        CustomerRepository customerRepo,
                        AgentRepository agentRepo,
                        PolicyTypeRepository typeRepo,
                        EmailService emailService,
                        FileStorageService fileStorageService,
                        RiskScoringClient riskScoringClient) {
        this.repo = repo;
        this.policyRepo = policyRepo;
        this.customerRepo = customerRepo;
        this.agentRepo = agentRepo;
        this.typeRepo = typeRepo;
        this.emailService = emailService;
        this.fileStorageService = fileStorageService;
        this.riskScoringClient = riskScoringClient;
    }

    public List<ClaimDTO> getAll() {
        return repo.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<ClaimDTO> getByStatus(String status) {
        return repo.findByStatus(status).stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<ClaimDTO> getByCustomerId(Integer custId) {
        List<Integer> policyIds = policyRepo.findByCustId(custId).stream()
                .map(Policy::getId).collect(Collectors.toList());
        if (policyIds.isEmpty()) return List.of();
        return repo.findByPolIdIn(policyIds).stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<ClaimDTO> getByAgentId(Integer agentId) {
        List<Integer> policyIds = policyRepo.findByAgentId(agentId).stream()
                .map(Policy::getId).collect(Collectors.toList());
        if (policyIds.isEmpty()) return List.of();
        return repo.findByPolIdIn(policyIds).stream().map(this::toDTO).collect(Collectors.toList());
    }

    public ClaimDTO getById(Integer id) {
        return toDTO(findById(id));
    }

    /** Customer raises a new claim (legacy path: attachment sent as base64 inside the JSON body) */
    @Transactional
    public ClaimDTO create(ClaimDTO dto) {
        return create(dto, null);
    }

    /**
     * Customer raises a new claim. If a MultipartFile is supplied (real file upload),
     * it's stored on disk via FileStorageService and only the file's URL is persisted —
     * this replaces stuffing base64 blobs into the database column.
     */
    @Transactional
    public ClaimDTO create(ClaimDTO dto, org.springframework.web.multipart.MultipartFile file) {
        if (dto.getPolId() == null) throw new BadRequestException("Policy is required");
        if (dto.getAmount() == null) throw new BadRequestException("Claim amount is required");
        if (dto.getDate() == null) throw new BadRequestException("Claim date is required");
        if (dto.getDescription() == null || dto.getDescription().isBlank())
            throw new BadRequestException("Description is required");

        String attachmentValue;
        if (file != null && !file.isEmpty()) {
            String url = fileStorageService.store(file);
            // "path:" prefix distinguishes disk-stored files from legacy base64 blobs
            attachmentValue = "path:" + file.getOriginalFilename() + "|" + file.getContentType() + "|" + url;
        } else {
            // legacy base64 format for backward compatibility with any existing frontend calls
            attachmentValue = buildAttachment(dto);
        }

        Claim claim = Claim.builder()
                .date(LocalDate.parse(dto.getDate()))
                .amount(dto.getAmount())
                .status("Pending")
                .description(dto.getDescription())
                .polId(dto.getPolId())
                .attachment(attachmentValue)
                .build();

        Claim saved = repo.save(claim);
        log.info("Claim created: id={}, polId={}, amount={}", saved.getId(), saved.getPolId(), saved.getAmount());
        return toDTO(saved);
    }

    /** Admin approves a claim */
    @Transactional
    public ClaimDTO approve(Integer id, BigDecimal approvedAmount) {
        if (approvedAmount == null || approvedAmount.compareTo(BigDecimal.ZERO) <= 0)
            throw new BadRequestException("Valid approved amount is required");

        Claim claim = findById(id);
        claim.setApprovedAmount(approvedAmount);
        claim.setStatus("Approved");
        Claim saved = repo.save(claim);
        log.info("Claim approved: id={}, approvedAmount={}", saved.getId(), approvedAmount);
        notifyClaimStatus(saved, true, approvedAmount);
        return toDTO(saved);
    }

    /** Admin rejects a claim */
    @Transactional
    public ClaimDTO reject(Integer id) {
        Claim claim = findById(id);
        claim.setStatus("Rejected");
        Claim saved = repo.save(claim);
        log.info("Claim rejected: id={}", saved.getId());
        notifyClaimStatus(saved, false, null);
        return toDTO(saved);
    }

    /**
     * Scores a claim for fraud risk via the AI microservice (see /insurance-ai).
     * Builds the claim-history features (prior claims on this policy, days since
     * the last one) from the DB, since only Java has that context.
     */
    public ClaimRiskDTO getRiskScore(Integer claimId) {
        Claim claim = findById(claimId);
        Policy policy = policyRepo.findById(claim.getPolId())
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found for this claim"));
        PolicyType type = policy.getTypeId() != null
                ? typeRepo.findById(policy.getTypeId()).orElse(null) : null;
        Customer customer = customerRepo.findById(policy.getCustId()).orElse(null);

        List<Claim> claimsOnPolicy = repo.findByPolId(policy.getId());
        List<Claim> priorClaims = claimsOnPolicy.stream()
                .filter(c -> !c.getId().equals(claim.getId()) && c.getDate().isBefore(claim.getDate()))
                .toList();
        int numPriorClaims = priorClaims.size();
        Integer daysSinceLastClaim = priorClaims.stream()
                .max(Comparator.comparing(Claim::getDate))
                .map(c -> (int) ChronoUnit.DAYS.between(c.getDate(), claim.getDate()))
                .orElse(null);

        BigDecimal riskFactor = type != null && type.getRiskFactor() != null ? type.getRiskFactor() : BigDecimal.ONE;
        String typeName = type != null ? type.getName() : "Term Life";

        return riskScoringClient.scoreClaim(
                claim.getAmount(),
                claim.getDate(),
                claim.getDescription(),
                policy.getStart(),
                policy.getEnd(),
                policy.getSumAssured() != null ? policy.getSumAssured() : policy.getPremium().multiply(new BigDecimal("100")),
                policy.getPremium(),
                riskFactor,
                typeName,
                customer != null ? customer.getDob() : null,
                numPriorClaims,
                daysSinceLastClaim
        );
    }

    private void notifyClaimStatus(Claim claim, boolean approved, BigDecimal approvedAmount) {
        Policy policy = policyRepo.findById(claim.getPolId()).orElse(null);
        if (policy == null) return;
        customerRepo.findById(policy.getCustId()).ifPresent(c ->
                emailService.sendClaimStatus(c.getEmail(), c.getName(), policy.getPolno(), approved, approvedAmount));
    }

    private Claim findById(Integer id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim not found"));
    }

    private String buildAttachment(ClaimDTO dto) {
        if (dto.getAttachment() == null || dto.getAttachment().isBlank()) return null;
        // Store as "name|type|base64data"
        return dto.getAttachmentName() + "|" + dto.getAttachmentType() + "|" + dto.getAttachment();
    }

    private ClaimDTO toDTO(Claim c) {
        Policy policy = policyRepo.findById(c.getPolId()).orElse(null);
        String policyNumber = policy != null ? policy.getPolno() : "-";
        String customerName = policy != null ?
                customerRepo.findById(policy.getCustId()).map(Customer::getName).orElse("-") : "-";
        String agentName = policy != null && policy.getAgentId() != null ?
                agentRepo.findById(policy.getAgentId()).map(Agent::getName).orElse("-") : "-";
        String typeName = policy != null && policy.getTypeId() != null ?
                typeRepo.findById(policy.getTypeId()).map(PolicyType::getName).orElse("-") : "-";

        // Parse attachment: either "path:name|type|/uploads/..." (new, disk-stored)
        // or legacy "name|type|base64data" (old, stored inline)
        String attName = null, attType = null, attData = null, attUrl = null;
        String raw = c.getAttachment();
        if (raw != null && raw.startsWith("path:")) {
            String[] parts = raw.substring(5).split("\\|", 3);
            if (parts.length == 3) { attName = parts[0]; attType = parts[1]; attUrl = parts[2]; }
        } else if (raw != null && raw.contains("|")) {
            String[] parts = raw.split("\\|", 3);
            if (parts.length == 3) { attName = parts[0]; attType = parts[1]; attData = parts[2]; }
        }

        return ClaimDTO.builder()
                .id(c.getId())
                .date(c.getDate().toString())
                .amount(c.getAmount())
                .approvedAmount(c.getApprovedAmount())
                .status(c.getStatus())
                .description(c.getDescription())
                .attachment(attData)
                .attachmentName(attName)
                .attachmentType(attType)
                .attachmentUrl(attUrl)
                .polId(c.getPolId())
                .policyNumber(policyNumber)
                .customerName(customerName)
                .agentName(agentName)
                .policyType(typeName)
                .build();
    }
}
