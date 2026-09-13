package com.finsight.simulation.calculator;

import com.finsight.domain.account.Currency;
import com.finsight.domain.investment.AssetType;
import com.finsight.domain.investment.Investment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestmentCalculatorTest {

    private static Investment investment(
            Currency currency, String quantity, String avgPrice, String currentPrice,
            String annualReturn, String monthlyContribution) {
        return new Investment(1L, 10L, "Test Investment", "TICK", AssetType.OTHER, currency,
                new BigDecimal(quantity), new BigDecimal(avgPrice), new BigDecimal(currentPrice),
                new BigDecimal(annualReturn), new BigDecimal(monthlyContribution));
    }

    private static void assertMoneyEquals(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }

    // ===================== Valuation =====================

    @Test
    void investedValueIsQuantityTimesAveragePurchasePrice() {
        Investment inv = investment(Currency.INR, "100", "200.00", "250.00", "0.12", "5000");

        assertMoneyEquals("20000.00", InvestmentCalculator.calculateInvestedValue(inv));
    }

    @Test
    void currentValueIsQuantityTimesCurrentPrice() {
        Investment inv = investment(Currency.INR, "100", "200.00", "250.00", "0.12", "5000");

        assertMoneyEquals("25000.00", InvestmentCalculator.calculateCurrentValue(inv));
    }

    @Test
    void positiveGain() {
        Investment inv = investment(Currency.INR, "100", "200.00", "250.00", "0", "0");

        assertMoneyEquals("5000.00", InvestmentCalculator.calculateGainLoss(inv));
    }

    @Test
    void negativeGain() {
        Investment inv = investment(Currency.INR, "10", "100.00", "80.00", "0", "0");

        assertMoneyEquals("-200.00", InvestmentCalculator.calculateGainLoss(inv));
    }

    @Test
    void zeroGainWhenCurrentPriceEqualsAveragePrice() {
        Investment inv = investment(Currency.INR, "50", "100.00", "100.00", "0", "0");

        assertMoneyEquals("0.00", InvestmentCalculator.calculateGainLoss(inv));
    }

    @Test
    void decimalQuantitiesAndPricesAreHandledPrecisely() {
        Investment inv = investment(Currency.INR, "2.5", "5000.75", "5200.25", "0", "0");

        assertMoneyEquals("12501.88", InvestmentCalculator.calculateInvestedValue(inv));
        assertMoneyEquals("13000.63", InvestmentCalculator.calculateCurrentValue(inv));
    }

    // ===================== Returns =====================

    @Test
    void zeroAnnualReturnYieldsZeroMonthlyRate() {
        Investment inv = investment(Currency.INR, "1", "100.00", "100.00", "0", "0");

        assertEquals(0, BigDecimal.ZERO.compareTo(InvestmentCalculator.calculateMonthlyRate(inv)));
    }

    @Test
    void monthlyRateIsDerivedFromEffectiveAnnualReturn() {
        Investment inv = investment(Currency.INR, "1", "100.00", "100.00", "0.12", "0");

        BigDecimal monthlyRate = InvestmentCalculator.calculateMonthlyRate(inv);

        // (1.12)^(1/12) - 1, verified independently to ~30 significant digits.
        BigDecimal expected = new BigDecimal("0.009488792934582974126355069193494");
        BigDecimal diff = monthlyRate.subtract(expected).abs();
        assertTrue(diff.compareTo(new BigDecimal("1E-20")) < 0,
                "expected ~" + expected + " but was " + monthlyRate);
    }

    @Test
    void monthlyRateIsNotAnnualReturnDividedByTwelve() {
        Investment inv = investment(Currency.INR, "1", "100.00", "100.00", "0.12", "0");

        BigDecimal monthlyRate = InvestmentCalculator.calculateMonthlyRate(inv);
        BigDecimal naiveDivision = new BigDecimal("0.01");

        assertTrue(monthlyRate.compareTo(naiveDivision) != 0,
                "monthly rate must not equal the naive annualRate/12");
    }

    // ===================== Projection =====================

    @Test
    void projectionWithZeroReturnAndZeroContributionStaysFlat() {
        Investment inv = investment(Currency.INR, "10", "80.00", "80.00", "0", "0");

        InvestmentProjection projection = InvestmentCalculator.projectValue(inv, 6);

        assertMoneyEquals("800.00", projection.getInitialValue());
        for (BigDecimal value : projection.getMonthlyValues()) {
            assertMoneyEquals("800.00", value);
        }
    }

    @Test
    void projectionWithReturnAndZeroContributionCompounds() {
        Investment inv = investment(Currency.USD, "10", "150.00", "180.00", "0.10", "0");

        InvestmentProjection projection = InvestmentCalculator.projectValue(inv, 3);

        assertMoneyEquals("1800.00", projection.getInitialValue());
        List<BigDecimal> values = projection.getMonthlyValues();
        assertMoneyEquals("1814.35", values.get(0));
        assertMoneyEquals("1828.82", values.get(1));
        assertMoneyEquals("1843.40", values.get(2));
        // strictly growing, since return > 0 and contribution == 0
        assertTrue(values.get(1).compareTo(values.get(0)) > 0);
        assertTrue(values.get(2).compareTo(values.get(1)) > 0);
    }

    @Test
    void projectionWithContributionAndZeroReturnIsLinear() {
        Investment inv = investment(Currency.INR, "1", "1000.00", "1000.00", "0", "2000");

        InvestmentProjection projection = InvestmentCalculator.projectValue(inv, 4);

        List<BigDecimal> values = projection.getMonthlyValues();
        assertMoneyEquals("3000.00", values.get(0));
        assertMoneyEquals("5000.00", values.get(1));
        assertMoneyEquals("7000.00", values.get(2));
        assertMoneyEquals("9000.00", values.get(3));
    }

    @Test
    void projectionWithBothReturnAndContribution() {
        Investment inv = investment(Currency.INR, "1", "100000.00", "100000.00", "0.12", "5000");

        InvestmentProjection projection = InvestmentCalculator.projectValue(inv, 3);

        List<BigDecimal> values = projection.getMonthlyValues();
        assertMoneyEquals("105948.88", values.get(0));
        assertMoneyEquals("111954.21", values.get(1));
        assertMoneyEquals("118016.52", values.get(2));
    }

    @Test
    void contributionIsAddedAtMonthEndAndDoesNotItselfEarnReturnThatMonth() {
        Investment inv = investment(Currency.INR, "1", "100000.00", "100000.00", "0.12", "5000");

        BigDecimal monthlyRate = InvestmentCalculator.calculateMonthlyRate(inv);
        BigDecimal expectedMonth1BeforeRounding = new BigDecimal("100000.00")
                .multiply(BigDecimal.ONE.add(monthlyRate))
                .add(new BigDecimal("5000"));

        BigDecimal actualMonth1 = InvestmentCalculator.projectValue(inv, 1).getMonthlyValues().get(0);

        // If the contribution had earned that month's return too, actualMonth1 would be
        // materially higher than 100000*(1+r) + 5000.
        assertEquals(0, expectedMonth1BeforeRounding.setScale(2, java.math.RoundingMode.HALF_UP).compareTo(actualMonth1));
    }

    @Test
    void projectionContainsExpectedNumberOfMonthlyValues() {
        Investment inv = investment(Currency.INR, "1", "100.00", "100.00", "0.05", "10");

        InvestmentProjection projection = InvestmentCalculator.projectValue(inv, 37);

        assertEquals(37, projection.getMonthlyValues().size());
    }

    @Test
    void startingValueIsCurrentInvestmentValue() {
        Investment inv = investment(Currency.INR, "10", "80.00", "95.00", "0.05", "10");

        InvestmentProjection projection = InvestmentCalculator.projectValue(inv, 5);

        assertEquals(0, InvestmentCalculator.calculateCurrentValue(inv).compareTo(projection.getInitialValue()));
    }

    @Test
    void projectionIsDeterministic() {
        Investment inv = investment(Currency.INR, "1", "100000.00", "100000.00", "0.12", "5000");

        InvestmentProjection first = InvestmentCalculator.projectValue(inv, 12);
        InvestmentProjection second = InvestmentCalculator.projectValue(inv, 12);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    // ===================== compoundOneMonth (shared monthly operation) =====================

    @Test
    void compoundOneMonthGrowsBeginningValueAndAddsContribution() {
        BigDecimal result = InvestmentCalculator.compoundOneMonth(
                new BigDecimal("100000.00"), new BigDecimal("0.01"), new BigDecimal("5000.00"));

        // 100000.00 * 1.01 + 5000.00 = 101000.00 + 5000.00 = 106000.00
        assertMoneyEquals("106000.00", result);
    }

    @Test
    void compoundOneMonthWithZeroRateAndZeroContributionLeavesValueUnchanged() {
        BigDecimal result = InvestmentCalculator.compoundOneMonth(
                new BigDecimal("800.00"), BigDecimal.ZERO, BigDecimal.ZERO);

        assertMoneyEquals("800.00", result);
    }

    @Test
    void compoundOneMonthDoesNotLetContributionEarnThatMonthsReturn() {
        // If the contribution earned a return too, the result would exceed
        // 100000.00 * 1.10 + 5000.00 = 115000.00.
        BigDecimal result = InvestmentCalculator.compoundOneMonth(
                new BigDecimal("100000.00"), new BigDecimal("0.10"), new BigDecimal("5000.00"));

        assertMoneyEquals("115000.00", result);
    }

    @Test
    void compoundOneMonthRoundsToTwoDecimalPlaces() {
        BigDecimal result = InvestmentCalculator.compoundOneMonth(
                new BigDecimal("100000.00"),
                new BigDecimal("0.009488792934582974126355069193494"),
                new BigDecimal("5000.00"));

        assertEquals(2, result.scale());
        assertMoneyEquals("105948.88", result);
    }

    @Test
    void compoundOneMonthMatchesProjectValueForTheSameRateAndContribution() {
        Investment inv = investment(Currency.INR, "1", "100000.00", "100000.00", "0.12", "5000");
        BigDecimal monthlyRate = InvestmentCalculator.calculateMonthlyRate(inv);
        BigDecimal currentValue = InvestmentCalculator.calculateCurrentValue(inv);

        BigDecimal viaCompoundOneMonth = InvestmentCalculator.compoundOneMonth(
                currentValue, monthlyRate, new BigDecimal("5000.00"));
        BigDecimal viaProjectValue = InvestmentCalculator.projectValue(inv, 1).getMonthlyValues().get(0);

        assertEquals(0, viaCompoundOneMonth.compareTo(viaProjectValue));
    }

    @Test
    void compoundOneMonthIsPureAndDeterministic() {
        BigDecimal beginningValue = new BigDecimal("50000.00");
        BigDecimal monthlyRate = new BigDecimal("0.02");
        BigDecimal contribution = new BigDecimal("1000.00");

        BigDecimal first = InvestmentCalculator.compoundOneMonth(beginningValue, monthlyRate, contribution);
        BigDecimal second = InvestmentCalculator.compoundOneMonth(beginningValue, monthlyRate, contribution);

        assertEquals(0, first.compareTo(second));
        // arguments themselves are untouched (BigDecimal is immutable, but assert the values
        // read back unchanged as a guard against any accidental future stateful rewrite)
        assertMoneyEquals("50000.00", beginningValue);
        assertEquals(0, new BigDecimal("0.02").compareTo(monthlyRate));
        assertMoneyEquals("1000.00", contribution);
    }

    @Test
    void compoundOneMonthRejectsNullArguments() {
        assertThrows(NullPointerException.class,
                () -> InvestmentCalculator.compoundOneMonth(null, BigDecimal.ZERO, BigDecimal.ZERO));
        assertThrows(NullPointerException.class,
                () -> InvestmentCalculator.compoundOneMonth(BigDecimal.ZERO, null, BigDecimal.ZERO));
        assertThrows(NullPointerException.class,
                () -> InvestmentCalculator.compoundOneMonth(BigDecimal.ZERO, BigDecimal.ZERO, null));
    }

    // ===================== US stock / FX =====================

    @Test
    void usdInvestmentConvertsCorrectlyToInr() {
        Investment usStock = investment(Currency.USD, "10", "150.00", "180.00", "0.10", "0");
        BigDecimal usdValue = InvestmentCalculator.calculateCurrentValue(usStock);

        BigDecimal inrValue = InvestmentCalculator.convertToInr(usStock, usdValue, new BigDecimal("83.00"));

        assertMoneyEquals("149400.00", inrValue);
    }

    @Test
    void inrInvestmentDoesNotRequireFxConversion() {
        Investment inrInvestment = investment(Currency.INR, "100", "200.00", "250.00", "0", "0");
        BigDecimal inrValue = InvestmentCalculator.calculateCurrentValue(inrInvestment);

        // No exchange rate needed - and even a null rate is accepted, since it's ignored.
        BigDecimal converted = InvestmentCalculator.convertToInr(inrInvestment, inrValue, null);

        assertMoneyEquals("25000.00", converted);
    }

    @Test
    void changingExchangeRateChangesInrValueButNotUnderlyingUsdValue() {
        Investment usStock = investment(Currency.USD, "10", "150.00", "180.00", "0.10", "0");
        BigDecimal usdValueBefore = InvestmentCalculator.calculateCurrentValue(usStock);

        BigDecimal inrAt83 = InvestmentCalculator.convertToInr(usStock, usdValueBefore, new BigDecimal("83.00"));
        BigDecimal inrAt85 = InvestmentCalculator.convertToInr(usStock, usdValueBefore, new BigDecimal("85.00"));

        assertTrue(inrAt83.compareTo(inrAt85) != 0);
        BigDecimal usdValueAfter = InvestmentCalculator.calculateCurrentValue(usStock);
        assertEquals(0, usdValueBefore.compareTo(usdValueAfter));
    }

    // ===================== Edge cases =====================

    @Test
    void oneMonthProjectionMatchesFirstMonthOfLongerProjection() {
        Investment inv = investment(Currency.INR, "1", "100000.00", "100000.00", "0.12", "5000");

        BigDecimal oneMonthValue = InvestmentCalculator.projectValue(inv, 1).getMonthlyValues().get(0);
        BigDecimal threeMonthFirstValue = InvestmentCalculator.projectValue(inv, 3).getMonthlyValues().get(0);

        assertEquals(0, oneMonthValue.compareTo(threeMonthFirstValue));
    }

    @Test
    void largePrincipalAndLongProjectionProduceValidValues() {
        Investment big = investment(Currency.INR, "1000000", "500.00", "600.00", "0.08", "1000000");

        InvestmentProjection projection = InvestmentCalculator.projectValue(big, 24);

        assertEquals(24, projection.getMonthlyValues().size());
        for (BigDecimal value : projection.getMonthlyValues()) {
            assertTrue(value.signum() >= 0, "projected value must never be negative");
        }
        // strictly increasing given a positive return and positive contribution each month
        BigDecimal previous = projection.getInitialValue();
        for (BigDecimal value : projection.getMonthlyValues()) {
            assertTrue(value.compareTo(previous) > 0);
            previous = value;
        }
    }

    @Test
    void verySmallDecimalValuesProduceNonNegativeRoundedResults() {
        Investment small = investment(Currency.INR, "0.001", "0.01", "0.02", "0.05", "0.01");

        assertMoneyEquals("0.00", InvestmentCalculator.calculateInvestedValue(small));
        assertMoneyEquals("0.00", InvestmentCalculator.calculateCurrentValue(small));

        InvestmentProjection projection = InvestmentCalculator.projectValue(small, 3);
        for (BigDecimal value : projection.getMonthlyValues()) {
            assertTrue(value.signum() >= 0);
        }
    }

    @Test
    void monetaryResultsAreRoundedToTwoDecimalPlaces() {
        Investment inv = investment(Currency.INR, "3", "33.333", "33.335", "0.07", "123.456");

        assertEquals(2, InvestmentCalculator.calculateInvestedValue(inv).scale());
        assertEquals(2, InvestmentCalculator.calculateCurrentValue(inv).scale());
        assertEquals(2, InvestmentCalculator.calculateGainLoss(inv).scale());

        InvestmentProjection projection = InvestmentCalculator.projectValue(inv, 2);
        for (BigDecimal value : projection.getMonthlyValues()) {
            assertEquals(2, value.scale());
        }
    }

    @Test
    void calculatorNeverProducesNegativeValuationForNonNegativeInputs() {
        Investment inv = investment(Currency.INR, "5", "10.00", "0.00", "0", "0");

        assertTrue(InvestmentCalculator.calculateInvestedValue(inv).signum() >= 0);
        assertTrue(InvestmentCalculator.calculateCurrentValue(inv).signum() >= 0);
        // current price is zero, so current value is zero, and gain/loss is legitimately negative
        assertMoneyEquals("0.00", InvestmentCalculator.calculateCurrentValue(inv));
    }

    // ===================== Validation =====================

    @Test
    void rejectsNullInvestmentForEveryOperation() {
        assertThrows(NullPointerException.class, () -> InvestmentCalculator.calculateInvestedValue(null));
        assertThrows(NullPointerException.class, () -> InvestmentCalculator.calculateCurrentValue(null));
        assertThrows(NullPointerException.class, () -> InvestmentCalculator.calculateGainLoss(null));
        assertThrows(NullPointerException.class, () -> InvestmentCalculator.calculateMonthlyRate((Investment) null));
        assertThrows(NullPointerException.class, () -> InvestmentCalculator.projectValue(null, 12));
        assertThrows(NullPointerException.class, () -> InvestmentCalculator.convertToInr(null, BigDecimal.ONE, BigDecimal.ONE));
    }

    @Test
    void calculateMonthlyRateFromAnnualRateRejectsNullAndNegativeRate() {
        assertThrows(NullPointerException.class, () -> InvestmentCalculator.calculateMonthlyRate((BigDecimal) null));
        assertThrows(IllegalArgumentException.class,
                () -> InvestmentCalculator.calculateMonthlyRate(new BigDecimal("-0.01")));
    }

    @Test
    void calculateMonthlyRateFromAnnualRateMatchesTheInvestmentOverloadForTheSameRate() {
        Investment inv = investment(Currency.INR, "10", "100.00", "100.00", "0.12", "0");
        assertEquals(0, InvestmentCalculator.calculateMonthlyRate(inv)
                .compareTo(InvestmentCalculator.calculateMonthlyRate(inv.getExpectedAnnualReturn())));
    }

    @Test
    void calculateMonthlyRateFromAnnualRateReturnsExactlyZeroForZeroRate() {
        assertEquals(0, BigDecimal.ZERO.compareTo(InvestmentCalculator.calculateMonthlyRate(BigDecimal.ZERO)));
    }

    @Test
    void projectValueRejectsZeroOrNegativeMonths() {
        Investment inv = investment(Currency.INR, "1", "100.00", "100.00", "0", "0");

        assertThrows(IllegalArgumentException.class, () -> InvestmentCalculator.projectValue(inv, 0));
        assertThrows(IllegalArgumentException.class, () -> InvestmentCalculator.projectValue(inv, -1));
    }

    @Test
    void convertToInrRequiresExchangeRateForUsdInvestment() {
        Investment usStock = investment(Currency.USD, "10", "150.00", "180.00", "0", "0");
        BigDecimal usdValue = InvestmentCalculator.calculateCurrentValue(usStock);

        assertThrows(NullPointerException.class, () -> InvestmentCalculator.convertToInr(usStock, usdValue, null));
    }

    @Test
    void convertToInrRejectsNonPositiveExchangeRateForUsdInvestment() {
        Investment usStock = investment(Currency.USD, "10", "150.00", "180.00", "0", "0");
        BigDecimal usdValue = InvestmentCalculator.calculateCurrentValue(usStock);

        assertThrows(IllegalArgumentException.class,
                () -> InvestmentCalculator.convertToInr(usStock, usdValue, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> InvestmentCalculator.convertToInr(usStock, usdValue, new BigDecimal("-1")));
    }

    @Test
    void convertToInrRejectsNullAmount() {
        Investment inv = investment(Currency.INR, "1", "100.00", "100.00", "0", "0");

        assertThrows(NullPointerException.class, () -> InvestmentCalculator.convertToInr(inv, null, null));
    }
}
