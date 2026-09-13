package com.finsight.domain.category;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoryTest {

    @Test
    void constructsWithValidArguments() {
        Category category = new Category(1L, "Groceries", CategoryType.EXPENSE);

        assertEquals(1L, category.getId());
        assertEquals("Groceries", category.getName());
        assertEquals(CategoryType.EXPENSE, category.getType());
    }

    @Test
    void allowsZeroId() {
        Category category = new Category(0L, "Salary", CategoryType.INCOME);

        assertEquals(0L, category.getId());
    }

    @Test
    void rejectsNegativeId() {
        assertThrows(IllegalArgumentException.class, () -> new Category(-1L, "Groceries", CategoryType.EXPENSE));
    }

    @Test
    void rejectsNullName() {
        assertThrows(NullPointerException.class, () -> new Category(1L, null, CategoryType.EXPENSE));
    }

    @Test
    void rejectsBlankName() {
        assertThrows(IllegalArgumentException.class, () -> new Category(1L, "   ", CategoryType.EXPENSE));
    }

    @Test
    void rejectsNullType() {
        assertThrows(NullPointerException.class, () -> new Category(1L, "Groceries", null));
    }

    @Test
    void enumHasExpectedValues() {
        assertEquals(2, CategoryType.values().length);
        assertTrue(java.util.Arrays.asList(CategoryType.values())
                .containsAll(java.util.List.of(CategoryType.INCOME, CategoryType.EXPENSE)));
    }

    @Test
    void equalityIsBasedOnIdOnly() {
        Category a = new Category(1L, "Groceries", CategoryType.EXPENSE);
        Category sameIdDifferentFields = new Category(1L, "Salary", CategoryType.INCOME);
        Category differentId = new Category(2L, "Groceries", CategoryType.EXPENSE);

        assertEquals(a, sameIdDifferentFields);
        assertEquals(a.hashCode(), sameIdDifferentFields.hashCode());
        assertNotEquals(a, differentId);
    }

    @Test
    void equalsHandlesSelfNullAndOtherTypes() {
        Category a = new Category(1L, "Groceries", CategoryType.EXPENSE);

        assertEquals(a, a);
        assertNotEquals(null, a);
        assertFalse(a.equals("not a category"));
    }

    @Test
    void withIdReturnsNewInstanceWithOnlyIdChanged() {
        Category original = new Category(1L, "Groceries", CategoryType.EXPENSE);

        Category withNewId = original.withId(42L);

        assertEquals(42L, withNewId.getId());
        assertEquals(original.getName(), withNewId.getName());
        assertEquals(original.getType(), withNewId.getType());
        // The original instance itself is untouched.
        assertEquals(1L, original.getId());
    }
}
