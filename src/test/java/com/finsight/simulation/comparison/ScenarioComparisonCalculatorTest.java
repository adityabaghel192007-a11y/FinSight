package com.finsight.simulation.comparison;

import com.finsight.domain.account.Currency;
import com.finsight.domain.investment.AssetType;
import com.finsight.domain.investment.Investment;
import com.finsight.domain.loan.Loan;
import com.finsight.simulation.engine.SimulationEngine;
import com.finsight.simulation.model.FinancialSnapshot;
import com.finsight.simulation.model.MonthlyFinancialState;
import com.finsight.simulation.model.Projection;
import com.finsight.simulation.model.SimulationParameters;
import com.finsight.simulation.scenario.ExpenseChangeEvent;
import com.finsight.simulation.scenario.IncomeChangeEvent;
import com.finsight.simulation.scenario.InvestmentContributionChangeEvent;
import com.finsight.simulation.scenario.OneTimeExpenseEvent;
import com.finsight.simulation.scenario.ScenarioEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 3G: tests for {@link ScenarioComparisonCalculator} and {@link ScenarioComparison}.
 */
class ScenarioComparisonCalculatorTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 1, 1);

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static MonthlyFinancialState state(
            LocalDate date, String cash, String investmentValue, String loanBalance,
            String income, String expenses, String contribution, String loanPayment,
            String netWorth, String cumulativeSavings) {
        return new MonthlyFinancialState(
                date, money(cash), money(investmentValue), money(loanBalance),
                money(income), money(expenses), money(contribution), money(loanPayment),
                money(netWorth), money(cumulativeSavings));
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static Projection projectionOf(MonthlyFinancialState... states) {
        List<MonthlyFinancialState> list = List.of(states);
        LocalDate start = list.get(0).getDate();
        LocalDate end = list.get(list.size() - 1).getDate();
        return new Projection(start, end, list);
    }

    private static void assertMoneyEquals(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }

    private static Investment investment(long id, Currency currency, String qty, String price,
                                          String annualReturn, String contribution) {
        return new Investment(id, 1L, "Inv" + id, null, AssetType.ETF, currency,
                new BigDecimal(qty), new BigDecimal(price), new BigDecimal(price),
                new BigDecimal(annualReturn), new BigDecimal(contribution));
    }

    private static Loan loan(long id, String principal, String annualRate, int tenureMonths) {
        // A "new" loan: currentBalance == principal, remainingTenureMonths == tenureMonths.
        return new Loan(id, "Loan" + id, new BigDecimal(principal), new BigDecimal(annualRate),
                tenureMonths, AS_OF.minusYears(1), new BigDecimal(principal), tenureMonths);
    }

    private static FinancialSnapshot snapshot(String cash, String income, String expenses,
                                               List<Investment> investments, List<Loan> loans) {
        return new FinancialSnapshot(AS_OF, money(cash), money(income), money(expenses), investments, loans, List.of());
    }

    private static SimulationParameters params(int months, String usdToInr) {
        return new SimulationParameters(months, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal(usdToInr));
    }

    // ------------------------------------------------------------------
    // 1: simple comparison of two projections
    // ------------------------------------------------------------------

    @Test
    void comparesTwoSimpleSingleMonthProjections() {
        Projection baseline = projectionOf(
                state(AS_OF.plusMonths(1), "1000", "5000", "2000", "8000", "5000", "1000", "500", "4000", "3000"));
        Projection scenario = projectionOf(
                state(AS_OF.plusMonths(1), "1500", "6000", "1500", "9000", "5000", "1200", "500", "6000", "4000"));

        ScenarioComparison comparison = ScenarioComparisonCalculator.compare(baseline, scenario);

        assertMoneyEquals("4000.00", comparison.getBaselineFinalNetWorth());
        assertMoneyEquals("6000.00", comparison.getScenarioFinalNetWorth());
        assertMoneyEquals("2000.00", comparison.getNetWorthDifference());
    }

    // ------------------------------------------------------------------
    // 2: correct final net-worth difference (multi-month, using the last state only)
    // ------------------------------------------------------------------

    @Test
    void netWorthDifferenceUsesOnlyTheFinalState() {
        Projection baseline = projectionOf(
                state(AS_OF.plusMonths(1), "0", "0", "0", "0", "0", "0", "0", "999999", "0"),
                state(AS_OF.plusMonths(2), "0", "0", "0", "0", "0", "0", "0", "10000", "0"));
        Projection scenario = projectionOf(
                state(AS_OF.plusMonths(1), "0", "0", "0", "0", "0", "0", "0", "0", "0"),
                state(AS_OF.plusMonths(2), "0", "0", "0", "0", "0", "0", "0", "15000", "0"));

        ScenarioComparison comparison = ScenarioComparisonCalculator.compare(baseline, scenario);

        assertMoneyEquals("10000.00", comparison.getBaselineFinalNetWorth());
        assertMoneyEquals("15000.00", comparison.getScenarioFinalNetWorth());
        assertMoneyEquals("5000.00", comparison.getNetWorthDifference());
    }

    // ------------------------------------------------------------------
    // 3: correct final investment-value difference
    // ------------------------------------------------------------------

    @Test
    void investmentValueDifferenceIsComputedCorrectly() {
        Projection baseline = projectionOf(state(AS_OF.plusMonths(1), "0", "50000", "0", "0", "0", "0", "0", "50000", "0"));
        Projection scenario = projectionOf(state(AS_OF.plusMonths(1), "0", "62000", "0", "0", "0", "0", "0", "62000", "0"));

        ScenarioComparison comparison = ScenarioComparisonCalculator.compare(baseline, scenario);

        assertMoneyEquals("50000.00", comparison.getBaselineFinalInvestmentValue());
        assertMoneyEquals("62000.00", comparison.getScenarioFinalInvestmentValue());
        assertMoneyEquals("12000.00", comparison.getInvestmentValueDifference());
    }

    // ------------------------------------------------------------------
    // 4: correct final loan-balance difference
    // ------------------------------------------------------------------

    @Test
    void loanBalanceDifferenceIsComputedCorrectlyAndNegativeMeansLessDebt() {
        Projection baseline = projectionOf(state(AS_OF.plusMonths(1), "0", "0", "20000", "0", "0", "0", "0", "-20000", "0"));
        Projection scenario = projectionOf(state(AS_OF.plusMonths(1), "0", "0", "12000", "0", "0", "0", "0", "-12000", "0"));

        ScenarioComparison comparison = ScenarioComparisonCalculator.compare(baseline, scenario);

        assertMoneyEquals("20000.00", comparison.getBaselineFinalLoanBalance());
        assertMoneyEquals("12000.00", comparison.getScenarioFinalLoanBalance());
        assertMoneyEquals("-8000.00", comparison.getLoanBalanceDifference());
        assertTrue(comparison.getLoanBalanceDifference().signum() < 0, "less scenario debt must show as a negative difference");
    }

    // ------------------------------------------------------------------
    // 5: correct final cash difference
    // ------------------------------------------------------------------

    @Test
    void cashBalanceDifferenceIsComputedCorrectly() {
        Projection baseline = projectionOf(state(AS_OF.plusMonths(1), "3000", "0", "0", "0", "0", "0", "0", "3000", "0"));
        Projection scenario = projectionOf(state(AS_OF.plusMonths(1), "5500", "0", "0", "0", "0", "0", "0", "5500", "0"));

        ScenarioComparison comparison = ScenarioComparisonCalculator.compare(baseline, scenario);

        assertMoneyEquals("3000.00", comparison.getBaselineFinalCashBalance());
        assertMoneyEquals("5500.00", comparison.getScenarioFinalCashBalance());
        assertMoneyEquals("2500.00", comparison.getCashBalanceDifference());
    }

    // ------------------------------------------------------------------
    // 6, 7: correct total investment contributions and contribution difference
    // ------------------------------------------------------------------

    @Test
    void totalInvestmentContributionsSumsAcrossAllStates() {
        Projection baseline = projectionOf(
                state(AS_OF.plusMonths(1), "0", "0", "0", "0", "0", "1000", "0", "0", "0"),
                state(AS_OF.plusMonths(2), "0", "0", "0", "0", "0", "1000", "0", "0", "0"),
                state(AS_OF.plusMonths(3), "0", "0", "0", "0", "0", "1000", "0", "0", "0"));
        Projection scenario = projectionOf(
                state(AS_OF.plusMonths(1), "0", "0", "0", "0", "0", "1500", "0", "0", "0"),
                state(AS_OF.plusMonths(2), "0", "0", "0", "0", "0", "1500", "0", "0", "0"),
                state(AS_OF.plusMonths(3), "0", "0", "0", "0", "0", "1500", "0", "0", "0"));

        ScenarioComparison comparison = ScenarioComparisonCalculator.compare(baseline, scenario);

        assertMoneyEquals("3000.00", comparison.getBaselineTotalInvestmentContributions());
        assertMoneyEquals("4500.00", comparison.getScenarioTotalInvestmentContributions());
        assertMoneyEquals("1500.00", comparison.getInvestmentContributionDifference());
    }

    // ------------------------------------------------------------------
    // 8: positive scenario differences (scenario strictly better on every "more is better" metric)
    // ------------------------------------------------------------------

    @Test
    void positiveScenarioDifferencesAcrossAllMetrics() {
        Projection baseline = projectionOf(
                state(AS_OF.plusMonths(1), "1000", "5000", "3000", "0", "0", "500", "0", "3000", "0"));
        Projection scenario = projectionOf(
                state(AS_OF.plusMonths(1), "2000", "8000", "1000", "0", "0", "900", "0", "9000", "0"));

        ScenarioComparison comparison = ScenarioComparisonCalculator.compare(baseline, scenario);

        assertTrue(comparison.getNetWorthDifference().signum() > 0);
        assertTrue(comparison.getInvestmentValueDifference().signum() > 0);
        assertTrue(comparison.getCashBalanceDifference().signum() > 0);
        assertTrue(comparison.getInvestmentContributionDifference().signum() > 0);
        // Loan balance went down, so its difference is negative even though the scenario "wins".
        assertTrue(comparison.getLoanBalanceDifference().signum() < 0);
    }

    // ------------------------------------------------------------------
    // 9: negative scenario differences (scenario strictly worse)
    // ------------------------------------------------------------------

    @Test
    void negativeScenarioDifferencesAcrossAllMetrics() {
        Projection baseline = projectionOf(
                state(AS_OF.plusMonths(1), "2000", "8000", "1000", "0", "0", "900", "0", "9000", "0"));
        Projection scenario = projectionOf(
                state(AS_OF.plusMonths(1), "1000", "5000", "3000", "0", "0", "500", "0", "3000", "0"));

        ScenarioComparison comparison = ScenarioComparisonCalculator.compare(baseline, scenario);

        assertTrue(comparison.getNetWorthDifference().signum() < 0);
        assertTrue(comparison.getInvestmentValueDifference().signum() < 0);
        assertTrue(comparison.getCashBalanceDifference().signum() < 0);
        assertTrue(comparison.getInvestmentContributionDifference().signum() < 0);
        // Loan balance went up, so its difference is positive even though the scenario is worse.
        assertTrue(comparison.getLoanBalanceDifference().signum() > 0);
    }

    // ------------------------------------------------------------------
    // 10: zero differences
    // ------------------------------------------------------------------

    @Test
    void identicalFinalStatesProduceZeroDifferences() {
        Projection baseline = projectionOf(state(AS_OF.plusMonths(1), "1000", "5000", "2000", "0", "0", "500", "0", "4000", "0"));
        Projection scenario = projectionOf(state(AS_OF.plusMonths(1), "1000", "5000", "2000", "0", "0", "500", "0", "4000", "0"));

        ScenarioComparison comparison = ScenarioComparisonCalculator.compare(baseline, scenario);

        assertMoneyEquals("0.00", comparison.getNetWorthDifference());
        assertMoneyEquals("0.00", comparison.getInvestmentValueDifference());
        assertMoneyEquals("0.00", comparison.getLoanBalanceDifference());
        assertMoneyEquals("0.00", comparison.getCashBalanceDifference());
        assertMoneyEquals("0.00", comparison.getInvestmentContributionDifference());
    }

    // ------------------------------------------------------------------
    // 11: percentage changes
    // ------------------------------------------------------------------

    @Test
    void percentageChangesAreComputedFromBaselineMagnitude() {
        Projection baseline = projectionOf(state(AS_OF.plusMonths(1), "0", "10000", "0", "0", "0", "1000", "0", "10000", "0"));
        Projection scenario = projectionOf(state(AS_OF.plusMonths(1), "0", "12000", "0", "0", "0", "1200", "0", "12000", "0"));

        ScenarioComparison comparison = ScenarioComparisonCalculator.compare(baseline, scenario);

        assertEquals(Optional.of(money("20.00")), comparison.getNetWorthPercentageChange());
        assertEquals(Optional.of(money("20.00")), comparison.getInvestmentValuePercentageChange());
        assertEquals(Optional.of(money("20.00")), comparison.getInvestmentContributionPercentageChange());
    }

    @Test
    void percentageChangeUsesAbsoluteValueOfANegativeBaseline() {
        // Baseline net worth is -10000 (a shortfall); scenario improves it to -5000. The
        // difference is +5000, and dividing by |baseline| = 10000 gives +50%, not -50%.
        Projection baseline = projectionOf(state(AS_OF.plusMonths(1), "0", "0", "10000", "0", "0", "0", "0", "-10000", "0"));
        Projection scenario = projectionOf(state(AS_OF.plusMonths(1), "0", "0", "5000", "0", "0", "0", "0", "-5000", "0"));

        ScenarioComparison comparison = ScenarioComparisonCalculator.compare(baseline, scenario);

        assertEquals(Optional.of(money("50.00")), comparison.getNetWorthPercentageChange());
    }

    // ------------------------------------------------------------------
    // 12: zero-baseline percentage handling
    // ------------------------------------------------------------------

    @Test
    void zeroBaselineProducesEmptyPercentageChangeRatherThanDividingByZero() {
        Projection baseline = projectionOf(state(AS_OF.plusMonths(1), "0", "0", "0", "0", "0", "0", "0", "0", "0"));
        Projection scenario = projectionOf(state(AS_OF.plusMonths(1), "500", "1000", "0", "0", "0", "200", "0", "1500", "0"));

        ScenarioComparison comparison = ScenarioComparisonCalculator.compare(baseline, scenario);

        assertEquals(Optional.empty(), comparison.getNetWorthPercentageChange());
        assertEquals(Optional.empty(), comparison.getInvestmentValuePercentageChange());
        assertEquals(Optional.empty(), comparison.getLoanBalancePercentageChange());
        assertEquals(Optional.empty(), comparison.getCashBalancePercentageChange());
        assertEquals(Optional.empty(), comparison.getInvestmentContributionPercentageChange());
    }

    // ------------------------------------------------------------------
    // 13: null validation
    // ------------------------------------------------------------------

    @Test
    void calculatorRejectsNullProjections() {
        Projection valid = projectionOf(state(AS_OF.plusMonths(1), "0", "0", "0", "0", "0", "0", "0", "0", "0"));

        assertThrows(NullPointerException.class, () -> ScenarioComparisonCalculator.compare(null, valid));
        assertThrows(NullPointerException.class, () -> ScenarioComparisonCalculator.compare(valid, null));
        assertThrows(NullPointerException.class, () -> ScenarioComparisonCalculator.compare(null, null));
    }

    @Test
    void constructorRejectsNullFigures() {
        BigDecimal v = BigDecimal.ZERO;
        assertThrows(NullPointerException.class,
                () -> new ScenarioComparison(null, v, v, v, v, v, v, v, v, v));
        assertThrows(NullPointerException.class,
                () -> new ScenarioComparison(v, v, v, v, v, v, v, v, v, null));
    }

    @Test
    void constructorRejectsNegativeInvestmentLoanOrContributionFigures() {
        BigDecimal v = BigDecimal.ZERO;
        BigDecimal negative = new BigDecimal("-1");

        assertThrows(IllegalArgumentException.class,
                () -> new ScenarioComparison(v, v, negative, v, v, v, v, v, v, v));
        assertThrows(IllegalArgumentException.class,
                () -> new ScenarioComparison(v, v, v, v, negative, v, v, v, v, v));
        assertThrows(IllegalArgumentException.class,
                () -> new ScenarioComparison(v, v, v, v, v, v, v, v, negative, v));
    }

    @Test
    void constructorAllowsNegativeNetWorthAndCashBalance() {
        BigDecimal v = BigDecimal.ZERO;
        BigDecimal negative = new BigDecimal("-500");

        ScenarioComparison comparison = new ScenarioComparison(negative, negative, v, v, v, v, negative, negative, v, v);
        assertMoneyEquals("-500.00", comparison.getBaselineFinalNetWorth());
        assertMoneyEquals("-500.00", comparison.getBaselineFinalCashBalance());
    }

    @Test
    void calculatorRejectsProjectionsWithNoStates() {
        Projection empty = new Projection(AS_OF, AS_OF, List.of());
        Projection valid = projectionOf(state(AS_OF.plusMonths(1), "0", "0", "0", "0", "0", "0", "0", "0", "0"));

        assertThrows(IllegalArgumentException.class, () -> ScenarioComparisonCalculator.compare(empty, valid));
        assertThrows(IllegalArgumentException.class, () -> ScenarioComparisonCalculator.compare(valid, empty));
    }

    // ------------------------------------------------------------------
    // 14: immutability / value-object equality
    // ------------------------------------------------------------------

    @Test
    void equalComparisonsAreEqualAndShareHashCode() {
        Projection baseline = projectionOf(state(AS_OF.plusMonths(1), "1000", "5000", "2000", "0", "0", "500", "0", "4000", "0"));
        Projection scenario = projectionOf(state(AS_OF.plusMonths(1), "1500", "6000", "1000", "0", "0", "700", "0", "6500", "0"));

        ScenarioComparison first = ScenarioComparisonCalculator.compare(baseline, scenario);
        ScenarioComparison second = ScenarioComparisonCalculator.compare(baseline, scenario);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void differentComparisonsAreNotEqual() {
        Projection baseline = projectionOf(state(AS_OF.plusMonths(1), "1000", "5000", "2000", "0", "0", "500", "0", "4000", "0"));
        Projection scenarioA = projectionOf(state(AS_OF.plusMonths(1), "1500", "6000", "1000", "0", "0", "700", "0", "6500", "0"));
        Projection scenarioB = projectionOf(state(AS_OF.plusMonths(1), "1600", "6000", "1000", "0", "0", "700", "0", "6600", "0"));

        ScenarioComparison a = ScenarioComparisonCalculator.compare(baseline, scenarioA);
        ScenarioComparison b = ScenarioComparisonCalculator.compare(baseline, scenarioB);

        assertNotEquals(a, b);
    }

    // ------------------------------------------------------------------
    // 15: comparing a projection with itself produces zero differences
    // ------------------------------------------------------------------

    @Test
    void comparingAProjectionWithItselfProducesZeroDifferences() {
        Projection projection = projectionOf(
                state(AS_OF.plusMonths(1), "1000", "5000", "2000", "8000", "5000", "500", "300", "4000", "3000"),
                state(AS_OF.plusMonths(2), "1200", "5500", "1700", "8000", "5000", "500", "300", "5000", "6000"));

        ScenarioComparison comparison = ScenarioComparisonCalculator.compare(projection, projection);

        assertMoneyEquals("0.00", comparison.getNetWorthDifference());
        assertMoneyEquals("0.00", comparison.getInvestmentValueDifference());
        assertMoneyEquals("0.00", comparison.getLoanBalanceDifference());
        assertMoneyEquals("0.00", comparison.getCashBalanceDifference());
        assertMoneyEquals("0.00", comparison.getInvestmentContributionDifference());
        assertEquals(Optional.of(money("0.00")), comparison.getNetWorthPercentageChange());
    }

    // ------------------------------------------------------------------
    // 16: comparison does not mutate either projection
    // ------------------------------------------------------------------

    @Test
    void comparisonDoesNotMutateEitherProjection() {
        Projection baseline = projectionOf(
                state(AS_OF.plusMonths(1), "1000", "5000", "2000", "8000", "5000", "500", "300", "4000", "3000"));
        Projection scenario = projectionOf(
                state(AS_OF.plusMonths(1), "1500", "6000", "1500", "9000", "5000", "700", "300", "6000", "4000"));

        Projection baselineBefore = baseline;
        Projection scenarioBefore = scenario;

        ScenarioComparisonCalculator.compare(baseline, scenario);

        assertEquals(baselineBefore, baseline);
        assertEquals(scenarioBefore, scenario);
        assertEquals(1, baseline.getStates().size());
        assertEquals(1, scenario.getStates().size());
        assertThrows(UnsupportedOperationException.class,
                () -> baseline.getStates().add(baseline.getStates().get(0)));
    }

    // ------------------------------------------------------------------
    // Integration: a realistic snapshot run through SimulationEngine, baseline vs. scenario
    // ------------------------------------------------------------------

    @Test
    void integrationComparisonReflectsTheScenarioSEffectOnARealSimulation() {
        Investment inv = investment(1L, Currency.INR, "100", "100", "0.10", "1000");
        Loan l = loan(1L, "60000", "0.10", 24);
        FinancialSnapshot snap = snapshot("20000", "80000", "50000", List.of(inv), List.of(l));
        SimulationParameters p = params(12, "83");

        List<ScenarioEvent> events = new ArrayList<>();
        events.add(new IncomeChangeEvent(AS_OF.plusMonths(3), new BigDecimal("95000")));
        events.add(new ExpenseChangeEvent(AS_OF.plusMonths(3), new BigDecimal("45000")));
        events.add(new InvestmentContributionChangeEvent(AS_OF.plusMonths(4), 1L, new BigDecimal("2000")));
        events.add(new OneTimeExpenseEvent(AS_OF.plusMonths(6), new BigDecimal("10000")));

        Projection baseline = SimulationEngine.simulate(snap, p);
        Projection scenario = SimulationEngine.simulate(snap, p, events);

        ScenarioComparison comparison = ScenarioComparisonCalculator.compare(baseline, scenario);

        MonthlyFinancialState baselineFinal = baseline.getStates().get(baseline.getStates().size() - 1);
        MonthlyFinancialState scenarioFinal = scenario.getStates().get(scenario.getStates().size() - 1);

        // Cross-check every stored figure against the actual projections independently, rather
        // than trusting the calculator's own arithmetic.
        assertMoneyEquals(baselineFinal.getNetWorth().toPlainString(), comparison.getBaselineFinalNetWorth());
        assertMoneyEquals(scenarioFinal.getNetWorth().toPlainString(), comparison.getScenarioFinalNetWorth());
        assertMoneyEquals(
                scenarioFinal.getNetWorth().subtract(baselineFinal.getNetWorth()).toPlainString(),
                comparison.getNetWorthDifference());

        assertMoneyEquals(baselineFinal.getInvestmentValue().toPlainString(), comparison.getBaselineFinalInvestmentValue());
        assertMoneyEquals(scenarioFinal.getInvestmentValue().toPlainString(), comparison.getScenarioFinalInvestmentValue());

        assertMoneyEquals(baselineFinal.getLoanBalance().toPlainString(), comparison.getBaselineFinalLoanBalance());
        assertMoneyEquals(scenarioFinal.getLoanBalance().toPlainString(), comparison.getScenarioFinalLoanBalance());

        assertMoneyEquals(baselineFinal.getCashBalance().toPlainString(), comparison.getBaselineFinalCashBalance());
        assertMoneyEquals(scenarioFinal.getCashBalance().toPlainString(), comparison.getScenarioFinalCashBalance());

        BigDecimal expectedBaselineContrib = sumContributions(baseline);
        BigDecimal expectedScenarioContrib = sumContributions(scenario);
        assertMoneyEquals(expectedBaselineContrib.toPlainString(), comparison.getBaselineTotalInvestmentContributions());
        assertMoneyEquals(expectedScenarioContrib.toPlainString(), comparison.getScenarioTotalInvestmentContributions());
        assertMoneyEquals(
                expectedScenarioContrib.subtract(expectedBaselineContrib).toPlainString(),
                comparison.getInvestmentContributionDifference());

        // The scenario's raise (month 4) and reduced expense outweigh its one-time expense and
        // higher SIP: it should show a materially higher net worth than the baseline by month 12.
        assertTrue(comparison.getNetWorthDifference().signum() > 0,
                "scenario net worth should exceed baseline given a raise and lower expenses");

        // Baseline was never touched by running the scenario simulation.
        Projection baselineAgain = SimulationEngine.simulate(snap, p);
        assertEquals(baselineAgain, baseline);
    }

    private static BigDecimal sumContributions(Projection projection) {
        BigDecimal total = BigDecimal.ZERO;
        for (MonthlyFinancialState s : projection.getStates()) {
            total = total.add(s.getInvestmentContribution());
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }
}
