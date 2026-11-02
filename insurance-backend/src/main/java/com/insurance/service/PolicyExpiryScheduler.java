package com.insurance.service;

import com.insurance.model.Customer;
import com.insurance.model.Policy;
import com.insurance.repository.CustomerRepository;
import com.insurance.repository.PolicyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Runs once a day (cron configurable via app.scheduler.expiry-check-cron):
 *  1. Marks any policy whose end date has passed as "Expired"
 *  2. Emails a renewal reminder to customers whose policy expires within the next 30 days
 *
 * No external scheduler/cron service needed — this uses Spring's built-in @Scheduled,
 * which is completely free and runs inside the same JVM as the app.
 */
@Service
public class PolicyExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(PolicyExpiryScheduler.class);
    private static final int RENEWAL_WINDOW_DAYS = 30;

    private final PolicyRepository policyRepo;
    private final CustomerRepository customerRepo;
    private final EmailService emailService;

    public PolicyExpiryScheduler(PolicyRepository policyRepo, CustomerRepository customerRepo, EmailService emailService) {
        this.policyRepo = policyRepo;
        this.customerRepo = customerRepo;
        this.emailService = emailService;
    }

    @Scheduled(cron = "${app.scheduler.expiry-check-cron:0 0 7 * * *}")
    public void runDailyCheck() {
        log.info("Running daily policy expiry/renewal check...");
        int result = checkAll();
        log.info("Policy expiry check complete: {} expired, {} renewal reminders sent", result, result);
    }

    /**
     * Also exposed for an admin-triggered "run now" button (useful for demoing this feature
     * live without waiting for the cron to fire).
     */
    @Transactional
    public int checkAll() {
        LocalDate today = LocalDate.now();
        List<Policy> policies = policyRepo.findAll();
        int expiredCount = 0;
        int reminderCount = 0;

        for (Policy p : policies) {
            if (p.getEnd() == null) continue;

            if (p.getEnd().isBefore(today) && !"Expired".equalsIgnoreCase(p.getStatus())) {
                p.setStatus("Expired");
                policyRepo.save(p);
                expiredCount++;
                continue;
            }

            long daysLeft = ChronoUnit.DAYS.between(today, p.getEnd());
            if (daysLeft >= 0 && daysLeft <= RENEWAL_WINDOW_DAYS) {
                sendReminder(p, daysLeft);
                reminderCount++;
            }
        }
        return expiredCount + reminderCount;
    }

    private void sendReminder(Policy policy, long daysLeft) {
        Customer customer = customerRepo.findById(policy.getCustId()).orElse(null);
        if (customer == null) return;
        emailService.sendRenewalReminder(customer.getEmail(), customer.getName(), policy.getPolno(), policy.getEnd(), daysLeft);
    }
}
