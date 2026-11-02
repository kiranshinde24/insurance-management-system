package com.insurance.controller;

import com.insurance.model.PolicyType;
import com.insurance.service.PolicyTypeService;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/policy-types")
public class PolicyTypeController {

    private final PolicyTypeService service;

    public PolicyTypeController(PolicyTypeService service) {
        this.service = service;
    }

    @GetMapping
    public List<PolicyType> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public PolicyType getById(@PathVariable Integer id) {
        return service.getById(id);
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody PolicyType type) {
        return ResponseEntity.ok(service.create(type));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id, @Valid @RequestBody PolicyType type) {
        return ResponseEntity.ok(service.update(id, type));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(Map.of("message", "Deleted successfully"));
    }
}
