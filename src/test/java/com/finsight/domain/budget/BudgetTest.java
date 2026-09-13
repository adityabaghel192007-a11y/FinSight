package com.finsight.domain.budget;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BudgetTest {

    private static final YearMonth MONTH = YearMonth.of(2026, 3);

    private static Budget valid() {
        return new Budget(1L, 5L, MONTH, new BigDecimal("15000"));
    }

    @Test
    void constructsWithValidArguments() {
        Budget budget = valid();

        assertEquals(1L, budget.getId());
        assertEquals(5L, budget.getCategoryId());
        assertEquals(MONTH, budget.getMonth());
        assertEquals(new BigDecimal("15000"), budget.getAmount());
    }

    @Test
    void allowsZeroAmount() {
        Budget budget = new Budget(1L, 5L, MONTH, BigDecimal.ZERO);

        assertEquals(0, BigDecimal.ZERO.compareTo(budget.getAmount()));
    }

    @Test
    void allowsZeroIdAndCategoryId() {
        Budget budget = new Budget(0L, 0L, MONTH, BigDecimal.ZERO);

        assertEquals(0L, budget.getId());
        assertEquals(0L, budget.getCategoryId());
    }

    @Test
    void rejectsNegativeId() {
        assertThrows(IllegalArgumentException.class, () -> new Budget(-1L, 5L, MONTH, BigDecimal.ZERO));
    }

    @Test
    void rejectsNegativeCategoryId() {
        assertThrows(IllegalArgumentException.class, () -> new Budget(1L, -1L, MONTH, BigDecimal.ZERO));
    }

    @Test
    void rejectsNullMonth() {
        assertThrows(NullPointerException.class, () -> new Budget(1L, 5L, null, BigDecimal.ZERO));
    }

    @Test
    void rejectsNullAmount() {
        assertThrows(NullPointerException.class, () -> new Budget(1L, 5L, MONTH, null));
    }

    @Test
    void rejectsNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> new Budget(1L, 5L, MONTH, new BigDecimal("-1")));
    }

    @Test
    void equalityIsBasedOnIdOnly() {
        Budget a = valid();
        Budget sameIdDifferentFields = new Budget(1L, 99L, MONTH.plusMonths(1), BigDecimal.ONE);
        Budget differentId = new Budget(2L, 5L, MONTH, new BigDecimal("15000"));

        assertEquals(a, sameIdDifferentFields);
        assertEquals(a.hashCode(), sameIdDifferentFields.hashCode());
        assertNotEquals(a, differentId);
    }

    @Test
    void equalsHandlesSelfNullAndOtherTypes() {
        Budget a = valid();

        assertEquals(a, a);
        assertNotEquals(null, a);
        assertFalse(a.equals("not a budget"));
    }

    @Test
    void withIdReturnsNewInstanceWithOnlyIdChanged() {
        Budget original = valid();

        Budget withNewId = original.withId(42L);

        assertEquals(42L, withNewId.getId());
        assertEquals(original.getCategoryId(), withNewId.getCategoryId());
        assertEquals(original.getMonth(), withNewId.getMonth());
        assertEquals(0, original.getAmount().compareTo(withNewId.getAmount()));
        // The original instance itself is untouched.
        assertEquals(1L, original.getId());
    }
}
