package com.insurance.service;

import com.insurance.dto.ClaimDTO;
import com.insurance.exception.BadRequestException;
import com.insurance.exception.ResourceNotFoundException;
import com.insurance.model.Claim;
import com.insurance.model.Customer;
import com.insurance.model.Policy;
import com.insurance.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {

    @Mock private ClaimRepository repo;
    @Mock private PolicyRepository policyRepo;
    @Mock private CustomerRepository customerRepo;
    @Mock private AgentRepository agentRepo;
    @Mock private PolicyTypeRepository typeRepo;
    @Mock private EmailService emailService;
    @Mock private FileStorageService fileStorageService;
    @Mock private RiskScoringClient riskScoringClient;

    private ClaimService service;

    @BeforeEach
    void setUp() {
        service = new ClaimService(repo, policyRepo, customerRepo, agentRepo, typeRepo, emailService, fileStorageService, riskScoringClient);
    }

    private Policy samplePolicy() {
        return Policy.builder().id(1).polno("POL-001").custId(5).typeId(null).agentId(null)
                .start(LocalDate.now()).end(LocalDate.now().plusYears(1))
                .premium(new BigDecimal("100.00")).status("Active").build();
    }

    @Test
    @DisplayName("create rejects a claim without a policy id")
    void create_missingPolicyId_throwsBadRequest() {
        ClaimDTO dto = ClaimDTO.builder()
                .amount(new BigDecimal("500")).date("2026-01-01").description("Broken arm")
                .build();

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Policy is required");
    }

    @Test
    @DisplayName("create rejects a claim without an amount")
    void create_missingAmount_throwsBadRequest() {
        ClaimDTO dto = ClaimDTO.builder()
                .polId(1).date("2026-01-01").description("Broken arm")
                .build();

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Claim amount is required");
    }

    @Test
    @DisplayName("create rejects a claim with a blank description")
    void create_blankDescription_throwsBadRequest() {
        ClaimDTO dto = ClaimDTO.builder()
                .polId(1).amount(new BigDecimal("500")).date("2026-01-01").description("   ")
                .build();

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Description is required");
    }

    @Test
    @DisplayName("create saves a legacy base64 claim with status Pending")
    void create_legacyAttachment_savesWithPendingStatus() {
        ClaimDTO dto = ClaimDTO.builder()
                .polId(1).amount(new BigDecimal("500")).date("2026-01-01")
                .description("Broken arm").attachment("base64data")
                .attachmentName("photo.png").attachmentType("image/png")
                .build();
        when(policyRepo.findById(1)).thenReturn(Optional.of(samplePolicy()));
        when(customerRepo.findById(5)).thenReturn(Optional.of(Customer.builder().id(5).name("Jane").build()));
        when(repo.save(any(Claim.class))).thenAnswer(inv -> {
            Claim c = inv.getArgument(0);
            c.setId(1);
            return c;
        });

        ClaimDTO saved = service.create(dto);

        assertThat(saved.getStatus()).isEqualTo("Pending");
        verify(repo).save(argThat(c -> "Pending".equals(c.getStatus())
                && "photo.png|image/png|base64data".equals(c.getAttachment())));
    }

    @Test
    @DisplayName("approve rejects a missing or non-positive approved amount")
    void approve_invalidAmount_throwsBadRequest() {
        assertThatThrownBy(() -> service.approve(1, null))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.approve(1, BigDecimal.ZERO))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(repo);
    }

    @Test
    @DisplayName("approve marks the claim Approved, stores the amount, and notifies the customer")
    void approve_validAmount_updatesClaimAndNotifies() {
        Claim claim = Claim.builder().id(1).polId(1).status("Pending")
                .date(LocalDate.now()).amount(new BigDecimal("500")).build();
        when(repo.findById(1)).thenReturn(Optional.of(claim));
        when(repo.save(any(Claim.class))).thenAnswer(inv -> inv.getArgument(0));
        when(policyRepo.findById(1)).thenReturn(Optional.of(samplePolicy()));
        when(customerRepo.findById(5)).thenReturn(Optional.of(
                Customer.builder().id(5).name("Jane").email("jane@example.com").build()));

        ClaimDTO result = service.approve(1, new BigDecimal("450"));

        assertThat(result.getStatus()).isEqualTo("Approved");
        assertThat(result.getApprovedAmount()).isEqualByComparingTo("450");
        verify(emailService).sendClaimStatus(eq("jane@example.com"), eq("Jane"), eq("POL-001"), eq(true), eq(new BigDecimal("450")));
    }

    @Test
    @DisplayName("approve throws ResourceNotFoundException for an unknown claim")
    void approve_unknownClaim_throws() {
        when(repo.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(99, new BigDecimal("100")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("reject marks the claim Rejected and notifies the customer")
    void reject_updatesClaimStatusAndNotifies() {
        Claim claim = Claim.builder().id(1).polId(1).status("Pending")
                .date(LocalDate.now()).amount(new BigDecimal("500")).build();
        when(repo.findById(1)).thenReturn(Optional.of(claim));
        when(repo.save(any(Claim.class))).thenAnswer(inv -> inv.getArgument(0));
        when(policyRepo.findById(1)).thenReturn(Optional.of(samplePolicy()));
        when(customerRepo.findById(5)).thenReturn(Optional.of(
                Customer.builder().id(5).name("Jane").email("jane@example.com").build()));

        ClaimDTO result = service.reject(1);

        assertThat(result.getStatus()).isEqualTo("Rejected");
        verify(emailService).sendClaimStatus(eq("jane@example.com"), eq("Jane"), eq("POL-001"), eq(false), isNull());
    }

    @Test
    @DisplayName("getByCustomerId returns an empty list when the customer has no policies")
    void getByCustomerId_noPolicies_returnsEmptyList() {
        when(policyRepo.findByCustId(5)).thenReturn(List.of());

        List<ClaimDTO> result = service.getByCustomerId(5);

        assertThat(result).isEmpty();
        verifyNoInteractions(repo);
    }

    @Test
    @DisplayName("getByCustomerId returns claims for all of the customer's policies")
    void getByCustomerId_withPolicies_returnsMatchingClaims() {
        Policy policy = samplePolicy();
        Claim claim = Claim.builder().id(1).polId(1).status("Pending")
                .date(LocalDate.now()).amount(new BigDecimal("500")).build();
        when(policyRepo.findByCustId(5)).thenReturn(List.of(policy));
        when(repo.findByPolIdIn(List.of(1))).thenReturn(List.of(claim));
        when(policyRepo.findById(1)).thenReturn(Optional.of(policy));
        when(customerRepo.findById(5)).thenReturn(Optional.of(Customer.builder().id(5).name("Jane").build()));

        List<ClaimDTO> result = service.getByCustomerId(5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPolicyNumber()).isEqualTo("POL-001");
    }
}
