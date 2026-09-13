package com.finsight.simulation.scenario;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OneTimeExpenseEventTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 1);

    @Test
    void constructsWithValidArgumentsAndExposesFields() {
        OneTimeExpenseEvent event = new OneTimeExpenseEvent(DATE, new BigDecimal("25000"));

        assertEquals(DATE, event.getEffectiveDate());
        assertEquals(new BigDecimal("25000"), event.getAmount());
    }

    @Test
    void allowsZeroAmount() {
        OneTimeExpenseEvent event = new OneTimeExpenseEvent(DATE, BigDecimal.ZERO);

        assertEquals(0, BigDecimal.ZERO.compareTo(event.getAmount()));
    }

    @Test
    void rejectsNullEffectiveDate() {
        assertThrows(NullPointerException.class, () -> new OneTimeExpenseEvent(null, BigDecimal.TEN));
    }

    @Test
    void rejectsNullAmount() {
        assertThrows(NullPointerException.class, () -> new OneTimeExpenseEvent(DATE, null));
    }

    @Test
    void rejectsNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> new OneTimeExpenseEvent(DATE, new BigDecimal("-0.01")));
    }

    @Test
    void equalityIsBasedOnCompleteStateIgnoringScale() {
        OneTimeExpenseEvent a = new OneTimeExpenseEvent(DATE, new BigDecimal("25000"));
        OneTimeExpenseEvent b = new OneTimeExpenseEvent(DATE, new BigDecimal("25000.00"));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        OneTimeExpenseEvent differentAmount = new OneTimeExpenseEvent(DATE, new BigDecimal("30000"));
        assertNotEquals(a, differentAmount);

        OneTimeExpenseEvent differentDate = new OneTimeExpenseEvent(DATE.plusDays(1), new BigDecimal("25000"));
        assertNotEquals(a, differentDate);
    }
}
