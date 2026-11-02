package com.insurance.controller;

import com.insurance.dto.AgentDTO;
import com.insurance.service.AgentService;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AgentController {

    private final AgentService service;

    public AgentController(AgentService service) {
        this.service = service;
    }

    /** Public — used on registration page dropdown */
    @GetMapping("/agents/list")
    public List<AgentDTO> list() {
        return service.getAll();
    }

    /** Admin: get all agents */
    @GetMapping("/admin/agents")
    public List<AgentDTO> getAll() {
        return service.getAll();
    }

    /** Admin: get agent by id */
    @GetMapping("/admin/agents/{id}")
    public AgentDTO getById(@PathVariable Integer id) {
        return service.getById(id);
    }

    /** Admin: create agent */
    @PostMapping("/admin/agents")
    public ResponseEntity<?> create(@Valid @RequestBody AgentDTO dto) {
        return ResponseEntity.ok(service.create(dto));
    }

    /** Admin: update agent */
    @PutMapping("/admin/agents/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id, @Valid @RequestBody AgentDTO dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    /** Admin: delete agent */
    @DeleteMapping("/admin/agents/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(Map.of("message", "Agent deleted"));
    }
}
