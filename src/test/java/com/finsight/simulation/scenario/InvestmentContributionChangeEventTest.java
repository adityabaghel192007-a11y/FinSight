package com.finsight.simulation.scenario;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvestmentContributionChangeEventTest {

    private static final LocalDate DATE = LocalDate.of(2026, 5, 1);

    @Test
    void constructsWithValidArgumentsAndExposesFields() {
        InvestmentContributionChangeEvent event = new InvestmentContributionChangeEvent(DATE, 5L, new BigDecimal("1500"));

        assertEquals(DATE, event.getEffectiveDate());
        assertEquals(5L, event.getInvestmentId());
        assertEquals(new BigDecimal("1500"), event.getNewMonthlyContribution());
    }

    @Test
    void allowsZeroInvestmentIdAndZeroContribution() {
        InvestmentContributionChangeEvent event = new InvestmentContributionChangeEvent(DATE, 0L, BigDecimal.ZERO);

        assertEquals(0L, event.getInvestmentId());
        assertEquals(0, BigDecimal.ZERO.compareTo(event.getNewMonthlyContribution()));
    }

    @Test
    void rejectsNullEffectiveDate() {
        assertThrows(NullPointerException.class, () -> new InvestmentContributionChangeEvent(null, 1L, BigDecimal.TEN));
    }

    @Test
    void rejectsNegativeInvestmentId() {
        assertThrows(IllegalArgumentException.class, () -> new InvestmentContributionChangeEvent(DATE, -1L, BigDecimal.TEN));
    }

    @Test
    void rejectsNullNewMonthlyContribution() {
        assertThrows(NullPointerException.class, () -> new InvestmentContributionChangeEvent(DATE, 1L, null));
    }

    @Test
    void rejectsNegativeNewMonthlyContribution() {
        assertThrows(IllegalArgumentException.class, () -> new InvestmentContributionChangeEvent(DATE, 1L, new BigDecimal("-1")));
    }

    @Test
    void equalityIsBasedOnCompleteStateIgnoringScale() {
        InvestmentContributionChangeEvent a = new InvestmentContributionChangeEvent(DATE, 1L, new BigDecimal("1500"));
        InvestmentContributionChangeEvent b = new InvestmentContributionChangeEvent(DATE, 1L, new BigDecimal("1500.00"));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        InvestmentContributionChangeEvent differentId = new InvestmentContributionChangeEvent(DATE, 2L, new BigDecimal("1500"));
        assertNotEquals(a, differentId);

        InvestmentContributionChangeEvent differentAmount = new InvestmentContributionChangeEvent(DATE, 1L, new BigDecimal("2000"));
        assertNotEquals(a, differentAmount);

        InvestmentContributionChangeEvent differentDate = new InvestmentContributionChangeEvent(DATE.plusDays(1), 1L, new BigDecimal("1500"));
        assertNotEquals(a, differentDate);
    }
}
