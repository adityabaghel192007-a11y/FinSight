package com.finsight.simulation.calculator;

import com.finsight.domain.loan.Loan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoanCalculatorTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private static Loan loan(String principal, String annualRate, int tenureMonths) {
        // A "new" loan: currentBalance == principal, remainingTenureMonths == tenureMonths.
        return new Loan(1L, "Test Loan", new BigDecimal(principal), new BigDecimal(annualRate), tenureMonths, START,
                new BigDecimal(principal), tenureMonths);
    }

    private static Loan loanWithCurrentState(
            String principal, String annualRate, int tenureMonths, String currentBalance, int remainingTenureMonths) {
        return new Loan(1L, "Test Loan", new BigDecimal(principal), new BigDecimal(annualRate), tenureMonths, START,
                new BigDecimal(currentBalance), remainingTenureMonths);
    }

    private static void assertMoneyEquals(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }

    private static void assertApproximately(String expected, BigDecimal actual) {
        BigDecimal diff = new BigDecimal(expected).subtract(actual).abs();
        assertTrue(diff.compareTo(TOLERANCE) <= 0,
                "expected approximately " + expected + " but was " + actual);
    }

    // 1. Standard EMI calculation.
    @Test
    void standardEmiMatchesKnownValue() {
        Loan loan = loan("100000", "0.12", 12);

        BigDecimal emi = LoanCalculator.calculateMonthlyPayment(loan);

        assertApproximately("8884.88", emi);
    }

    // 2. Zero-interest loan.
    @Test
    void zeroInterestEmiIsPrincipalDividedByTenure() {
        Loan loan = loan("120000", "0", 12);

        BigDecimal emi = LoanCalculator.calculateMonthlyPayment(loan);

        assertMoneyEquals("10000.00", emi);
    }

    // 3. One-month loan.
    @Test
    void oneMonthLoanRepaysEntirePrincipalInSinglePayment() {
        Loan loan = loan("5000", "0.10", 1);

        AmortizationSchedule schedule = LoanCalculator.calculateAmortizationSchedule(loan);

        assertEquals(1, schedule.getPayments().size());
        LoanPayment onlyPayment = schedule.getPayments().get(0);
        assertMoneyEquals("5000.00", onlyPayment.getPrincipal());
        assertMoneyEquals("0.00", onlyPayment.getRemainingBalance());
        // payment must cover the full principal plus that month's interest
        assertEquals(0, onlyPayment.getPayment().compareTo(onlyPayment.getPrincipal().add(onlyPayment.getInterest())));
    }

    // 4. Amortization schedule contains exactly tenureMonths payments.
    @Test
    void scheduleContainsExactlyTenureMonthsPayments() {
        Loan loan = loan("250000", "0.09", 60);

        AmortizationSchedule schedule = LoanCalculator.calculateAmortizationSchedule(loan);

        assertEquals(60, schedule.getPayments().size());
        for (int i = 0; i < 60; i++) {
            assertEquals(i + 1, schedule.getPayments().get(i).getPaymentNumber());
        }
    }

    // 5. Every payment: payment = interest + principal.
    @Test
    void everyPaymentEqualsInterestPlusPrincipal() {
        Loan loan = loan("100000", "0.12", 12);

        AmortizationSchedule schedule = LoanCalculator.calculateAmortizationSchedule(loan);

        for (LoanPayment payment : schedule.getPayments()) {
            BigDecimal sum = payment.getInterest().add(payment.getPrincipal());
            assertEquals(0, payment.getPayment().compareTo(sum),
                    () -> "payment " + payment.getPaymentNumber() + ": " + payment);
        }
    }

    // 6. Remaining balance decreases over time.
    @Test
    void remainingBalanceDecreasesEachPeriod() {
        Loan loan = loan("100000", "0.12", 12);

        List<LoanPayment> payments = LoanCalculator.calculateAmortizationSchedule(loan).getPayments();

        BigDecimal previousBalance = loan.getPrincipal();
        for (LoanPayment payment : payments) {
            assertTrue(payment.getRemainingBalance().compareTo(previousBalance) < 0,
                    "balance should strictly decrease at payment " + payment.getPaymentNumber());
            previousBalance = payment.getRemainingBalance();
        }
    }

    // 7. Final remaining balance = 0.00.
    @Test
    void finalRemainingBalanceIsExactlyZero() {
        Loan loan = loan("837465.37", "0.0725", 84);

        List<LoanPayment> payments = LoanCalculator.calculateAmortizationSchedule(loan).getPayments();

        LoanPayment last = payments.get(payments.size() - 1);
        assertMoneyEquals("0.00", last.getRemainingBalance());
    }

    // 8. Sum of principal payments = original principal.
    @Test
    void sumOfPrincipalPaymentsEqualsOriginalPrincipal() {
        Loan loan = loan("100000", "0.12", 12);

        List<LoanPayment> payments = LoanCalculator.calculateAmortizationSchedule(loan).getPayments();

        BigDecimal totalPrincipal = payments.stream()
                .map(LoanPayment::getPrincipal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertMoneyEquals("100000.00", totalPrincipal);
    }

    // 9. Total interest is non-negative.
    @Test
    void totalInterestIsNonNegative() {
        Loan loan = loan("100000", "0.12", 12);

        List<LoanPayment> payments = LoanCalculator.calculateAmortizationSchedule(loan).getPayments();

        BigDecimal totalInterest = payments.stream()
                .map(LoanPayment::getInterest)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertTrue(totalInterest.signum() >= 0);
    }

    @Test
    void zeroInterestLoanHasZeroTotalInterest() {
        Loan loan = loan("120000", "0", 12);

        List<LoanPayment> payments = LoanCalculator.calculateAmortizationSchedule(loan).getPayments();

        BigDecimal totalInterest = payments.stream()
                .map(LoanPayment::getInterest)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertMoneyEquals("0.00", totalInterest);
    }

    // 10. Large principal / long tenure does not produce invalid values.
    @Test
    void largePrincipalLongTenureProducesValidSchedule() {
        Loan loan = loan("50000000", "0.085", 360);

        AmortizationSchedule schedule = LoanCalculator.calculateAmortizationSchedule(loan);

        assertEquals(360, schedule.getPayments().size());

        BigDecimal totalPrincipal = BigDecimal.ZERO;
        for (LoanPayment payment : schedule.getPayments()) {
            assertTrue(payment.getRemainingBalance().signum() >= 0, "balance must never go negative");
            assertTrue(payment.getPayment().signum() >= 0);
            assertTrue(payment.getInterest().signum() >= 0);
            assertTrue(payment.getPrincipal().signum() >= 0);
            totalPrincipal = totalPrincipal.add(payment.getPrincipal());
        }

        assertMoneyEquals("0.00", schedule.getPayments().get(359).getRemainingBalance());
        assertMoneyEquals("50000000.00", totalPrincipal);
    }

    @Test
    void veryLowInterestRateProducesValidSchedule() {
        Loan loan = loan("10000", "0.0001", 6);

        AmortizationSchedule schedule = LoanCalculator.calculateAmortizationSchedule(loan);

        assertEquals(6, schedule.getPayments().size());
        assertMoneyEquals("0.00", schedule.getPayments().get(5).getRemainingBalance());
    }

    // ===================== Current-state amortization (currentBalance + remainingTenureMonths) =====================

    @Test
    void partiallyPaidLoanAmortizesFromCurrentBalanceOverRemainingTenureNotOriginalTerms() {
        // Matches the architecture decision's own example: original 10,00,000 over 60 months,
        // currently 7,20,000 outstanding with 42 months left.
        Loan original = loanWithCurrentState("1000000", "0.09", 60, "1000000", 60);
        Loan partiallyPaid = loanWithCurrentState("1000000", "0.09", 60, "720000", 42);

        AmortizationSchedule originalSchedule = LoanCalculator.calculateAmortizationSchedule(original);
        AmortizationSchedule partialSchedule = LoanCalculator.calculateAmortizationSchedule(partiallyPaid);

        // The partially-paid loan's schedule is driven by its current state, not the original
        // principal/tenure: a shorter schedule, starting from a smaller balance.
        assertEquals(60, originalSchedule.getPayments().size());
        assertEquals(42, partialSchedule.getPayments().size());
        assertMoneyEquals("720000.00", partiallyPaid.getCurrentBalance());
        assertTrue(partialSchedule.getMonthlyPayment().compareTo(originalSchedule.getMonthlyPayment()) < 0,
                "a smaller current balance amortized over fewer months should still need a smaller EMI here");

        // The partial schedule's first beginning balance is currentBalance, not principal.
        LoanPayment firstPayment = partialSchedule.getPayments().get(0);
        BigDecimal impliedBeginningBalance = firstPayment.getRemainingBalance()
                .add(firstPayment.getPrincipal());
        assertMoneyEquals("720000.00", impliedBeginningBalance);
    }

    @Test
    void oneMonthRemainingLoanRepaysEntireCurrentBalanceInSinglePayment() {
        Loan loan = loanWithCurrentState("50000", "0.10", 24, "4800", 1);

        AmortizationSchedule schedule = LoanCalculator.calculateAmortizationSchedule(loan);

        assertEquals(1, schedule.getPayments().size());
        LoanPayment onlyPayment = schedule.getPayments().get(0);
        assertMoneyEquals("4800.00", onlyPayment.getPrincipal());
        assertMoneyEquals("0.00", onlyPayment.getRemainingBalance());
        assertEquals(0, onlyPayment.getPayment().compareTo(onlyPayment.getPrincipal().add(onlyPayment.getInterest())));
    }

    @Test
    void finalPaymentOfPartiallyPaidLoanClearsCurrentBalanceExactly() {
        Loan loan = loanWithCurrentState("1000000", "0.09", 60, "720000", 42);

        List<LoanPayment> payments = LoanCalculator.calculateAmortizationSchedule(loan).getPayments();
        LoanPayment last = payments.get(payments.size() - 1);

        assertMoneyEquals("0.00", last.getRemainingBalance());
        // Sum of every principal portion must equal the current balance being amortized, not the
        // original principal.
        BigDecimal totalPrincipal = payments.stream()
                .map(LoanPayment::getPrincipal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertMoneyEquals("720000.00", totalPrincipal);
    }

    @Test
    void noNegativeBalanceForPartiallyPaidLoanAcrossFullSchedule() {
        Loan loan = loanWithCurrentState("1000000", "0.09", 60, "720000", 42);

        for (LoanPayment payment : LoanCalculator.calculateAmortizationSchedule(loan).getPayments()) {
            assertTrue(payment.getRemainingBalance().signum() >= 0, "balance must never go negative");
        }
    }

    @Test
    void fullyPaidLoanProducesEmptyScheduleAndZeroMonthlyPayment() {
        Loan loan = loanWithCurrentState("50000", "0.10", 12, "0", 0);

        assertMoneyEquals("0.00", LoanCalculator.calculateMonthlyPayment(loan));
        AmortizationSchedule schedule = LoanCalculator.calculateAmortizationSchedule(loan);
        assertEquals(0, schedule.getPayments().size());
        assertMoneyEquals("0.00", schedule.getMonthlyPayment());
    }

    @Test
    void zeroInterestPartiallyPaidLoanAmortizesRemainingBalanceEvenly() {
        Loan loan = loanWithCurrentState("120000", "0", 12, "60000", 6);

        BigDecimal emi = LoanCalculator.calculateMonthlyPayment(loan);
        assertMoneyEquals("10000.00", emi);

        List<LoanPayment> payments = LoanCalculator.calculateAmortizationSchedule(loan).getPayments();
        assertEquals(6, payments.size());
        assertMoneyEquals("0.00", payments.get(5).getRemainingBalance());
    }

    // 11. Repeated calculation with the same Loan produces identical results.
    @Test
    void calculationIsDeterministic() {
        Loan loan = loan("100000", "0.12", 12);

        BigDecimal emi1 = LoanCalculator.calculateMonthlyPayment(loan);
        BigDecimal emi2 = LoanCalculator.calculateMonthlyPayment(loan);
        assertEquals(0, emi1.compareTo(emi2));

        AmortizationSchedule schedule1 = LoanCalculator.calculateAmortizationSchedule(loan);
        AmortizationSchedule schedule2 = LoanCalculator.calculateAmortizationSchedule(loan);
        assertEquals(schedule1, schedule2);
        assertEquals(schedule1.hashCode(), schedule2.hashCode());
    }

    @Test
    void calculateMonthlyPaymentRejectsNullLoan() {
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> LoanCalculator.calculateMonthlyPayment(null));
    }

    @Test
    void calculateAmortizationScheduleRejectsNullLoan() {
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> LoanCalculator.calculateAmortizationSchedule(null));
    }
}
