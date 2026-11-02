package com.insurance.service;

import com.insurance.exception.BadRequestException;
import com.insurance.model.PolicyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the premium calculation engine.
 *
 * premium = (sumAssured / 1000) x baseRate x ageFactor x riskFactor x termYears
 */
class PremiumCalculationServiceTest {

    private PremiumCalculationService service;
    private PolicyType standardType;

    @BeforeEach
    void setUp() {
        service = new PremiumCalculationService();
        standardType = PolicyType.builder()
                .id(1)
                .name("Term Life")
                .baseRate(new BigDecimal("1.00"))
                .riskFactor(new BigDecimal("1.00"))
                .build();
    }

    @Test
    @DisplayName("Calculates a straightforward one-year premium correctly")
    void calculate_basicOneYearTerm_returnsExpectedPremium() {
        LocalDate dob = LocalDate.now().minusYears(30); // age 30 -> ageFactor 1.00
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusYears(1);

        BigDecimal premium = service.calculate(new BigDecimal("100000"), standardType, dob, start, end);

        // (100000 / 1000) * 1.00 * 1.00 * 1.00 * 1 year = 100.00
        assertThat(premium).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Doubles the premium when the term is two years")
    void calculate_twoYearTerm_scalesLinearlyWithTermLength() {
        LocalDate dob = LocalDate.now().minusYears(30);
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusYears(2);

        BigDecimal premium = service.calculate(new BigDecimal("100000"), standardType, dob, start, end);

        assertThat(premium).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("Applies the base rate and risk factor from the policy type")
    void calculate_appliesTypeBaseRateAndRiskFactor() {
        PolicyType health = PolicyType.builder()
                .id(2)
                .name("Health")
                .baseRate(new BigDecimal("2.00"))
                .riskFactor(new BigDecimal("1.50"))
                .build();
        LocalDate dob = LocalDate.now().minusYears(30);
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusYears(1);

        BigDecimal premium = service.calculate(new BigDecimal("100000"), health, dob, start, end);

        // (100000/1000) * 2.00 * 1.00 * 1.50 * 1 = 300.00
        assertThat(premium).isEqualByComparingTo("300.00");
    }

    @Test
    @DisplayName("Young applicants (<=25) get the discounted age factor of 0.90")
    void calculate_youngApplicant_getsDiscountFactor() {
        LocalDate dob = LocalDate.now().minusYears(25);
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusYears(1);

        BigDecimal premium = service.calculate(new BigDecimal("100000"), standardType, dob, start, end);

        assertThat(premium).isEqualByComparingTo("90.00");
    }

    @Test
    @DisplayName("Applicants aged 56-65 get the 1.60 age loading")
    void calculate_olderApplicant_getsHigherAgeFactor() {
        LocalDate dob = LocalDate.now().minusYears(60);
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusYears(1);

        BigDecimal premium = service.calculate(new BigDecimal("100000"), standardType, dob, start, end);

        assertThat(premium).isEqualByComparingTo("160.00");
    }

    @Test
    @DisplayName("Applicants over 65 get the maximum 2.00 age loading")
    void calculate_veryOldApplicant_getsMaxAgeFactor() {
        LocalDate dob = LocalDate.now().minusYears(70);
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusYears(1);

        BigDecimal premium = service.calculate(new BigDecimal("100000"), standardType, dob, start, end);

        assertThat(premium).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("Missing date of birth falls back to a default age of 30 (ageFactor 1.00)")
    void calculate_nullDob_defaultsToAgeFactorOne() {
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusYears(1);

        BigDecimal premium = service.calculate(new BigDecimal("100000"), standardType, null, start, end);

        assertThat(premium).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("A partial-month term is rounded up to the next full month")
    void calculate_partialTerm_roundsUpToNextMonth() {
        LocalDate dob = LocalDate.now().minusYears(30);
        LocalDate start = LocalDate.now();
        // Exactly 1 month + 1 day -> rounds up to 2 months -> 2/12 years
        LocalDate end = start.plusMonths(1).plusDays(1);

        BigDecimal premium = service.calculate(new BigDecimal("120000"), standardType, dob, start, end);

        // (120000/1000) * 1.00 * 1.00 * 1.00 * (2/12) = 120 * 0.166667 = 20.00
        assertThat(premium).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("Null sum assured is rejected")
    void calculate_nullSumAssured_throwsBadRequest() {
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusYears(1);

        assertThatThrownBy(() -> service.calculate(null, standardType, LocalDate.now().minusYears(30), start, end))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Sum assured");
    }

    @Test
    @DisplayName("Zero or negative sum assured is rejected")
    void calculate_zeroSumAssured_throwsBadRequest() {
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusYears(1);

        assertThatThrownBy(() -> service.calculate(BigDecimal.ZERO, standardType, LocalDate.now().minusYears(30), start, end))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Missing policy type is rejected")
    void calculate_nullType_throwsBadRequest() {
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusYears(1);

        assertThatThrownBy(() -> service.calculate(new BigDecimal("100000"), null, LocalDate.now().minusYears(30), start, end))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Policy type");
    }

    @Test
    @DisplayName("End date on or before the start date is rejected")
    void calculate_endNotAfterStart_throwsBadRequest() {
        LocalDate start = LocalDate.now();

        assertThatThrownBy(() -> service.calculate(new BigDecimal("100000"), standardType,
                LocalDate.now().minusYears(30), start, start))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("start and end date");
    }

    @Test
    @DisplayName("Missing base rate / risk factor on the policy type falls back to sensible defaults")
    void calculate_typeWithoutRates_usesDefaults() {
        PolicyType bareType = PolicyType.builder().id(3).name("Bare").build();
        bareType.setBaseRate(null);
        bareType.setRiskFactor(null);
        LocalDate dob = LocalDate.now().minusYears(30);
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusYears(1);

        BigDecimal premium = service.calculate(new BigDecimal("100000"), bareType, dob, start, end);

        assertThat(premium).isEqualByComparingTo("100.00");
    }
}
