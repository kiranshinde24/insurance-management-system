package com.insurance.controller;

import com.insurance.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/admin/dashboard")
    public ResponseEntity<?> adminDashboard() {
        return ResponseEntity.ok(service.getAdminDashboard());
    }

    @GetMapping("/customer/dashboard")
    public ResponseEntity<?> customerDashboard(Authentication auth) {
        Integer custId = (Integer) auth.getCredentials();
        return ResponseEntity.ok(service.getCustomerDashboard(custId));
    }

    @GetMapping("/agent/dashboard")
    public ResponseEntity<?> agentDashboard(Authentication auth) {
        Integer agentId = (Integer) auth.getCredentials();
        return ResponseEntity.ok(service.getAgentDashboard(agentId));
    }
}
