package com.insurance.service;

import com.insurance.dto.CustomerDTO;
import com.insurance.exception.ResourceNotFoundException;
import com.insurance.model.Customer;
import com.insurance.repository.CustomerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CustomerService {

    private final CustomerRepository repo;
    private final PasswordEncoder encoder;

    public CustomerService(CustomerRepository repo, PasswordEncoder encoder) {
        this.repo = repo;
        this.encoder = encoder;
    }

    public List<CustomerDTO> getAll() {
        return repo.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<CustomerDTO> search(String query) {
        return repo.search(query).stream().map(this::toDTO).collect(Collectors.toList());
    }

    public CustomerDTO getById(Integer id) {
        return toDTO(findById(id));
    }

    public List<CustomerDTO> getByAgentId(Integer agentId) {
        return repo.findByAgentId(agentId).stream().map(this::toDTO).collect(Collectors.toList());
    }

    /** Admin adds a customer (no login credentials — admin-created customers can't log in yet) */
    @Transactional
    public CustomerDTO create(CustomerDTO dto) {
        Customer c = Customer.builder()
                .name(dto.getName())
                .dob(dto.getDob() != null ? LocalDate.parse(dto.getDob()) : null)
                .pan(dto.getPan() != null ? dto.getPan().toUpperCase() : null)
                .phone(dto.getPhone())
                .email(dto.getEmail())
                .city(dto.getCity())
                .agentId(dto.getAgentId())
                .build();
        Customer saved = repo.save(c);
        log.info("Customer created by admin: id={}", saved.getId());
        return toDTO(saved);
    }

    /** Customer updates their own profile */
    @Transactional
    public CustomerDTO updateProfile(Integer id, CustomerDTO dto) {
        Customer c = findById(id);
        c.setName(dto.getName());
        c.setDob(dto.getDob() != null ? LocalDate.parse(dto.getDob()) : c.getDob());
        c.setPan(dto.getPan() != null ? dto.getPan().toUpperCase() : c.getPan());
        c.setPhone(dto.getPhone());
        c.setEmail(dto.getEmail());
        c.setCity(dto.getCity());
        Customer saved = repo.save(c);
        log.info("Customer profile updated: id={}", saved.getId());
        return toDTO(saved);
    }

    private Customer findById(Integer id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
    }

    public CustomerDTO toDTO(Customer c) {
        return CustomerDTO.builder()
                .id(c.getId())
                .name(c.getName())
                .dob(c.getDob() != null ? c.getDob().toString() : null)
                .pan(c.getPan())
                .phone(c.getPhone())
                .email(c.getEmail())
                .city(c.getCity())
                .agentId(c.getAgentId())
                .build();
    }
}
