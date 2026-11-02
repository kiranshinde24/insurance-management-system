package com.insurance.service;

import com.insurance.exception.BadRequestException;
import com.insurance.model.PolicyType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;

/**
 * Calculates policy premiums from first principles instead of accepting a manually
 * entered number. This is the core "business logic" of the system:
 *
 *   premium = (sumAssured / 1000) x baseRate x ageFactor x riskFactor x termYears
 *
 * - baseRate and riskFactor come from the PolicyType (e.g. Health is priced differently than Term Life)
 * - ageFactor is derived from the customer's age at the time the policy starts (older = higher risk)
 * - termYears is derived from the policy start/end dates
 */
@Service
public class PremiumCalculationService {

    public BigDecimal calculate(BigDecimal sumAssured, PolicyType type, LocalDate customerDob,
                                 LocalDate start, LocalDate end) {
        if (sumAssured == null || sumAssured.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Sum assured must be greater than zero to calculate premium");
        }
        if (type == null) {
            throw new BadRequestException("Policy type is required to calculate premium");
        }
        if (start == null || end == null || !end.isAfter(start)) {
            throw new BadRequestException("A valid start and end date are required to calculate premium");
        }

        BigDecimal baseRate = type.getBaseRate() != null ? type.getBaseRate() : new BigDecimal("1.00");
        BigDecimal riskFactor = type.getRiskFactor() != null ? type.getRiskFactor() : BigDecimal.ONE;
        BigDecimal ageFactor = ageFactor(computeAge(customerDob));
        BigDecimal termYears = termInYears(start, end);

        BigDecimal perYearPremium = sumAssured
                .divide(new BigDecimal("1000"), 6, RoundingMode.HALF_UP)
                .multiply(baseRate)
                .multiply(ageFactor)
                .multiply(riskFactor);

        return perYearPremium.multiply(termYears).setScale(2, RoundingMode.HALF_UP);
    }

    private int computeAge(LocalDate dob) {
        if (dob == null) return 30; // sensible default if DOB isn't on file
        return Period.between(dob, LocalDate.now()).getYears();
    }

    /** Simple age-banded risk loading, same idea real insurers use. */
    private BigDecimal ageFactor(int age) {
        if (age <= 25) return new BigDecimal("0.90");
        if (age <= 40) return new BigDecimal("1.00");
        if (age <= 55) return new BigDecimal("1.25");
        if (age <= 65) return new BigDecimal("1.60");
        return new BigDecimal("2.00");
    }

    /** Term length in (fractional) years, rounded up to the nearest month, minimum 1 year. */
    private BigDecimal termInYears(LocalDate start, LocalDate end) {
        Period period = Period.between(start, end);
        int totalMonths = period.getYears() * 12 + period.getMonths() + (period.getDays() > 0 ? 1 : 0);
        totalMonths = Math.max(totalMonths, 1);
        return new BigDecimal(totalMonths).divide(new BigDecimal("12"), 6, RoundingMode.HALF_UP);
    }
}
