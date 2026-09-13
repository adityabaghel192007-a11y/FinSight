package com.finsight.domain.transaction;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionTest {

    private static final LocalDate DATE = LocalDate.of(2026, 1, 15);

    @Test
    void constructsWithAllFieldsPresent() {
        Transaction tx = new Transaction(
                1L, 10L, new BigDecimal("50.00"), TransactionType.EXPENSE,
                2L, DATE, "Groceries", 99L);

        assertEquals(1L, tx.getId());
        assertEquals(10L, tx.getAccountId());
        assertEquals(new BigDecimal("50.00"), tx.getAmount());
        assertEquals(TransactionType.EXPENSE, tx.getType());
        assertEquals(Long.valueOf(2L), tx.getCategoryId().orElseThrow());
        assertEquals(DATE, tx.getDate());
        assertEquals("Groceries", tx.getDescription().orElseThrow());
        assertEquals(Long.valueOf(99L), tx.getTransferGroupId().orElseThrow());
    }

    @Test
    void constructsWithOptionalFieldsAbsent() {
        Transaction tx = new Transaction(
                1L, 10L, new BigDecimal("50.00"), TransactionType.TRANSFER,
                null, DATE, null, null);

        assertTrue(tx.getCategoryId().isEmpty());
        assertTrue(tx.getDescription().isEmpty());
        assertTrue(tx.getTransferGroupId().isEmpty());
    }

    @Test
    void allowsBlankDescription() {
        Transaction tx = new Transaction(
                1L, 10L, new BigDecimal("50.00"), TransactionType.INCOME,
                null, DATE, "   ", null);

        assertEquals("   ", tx.getDescription().orElseThrow());
    }

    @Test
    void amountDoesNotEncodeDirection() {
        Transaction expense = new Transaction(
                1L, 10L, new BigDecimal("50.00"), TransactionType.EXPENSE, null, DATE, null, null);
        Transaction income = new Transaction(
                2L, 10L, new BigDecimal("50.00"), TransactionType.INCOME, null, DATE, null, null);

        // Same positive amount either way; meaning comes entirely from type.
        assertEquals(0, expense.getAmount().compareTo(income.getAmount()));
        assertTrue(expense.getAmount().signum() > 0);
        assertTrue(income.getAmount().signum() > 0);
    }

    @Test
    void rejectsNegativeId() {
        assertThrows(IllegalArgumentException.class, () -> new Transaction(
                -1L, 10L, new BigDecimal("1"), TransactionType.INCOME, null, DATE, null, null));
    }

    @Test
    void rejectsNegativeAccountId() {
        assertThrows(IllegalArgumentException.class, () -> new Transaction(
                1L, -1L, new BigDecimal("1"), TransactionType.INCOME, null, DATE, null, null));
    }

    @Test
    void rejectsNullAmount() {
        assertThrows(NullPointerException.class, () -> new Transaction(
                1L, 10L, null, TransactionType.INCOME, null, DATE, null, null));
    }

    @Test
    void rejectsZeroAmount() {
        assertThrows(IllegalArgumentException.class, () -> new Transaction(
                1L, 10L, BigDecimal.ZERO, TransactionType.INCOME, null, DATE, null, null));
    }

    @Test
    void rejectsNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> new Transaction(
                1L, 10L, new BigDecimal("-1"), TransactionType.INCOME, null, DATE, null, null));
    }

    @Test
    void rejectsNullType() {
        assertThrows(NullPointerException.class, () -> new Transaction(
                1L, 10L, new BigDecimal("1"), null, null, DATE, null, null));
    }

    @Test
    void rejectsNullDate() {
        assertThrows(NullPointerException.class, () -> new Transaction(
                1L, 10L, new BigDecimal("1"), TransactionType.INCOME, null, null, null, null));
    }

    @Test
    void rejectsNegativeCategoryIdWhenPresent() {
        assertThrows(IllegalArgumentException.class, () -> new Transaction(
                1L, 10L, new BigDecimal("1"), TransactionType.EXPENSE, -1L, DATE, null, null));
    }

    @Test
    void rejectsNegativeTransferGroupIdWhenPresent() {
        assertThrows(IllegalArgumentException.class, () -> new Transaction(
                1L, 10L, new BigDecimal("1"), TransactionType.TRANSFER, null, DATE, null, -1L));
    }

    @Test
    void enumHasExpectedValues() {
        assertEquals(3, TransactionType.values().length);
        assertTrue(java.util.Arrays.asList(TransactionType.values())
                .containsAll(java.util.List.of(TransactionType.INCOME, TransactionType.EXPENSE, TransactionType.TRANSFER)));
    }

    @Test
    void equalityIsBasedOnIdOnly() {
        Transaction a = new Transaction(1L, 10L, new BigDecimal("1"), TransactionType.INCOME, null, DATE, null, null);
        Transaction sameIdDifferentFields = new Transaction(
                1L, 20L, new BigDecimal("99"), TransactionType.EXPENSE, 5L, DATE.plusDays(1), "x", 7L);
        Transaction differentId = new Transaction(2L, 10L, new BigDecimal("1"), TransactionType.INCOME, null, DATE, null, null);

        assertEquals(a, sameIdDifferentFields);
        assertEquals(a.hashCode(), sameIdDifferentFields.hashCode());
        assertNotEquals(a, differentId);
    }

    @Test
    void equalsHandlesSelfNullAndOtherTypes() {
        Transaction a = new Transaction(1L, 10L, new BigDecimal("1"), TransactionType.INCOME, null, DATE, null, null);

        assertEquals(a, a);
        assertNotEquals(null, a);
        assertFalse(a.equals("not a transaction"));
    }

    @Test
    void withIdReturnsNewInstanceWithOnlyIdChanged() {
        Transaction original = new Transaction(
                1L, 10L, new BigDecimal("50.00"), TransactionType.EXPENSE, 2L, DATE, "Groceries", 99L);

        Transaction withNewId = original.withId(42L);

        assertEquals(42L, withNewId.getId());
        assertEquals(original.getAccountId(), withNewId.getAccountId());
        assertEquals(0, original.getAmount().compareTo(withNewId.getAmount()));
        assertEquals(original.getType(), withNewId.getType());
        assertEquals(original.getCategoryId(), withNewId.getCategoryId());
        assertEquals(original.getDate(), withNewId.getDate());
        assertEquals(original.getDescription(), withNewId.getDescription());
        assertEquals(original.getTransferGroupId(), withNewId.getTransferGroupId());
        // The original instance itself is untouched.
        assertEquals(1L, original.getId());
    }
}
