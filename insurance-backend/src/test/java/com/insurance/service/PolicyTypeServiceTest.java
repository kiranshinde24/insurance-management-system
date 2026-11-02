package com.insurance.service;

import com.insurance.exception.BadRequestException;
import com.insurance.exception.ConflictException;
import com.insurance.exception.ResourceNotFoundException;
import com.insurance.model.Policy;
import com.insurance.model.PolicyType;
import com.insurance.repository.PolicyRepository;
import com.insurance.repository.PolicyTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyTypeServiceTest {

    @Mock private PolicyTypeRepository repo;
    @Mock private PolicyRepository policyRepo;

    private PolicyTypeService service;

    @BeforeEach
    void setUp() {
        service = new PolicyTypeService(repo, policyRepo);
    }

    @Test
    @DisplayName("create rejects a policy type with a blank name")
    void create_blankName_throwsBadRequest() {
        PolicyType type = PolicyType.builder().name("  ").build();

        assertThatThrownBy(() -> service.create(type))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Type name is required");

        verifyNoInteractions(repo);
    }

    @Test
    @DisplayName("create saves a valid policy type")
    void create_validType_savesSuccessfully() {
        PolicyType type = PolicyType.builder().name("Health")
                .baseRate(new BigDecimal("2.00")).riskFactor(new BigDecimal("1.50")).build();
        when(repo.save(type)).thenAnswer(inv -> inv.getArgument(0));

        PolicyType saved = service.create(type);

        assertThat(saved.getName()).isEqualTo("Health");
        verify(repo).save(type);
    }

    @Test
    @DisplayName("getById throws ResourceNotFoundException for an unknown id")
    void getById_unknown_throws() {
        when(repo.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("update only overwrites base rate / risk factor when explicitly provided")
    void update_partialFields_keepsExistingRatesIfNull() {
        PolicyType existing = PolicyType.builder().id(1).name("Old Name")
                .baseRate(new BigDecimal("1.50")).riskFactor(new BigDecimal("1.20")).build();
        when(repo.findById(1)).thenReturn(Optional.of(existing));
        when(repo.save(any(PolicyType.class))).thenAnswer(inv -> inv.getArgument(0));

        PolicyType update = PolicyType.builder().name("New Name").desc("desc").build();
        update.setBaseRate(null);
        update.setRiskFactor(null);

        PolicyType result = service.update(1, update);

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getBaseRate()).isEqualByComparingTo("1.50"); // unchanged
        assertThat(result.getRiskFactor()).isEqualByComparingTo("1.20"); // unchanged
    }

    @Test
    @DisplayName("delete is blocked while policies still reference this type")
    void delete_typeInUse_throwsConflict() {
        when(policyRepo.findByTypeId(1)).thenReturn(List.of(Policy.builder().id(1).build()));

        assertThatThrownBy(() -> service.delete(1))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Cannot delete");

        verify(repo, never()).deleteById(any());
    }

    @Test
    @DisplayName("delete succeeds once no policies reference the type")
    void delete_typeNotInUse_deletesSuccessfully() {
        when(policyRepo.findByTypeId(1)).thenReturn(List.of());

        service.delete(1);

        verify(repo).deleteById(1);
    }

    @Test
    @DisplayName("countByTypeId reports how many policies use a type")
    void countByTypeId_returnsPolicyCount() {
        when(policyRepo.findByTypeId(1)).thenReturn(List.of(Policy.builder().id(1).build(), Policy.builder().id(2).build()));

        long count = service.countByTypeId(1);

        assertThat(count).isEqualTo(2);
    }
}
