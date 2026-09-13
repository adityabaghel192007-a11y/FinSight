package com.finsight.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Directly verifies {@link FinancialPrecision}'s constants and {@link FinancialPrecision#roundMoney}
 * behavior — the exact policy every calculator and simulation class in this project previously
 * declared independently (see {@link FinancialPrecision}'s own class-level Javadoc). These
 * assertions pin down that the centralized policy is byte-for-byte identical to what was removed
 * from each of those classes: 2 decimal places, {@link RoundingMode#HALF_UP}, and a 34-digit
 * {@code HALF_UP} {@link java.math.MathContext} for high-precision intermediates.
 */
class FinancialPrecisionTest {

    @Test
    void moneyScaleIsTwoDecimalPlaces() {
        assertEquals(2, FinancialPrecision.MONEY_SCALE);
    }

    @Test
    void moneyRoundingIsHalfUp() {
        assertEquals(RoundingMode.HALF_UP, FinancialPrecision.MONEY_ROUNDING);
    }

    @Test
    void calculationContextIsThirtyFourDigitsHalfUp() {
        assertEquals(34, FinancialPrecision.CALCULATION_CONTEXT.getPrecision());
        assertEquals(RoundingMode.HALF_UP, FinancialPrecision.CALCULATION_CONTEXT.getRoundingMode());
    }

    @Test
    void roundMoneyRoundsToTwoDecimalPlacesHalfUp() {
        assertEquals(0, new BigDecimal("10.005").setScale(2, RoundingMode.HALF_UP)
                .compareTo(FinancialPrecision.roundMoney(new BigDecimal("10.005"))));
        assertEquals(0, new BigDecimal("10.004").setScale(2, RoundingMode.HALF_UP)
                .compareTo(FinancialPrecision.roundMoney(new BigDecimal("10.004"))));
    }

    @Test
    void roundMoneyExpandsScaleWhenValueHasFewerDecimalPlaces() {
        BigDecimal rounded = FinancialPrecision.roundMoney(new BigDecimal("5"));
        assertEquals(2, rounded.scale());
        assertEquals(0, new BigDecimal("5.00").compareTo(rounded));
    }

    @Test
    void roundMoneyPreservesNegativeValues() {
        BigDecimal rounded = FinancialPrecision.roundMoney(new BigDecimal("-123.455"));
        assertEquals(0, new BigDecimal("-123.46").compareTo(rounded));
    }

    @Test
    void roundMoneyIsIdempotentOnAnAlreadyRoundedValue() {
        BigDecimal once = FinancialPrecision.roundMoney(new BigDecimal("42.10"));
        BigDecimal twice = FinancialPrecision.roundMoney(once);
        assertEquals(0, once.compareTo(twice));
        assertEquals(once.scale(), twice.scale());
    }
}
