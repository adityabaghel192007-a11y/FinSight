package com.finsight.simulation.scenario;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncomeChangeEventTest {

    private static final LocalDate DATE = LocalDate.of(2026, 3, 1);

    @Test
    void constructsWithValidArgumentsAndExposesFields() {
        IncomeChangeEvent event = new IncomeChangeEvent(DATE, new BigDecimal("6000"));

        assertEquals(DATE, event.getEffectiveDate());
        assertEquals(new BigDecimal("6000"), event.getNewMonthlyIncome());
    }

    @Test
    void allowsZeroIncome() {
        IncomeChangeEvent event = new IncomeChangeEvent(DATE, BigDecimal.ZERO);

        assertEquals(0, BigDecimal.ZERO.compareTo(event.getNewMonthlyIncome()));
    }

    @Test
    void rejectsNullEffectiveDate() {
        assertThrows(NullPointerException.class, () -> new IncomeChangeEvent(null, BigDecimal.TEN));
    }

    @Test
    void rejectsNullNewMonthlyIncome() {
        assertThrows(NullPointerException.class, () -> new IncomeChangeEvent(DATE, null));
    }

    @Test
    void rejectsNegativeNewMonthlyIncome() {
        assertThrows(IllegalArgumentException.class, () -> new IncomeChangeEvent(DATE, new BigDecimal("-0.01")));
    }

    @Test
    void equalityIsBasedOnCompleteStateIgnoringScale() {
        IncomeChangeEvent a = new IncomeChangeEvent(DATE, new BigDecimal("6000"));
        IncomeChangeEvent b = new IncomeChangeEvent(DATE, new BigDecimal("6000.00"));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        IncomeChangeEvent differentAmount = new IncomeChangeEvent(DATE, new BigDecimal("7000"));
        assertNotEquals(a, differentAmount);

        IncomeChangeEvent differentDate = new IncomeChangeEvent(DATE.plusDays(1), new BigDecimal("6000"));
        assertNotEquals(a, differentDate);
    }
}
