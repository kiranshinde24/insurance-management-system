package com.insurance.service;

import com.insurance.dto.PaymentDTO;
import com.insurance.exception.BadRequestException;
import com.insurance.exception.ForbiddenException;
import com.insurance.exception.ResourceNotFoundException;
import com.insurance.model.*;
import com.insurance.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PaymentService {

    private final PaymentRepository repo;
    private final PolicyRepository policyRepo;
    private final CustomerRepository customerRepo;
    private final EmailService emailService;
    private final PdfService pdfService;

    public PaymentService(PaymentRepository repo,
                          PolicyRepository policyRepo,
                          CustomerRepository customerRepo,
                          EmailService emailService,
                          PdfService pdfService) {
        this.repo = repo;
        this.policyRepo = policyRepo;
        this.customerRepo = customerRepo;
        this.emailService = emailService;
        this.pdfService = pdfService;
    }

    /**
     * Generates the payment receipt PDF. If requesterCustId is non-null, enforces that the
     * requester actually owns the underlying policy (used by the customer-facing download endpoint).
     */
    public byte[] generateReceipt(Integer paymentId, Integer requesterCustId) {
        Payment payment = repo.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        Policy policy = policyRepo.findById(payment.getPolId()).orElse(null);
        if (requesterCustId != null && (policy == null || !requesterCustId.equals(policy.getCustId()))) {
            throw new ForbiddenException("You are not authorized to view this receipt");
        }
        Customer customer = policy != null ? customerRepo.findById(policy.getCustId()).orElse(null) : null;
        return pdfService.generatePaymentReceipt(payment, policy, customer);
    }

    public List<PaymentDTO> getAll() {
        return repo.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<PaymentDTO> getByCustomerId(Integer custId) {
        List<Integer> policyIds = policyRepo.findByCustId(custId).stream()
                .map(Policy::getId).collect(Collectors.toList());
        if (policyIds.isEmpty()) return List.of();
        return repo.findByPolIdIn(policyIds).stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<PaymentDTO> getByAgentId(Integer agentId) {
        List<Integer> policyIds = policyRepo.findByAgentId(agentId).stream()
                .map(Policy::getId).collect(Collectors.toList());
        if (policyIds.isEmpty()) return List.of();
        return repo.findByPolIdIn(policyIds).stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<PaymentDTO> getByPolicyId(Integer polId) {
        return repo.findByPolId(polId).stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public PaymentDTO create(PaymentDTO dto) {
        if (dto.getPolId() == null) throw new BadRequestException("Policy is required");
        if (dto.getAmount() == null) throw new BadRequestException("Amount is required");
        if (dto.getDate() == null) throw new BadRequestException("Date is required");
        if (dto.getMode() == null || dto.getMode().isBlank()) throw new BadRequestException("Mode is required");

        Payment payment = Payment.builder()
                .date(LocalDate.parse(dto.getDate()))
                .amount(dto.getAmount())
                .mode(dto.getMode())
                .polId(dto.getPolId())
                .build();
        Payment saved = repo.save(payment);
        log.info("Payment recorded: id={}, polId={}, amount={}, mode={}",
                saved.getId(), saved.getPolId(), saved.getAmount(), saved.getMode());

        policyRepo.findById(saved.getPolId()).ifPresent(policy ->
                customerRepo.findById(policy.getCustId()).ifPresent(c ->
                        emailService.sendPaymentReceived(c.getEmail(), c.getName(), policy.getPolno(), saved.getAmount())));

        return toDTO(saved);
    }

    private PaymentDTO toDTO(Payment p) {
        String policyNumber = policyRepo.findById(p.getPolId())
                .map(Policy::getPolno).orElse("-");
        String customerName = policyRepo.findById(p.getPolId())
                .map(pol -> customerRepo.findById(pol.getCustId())
                        .map(Customer::getName).orElse("-"))
                .orElse("-");

        return PaymentDTO.builder()
                .id(p.getId())
                .date(p.getDate().toString())
                .amount(p.getAmount())
                .mode(p.getMode())
                .polId(p.getPolId())
                .policyNumber(policyNumber)
                .customerName(customerName)
                .build();
    }
}
