package com.finsight.domain.loan;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoanTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    private static Loan valid() {
        return new Loan(1L, "Home Loan", new BigDecimal("5000000"), new BigDecimal("0.085"), 240, START,
                new BigDecimal("5000000"), 240);
    }

    private static Loan partiallyPaid() {
        // principal = 10,00,000 over 60 months; currently 7,20,000 outstanding with 42 left,
        // matching the architecture decision's own example exactly.
        return new Loan(1L, "Home Loan", new BigDecimal("1000000"), new BigDecimal("0.09"), 60, START,
                new BigDecimal("720000"), 42);
    }

    @Test
    void constructsWithValidArguments() {
        Loan loan = valid();

        assertEquals(1L, loan.getId());
        assertEquals("Home Loan", loan.getName());
        assertEquals(new BigDecimal("5000000"), loan.getPrincipal());
        assertEquals(new BigDecimal("0.085"), loan.getAnnualInterestRate());
        assertEquals(240, loan.getTenureMonths());
        assertEquals(START, loan.getStartDate());
        assertEquals(new BigDecimal("5000000"), loan.getCurrentBalance());
        assertEquals(240, loan.getRemainingTenureMonths());
    }

    @Test
    void newLoanHasCurrentBalanceEqualToPrincipalAndFullRemainingTenure() {
        Loan loan = valid();

        assertEquals(0, loan.getPrincipal().compareTo(loan.getCurrentBalance()));
        assertEquals(loan.getTenureMonths(), loan.getRemainingTenureMonths());
    }

    @Test
    void existingPartiallyPaidLoanKeepsOriginalTermsDistinctFromCurrentState() {
        Loan loan = partiallyPaid();

        // Original terms: unaffected by how much has been paid down since.
        assertEquals(0, new BigDecimal("1000000").compareTo(loan.getPrincipal()));
        assertEquals(60, loan.getTenureMonths());
        // Current state: what's actually left to pay, as of this snapshot.
        assertEquals(0, new BigDecimal("720000").compareTo(loan.getCurrentBalance()));
        assertEquals(42, loan.getRemainingTenureMonths());
    }

    @Test
    void fullyPaidLoanHasZeroCurrentBalanceAndZeroRemainingTenure() {
        Loan loan = new Loan(1L, "Paid Off Loan", new BigDecimal("50000"), new BigDecimal("0.10"), 12, START,
                BigDecimal.ZERO, 0);

        assertEquals(0, BigDecimal.ZERO.compareTo(loan.getCurrentBalance()));
        assertEquals(0, loan.getRemainingTenureMonths());
    }

    @Test
    void allowsZeroInterestRate() {
        Loan loan = new Loan(1L, "Interest-free loan", BigDecimal.TEN, BigDecimal.ZERO, 12, START, BigDecimal.TEN, 12);

        assertEquals(0, BigDecimal.ZERO.compareTo(loan.getAnnualInterestRate()));
    }

    @Test
    void rejectsNegativeId() {
        assertThrows(IllegalArgumentException.class,
                () -> new Loan(-1L, "Home Loan", BigDecimal.TEN, BigDecimal.ZERO, 12, START, BigDecimal.TEN, 12));
    }

    @Test
    void rejectsNullName() {
        assertThrows(NullPointerException.class,
                () -> new Loan(1L, null, BigDecimal.TEN, BigDecimal.ZERO, 12, START, BigDecimal.TEN, 12));
    }

    @Test
    void rejectsBlankName() {
        assertThrows(IllegalArgumentException.class,
                () -> new Loan(1L, "   ", BigDecimal.TEN, BigDecimal.ZERO, 12, START, BigDecimal.TEN, 12));
    }

    @Test
    void rejectsNullPrincipal() {
        assertThrows(NullPointerException.class,
                () -> new Loan(1L, "Home Loan", null, BigDecimal.ZERO, 12, START, BigDecimal.TEN, 12));
    }

    @Test
    void rejectsZeroPrincipal() {
        assertThrows(IllegalArgumentException.class,
                () -> new Loan(1L, "Home Loan", BigDecimal.ZERO, BigDecimal.ZERO, 12, START, BigDecimal.ZERO, 12));
    }

    @Test
    void rejectsNegativePrincipal() {
        assertThrows(IllegalArgumentException.class,
                () -> new Loan(1L, "Home Loan", new BigDecimal("-1"), BigDecimal.ZERO, 12, START, BigDecimal.TEN, 12));
    }

    @Test
    void rejectsNullAnnualInterestRate() {
        assertThrows(NullPointerException.class,
                () -> new Loan(1L, "Home Loan", BigDecimal.TEN, null, 12, START, BigDecimal.TEN, 12));
    }

    @Test
    void rejectsNegativeAnnualInterestRate() {
        assertThrows(IllegalArgumentException.class,
                () -> new Loan(1L, "Home Loan", BigDecimal.TEN, new BigDecimal("-0.01"), 12, START, BigDecimal.TEN, 12));
    }

    @Test
    void rejectsZeroTenureMonths() {
        assertThrows(IllegalArgumentException.class,
                () -> new Loan(1L, "Home Loan", BigDecimal.TEN, BigDecimal.ZERO, 0, START, BigDecimal.TEN, 0));
    }

    @Test
    void rejectsNegativeTenureMonths() {
        assertThrows(IllegalArgumentException.class,
                () -> new Loan(1L, "Home Loan", BigDecimal.TEN, BigDecimal.ZERO, -1, START, BigDecimal.TEN, 0));
    }

    @Test
    void rejectsNullStartDate() {
        assertThrows(NullPointerException.class,
                () -> new Loan(1L, "Home Loan", BigDecimal.TEN, BigDecimal.ZERO, 12, null, BigDecimal.TEN, 12));
    }

    // ===================== currentBalance / remainingTenureMonths validation =====================

    @Test
    void rejectsNullCurrentBalance() {
        assertThrows(NullPointerException.class,
                () -> new Loan(1L, "Home Loan", BigDecimal.TEN, BigDecimal.ZERO, 12, START, null, 12));
    }

    @Test
    void rejectsNegativeCurrentBalance() {
        assertThrows(IllegalArgumentException.class,
                () -> new Loan(1L, "Home Loan", BigDecimal.TEN, BigDecimal.ZERO, 12, START, new BigDecimal("-1"), 12));
    }

    @Test
    void allowsZeroCurrentBalance() {
        Loan loan = new Loan(1L, "Home Loan", BigDecimal.TEN, BigDecimal.ZERO, 12, START, BigDecimal.ZERO, 0);

        assertEquals(0, BigDecimal.ZERO.compareTo(loan.getCurrentBalance()));
    }

    @Test
    void rejectsCurrentBalanceGreaterThanPrincipal() {
        assertThrows(IllegalArgumentException.class,
                () -> new Loan(1L, "Home Loan", BigDecimal.TEN, BigDecimal.ZERO, 12, START, new BigDecimal("10.01"), 12));
    }

    @Test
    void allowsCurrentBalanceEqualToPrincipal() {
        Loan loan = new Loan(1L, "Home Loan", BigDecimal.TEN, BigDecimal.ZERO, 12, START, BigDecimal.TEN, 12);

        assertEquals(0, BigDecimal.TEN.compareTo(loan.getCurrentBalance()));
    }

    @Test
    void rejectsNegativeRemainingTenureMonths() {
        assertThrows(IllegalArgumentException.class,
                () -> new Loan(1L, "Home Loan", BigDecimal.TEN, BigDecimal.ZERO, 12, START, BigDecimal.TEN, -1));
    }

    @Test
    void rejectsRemainingTenureMonthsGreaterThanTenureMonths() {
        assertThrows(IllegalArgumentException.class,
                () -> new Loan(1L, "Home Loan", BigDecimal.TEN, BigDecimal.ZERO, 12, START, BigDecimal.TEN, 13));
    }

    @Test
    void allowsRemainingTenureMonthsEqualToTenureMonths() {
        Loan loan = new Loan(1L, "Home Loan", BigDecimal.TEN, BigDecimal.ZERO, 12, START, BigDecimal.TEN, 12);

        assertEquals(12, loan.getRemainingTenureMonths());
    }

    @Test
    void rejectsPositiveCurrentBalanceWithZeroRemainingTenureMonths() {
        assertThrows(IllegalArgumentException.class,
                () -> new Loan(1L, "Home Loan", BigDecimal.TEN, BigDecimal.ZERO, 12, START, BigDecimal.ONE, 0));
    }

    @Test
    void allowsZeroCurrentBalanceWithPositiveRemainingTenureMonths() {
        // Unusual (paid off early) but not internally contradictory: LoanCalculator produces a
        // degenerate zero-payment schedule for the remaining months rather than crashing.
        Loan loan = new Loan(1L, "Home Loan", BigDecimal.TEN, BigDecimal.ZERO, 12, START, BigDecimal.ZERO, 5);

        assertEquals(0, BigDecimal.ZERO.compareTo(loan.getCurrentBalance()));
        assertEquals(5, loan.getRemainingTenureMonths());
    }

    // ===================== equality =====================

    @Test
    void equalityIsBasedOnIdOnly() {
        Loan a = valid();
        Loan sameIdDifferentFields = new Loan(1L, "Car Loan", BigDecimal.ONE, BigDecimal.ONE, 1, START.plusDays(1),
                BigDecimal.ONE, 1);
        Loan differentId = new Loan(2L, "Home Loan", new BigDecimal("5000000"), new BigDecimal("0.085"), 240, START,
                new BigDecimal("5000000"), 240);

        assertEquals(a, sameIdDifferentFields);
        assertEquals(a.hashCode(), sameIdDifferentFields.hashCode());
        assertNotEquals(a, differentId);
    }

    @Test
    void equalityIsUnaffectedByCurrentBalanceOrRemainingTenureMonths() {
        Loan a = valid();
        Loan sameIdDifferentCurrentState = new Loan(1L, "Home Loan", new BigDecimal("5000000"),
                new BigDecimal("0.085"), 240, START, new BigDecimal("3000000"), 120);

        assertEquals(a, sameIdDifferentCurrentState);
        assertEquals(a.hashCode(), sameIdDifferentCurrentState.hashCode());
    }

    @Test
    void equalsHandlesSelfNullAndOtherTypes() {
        Loan a = valid();

        assertEquals(a, a);
        assertNotEquals(null, a);
        assertFalse(a.equals("not a loan"));
    }

    @Test
    void withIdReturnsNewInstanceWithOnlyIdChanged() {
        Loan original = partiallyPaid();

        Loan withNewId = original.withId(42L);

        assertEquals(42L, withNewId.getId());
        assertEquals(original.getName(), withNewId.getName());
        assertEquals(0, original.getPrincipal().compareTo(withNewId.getPrincipal()));
        assertEquals(0, original.getAnnualInterestRate().compareTo(withNewId.getAnnualInterestRate()));
        assertEquals(original.getTenureMonths(), withNewId.getTenureMonths());
        assertEquals(original.getStartDate(), withNewId.getStartDate());
        assertEquals(0, original.getCurrentBalance().compareTo(withNewId.getCurrentBalance()));
        assertEquals(original.getRemainingTenureMonths(), withNewId.getRemainingTenureMonths());
        // The original instance itself is untouched.
        assertEquals(1L, original.getId());
    }
}
