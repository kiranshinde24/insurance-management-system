package com.insurance.service;

import com.insurance.model.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates simple, clean single-page PDFs for policy certificates and payment receipts
 * using Apache PDFBox — a free, open-source library (no paid API, no external service).
 */
@Service
public class PdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final PDType1Font FONT_REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font FONT_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    public byte[] generatePolicyCertificate(Policy policy, Customer customer, PolicyType type, Agent agent) {
        List<String[]> rows = List.of(
                new String[]{"Policy Number", policy.getPolno()},
                new String[]{"Policy Type", type != null ? type.getName() : "-"},
                new String[]{"Status", policy.getStatus()},
                new String[]{"Start Date", policy.getStart().format(DATE_FMT)},
                new String[]{"End Date", policy.getEnd().format(DATE_FMT)},
                new String[]{"Sum Assured", policy.getSumAssured() != null ? ("Rs. " + policy.getSumAssured()) : "-"},
                new String[]{"Premium", "Rs. " + policy.getPremium()},
                new String[]{"Policyholder", customer != null ? customer.getName() : "-"},
                new String[]{"Policyholder Email", customer != null ? String.valueOf(customer.getEmail()) : "-"},
                new String[]{"Servicing Agent", agent != null ? agent.getName() : "Direct / Unassigned"}
        );
        return renderDocument("POLICY CERTIFICATE",
                "This certifies that the policy detailed below is active and issued by the company.", rows);
    }

    public byte[] generatePaymentReceipt(Payment payment, Policy policy, Customer customer) {
        List<String[]> rows = List.of(
                new String[]{"Receipt No.", "RCPT-" + payment.getId()},
                new String[]{"Payment Date", payment.getDate().format(DATE_FMT)},
                new String[]{"Amount Paid", "Rs. " + payment.getAmount()},
                new String[]{"Payment Mode", payment.getMode()},
                new String[]{"Policy Number", policy != null ? policy.getPolno() : "-"},
                new String[]{"Paid By", customer != null ? customer.getName() : "-"}
        );
        return renderDocument("PAYMENT RECEIPT",
                "This receipt confirms payment received against the policy detailed below.", rows);
    }

    private byte[] renderDocument(String title, String subtitle, List<String[]> rows) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            float margin = 60;
            float y = page.getMediaBox().getHeight() - margin;
            float leftLabelX = margin;
            float leftValueX = margin + 180;

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // Header
                cs.beginText();
                cs.setFont(FONT_BOLD, 20);
                cs.newLineAtOffset(margin, y);
                cs.showText("Insurance Policy Management System");
                cs.endText();
                y -= 30;

                cs.beginText();
                cs.setFont(FONT_BOLD, 16);
                cs.newLineAtOffset(margin, y);
                cs.showText(title);
                cs.endText();
                y -= 22;

                cs.beginText();
                cs.setFont(FONT_REGULAR, 10);
                cs.newLineAtOffset(margin, y);
                cs.showText(subtitle);
                cs.endText();
                y -= 10;

                // Divider
                cs.setLineWidth(1f);
                cs.moveTo(margin, y);
                cs.lineTo(page.getMediaBox().getWidth() - margin, y);
                cs.stroke();
                y -= 30;

                // Rows
                for (String[] row : rows) {
                    cs.beginText();
                    cs.setFont(FONT_BOLD, 11);
                    cs.newLineAtOffset(leftLabelX, y);
                    cs.showText(row[0] + ":");
                    cs.endText();

                    cs.beginText();
                    cs.setFont(FONT_REGULAR, 11);
                    cs.newLineAtOffset(leftValueX, y);
                    cs.showText(row[1] != null ? row[1] : "-");
                    cs.endText();

                    y -= 24;
                }

                y -= 20;
                cs.setLineWidth(0.5f);
                cs.moveTo(margin, y);
                cs.lineTo(page.getMediaBox().getWidth() - margin, y);
                cs.stroke();
                y -= 20;

                cs.beginText();
                cs.setFont(FONT_REGULAR, 9);
                cs.newLineAtOffset(margin, y);
                cs.showText("This is a system-generated document and does not require a signature.");
                cs.endText();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate PDF: " + e.getMessage(), e);
        }
    }
}
