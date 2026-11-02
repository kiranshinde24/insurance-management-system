package com.insurance.controller;

import com.insurance.dto.PaymentDTO;
import com.insurance.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    // ========== ADMIN ==========

    @GetMapping("/admin/payments")
    public List<PaymentDTO> adminGetAll(@RequestParam(required = false) String mode) {
        List<PaymentDTO> list = service.getAll();
        if (mode != null && !mode.isBlank()) {
            list = list.stream().filter(p -> mode.equals(p.getMode())).toList();
        }
        return list;
    }

    @PostMapping("/admin/payments")
    public ResponseEntity<?> adminCreate(@Valid @RequestBody PaymentDTO dto) {
        return ResponseEntity.ok(service.create(dto));
    }

    // ========== CUSTOMER ==========

    @GetMapping("/customer/payments")
    public List<PaymentDTO> customerPayments(Authentication auth) {
        Integer custId = (Integer) auth.getCredentials();
        return service.getByCustomerId(custId);
    }

    @PostMapping("/customer/payments")
    public ResponseEntity<?> customerCreate(@Valid @RequestBody PaymentDTO dto, Authentication auth) {
        return ResponseEntity.ok(service.create(dto));
    }

    // ========== AGENT ==========

    @GetMapping("/agent/payments")
    public List<PaymentDTO> agentPayments(Authentication auth) {
        Integer agentId = (Integer) auth.getCredentials();
        return service.getByAgentId(agentId);
    }

    // ========== RECEIPTS ==========

    /** Customer downloads the PDF receipt for their own payment. */
    @GetMapping("/customer/payments/{id}/receipt")
    public ResponseEntity<?> customerReceipt(@PathVariable Integer id, Authentication auth) {
        Integer custId = (Integer) auth.getCredentials();
        byte[] pdf = service.generateReceipt(id, custId);
        return pdfResponse(pdf, "payment-receipt-" + id + ".pdf");
    }

    /** Admin downloads the PDF receipt for any payment. */
    @GetMapping("/admin/payments/{id}/receipt")
    public ResponseEntity<?> adminReceipt(@PathVariable Integer id) {
        byte[] pdf = service.generateReceipt(id, null);
        return pdfResponse(pdf, "payment-receipt-" + id + ".pdf");
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(pdf);
    }
}
