package com.insurance.controller;

import com.insurance.dto.PolicyDTO;
import com.insurance.service.PolicyExpiryScheduler;
import com.insurance.service.PolicyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PolicyController {

    private final PolicyService service;
    private final PolicyExpiryScheduler expiryScheduler;

    public PolicyController(PolicyService service, PolicyExpiryScheduler expiryScheduler) {
        this.service = service;
        this.expiryScheduler = expiryScheduler;
    }

    /** Admin: manually trigger the expiry/renewal check instead of waiting for the daily cron. */
    @PostMapping("/admin/policies/run-expiry-check")
    public ResponseEntity<?> runExpiryCheck() {
        int processed = expiryScheduler.checkAll();
        return ResponseEntity.ok(Map.of("message", "Expiry check complete", "processed", processed));
    }

    // ========== PREMIUM CALCULATOR (admin + agent, live preview before saving) ==========

    /**
     * POST /api/policy/calculate-premium
     * body: { "sumAssured": 500000, "typeId": 2, "custId": 101, "start": "2026-07-15", "end": "2027-07-15" }
     * Lets the frontend show the computed premium in real time as the form is filled in.
     */
    @PostMapping("/policy/calculate-premium")
    public ResponseEntity<?> calculatePremium(@RequestBody Map<String, Object> body) {
        BigDecimal sumAssured;
        Integer typeId;
        Integer custId;
        LocalDate start;
        LocalDate end;
        try {
            sumAssured = new BigDecimal(body.get("sumAssured").toString());
            typeId = Integer.valueOf(body.get("typeId").toString());
            custId = Integer.valueOf(body.get("custId").toString());
            start = LocalDate.parse(body.get("start").toString());
            end = LocalDate.parse(body.get("end").toString());
        } catch (Exception e) {
            throw new com.insurance.exception.BadRequestException(
                    "Invalid request body: expected sumAssured, typeId, custId, start and end (yyyy-MM-dd)");
        }
        BigDecimal premium = service.calculatePremium(sumAssured, typeId, custId, start, end);
        return ResponseEntity.ok(Map.of("premium", premium));
    }

    // ========== ADMIN ==========

    /**
     * Search/filter policies. Pass page & size to get a paginated {@link com.insurance.dto.PageResponse};
     * omit them to get the plain list (kept for backward compatibility with the existing frontend).
     */
    @GetMapping("/admin/policies")
    public Object adminGetAll(@RequestParam(required = false) String search,
                              @RequestParam(required = false) String status,
                              @RequestParam(required = false) Integer page,
                              @RequestParam(required = false) Integer size) {
        List<PolicyDTO> list = service.getAll();
        if (status != null && !status.isBlank()) {
            list = list.stream().filter(p -> status.equals(p.getStatus())).toList();
        }
        if (search != null && !search.isBlank()) {
            String q = search.toLowerCase();
            list = list.stream()
                    .filter(p -> p.getPolno().toLowerCase().contains(q) ||
                                 (p.getCustomerName() != null && p.getCustomerName().toLowerCase().contains(q)))
                    .toList();
        }
        if (page != null || size != null) {
            return com.insurance.util.PaginationUtil.paginate(list, page, size);
        }
        return list;
    }

    @PostMapping("/admin/policies")
    public ResponseEntity<?> create(@Valid @RequestBody PolicyDTO dto) {
        return ResponseEntity.ok(service.create(dto));
    }

    @PutMapping("/admin/policies/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id, @Valid @RequestBody PolicyDTO dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/admin/policies/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(Map.of("message", "Policy deleted"));
    }

    // ========== AGENT ==========

    @GetMapping("/agent/policies")
    public List<PolicyDTO> agentPolicies(Authentication auth) {
        Integer agentId = (Integer) auth.getCredentials();
        return service.getByAgentId(agentId);
    }

    @PostMapping("/agent/policies")
    public ResponseEntity<?> agentCreate(@Valid @RequestBody PolicyDTO dto, Authentication auth) {
        Integer agentId = (Integer) auth.getCredentials();
        dto.setAgentId(agentId);
        return ResponseEntity.ok(service.create(dto));
    }

    @GetMapping("/agent/policies/next-number")
    public Map<String, String> nextPolno() {
        return Map.of("polno", service.generatePolno());
    }

    // ========== CUSTOMER ==========

    @GetMapping("/customer/policies")
    public List<PolicyDTO> customerPolicies(Authentication auth) {
        Integer custId = (Integer) auth.getCredentials();
        return service.getByCustomerId(custId);
    }

    /** Customer downloads the PDF certificate for their own policy. */
    @GetMapping("/customer/policies/{id}/certificate")
    public ResponseEntity<?> customerCertificate(@PathVariable Integer id, Authentication auth) {
        Integer custId = (Integer) auth.getCredentials();
        byte[] pdf = service.generateCertificate(id, custId);
        return pdfResponse(pdf, "policy-certificate-" + id + ".pdf");
    }

    /** Admin downloads the PDF certificate for any policy. */
    @GetMapping("/admin/policies/{id}/certificate")
    public ResponseEntity<?> adminCertificate(@PathVariable Integer id) {
        byte[] pdf = service.generateCertificate(id, null);
        return pdfResponse(pdf, "policy-certificate-" + id + ".pdf");
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(pdf);
    }
}
