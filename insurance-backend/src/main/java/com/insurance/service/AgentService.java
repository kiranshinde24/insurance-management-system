package com.insurance.service;

import com.insurance.dto.AgentDTO;
import com.insurance.exception.BadRequestException;
import com.insurance.exception.ConflictException;
import com.insurance.exception.ResourceNotFoundException;
import com.insurance.model.Agent;
import com.insurance.repository.AgentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AgentService {

    private final AgentRepository repo;
    private final PasswordEncoder encoder;

    public AgentService(AgentRepository repo, PasswordEncoder encoder) {
        this.repo = repo;
        this.encoder = encoder;
    }

    public List<AgentDTO> getAll() {
        return repo.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public AgentDTO getById(Integer id) {
        return toDTO(findById(id));
    }

    @Transactional
    public AgentDTO create(AgentDTO dto) {
        validate(dto, null);

        Agent agent = Agent.builder()
                .name(dto.getName())
                .username(dto.getUsername())
                .password(encoder.encode(dto.getPassword()))
                .phone(dto.getPhone())
                .email(dto.getEmail())
                .comm(dto.getComm())
                .build();

        Agent saved = repo.save(agent);
        log.info("Agent created: id={}, username={}", saved.getId(), saved.getUsername());
        return toDTO(saved);
    }

    @Transactional
    public AgentDTO update(Integer id, AgentDTO dto) {
        Agent agent = findById(id);
        validate(dto, id);

        agent.setName(dto.getName());
        agent.setUsername(dto.getUsername());
        agent.setPhone(dto.getPhone());
        agent.setEmail(dto.getEmail());
        agent.setComm(dto.getComm());

        // Update password only if provided
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            agent.setPassword(encoder.encode(dto.getPassword()));
            log.info("Agent password changed: id={}", id);
        }

        Agent saved = repo.save(agent);
        log.info("Agent updated: id={}, username={}", saved.getId(), saved.getUsername());
        return toDTO(saved);
    }

    @Transactional
    public void delete(Integer id) {
        repo.deleteById(id);
        log.info("Agent deleted: id={}", id);
    }

    private void validate(AgentDTO dto, Integer excludeId) {
        if (dto.getName() == null || dto.getName().isBlank())
            throw new BadRequestException("Agent name is required");
        if (dto.getUsername() == null || dto.getUsername().isBlank())
            throw new BadRequestException("Username is required");
        if (dto.getComm() == null)
            throw new BadRequestException("Commission is required");

        repo.findByUsername(dto.getUsername()).ifPresent(existing -> {
            if (excludeId == null || !existing.getId().equals(excludeId)) {
                throw new ConflictException("Username already exists");
            }
        });
    }

    private Agent findById(Integer id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent not found"));
    }

    private AgentDTO toDTO(Agent a) {
        return AgentDTO.builder()
                .id(a.getId())
                .name(a.getName())
                .username(a.getUsername())
                .phone(a.getPhone())
                .email(a.getEmail())
                .comm(a.getComm())
                .build();   // never return password
    }
}
