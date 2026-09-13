package com.finsight.domain.account;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountTest {

    @Test
    void constructsWithValidArguments() {
        Account account = new Account(1L, "Checking", AccountType.BANK, Currency.INR, new BigDecimal("100.00"));

        assertEquals(1L, account.getId());
        assertEquals("Checking", account.getName());
        assertEquals(AccountType.BANK, account.getType());
        assertEquals(Currency.INR, account.getCurrency());
        assertEquals(new BigDecimal("100.00"), account.getOpeningBalance());
    }

    @Test
    void allowsZeroOpeningBalance() {
        Account account = new Account(1L, "Wallet", AccountType.CASH, Currency.INR, BigDecimal.ZERO);

        assertEquals(BigDecimal.ZERO, account.getOpeningBalance());
    }

    @Test
    void allowsZeroId() {
        Account account = new Account(0L, "Wallet", AccountType.CASH, Currency.INR, BigDecimal.ZERO);

        assertEquals(0L, account.getId());
    }

    @Test
    void rejectsNegativeId() {
        assertThrows(IllegalArgumentException.class,
                () -> new Account(-1L, "Checking", AccountType.BANK, Currency.INR, BigDecimal.ZERO));
    }

    @Test
    void rejectsNullName() {
        assertThrows(NullPointerException.class,
                () -> new Account(1L, null, AccountType.BANK, Currency.INR, BigDecimal.ZERO));
    }

    @Test
    void rejectsBlankName() {
        assertThrows(IllegalArgumentException.class,
                () -> new Account(1L, "   ", AccountType.BANK, Currency.INR, BigDecimal.ZERO));
    }

    @Test
    void rejectsNullType() {
        assertThrows(NullPointerException.class,
                () -> new Account(1L, "Checking", null, Currency.INR, BigDecimal.ZERO));
    }

    @Test
    void rejectsNullCurrency() {
        assertThrows(NullPointerException.class,
                () -> new Account(1L, "Checking", AccountType.BANK, null, BigDecimal.ZERO));
    }

    @Test
    void rejectsNullOpeningBalance() {
        assertThrows(NullPointerException.class,
                () -> new Account(1L, "Checking", AccountType.BANK, Currency.INR, null));
    }

    @Test
    void rejectsNegativeOpeningBalance() {
        assertThrows(IllegalArgumentException.class,
                () -> new Account(1L, "Checking", AccountType.BANK, Currency.INR, new BigDecimal("-0.01")));
    }

    @Test
    void enumsHaveExpectedValues() {
        assertEquals(3, AccountType.values().length);
        assertTrue(java.util.Arrays.asList(AccountType.values())
                .containsAll(java.util.List.of(AccountType.BANK, AccountType.CASH, AccountType.INVESTMENT)));

        assertEquals(2, Currency.values().length);
        assertTrue(java.util.Arrays.asList(Currency.values())
                .containsAll(java.util.List.of(Currency.INR, Currency.USD)));
    }

    @Test
    void equalityIsBasedOnIdOnly() {
        Account a = new Account(1L, "Checking", AccountType.BANK, Currency.INR, new BigDecimal("100"));
        Account sameIdDifferentFields = new Account(1L, "Savings", AccountType.INVESTMENT, Currency.USD, BigDecimal.ZERO);
        Account differentId = new Account(2L, "Checking", AccountType.BANK, Currency.INR, new BigDecimal("100"));

        assertEquals(a, sameIdDifferentFields);
        assertEquals(a.hashCode(), sameIdDifferentFields.hashCode());
        assertNotEquals(a, differentId);
    }

    @Test
    void equalsHandlesSelfNullAndOtherTypes() {
        Account a = new Account(1L, "Checking", AccountType.BANK, Currency.INR, BigDecimal.ZERO);

        assertEquals(a, a);
        assertNotEquals(null, a);
        assertFalse(a.equals("not an account"));
    }

    @Test
    void withIdReturnsNewInstanceWithOnlyIdChanged() {
        Account original = new Account(1L, "Checking", AccountType.BANK, Currency.INR, new BigDecimal("100.00"));

        Account withNewId = original.withId(42L);

        assertEquals(42L, withNewId.getId());
        assertEquals(original.getName(), withNewId.getName());
        assertEquals(original.getType(), withNewId.getType());
        assertEquals(original.getCurrency(), withNewId.getCurrency());
        assertEquals(0, original.getOpeningBalance().compareTo(withNewId.getOpeningBalance()));
        // The original instance itself is untouched.
        assertEquals(1L, original.getId());
    }

    @Test
    void toStringContainsKeyFields() {
        Account a = new Account(1L, "Checking", AccountType.BANK, Currency.INR, new BigDecimal("100"));

        String s = a.toString();
        assertTrue(s.contains("Checking"));
        assertTrue(s.contains("BANK"));
    }
}
