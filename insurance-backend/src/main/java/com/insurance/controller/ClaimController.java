package com.insurance.controller;

import com.insurance.dto.ClaimDTO;
import com.insurance.service.ClaimService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ClaimController {

    private final ClaimService service;

    public ClaimController(ClaimService service) {
        this.service = service;
    }

    // ========== ADMIN ==========

    /**
     * Pass page & size to get a paginated {@link com.insurance.dto.PageResponse}; omit them
     * to get the plain list (kept for backward compatibility with the existing frontend).
     */
    @GetMapping("/admin/claims")
    public Object adminGetAll(@RequestParam(required = false) String status,
                              @RequestParam(required = false) Integer page,
                              @RequestParam(required = false) Integer size) {
        List<ClaimDTO> list = (status != null && !status.isBlank()) ? service.getByStatus(status) : service.getAll();
        if (page != null || size != null) {
            return com.insurance.util.PaginationUtil.paginate(list, page, size);
        }
        return list;
    }

    @GetMapping("/admin/claims/{id}")
    public ClaimDTO adminGetById(@PathVariable Integer id) {
        return service.getById(id);
    }

    /** AI fraud/risk score for a claim, computed by the Python model in /insurance-ai. */
    @GetMapping("/admin/claims/{id}/risk-score")
    public com.insurance.dto.ClaimRiskDTO riskScore(@PathVariable Integer id) {
        return service.getRiskScore(id);
    }

    /** Admin approves a claim: PUT /api/admin/claims/{id}/approve  body: { "approvedAmount": 5000 } */
    @PutMapping("/admin/claims/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Integer id,
                                     @RequestBody Map<String, BigDecimal> body) {
        BigDecimal amount = body.get("approvedAmount");
        return ResponseEntity.ok(service.approve(id, amount));
    }

    /** Admin rejects a claim: PUT /api/admin/claims/{id}/reject */
    @PutMapping("/admin/claims/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Integer id) {
        return ResponseEntity.ok(service.reject(id));
    }

    // ========== CUSTOMER ==========

    @GetMapping("/customer/claims")
    public List<ClaimDTO> customerClaims(Authentication auth) {
        Integer custId = (Integer) auth.getCredentials();
        return service.getByCustomerId(custId);
    }

    /** Legacy path: attachment (if any) sent as base64 inside the JSON body. */
    @PostMapping(value = "/customer/claims", consumes = "application/json")
    public ResponseEntity<?> customerCreate(@Valid @RequestBody ClaimDTO dto, Authentication auth) {
        return ResponseEntity.ok(service.create(dto));
    }

    /**
     * Preferred path: real file upload via multipart/form-data. The file is stored on disk
     * and only its URL is saved in the DB — no more base64 blobs.
     * Form fields: polId, amount, date, description, file (optional)
     */
    @PostMapping(value = "/customer/claims", consumes = "multipart/form-data")
    public ResponseEntity<?> customerCreateWithFile(@RequestParam Integer polId,
                                                     @RequestParam BigDecimal amount,
                                                     @RequestParam String date,
                                                     @RequestParam String description,
                                                     @RequestParam(required = false) MultipartFile file,
                                                     Authentication auth) {
        ClaimDTO dto = ClaimDTO.builder()
                .polId(polId).amount(amount).date(date).description(description)
                .build();
        return ResponseEntity.ok(service.create(dto, file));
    }

    // ========== AGENT ==========

    @GetMapping("/agent/claims")
    public List<ClaimDTO> agentClaims(Authentication auth) {
        Integer agentId = (Integer) auth.getCredentials();
        return service.getByAgentId(agentId);
    }
}
