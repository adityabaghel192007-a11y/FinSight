package com.finsight.simulation.scenario;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExpenseChangeEventTest {

    private static final LocalDate DATE = LocalDate.of(2026, 4, 1);

    @Test
    void constructsWithValidArgumentsAndExposesFields() {
        ExpenseChangeEvent event = new ExpenseChangeEvent(DATE, new BigDecimal("3000"));

        assertEquals(DATE, event.getEffectiveDate());
        assertEquals(new BigDecimal("3000"), event.getNewMonthlyExpenses());
    }

    @Test
    void allowsZeroExpenses() {
        ExpenseChangeEvent event = new ExpenseChangeEvent(DATE, BigDecimal.ZERO);

        assertEquals(0, BigDecimal.ZERO.compareTo(event.getNewMonthlyExpenses()));
    }

    @Test
    void rejectsNullEffectiveDate() {
        assertThrows(NullPointerException.class, () -> new ExpenseChangeEvent(null, BigDecimal.TEN));
    }

    @Test
    void rejectsNullNewMonthlyExpenses() {
        assertThrows(NullPointerException.class, () -> new ExpenseChangeEvent(DATE, null));
    }

    @Test
    void rejectsNegativeNewMonthlyExpenses() {
        assertThrows(IllegalArgumentException.class, () -> new ExpenseChangeEvent(DATE, new BigDecimal("-0.01")));
    }

    @Test
    void equalityIsBasedOnCompleteStateIgnoringScale() {
        ExpenseChangeEvent a = new ExpenseChangeEvent(DATE, new BigDecimal("3000"));
        ExpenseChangeEvent b = new ExpenseChangeEvent(DATE, new BigDecimal("3000.00"));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        ExpenseChangeEvent differentAmount = new ExpenseChangeEvent(DATE, new BigDecimal("4000"));
        assertNotEquals(a, differentAmount);

        ExpenseChangeEvent differentDate = new ExpenseChangeEvent(DATE.plusDays(1), new BigDecimal("3000"));
        assertNotEquals(a, differentDate);
    }
}
