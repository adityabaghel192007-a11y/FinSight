package com.finsight.simulation.scenario;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvestmentReturnChangeEventTest {

    private static final LocalDate DATE = LocalDate.of(2026, 6, 1);

    @Test
    void constructsWithValidArgumentsAndExposesFields() {
        InvestmentReturnChangeEvent event = new InvestmentReturnChangeEvent(DATE, 7L, new BigDecimal("0.15"));

        assertEquals(DATE, event.getEffectiveDate());
        assertEquals(7L, event.getInvestmentId());
        assertEquals(new BigDecimal("0.15"), event.getNewExpectedAnnualReturn());
    }

    @Test
    void allowsZeroInvestmentIdAndZeroReturn() {
        InvestmentReturnChangeEvent event = new InvestmentReturnChangeEvent(DATE, 0L, BigDecimal.ZERO);

        assertEquals(0L, event.getInvestmentId());
        assertEquals(0, BigDecimal.ZERO.compareTo(event.getNewExpectedAnnualReturn()));
    }

    @Test
    void rejectsNullEffectiveDate() {
        assertThrows(NullPointerException.class, () -> new InvestmentReturnChangeEvent(null, 1L, new BigDecimal("0.1")));
    }

    @Test
    void rejectsNegativeInvestmentId() {
        assertThrows(IllegalArgumentException.class, () -> new InvestmentReturnChangeEvent(DATE, -1L, new BigDecimal("0.1")));
    }

    @Test
    void rejectsNullNewExpectedAnnualReturn() {
        assertThrows(NullPointerException.class, () -> new InvestmentReturnChangeEvent(DATE, 1L, null));
    }

    @Test
    void rejectsNegativeNewExpectedAnnualReturn() {
        assertThrows(IllegalArgumentException.class, () -> new InvestmentReturnChangeEvent(DATE, 1L, new BigDecimal("-0.01")));
    }

    @Test
    void equalityIsBasedOnCompleteStateIgnoringScale() {
        InvestmentReturnChangeEvent a = new InvestmentReturnChangeEvent(DATE, 1L, new BigDecimal("0.15"));
        InvestmentReturnChangeEvent b = new InvestmentReturnChangeEvent(DATE, 1L, new BigDecimal("0.150"));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        InvestmentReturnChangeEvent differentId = new InvestmentReturnChangeEvent(DATE, 2L, new BigDecimal("0.15"));
        assertNotEquals(a, differentId);

        InvestmentReturnChangeEvent differentRate = new InvestmentReturnChangeEvent(DATE, 1L, new BigDecimal("0.20"));
        assertNotEquals(a, differentRate);

        InvestmentReturnChangeEvent differentDate = new InvestmentReturnChangeEvent(DATE.plusDays(1), 1L, new BigDecimal("0.15"));
        assertNotEquals(a, differentDate);
    }
}
