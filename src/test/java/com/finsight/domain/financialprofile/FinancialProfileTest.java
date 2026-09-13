package com.finsight.domain.financialprofile;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinancialProfileTest {

    @Test
    void constructsWithValidArguments() {
        FinancialProfile profile = new FinancialProfile(1L, new BigDecimal("5000.00"), new BigDecimal("3200.00"));

        assertEquals(1L, profile.getId());
        assertEquals(new BigDecimal("5000.00"), profile.getMonthlyIncome());
        assertEquals(new BigDecimal("3200.00"), profile.getMonthlyExpenses());
    }

    @Test
    void allowsZeroMonthlyIncome() {
        FinancialProfile profile = new FinancialProfile(1L, BigDecimal.ZERO, new BigDecimal("100.00"));

        assertEquals(BigDecimal.ZERO, profile.getMonthlyIncome());
    }

    @Test
    void allowsZeroMonthlyExpenses() {
        FinancialProfile profile = new FinancialProfile(1L, new BigDecimal("100.00"), BigDecimal.ZERO);

        assertEquals(BigDecimal.ZERO, profile.getMonthlyExpenses());
    }

    @Test
    void allowsZeroId() {
        FinancialProfile profile = new FinancialProfile(0L, BigDecimal.ZERO, BigDecimal.ZERO);

        assertEquals(0L, profile.getId());
    }

    @Test
    void rejectsNegativeId() {
        assertThrows(IllegalArgumentException.class,
                () -> new FinancialProfile(-1L, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void rejectsNullMonthlyIncome() {
        assertThrows(NullPointerException.class,
                () -> new FinancialProfile(1L, null, BigDecimal.ZERO));
    }

    @Test
    void rejectsNegativeMonthlyIncome() {
        assertThrows(IllegalArgumentException.class,
                () -> new FinancialProfile(1L, new BigDecimal("-0.01"), BigDecimal.ZERO));
    }

    @Test
    void rejectsNullMonthlyExpenses() {
        assertThrows(NullPointerException.class,
                () -> new FinancialProfile(1L, BigDecimal.ZERO, null));
    }

    @Test
    void rejectsNegativeMonthlyExpenses() {
        assertThrows(IllegalArgumentException.class,
                () -> new FinancialProfile(1L, BigDecimal.ZERO, new BigDecimal("-0.01")));
    }

    @Test
    void equalityIsBasedOnIdOnly() {
        FinancialProfile a = new FinancialProfile(1L, new BigDecimal("100"), new BigDecimal("50"));
        FinancialProfile sameIdDifferentFields = new FinancialProfile(1L, new BigDecimal("999"), new BigDecimal("1"));
        FinancialProfile differentId = new FinancialProfile(2L, new BigDecimal("100"), new BigDecimal("50"));

        assertEquals(a, sameIdDifferentFields);
        assertEquals(a.hashCode(), sameIdDifferentFields.hashCode());
        assertNotEquals(a, differentId);
    }

    @Test
    void equalsHandlesSelfNullAndOtherTypes() {
        FinancialProfile a = new FinancialProfile(1L, BigDecimal.ZERO, BigDecimal.ZERO);

        assertEquals(a, a);
        assertNotEquals(null, a);
        assertFalse(a.equals("not a financial profile"));
    }

    @Test
    void withIdReturnsNewInstanceWithOnlyIdChanged() {
        FinancialProfile original = new FinancialProfile(1L, new BigDecimal("5000.00"), new BigDecimal("3200.00"));

        FinancialProfile withNewId = original.withId(42L);

        assertEquals(42L, withNewId.getId());
        assertEquals(0, original.getMonthlyIncome().compareTo(withNewId.getMonthlyIncome()));
        assertEquals(0, original.getMonthlyExpenses().compareTo(withNewId.getMonthlyExpenses()));
        // The original instance itself is untouched.
        assertEquals(1L, original.getId());
    }

    @Test
    void toStringContainsKeyFields() {
        FinancialProfile profile = new FinancialProfile(1L, new BigDecimal("5000.00"), new BigDecimal("3200.00"));

        String s = profile.toString();
        assertTrue(s.contains("5000.00"));
        assertTrue(s.contains("3200.00"));
    }
}
