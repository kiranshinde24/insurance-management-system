package com.insurance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Centralized email notifications: policy issued, claim approved/rejected,
 * payment received, renewal reminder.
 *
 * Wraps every send in a try/catch and checks app.mail.enabled so that a missing/incorrect
 * Gmail app-password never breaks the actual business operation (creating a policy, approving
 * a claim, etc.) — it just logs a warning instead. Flip app.mail.enabled=true in
 * application.properties once you've set MAIL_USERNAME / MAIL_PASSWORD.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final JavaMailSender mailSender;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from:}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPolicyIssued(String to, String customerName, String polno, BigDecimal premium) {
        String subject = "Your policy " + polno + " has been issued";
        String body = "Dear " + safe(customerName) + ",\n\n"
                + "Your insurance policy " + polno + " has been issued successfully.\n"
                + "Annual/term premium: Rs. " + premium + "\n\n"
                + "You can view full details by logging into your account.\n\n"
                + "Thank you for choosing us.";
        send(to, subject, body);
    }

    public void sendClaimStatus(String to, String customerName, String polno, boolean approved, BigDecimal approvedAmount) {
        String subject = "Claim on policy " + polno + " has been " + (approved ? "approved" : "rejected");
        String body = "Dear " + safe(customerName) + ",\n\n"
                + "Your claim on policy " + polno + " has been " + (approved ? "APPROVED" : "REJECTED") + ".\n"
                + (approved ? ("Approved amount: Rs. " + approvedAmount + "\n") : "")
                + "\nLog in to your account for more details.\n\nThank you.";
        send(to, subject, body);
    }

    public void sendPaymentReceived(String to, String customerName, String polno, BigDecimal amount) {
        String subject = "Payment received for policy " + polno;
        String body = "Dear " + safe(customerName) + ",\n\n"
                + "We've received your payment of Rs. " + amount + " for policy " + polno + ".\n"
                + "A receipt is available for download from your account.\n\nThank you.";
        send(to, subject, body);
    }

    public void sendRenewalReminder(String to, String customerName, String polno, LocalDate endDate, long daysLeft) {
        String subject = "Reminder: policy " + polno + " expires in " + daysLeft + " day(s)";
        String body = "Dear " + safe(customerName) + ",\n\n"
                + "Your policy " + polno + " is due to expire on " + endDate.format(DATE_FMT)
                + " (" + daysLeft + " day(s) from today).\n"
                + "Please renew it soon to keep your coverage active.\n\nThank you.";
        send(to, subject, body);
    }

    private void send(String to, String subject, String body) {
        if (!mailEnabled) {
            log.info("[EMAIL SKIPPED - app.mail.enabled=false] To: {} | Subject: {}", to, subject);
            return;
        }
        if (to == null || to.isBlank()) {
            log.warn("[EMAIL SKIPPED - no address on file] Subject: {}", subject);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (fromAddress != null && !fromAddress.isBlank()) message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            // Never let an email failure break the underlying business operation
            log.error("Failed to send email to {} ({}): {}", to, subject, e.getMessage());
        }
    }

    private String safe(String name) {
        return name != null ? name : "Customer";
    }
}
