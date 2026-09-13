package com.finsight.simulation.calculator;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetWorthCalculatorTest {

    private static void assertMoneyEquals(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }

    // 1. Normal positive net worth
    @Test
    void normalPositiveNetWorth() {
        BigDecimal result = NetWorthCalculator.calculateNetWorth(
                new BigDecimal("50000"), new BigDecimal("200000"), new BigDecimal("80000"));

        assertMoneyEquals("170000.00", result);
    }

    // 2. Zero cash
    @Test
    void zeroCash() {
        BigDecimal result = NetWorthCalculator.calculateNetWorth(
                BigDecimal.ZERO, new BigDecimal("200000"), new BigDecimal("80000"));

        assertMoneyEquals("120000.00", result);
    }

    // 3. Zero investments
    @Test
    void zeroInvestments() {
        BigDecimal result = NetWorthCalculator.calculateNetWorth(
                new BigDecimal("50000"), BigDecimal.ZERO, new BigDecimal("30000"));

        assertMoneyEquals("20000.00", result);
    }

    // 4. Zero loans
    @Test
    void zeroLoans() {
        BigDecimal result = NetWorthCalculator.calculateNetWorth(
                new BigDecimal("50000"), new BigDecimal("200000"), BigDecimal.ZERO);

        assertMoneyEquals("250000.00", result);
    }

    // 5. Net worth can be negative when liabilities exceed assets
    @Test
    void negativeNetWorthWhenLiabilitiesExceedAssets() {
        BigDecimal result = NetWorthCalculator.calculateNetWorth(
                new BigDecimal("1000"), new BigDecimal("2000"), new BigDecimal("500000"));

        assertMoneyEquals("-497000.00", result);
        assertTrue(result.signum() < 0);
    }

    // 6. Decimal BigDecimal values
    @Test
    void decimalValuesRoundCorrectly() {
        BigDecimal result = NetWorthCalculator.calculateNetWorth(
                new BigDecimal("100.555"), new BigDecimal("200.005"), new BigDecimal("50.001"));

        // 100.555 + 200.005 - 50.001 = 250.559 -> HALF_UP -> 250.56
        assertMoneyEquals("250.56", result);
    }

    // 7. Large values
    @Test
    void largeValuesAreHandledCorrectly() {
        BigDecimal result = NetWorthCalculator.calculateNetWorth(
                new BigDecimal("100000000.00"), new BigDecimal("500000000.00"), new BigDecimal("50000000.00"));

        assertMoneyEquals("550000000.00", result);
    }

    // 8. Null cash rejected
    @Test
    void rejectsNullCash() {
        assertThrows(NullPointerException.class,
                () -> NetWorthCalculator.calculateNetWorth(null, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    // 9. Null investments rejected
    @Test
    void rejectsNullInvestments() {
        assertThrows(NullPointerException.class,
                () -> NetWorthCalculator.calculateNetWorth(BigDecimal.ZERO, null, BigDecimal.ZERO));
    }

    // 10. Null loans rejected
    @Test
    void rejectsNullLoans() {
        assertThrows(NullPointerException.class,
                () -> NetWorthCalculator.calculateNetWorth(BigDecimal.ZERO, BigDecimal.ZERO, null));
    }

    // 11. Determinism
    @Test
    void resultIsDeterministic() {
        BigDecimal first = NetWorthCalculator.calculateNetWorth(
                new BigDecimal("50000"), new BigDecimal("200000"), new BigDecimal("80000"));
        BigDecimal second = NetWorthCalculator.calculateNetWorth(
                new BigDecimal("50000"), new BigDecimal("200000"), new BigDecimal("80000"));

        assertEquals(0, first.compareTo(second));
    }

    // 12. Appropriate monetary rounding
    @Test
    void resultIsAlwaysScaledToTwoDecimalPlaces() {
        BigDecimal wholeNumberInputs = NetWorthCalculator.calculateNetWorth(
                new BigDecimal("100"), new BigDecimal("200"), new BigDecimal("50"));
        BigDecimal manyDecimalInputs = NetWorthCalculator.calculateNetWorth(
                new BigDecimal("100.1"), new BigDecimal("200.12345"), new BigDecimal("50.001"));

        assertEquals(2, wholeNumberInputs.scale());
        assertEquals(2, manyDecimalInputs.scale());
    }
}
