package com.finsight.domain.investment;

import com.finsight.domain.account.Currency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestmentTest {

    private static Investment valid() {
        return new Investment(
                1L, 10L, "Nifty 50 Index Fund", "NIFTYBEES", AssetType.ETF, Currency.INR,
                new BigDecimal("100"), new BigDecimal("200.00"), new BigDecimal("250.00"),
                new BigDecimal("0.12"), new BigDecimal("5000"));
    }

    @Test
    void constructsWithValidArguments() {
        Investment inv = valid();

        assertEquals(1L, inv.getId());
        assertEquals(10L, inv.getAccountId());
        assertEquals("Nifty 50 Index Fund", inv.getName());
        assertEquals("NIFTYBEES", inv.getTicker().orElseThrow());
        assertEquals(AssetType.ETF, inv.getAssetType());
        assertEquals(Currency.INR, inv.getCurrency());
        assertEquals(new BigDecimal("100"), inv.getQuantity());
        assertEquals(new BigDecimal("200.00"), inv.getAveragePurchasePrice());
        assertEquals(new BigDecimal("250.00"), inv.getCurrentPrice());
        assertEquals(new BigDecimal("0.12"), inv.getExpectedAnnualReturn());
        assertEquals(new BigDecimal("5000"), inv.getMonthlyContribution());
    }

    @Test
    void allowsNullTicker() {
        Investment inv = new Investment(
                1L, 10L, "Fixed Deposit", null, AssetType.FIXED_DEPOSIT, Currency.INR,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("0.07"), BigDecimal.ZERO);

        assertTrue(inv.getTicker().isEmpty());
    }

    @Test
    void allowsBlankTicker() {
        Investment inv = new Investment(
                1L, 10L, "Gold", "   ", AssetType.GOLD, Currency.INR,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        assertEquals("   ", inv.getTicker().orElseThrow());
    }

    @Test
    void allowsZeroPricesRatesAndContribution() {
        Investment inv = new Investment(
                1L, 10L, "Gold", null, AssetType.GOLD, Currency.INR,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        assertEquals(0, BigDecimal.ZERO.compareTo(inv.getAveragePurchasePrice()));
        assertEquals(0, BigDecimal.ZERO.compareTo(inv.getCurrentPrice()));
        assertEquals(0, BigDecimal.ZERO.compareTo(inv.getExpectedAnnualReturn()));
        assertEquals(0, BigDecimal.ZERO.compareTo(inv.getMonthlyContribution()));
    }

    @Test
    void rejectsNegativeId() {
        assertThrows(IllegalArgumentException.class, () -> new Investment(
                -1L, 10L, "X", null, AssetType.OTHER, Currency.INR,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void rejectsNegativeAccountId() {
        assertThrows(IllegalArgumentException.class, () -> new Investment(
                1L, -1L, "X", null, AssetType.OTHER, Currency.INR,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void rejectsNullName() {
        assertThrows(NullPointerException.class, () -> new Investment(
                1L, 10L, null, null, AssetType.OTHER, Currency.INR,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void rejectsBlankName() {
        assertThrows(IllegalArgumentException.class, () -> new Investment(
                1L, 10L, "   ", null, AssetType.OTHER, Currency.INR,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void rejectsNullAssetType() {
        assertThrows(NullPointerException.class, () -> new Investment(
                1L, 10L, "X", null, null, Currency.INR,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void rejectsNullCurrency() {
        assertThrows(NullPointerException.class, () -> new Investment(
                1L, 10L, "X", null, AssetType.OTHER, null,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void rejectsNullQuantity() {
        assertThrows(NullPointerException.class, () -> new Investment(
                1L, 10L, "X", null, AssetType.OTHER, Currency.INR,
                null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void rejectsZeroQuantity() {
        assertThrows(IllegalArgumentException.class, () -> new Investment(
                1L, 10L, "X", null, AssetType.OTHER, Currency.INR,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void rejectsNegativeQuantity() {
        assertThrows(IllegalArgumentException.class, () -> new Investment(
                1L, 10L, "X", null, AssetType.OTHER, Currency.INR,
                new BigDecimal("-1"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void rejectsNullAveragePurchasePrice() {
        assertThrows(NullPointerException.class, () -> new Investment(
                1L, 10L, "X", null, AssetType.OTHER, Currency.INR,
                BigDecimal.ONE, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void rejectsNegativeAveragePurchasePrice() {
        assertThrows(IllegalArgumentException.class, () -> new Investment(
                1L, 10L, "X", null, AssetType.OTHER, Currency.INR,
                BigDecimal.ONE, new BigDecimal("-1"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void rejectsNullCurrentPrice() {
        assertThrows(NullPointerException.class, () -> new Investment(
                1L, 10L, "X", null, AssetType.OTHER, Currency.INR,
                BigDecimal.ONE, BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void rejectsNegativeCurrentPrice() {
        assertThrows(IllegalArgumentException.class, () -> new Investment(
                1L, 10L, "X", null, AssetType.OTHER, Currency.INR,
                BigDecimal.ONE, BigDecimal.ZERO, new BigDecimal("-1"), BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void rejectsNullExpectedAnnualReturn() {
        assertThrows(NullPointerException.class, () -> new Investment(
                1L, 10L, "X", null, AssetType.OTHER, Currency.INR,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, null, BigDecimal.ZERO));
    }

    @Test
    void rejectsNegativeExpectedAnnualReturn() {
        assertThrows(IllegalArgumentException.class, () -> new Investment(
                1L, 10L, "X", null, AssetType.OTHER, Currency.INR,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("-0.01"), BigDecimal.ZERO));
    }

    @Test
    void rejectsNullMonthlyContribution() {
        assertThrows(NullPointerException.class, () -> new Investment(
                1L, 10L, "X", null, AssetType.OTHER, Currency.INR,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null));
    }

    @Test
    void rejectsNegativeMonthlyContribution() {
        assertThrows(IllegalArgumentException.class, () -> new Investment(
                1L, 10L, "X", null, AssetType.OTHER, Currency.INR,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("-1")));
    }

    @Test
    void enumHasExpectedValues() {
        assertEquals(7, AssetType.values().length);
        assertTrue(java.util.Arrays.asList(AssetType.values()).containsAll(java.util.List.of(
                AssetType.INDIAN_STOCK, AssetType.US_STOCK, AssetType.MUTUAL_FUND,
                AssetType.ETF, AssetType.FIXED_DEPOSIT, AssetType.GOLD, AssetType.OTHER)));
    }

    @Test
    void equalityIsBasedOnIdOnly() {
        Investment a = valid();
        Investment sameIdDifferentFields = new Investment(
                1L, 99L, "Other", "OTHER", AssetType.GOLD, Currency.USD,
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO);
        Investment differentId = new Investment(
                2L, 10L, "Nifty 50 Index Fund", "NIFTYBEES", AssetType.ETF, Currency.INR,
                new BigDecimal("100"), new BigDecimal("200.00"), new BigDecimal("250.00"),
                new BigDecimal("0.12"), new BigDecimal("5000"));

        assertEquals(a, sameIdDifferentFields);
        assertEquals(a.hashCode(), sameIdDifferentFields.hashCode());
        assertNotEquals(a, differentId);
    }

    @Test
    void equalsHandlesSelfNullAndOtherTypes() {
        Investment a = valid();

        assertEquals(a, a);
        assertNotEquals(null, a);
        assertFalse(a.equals("not an investment"));
    }

    @Test
    void withIdReturnsNewInstanceWithOnlyIdChanged() {
        Investment original = valid();

        Investment withNewId = original.withId(42L);

        assertEquals(42L, withNewId.getId());
        assertEquals(original.getAccountId(), withNewId.getAccountId());
        assertEquals(original.getName(), withNewId.getName());
        assertEquals(original.getTicker(), withNewId.getTicker());
        assertEquals(original.getAssetType(), withNewId.getAssetType());
        assertEquals(original.getCurrency(), withNewId.getCurrency());
        assertEquals(0, original.getQuantity().compareTo(withNewId.getQuantity()));
        assertEquals(0, original.getAveragePurchasePrice().compareTo(withNewId.getAveragePurchasePrice()));
        assertEquals(0, original.getCurrentPrice().compareTo(withNewId.getCurrentPrice()));
        assertEquals(0, original.getExpectedAnnualReturn().compareTo(withNewId.getExpectedAnnualReturn()));
        assertEquals(0, original.getMonthlyContribution().compareTo(withNewId.getMonthlyContribution()));
        // The original instance itself is untouched.
        assertEquals(1L, original.getId());
    }
}
