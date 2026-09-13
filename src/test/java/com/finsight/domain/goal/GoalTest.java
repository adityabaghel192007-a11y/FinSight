package com.finsight.domain.goal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoalTest {

    private static final LocalDate TARGET_DATE = LocalDate.of(2030, 6, 1);

    private static Goal valid() {
        return new Goal(1L, "Emergency Fund", new BigDecimal("500000"), new BigDecimal("100000"),
                TARGET_DATE, new BigDecimal("10000"));
    }

    @Test
    void constructsWithValidArguments() {
        Goal goal = valid();

        assertEquals(1L, goal.getId());
        assertEquals("Emergency Fund", goal.getName());
        assertEquals(new BigDecimal("500000"), goal.getTargetAmount());
        assertEquals(new BigDecimal("100000"), goal.getCurrentAmount());
        assertEquals(TARGET_DATE, goal.getTargetDate());
        assertEquals(new BigDecimal("10000"), goal.getMonthlyContribution());
    }

    @Test
    void allowsZeroCurrentAmountAndMonthlyContribution() {
        Goal goal = new Goal(1L, "New Goal", BigDecimal.TEN, BigDecimal.ZERO, TARGET_DATE, BigDecimal.ZERO);

        assertEquals(0, BigDecimal.ZERO.compareTo(goal.getCurrentAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(goal.getMonthlyContribution()));
    }

    @Test
    void allowsCurrentAmountEqualToTargetAmount() {
        Goal goal = new Goal(1L, "Almost done", BigDecimal.TEN, BigDecimal.TEN, TARGET_DATE, BigDecimal.ZERO);

        assertEquals(0, goal.getCurrentAmount().compareTo(goal.getTargetAmount()));
    }

    @Test
    void rejectsCurrentAmountGreaterThanTargetAmount() {
        assertThrows(IllegalArgumentException.class, () -> new Goal(
                1L, "Over target", BigDecimal.TEN, new BigDecimal("10.01"), TARGET_DATE, BigDecimal.ZERO));
    }

    @Test
    void rejectsNegativeId() {
        assertThrows(IllegalArgumentException.class, () -> new Goal(
                -1L, "Emergency Fund", BigDecimal.TEN, BigDecimal.ZERO, TARGET_DATE, BigDecimal.ZERO));
    }

    @Test
    void rejectsNullName() {
        assertThrows(NullPointerException.class, () -> new Goal(
                1L, null, BigDecimal.TEN, BigDecimal.ZERO, TARGET_DATE, BigDecimal.ZERO));
    }

    @Test
    void rejectsBlankName() {
        assertThrows(IllegalArgumentException.class, () -> new Goal(
                1L, "   ", BigDecimal.TEN, BigDecimal.ZERO, TARGET_DATE, BigDecimal.ZERO));
    }

    @Test
    void rejectsNullTargetAmount() {
        assertThrows(NullPointerException.class, () -> new Goal(
                1L, "Emergency Fund", null, BigDecimal.ZERO, TARGET_DATE, BigDecimal.ZERO));
    }

    @Test
    void rejectsZeroTargetAmount() {
        assertThrows(IllegalArgumentException.class, () -> new Goal(
                1L, "Emergency Fund", BigDecimal.ZERO, BigDecimal.ZERO, TARGET_DATE, BigDecimal.ZERO));
    }

    @Test
    void rejectsNegativeTargetAmount() {
        assertThrows(IllegalArgumentException.class, () -> new Goal(
                1L, "Emergency Fund", new BigDecimal("-1"), BigDecimal.ZERO, TARGET_DATE, BigDecimal.ZERO));
    }

    @Test
    void rejectsNullCurrentAmount() {
        assertThrows(NullPointerException.class, () -> new Goal(
                1L, "Emergency Fund", BigDecimal.TEN, null, TARGET_DATE, BigDecimal.ZERO));
    }

    @Test
    void rejectsNegativeCurrentAmount() {
        assertThrows(IllegalArgumentException.class, () -> new Goal(
                1L, "Emergency Fund", BigDecimal.TEN, new BigDecimal("-1"), TARGET_DATE, BigDecimal.ZERO));
    }

    @Test
    void rejectsNullTargetDate() {
        assertThrows(NullPointerException.class, () -> new Goal(
                1L, "Emergency Fund", BigDecimal.TEN, BigDecimal.ZERO, null, BigDecimal.ZERO));
    }

    @Test
    void rejectsNullMonthlyContribution() {
        assertThrows(NullPointerException.class, () -> new Goal(
                1L, "Emergency Fund", BigDecimal.TEN, BigDecimal.ZERO, TARGET_DATE, null));
    }

    @Test
    void rejectsNegativeMonthlyContribution() {
        assertThrows(IllegalArgumentException.class, () -> new Goal(
                1L, "Emergency Fund", BigDecimal.TEN, BigDecimal.ZERO, TARGET_DATE, new BigDecimal("-1")));
    }

    @Test
    void equalityIsBasedOnIdOnly() {
        Goal a = valid();
        Goal sameIdDifferentFields = new Goal(
                1L, "Different", BigDecimal.ONE, BigDecimal.ZERO, TARGET_DATE.plusDays(1), BigDecimal.ZERO);
        Goal differentId = new Goal(
                2L, "Emergency Fund", new BigDecimal("500000"), new BigDecimal("100000"), TARGET_DATE, new BigDecimal("10000"));

        assertEquals(a, sameIdDifferentFields);
        assertEquals(a.hashCode(), sameIdDifferentFields.hashCode());
        assertNotEquals(a, differentId);
    }

    @Test
    void equalsHandlesSelfNullAndOtherTypes() {
        Goal a = valid();

        assertEquals(a, a);
        assertNotEquals(null, a);
        assertFalse(a.equals("not a goal"));
    }

    @Test
    void withIdReturnsNewInstanceWithOnlyIdChanged() {
        Goal original = valid();

        Goal withNewId = original.withId(42L);

        assertEquals(42L, withNewId.getId());
        assertEquals(original.getName(), withNewId.getName());
        assertEquals(0, original.getTargetAmount().compareTo(withNewId.getTargetAmount()));
        assertEquals(0, original.getCurrentAmount().compareTo(withNewId.getCurrentAmount()));
        assertEquals(original.getTargetDate(), withNewId.getTargetDate());
        assertEquals(0, original.getMonthlyContribution().compareTo(withNewId.getMonthlyContribution()));
        // The original instance itself is untouched.
        assertEquals(1L, original.getId());
    }
}
