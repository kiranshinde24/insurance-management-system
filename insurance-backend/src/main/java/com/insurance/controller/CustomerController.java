package com.insurance.controller;

import com.insurance.dto.CustomerDTO;
import com.insurance.service.CustomerService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    /**
     * Admin: list all customers, with optional search. Pass page & size to get a paginated
     * {@link com.insurance.dto.PageResponse}; omit them to get the plain list (kept for
     * backward compatibility with the existing frontend).
     */
    @GetMapping("/admin/customers")
    public Object getAll(@RequestParam(required = false) String search,
                         @RequestParam(required = false) Integer page,
                         @RequestParam(required = false) Integer size) {
        List<CustomerDTO> list = (search != null && !search.isBlank()) ? service.search(search) : service.getAll();
        if (page != null || size != null) {
            return com.insurance.util.PaginationUtil.paginate(list, page, size);
        }
        return list;
    }

    /** Admin: add a customer */
    @PostMapping("/admin/customers")
    public ResponseEntity<?> create(@Valid @RequestBody CustomerDTO dto) {
        return ResponseEntity.ok(service.create(dto));
    }

    /** Agent: get customers assigned to this agent */
    @GetMapping("/agent/customers")
    public List<CustomerDTO> getByAgent(Authentication auth) {
        Integer agentId = (Integer) auth.getCredentials();
        return service.getByAgentId(agentId);
    }

    /** Customer: get own profile */
    @GetMapping("/customer/profile")
    public ResponseEntity<?> getProfile(Authentication auth) {
        Integer custId = (Integer) auth.getCredentials();
        return ResponseEntity.ok(service.getById(custId));
    }

    /** Customer: update own profile */
    @PutMapping("/customer/profile")
    public ResponseEntity<?> updateProfile(@Valid @RequestBody CustomerDTO dto, Authentication auth) {
        Integer custId = (Integer) auth.getCredentials();
        return ResponseEntity.ok(service.updateProfile(custId, dto));
    }
}
