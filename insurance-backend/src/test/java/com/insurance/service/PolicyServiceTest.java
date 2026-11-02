package com.insurance.service;

import com.insurance.dto.PolicyDTO;
import com.insurance.exception.BadRequestException;
import com.insurance.exception.ForbiddenException;
import com.insurance.exception.ResourceNotFoundException;
import com.insurance.model.*;
import com.insurance.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    @Mock private PolicyRepository repo;
    @Mock private CustomerRepository customerRepo;
    @Mock private AgentRepository agentRepo;
    @Mock private PolicyTypeRepository typeRepo;
    @Mock private PremiumCalculationService premiumCalculationService;
    @Mock private EmailService emailService;
    @Mock private PdfService pdfService;

    private PolicyService service;

    @BeforeEach
    void setUp() {
        service = new PolicyService(repo, customerRepo, agentRepo, typeRepo,
                premiumCalculationService, emailService, pdfService);
    }

    private Customer sampleCustomer() {
        return Customer.builder().id(1).name("Jane Doe").email("jane@example.com")
                .dob(LocalDate.of(1990, 1, 1)).build();
    }

    private PolicyType sampleType() {
        return PolicyType.builder().id(2).name("Term Life")
                .baseRate(new BigDecimal("1.00")).riskFactor(new BigDecimal("1.00")).build();
    }

    @Test
    @DisplayName("create auto-calculates the premium via the premium engine when requested")
    void create_autoCalculatePremium_usesEngineResult() {
        PolicyDTO dto = PolicyDTO.builder()
                .start("2026-01-01").end("2027-01-01")
                .sumAssured(new BigDecimal("100000"))
                .autoCalculatePremium(true)
                .custId(1).typeId(2)
                .build();

        when(customerRepo.findById(1)).thenReturn(Optional.of(sampleCustomer()));
        when(typeRepo.findById(2)).thenReturn(Optional.of(sampleType()));
        when(premiumCalculationService.calculate(eq(new BigDecimal("100000")), any(), any(), any(), any()))
                .thenReturn(new BigDecimal("123.45"));
        when(repo.findMaxPolno()).thenReturn(null);
        when(repo.save(any(Policy.class))).thenAnswer(inv -> {
            Policy p = inv.getArgument(0);
            p.setId(1);
            return p;
        });

        PolicyDTO saved = service.create(dto);

        assertThat(saved.getPremium()).isEqualByComparingTo("123.45");
        assertThat(saved.getStatus()).isEqualTo("Active");
        verify(emailService).sendPolicyIssued(eq("jane@example.com"), eq("Jane Doe"), anyString(), eq(new BigDecimal("123.45")));
    }

    @Test
    @DisplayName("create trusts a manually supplied premium when auto-calculate isn't requested")
    void create_manualPremium_skipsEngine() {
        PolicyDTO dto = PolicyDTO.builder()
                .start("2026-01-01").end("2027-01-01")
                .premium(new BigDecimal("500.00"))
                .autoCalculatePremium(false)
                .custId(1).typeId(2)
                .build();

        when(customerRepo.findById(1)).thenReturn(Optional.of(sampleCustomer()));
        when(typeRepo.findById(2)).thenReturn(Optional.of(sampleType()));
        when(repo.findMaxPolno()).thenReturn(null);
        when(repo.save(any(Policy.class))).thenAnswer(inv -> {
            Policy p = inv.getArgument(0);
            p.setId(1);
            return p;
        });

        PolicyDTO saved = service.create(dto);

        assertThat(saved.getPremium()).isEqualByComparingTo("500.00");
        verifyNoInteractions(premiumCalculationService);
    }

    @Test
    @DisplayName("create rejects a missing customer id")
    void create_missingCustomerId_throwsBadRequest() {
        PolicyDTO dto = PolicyDTO.builder()
                .start("2026-01-01").end("2027-01-01")
                .sumAssured(new BigDecimal("100000"))
                .autoCalculatePremium(true)
                .typeId(2)
                .build();

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Customer is required");

        verifyNoInteractions(repo);
    }

    @Test
    @DisplayName("create rejects auto-calculate requests with no sum assured")
    void create_autoCalculateWithoutSumAssured_throwsBadRequest() {
        PolicyDTO dto = PolicyDTO.builder()
                .start("2026-01-01").end("2027-01-01")
                .autoCalculatePremium(true)
                .custId(1).typeId(2)
                .build();

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Sum assured");
    }

    @Test
    @DisplayName("create rejects a manual premium of zero or less")
    void create_manualPremiumNotPositive_throwsBadRequest() {
        PolicyDTO dto = PolicyDTO.builder()
                .start("2026-01-01").end("2027-01-01")
                .premium(BigDecimal.ZERO)
                .autoCalculatePremium(false)
                .custId(1).typeId(2)
                .build();

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Premium must be greater than zero");
    }

    @Test
    @DisplayName("generatePolno starts numbering at POL-001 when no policies exist yet")
    void generatePolno_noExistingPolicies_startsAtOne() {
        when(repo.findMaxPolno()).thenReturn(null);

        String polno = service.generatePolno();

        assertThat(polno).isEqualTo("POL-001");
    }

    @Test
    @DisplayName("generatePolno increments from the highest existing policy number")
    void generatePolno_existingPolicies_incrementsFromMax() {
        when(repo.findMaxPolno()).thenReturn("POL-007");

        String polno = service.generatePolno();

        assertThat(polno).isEqualTo("POL-008");
    }

    @Test
    @DisplayName("getById throws ResourceNotFoundException for an unknown policy")
    void getById_unknownPolicy_throws() {
        when(repo.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Policy not found");
    }

    @Test
    @DisplayName("update recomputes status to Expired when the new end date is in the past")
    void update_endDateInPast_marksExpired() {
        Policy existing = Policy.builder().id(1).polno("POL-001").custId(1).typeId(2)
                .start(LocalDate.now().minusYears(2)).end(LocalDate.now().minusYears(1))
                .premium(new BigDecimal("100.00")).status("Active").build();
        when(repo.findById(1)).thenReturn(Optional.of(existing));
        when(customerRepo.findById(1)).thenReturn(Optional.of(sampleCustomer()));
        when(typeRepo.findById(2)).thenReturn(Optional.of(sampleType()));
        when(repo.save(any(Policy.class))).thenAnswer(inv -> inv.getArgument(0));

        PolicyDTO dto = PolicyDTO.builder()
                .polno("POL-001")
                .start(LocalDate.now().minusYears(2).toString())
                .end(LocalDate.now().minusDays(1).toString())
                .premium(new BigDecimal("100.00"))
                .autoCalculatePremium(false)
                .custId(1).typeId(2)
                .build();

        PolicyDTO result = service.update(1, dto);

        assertThat(result.getStatus()).isEqualTo("Expired");
    }

    @Test
    @DisplayName("generateCertificate rejects a customer trying to view another customer's certificate")
    void generateCertificate_wrongOwner_throwsForbidden() {
        Policy policy = Policy.builder().id(1).custId(1).typeId(2).agentId(null)
                .start(LocalDate.now()).end(LocalDate.now().plusYears(1))
                .premium(new BigDecimal("100.00")).status("Active").build();
        when(repo.findById(1)).thenReturn(Optional.of(policy));

        assertThatThrownBy(() -> service.generateCertificate(1, 999))
                .isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(pdfService);
    }

    @Test
    @DisplayName("generateCertificate succeeds for the owning customer")
    void generateCertificate_correctOwner_generatesPdf() {
        Policy policy = Policy.builder().id(1).custId(1).typeId(2).agentId(null)
                .start(LocalDate.now()).end(LocalDate.now().plusYears(1))
                .premium(new BigDecimal("100.00")).status("Active").build();
        when(repo.findById(1)).thenReturn(Optional.of(policy));
        when(customerRepo.findById(1)).thenReturn(Optional.of(sampleCustomer()));
        when(typeRepo.findById(2)).thenReturn(Optional.of(sampleType()));
        when(pdfService.generatePolicyCertificate(any(), any(), any(), any())).thenReturn(new byte[]{1, 2, 3});

        byte[] result = service.generateCertificate(1, 1);

        assertThat(result).isEqualTo(new byte[]{1, 2, 3});
    }

    @Test
    @DisplayName("calculatePremium throws when the policy type doesn't exist")
    void calculatePremium_unknownType_throws() {
        when(typeRepo.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calculatePremium(new BigDecimal("1000"), 99, 1,
                LocalDate.now(), LocalDate.now().plusYears(1)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Policy type not found");
    }
}
