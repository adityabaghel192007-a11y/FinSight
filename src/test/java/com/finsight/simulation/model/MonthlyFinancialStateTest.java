package com.finsight.simulation.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonthlyFinancialStateTest {

    private static final LocalDate DATE = LocalDate.of(2026, 2, 1);

    private static MonthlyFinancialState state(
            BigDecimal cashBalance, BigDecimal investmentValue, BigDecimal loanBalance,
            BigDecimal monthlyIncome, BigDecimal monthlyExpenses, BigDecimal investmentContribution,
            BigDecimal loanPayment, BigDecimal netWorth, BigDecimal cumulativeSavings) {
        return new MonthlyFinancialState(DATE, cashBalance, investmentValue, loanBalance, monthlyIncome,
                monthlyExpenses, investmentContribution, loanPayment, netWorth, cumulativeSavings);
    }

    private static MonthlyFinancialState valid() {
        return state(new BigDecimal("5000"), new BigDecimal("110000"), new BigDecimal("90000"),
                new BigDecimal("80000"), new BigDecimal("50000"), new BigDecimal("5000"),
                new BigDecimal("8000"), new BigDecimal("125000"), new BigDecimal("30000"));
    }

    @Test
    void constructsWithValidArgumentsAndExposesAllFields() {
        MonthlyFinancialState s = valid();

        assertEquals(DATE, s.getDate());
        assertEquals(new BigDecimal("5000"), s.getCashBalance());
        assertEquals(new BigDecimal("110000"), s.getInvestmentValue());
        assertEquals(new BigDecimal("90000"), s.getLoanBalance());
        assertEquals(new BigDecimal("80000"), s.getMonthlyIncome());
        assertEquals(new BigDecimal("50000"), s.getMonthlyExpenses());
        assertEquals(new BigDecimal("5000"), s.getInvestmentContribution());
        assertEquals(new BigDecimal("8000"), s.getLoanPayment());
        assertEquals(new BigDecimal("125000"), s.getNetWorth());
        assertEquals(new BigDecimal("30000"), s.getCumulativeSavings());
    }

    @Test
    void allowsNegativeCashBalanceNetWorthAndCumulativeSavings() {
        MonthlyFinancialState s = state(new BigDecimal("-500"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("-1000"), new BigDecimal("-3000"));

        assertTrue(s.getCashBalance().signum() < 0);
        assertTrue(s.getNetWorth().signum() < 0);
        assertTrue(s.getCumulativeSavings().signum() < 0);
    }

    @Test
    void rejectsNullDate() {
        assertThrows(NullPointerException.class, () -> new MonthlyFinancialState(
                null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void rejectsNullMonetaryFields() {
        assertThrows(NullPointerException.class, () -> state(
                null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        assertThrows(NullPointerException.class, () -> state(
                BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        assertThrows(NullPointerException.class, () -> state(
                BigDecimal.ZERO, BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        assertThrows(NullPointerException.class, () -> state(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        assertThrows(NullPointerException.class, () -> state(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        assertThrows(NullPointerException.class, () -> state(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        assertThrows(NullPointerException.class, () -> state(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO));
        assertThrows(NullPointerException.class, () -> state(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, null, BigDecimal.ZERO));
        assertThrows(NullPointerException.class, () -> state(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null));
    }

    @Test
    void rejectsNegativeInvestmentValue() {
        assertThrows(IllegalArgumentException.class, () -> state(
                BigDecimal.ZERO, new BigDecimal("-1"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void rejectsNegativeLoanBalance() {
        assertThrows(IllegalArgumentException.class, () -> state(
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("-1"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void rejectsNegativeMonthlyIncome() {
        assertThrows(IllegalArgumentException.class, () -> state(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("-1"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void rejectsNegativeMonthlyExpenses() {
        assertThrows(IllegalArgumentException.class, () -> state(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("-1"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void rejectsNegativeInvestmentContribution() {
        assertThrows(IllegalArgumentException.class, () -> state(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("-1"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void rejectsNegativeLoanPayment() {
        assertThrows(IllegalArgumentException.class, () -> state(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("-1"), BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void equalityIsBasedOnCompleteStateIgnoringScale() {
        MonthlyFinancialState a = valid();
        MonthlyFinancialState b = new MonthlyFinancialState(DATE, new BigDecimal("5000.00"),
                new BigDecimal("110000.00"), new BigDecimal("90000.00"), new BigDecimal("80000.00"),
                new BigDecimal("50000.00"), new BigDecimal("5000.00"), new BigDecimal("8000.00"),
                new BigDecimal("125000.00"), new BigDecimal("30000.00"));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        MonthlyFinancialState different = state(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        assertNotEquals(a, different);
    }
}
