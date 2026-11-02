package com.insurance.service;

import com.insurance.dto.CustomerDTO;
import com.insurance.exception.ResourceNotFoundException;
import com.insurance.model.Customer;
import com.insurance.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock private CustomerRepository repo;
    @Mock private PasswordEncoder encoder;

    private CustomerService service;

    @BeforeEach
    void setUp() {
        service = new CustomerService(repo, encoder);
    }

    private Customer sampleCustomer() {
        return Customer.builder()
                .id(1)
                .name("Jane Doe")
                .dob(LocalDate.of(1990, 1, 1))
                .pan("abcde1234f")
                .phone("9876543210")
                .email("jane@example.com")
                .city("Mumbai")
                .agentId(3)
                .build();
    }

    @Test
    @DisplayName("getAll maps every customer entity to a DTO")
    void getAll_returnsMappedDtos() {
        when(repo.findAll()).thenReturn(List.of(sampleCustomer()));

        List<CustomerDTO> result = service.getAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Jane Doe");
        assertThat(result.get(0).getDob()).isEqualTo("1990-01-01");
    }

    @Test
    @DisplayName("getById returns the DTO when the customer exists")
    void getById_found_returnsDto() {
        when(repo.findById(1)).thenReturn(Optional.of(sampleCustomer()));

        CustomerDTO dto = service.getById(1);

        assertThat(dto.getId()).isEqualTo(1);
        assertThat(dto.getCity()).isEqualTo("Mumbai");
    }

    @Test
    @DisplayName("getById throws ResourceNotFoundException when the customer doesn't exist")
    void getById_notFound_throws() {
        when(repo.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Customer not found");
    }

    @Test
    @DisplayName("create uppercases the PAN and saves a new customer without login credentials")
    void create_savesCustomerWithUppercasedPan() {
        CustomerDTO dto = CustomerDTO.builder()
                .name("New Guy")
                .dob("1992-02-02")
                .pan("xyzab1234c")
                .phone("1112223333")
                .email("new@example.com")
                .city("Pune")
                .agentId(2)
                .build();
        when(repo.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(10);
            return c;
        });

        CustomerDTO saved = service.create(dto);

        assertThat(saved.getId()).isEqualTo(10);
        verify(repo).save(argThat(c -> "XYZAB1234C".equals(c.getPan())));
    }

    @Test
    @DisplayName("updateProfile only overwrites fields the customer is allowed to change")
    void updateProfile_updatesExistingCustomer() {
        Customer existing = sampleCustomer();
        when(repo.findById(1)).thenReturn(Optional.of(existing));
        when(repo.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerDTO update = CustomerDTO.builder()
                .name("Jane Updated")
                .phone("0000000000")
                .email("updated@example.com")
                .city("Nagpur")
                .build();

        CustomerDTO result = service.updateProfile(1, update);

        assertThat(result.getName()).isEqualTo("Jane Updated");
        assertThat(result.getCity()).isEqualTo("Nagpur");
        // DOB wasn't supplied in the update -> should keep original
        assertThat(result.getDob()).isEqualTo("1990-01-01");
    }

    @Test
    @DisplayName("updateProfile throws ResourceNotFoundException for an unknown customer id")
    void updateProfile_unknownCustomer_throws() {
        when(repo.findById(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateProfile(404, CustomerDTO.builder().build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("search delegates to the repository's search query")
    void search_delegatesToRepository() {
        when(repo.search("jane")).thenReturn(List.of(sampleCustomer()));

        List<CustomerDTO> result = service.search("jane");

        assertThat(result).hasSize(1);
        verify(repo).search("jane");
    }

    @Test
    @DisplayName("getByAgentId filters customers by their assigned agent")
    void getByAgentId_returnsOnlyThatAgentsCustomers() {
        when(repo.findByAgentId(3)).thenReturn(List.of(sampleCustomer()));

        List<CustomerDTO> result = service.getByAgentId(3);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAgentId()).isEqualTo(3);
    }
}
