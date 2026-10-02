package com.finsight.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Validation and equality tests for {@link FinancialOverviewData}, mirroring the style already
 * established by {@code FinancialSnapshotTest} for the closest existing precedent value object.
 * {@link FinancialOverviewApplicationServiceTest} separately covers the aggregation that produces
 * these values in the first place; this class only exercises the value object itself.
 */
class FinancialOverviewDataTest {

    private static FinancialOverviewData data(
            String income, String expenses, String cash, String investments, String loans, String netWorth) {
        return new FinancialOverviewData(
                new BigDecimal(income), new BigDecimal(expenses), new BigDecimal(cash),
                new BigDecimal(investments), new BigDecimal(loans), new BigDecimal(netWorth));
    }

    @Test
    void constructsWithValidArgumentsAndExposesEveryFigure() {
        FinancialOverviewData overview = data("60000.00", "40000.00", "150000.00", "25000.00", "50000.00", "125000.00");

        assertEquals(0, new BigDecimal("60000.00").compareTo(overview.getMonthlyIncome()));
        assertEquals(0, new BigDecimal("40000.00").compareTo(overview.getMonthlyExpenses()));
        assertEquals(0, new BigDecimal("150000.00").compareTo(overview.getCash()));
        assertEquals(0, new BigDecimal("25000.00").compareTo(overview.getInvestments()));
        assertEquals(0, new BigDecimal("50000.00").compareTo(overview.getLoans()));
        assertEquals(0, new BigDecimal("125000.00").compareTo(overview.getNetWorth()));
    }

    @Test
    void computesMonthlySavingsOnceFromIncomeAndExpensesRatherThanAcceptingItAsAParameter() {
        FinancialOverviewData overview = data("60000.00", "40000.00", "0", "0", "0", "0");

        assertEquals(0, new BigDecimal("20000.00").compareTo(overview.getMonthlySavings()));
    }

    @Test
    void monthlySavingsIsNegativeWhenExpensesExceedIncomeAndIsNotRejected() {
        FinancialOverviewData overview = data("30000.00", "45000.00", "0", "0", "0", "0");

        assertEquals(0, new BigDecimal("-15000.00").compareTo(overview.getMonthlySavings()));
    }

    @Test
    void doesNotRecomputeOrCrossCheckTheSuppliedNetWorth() {
        // A netWorth that does NOT actually equal cash + investments - loans must still be
        // accepted and returned unchanged: this class deliberately trusts its caller
        // (FinancialOverviewApplicationService) rather than re-deriving NetWorthCalculator's
        // formula itself. See this class's own class-level documentation.
        FinancialOverviewData overview = data("0", "0", "100.00", "0", "0", "999999.00");

        assertEquals(0, new BigDecimal("999999.00").compareTo(overview.getNetWorth()));
    }

    @Test
    void allowsNegativeCash() {
        FinancialOverviewData overview = data("0", "0", "-500.00", "0", "0", "-500.00");

        assertEquals(0, new BigDecimal("-500.00").compareTo(overview.getCash()));
    }

    @Test
    void allowsNegativeNetWorth() {
        FinancialOverviewData overview = data("0", "0", "0", "0", "50000.00", "-50000.00");

        assertEquals(0, new BigDecimal("-50000.00").compareTo(overview.getNetWorth()));
    }

    @Test
    void rejectsNegativeMonthlyIncome() {
        assertThrows(IllegalArgumentException.class, () -> data("-1", "0", "0", "0", "0", "0"));
    }

    @Test
    void rejectsNegativeMonthlyExpenses() {
        assertThrows(IllegalArgumentException.class, () -> data("0", "-1", "0", "0", "0", "0"));
    }

    @Test
    void rejectsNegativeInvestments() {
        assertThrows(IllegalArgumentException.class, () -> data("0", "0", "0", "-1", "0", "0"));
    }

    @Test
    void rejectsNegativeLoans() {
        assertThrows(IllegalArgumentException.class, () -> data("0", "0", "0", "0", "-1", "0"));
    }

    @Test
    void rejectsNullMonthlyIncome() {
        assertThrows(NullPointerException.class, () -> new FinancialOverviewData(
                null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void rejectsNullMonthlyExpenses() {
        assertThrows(NullPointerException.class, () -> new FinancialOverviewData(
                BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void rejectsNullCash() {
        assertThrows(NullPointerException.class, () -> new FinancialOverviewData(
                BigDecimal.ZERO, BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void rejectsNullInvestments() {
        assertThrows(NullPointerException.class, () -> new FinancialOverviewData(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void rejectsNullLoans() {
        assertThrows(NullPointerException.class, () -> new FinancialOverviewData(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, BigDecimal.ZERO));
    }

    @Test
    void rejectsNullNetWorth() {
        assertThrows(NullPointerException.class, () -> new FinancialOverviewData(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null));
    }

    @Test
    void equalityIsBasedOnAllSevenFiguresIgnoringBigDecimalScale() {
        FinancialOverviewData a = data("60000.00", "40000.00", "150000", "25000", "50000", "125000");
        FinancialOverviewData b = data("60000.00", "40000.00", "150000.00", "25000.00", "50000.00", "125000.00");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        FinancialOverviewData different = data("60000.00", "40000.00", "150000.00", "25000.00", "50000.00", "999.00");
        assertNotEquals(a, different);
    }
}
