package com.finsight.ui;

import com.finsight.service.SimulationResult;
import com.finsight.simulation.comparison.ScenarioComparison;
import com.finsight.simulation.model.MonthlyFinancialState;
import com.finsight.simulation.model.Projection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pure unit tests for {@link SimulationResultFormatter} — no JavaFX toolkit, no database, no
 * {@code CompositionRoot}. Every input is a hand-built {@link SimulationResult}, so these tests
 * verify only the extraction/formatting logic itself, independent of whether the simulation or
 * persistence layers behind it are working (those are covered elsewhere — see
 * {@code SimulationApplicationServiceTest} and the fresh-launch integration test).
 *
 * <p><b>What "starting"/"ending" mean here (post-5E-discrepancy-investigation):</b>
 * {@link SimulationResultView#getStartingNetWorth()} is exactly {@code Projection}'s <em>first</em>
 * simulated month's net worth, and {@link SimulationResultView#getEndingNetWorth()} is exactly its
 * <em>last</em> simulated month's — never the pre-simulation snapshot. {@code FinSightApplication}
 * now labels these two values "Net worth after month 1" and "Net worth after month N" respectively
 * (and their difference "Net worth change (month 1 → N)"), specifically so the UI text matches what
 * is actually being shown. This formatter's own extraction logic did not change — only
 * {@code FinSightApplication}'s row labels did — so {@link #formatsAllFieldsFromTheFirstAndLastBaselineMonths}
 * and {@link #verifiesTheExactFiguresTheUiLabelsAsNetWorthAfterMonth1AndMonth12} are what verify the
 * values behind those corrected labels are still exactly right.
 */
class SimulationResultFormatterTest {

    private static MonthlyFinancialState state(
            LocalDate date, BigDecimal netWorth, BigDecimal income, BigDecimal expenses) {
        return new MonthlyFinancialState(
                date, netWorth, BigDecimal.ZERO, BigDecimal.ZERO, income, expenses,
                BigDecimal.ZERO, BigDecimal.ZERO, netWorth, BigDecimal.ZERO);
    }

    private static SimulationResult resultFromBaselineStates(List<MonthlyFinancialState> states) {
        Projection baseline = new Projection(states.get(0).getDate(), states.get(states.size() - 1).getDate(), states);
        // The scenario side and the comparison are irrelevant to SimulationResultFormatter (see its
        // own documentation on why only the baseline is shown), so both simply mirror the baseline.
        Projection scenario = baseline;
        BigDecimal finalNetWorth = states.get(states.size() - 1).getNetWorth();
        ScenarioComparison comparison = new ScenarioComparison(
                finalNetWorth, finalNetWorth,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
        return new SimulationResult(baseline, scenario, comparison);
    }

    @Test
    void formatsAllFieldsFromTheFirstAndLastBaselineMonths() {
        MonthlyFinancialState month1 = state(
                LocalDate.of(2026, 1, 1), new BigDecimal("100000.00"), new BigDecimal("60000.00"), new BigDecimal("40000.00"));
        MonthlyFinancialState month2 = state(
                LocalDate.of(2026, 2, 1), new BigDecimal("112345.67"), new BigDecimal("60000.00"), new BigDecimal("40000.00"));

        SimulationResultView view = SimulationResultFormatter.format(resultFromBaselineStates(List.of(month1, month2)));

        assertEquals("2026-01-01 to 2026-02-01 (2 months)", view.getProjectionPeriod());
        assertEquals("100,000.00", view.getStartingNetWorth());
        assertEquals("112,345.67", view.getEndingNetWorth());
        assertEquals("+12,345.67", view.getNetWorthChange());
        assertEquals("60,000.00", view.getMonthlyIncome());
        assertEquals("40,000.00", view.getMonthlyExpenses());
        assertEquals("+20,000.00", view.getMonthlySurplus(), "60,000.00 income minus 40,000.00 expenses");
    }

    /**
     * Reproduces exactly the 12-month, zero-growth demo-data figures from the Milestone 5E
     * discrepancy investigation (opening cash 150,000.00, net monthly cash flow +20,000.00, no
     * investments/loans): month 1's net worth is 170,000.00 and month 12's is 390,000.00 — a
     * change of +220,000.00 (11 months of flow between month 1 and month 12, not 12), exactly as
     * the investigation traced through {@code SimulationEngine}. These are precisely the figures
     * {@code FinSightApplication} now displays next to the labels "Net worth after month 1",
     * "Net worth after month 12", and "Net worth change (month 1 → 12)" — this test exists to
     * keep those figures pinned to a known-correct value, independent of the label text itself
     * (which lives in {@code FinSightApplication} and is not exercised by this formatter).
     */
    @Test
    void verifiesTheExactFiguresTheUiLabelsAsNetWorthAfterMonth1AndMonth12() {
        LocalDate asOfDate = LocalDate.of(2026, 4, 1);
        BigDecimal income = new BigDecimal("60000.00");
        BigDecimal expenses = new BigDecimal("40000.00");
        BigDecimal netWorth = new BigDecimal("150000.00"); // opening cash before month 1's flow
        List<MonthlyFinancialState> states = new java.util.ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            netWorth = netWorth.add(income).subtract(expenses);
            states.add(state(asOfDate.plusMonths(month), netWorth, income, expenses));
        }

        SimulationResultView view = SimulationResultFormatter.format(resultFromBaselineStates(states));

        assertEquals("170,000.00", view.getStartingNetWorth(), "month 1's net worth — labeled \"Net worth after month 1\" in the UI");
        assertEquals("390,000.00", view.getEndingNetWorth(), "month 12's net worth — labeled \"Net worth after month 12\" in the UI");
        assertEquals("+220,000.00", view.getNetWorthChange(),
                "the difference between month 12 and month 1 is 11 months of flow, not 12 — "
                        + "labeled \"Net worth change (month 1 → 12)\" in the UI so this is no longer implied to be a full 12-month change");
        assertEquals("+20,000.00", view.getMonthlySurplus(), "60,000.00 income minus 40,000.00 expenses");
    }

    @Test
    void usesSingularMonthWordingForAOneMonthProjection() {
        MonthlyFinancialState onlyMonth = state(
                LocalDate.of(2026, 1, 1), new BigDecimal("100000.00"), new BigDecimal("60000.00"), new BigDecimal("40000.00"));

        SimulationResultView view = SimulationResultFormatter.format(resultFromBaselineStates(List.of(onlyMonth)));

        assertEquals("2026-01-01 to 2026-01-01 (1 month)", view.getProjectionPeriod());
        assertEquals("100,000.00", view.getStartingNetWorth());
        assertEquals("100,000.00", view.getEndingNetWorth());
        assertEquals("0.00", view.getNetWorthChange(), "zero change must not be prefixed with a '+' sign");
        assertEquals("+20,000.00", view.getMonthlySurplus());
    }

    @Test
    void doesNotPrefixANegativeNetWorthChangeWithAPlusSign() {
        MonthlyFinancialState month1 = state(
                LocalDate.of(2026, 1, 1), new BigDecimal("100000.00"), new BigDecimal("40000.00"), new BigDecimal("60000.00"));
        MonthlyFinancialState month2 = state(
                LocalDate.of(2026, 2, 1), new BigDecimal("80000.00"), new BigDecimal("40000.00"), new BigDecimal("60000.00"));

        SimulationResultView view = SimulationResultFormatter.format(resultFromBaselineStates(List.of(month1, month2)));

        assertEquals("-20,000.00", view.getNetWorthChange());
        assertEquals("-20,000.00", view.getMonthlySurplus(), "40,000.00 income minus 60,000.00 expenses");
    }

    // ===================== formatComparison (Milestone 5F) =====================

    /**
     * Proves {@link SimulationResultFormatter#formatComparison} performs no arithmetic of its own
     * and reads every figure directly off the given {@link SimulationResult}'s
     * {@link ScenarioComparison} — never re-deriving anything from either {@link Projection}. The
     * baseline/scenario projections below are deliberately built with net-worth figures that do
     * <em>not</em> match the {@link ScenarioComparison} passed alongside them (a real
     * {@code ScenarioComparisonCalculator} output would never disagree with its own inputs like
     * this — this is purely a probe). If {@link SimulationResultFormatter#formatComparison} were
     * (incorrectly) recomputing the comparison from the projections' final states instead of
     * reading {@link SimulationResult#getComparison()}, this test would see the projections'
     * figures (999,999.99 / 111,111.11) rather than the comparison's own (390,000.00 / 270,000.00).
     */
    @Test
    void formatComparisonReadsOnlyFromTheScenarioComparisonNeverFromTheProjectionsDirectly() {
        LocalDate date = LocalDate.of(2026, 4, 1);
        MonthlyFinancialState baselineMonth = state(date, new BigDecimal("999999.99"), BigDecimal.ZERO, BigDecimal.ZERO);
        MonthlyFinancialState scenarioMonth = state(date, new BigDecimal("111111.11"), BigDecimal.ZERO, BigDecimal.ZERO);
        Projection baseline = new Projection(date, date, List.of(baselineMonth));
        Projection scenario = new Projection(date, date, List.of(scenarioMonth));

        ScenarioComparison comparison = new ScenarioComparison(
                new BigDecimal("390000.00"), new BigDecimal("270000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
        SimulationResult result = new SimulationResult(baseline, scenario, comparison);

        ScenarioComparisonView view = SimulationResultFormatter.formatComparison(result);

        assertEquals("390,000.00", view.getBaselineNetWorthAfterFinalMonth());
        assertEquals("270,000.00", view.getScenarioNetWorthAfterFinalMonth());
        assertEquals("-120,000.00", view.getNetWorthImpact(),
                "must be exactly ScenarioComparison.getNetWorthDifference() (270000 - 390000), "
                        + "not anything derived from the mismatched projection states above");
    }

    /**
     * The deterministic 5F demo scenario: baseline monthly expenses 40,000.00 vs. a scenario of
     * 50,000.00 from month 1 onward, both against the seeded demo profile's 150,000.00 opening
     * cash and 60,000.00 monthly income (see {@code DemoDataSeeder}). Baseline final (month 12)
     * net worth is 390,000.00 (150,000 + 12 × 20,000 — see the 5E discrepancy investigation);
     * scenario final net worth is 270,000.00 (150,000 + 12 × 10,000); impact is -120,000.00. This
     * mirrors {@code FinSightApplication}'s own default "New monthly expenses" value and is
     * cross-checked end to end (through a real {@code CompositionRoot}) by
     * {@code ScenarioComparisonWorkflowTest}.
     */
    @Test
    void formatsTheDeterministicDemoExpenseIncreaseScenarioComparison() {
        LocalDate date = LocalDate.of(2026, 4, 1);
        ScenarioComparison comparison = new ScenarioComparison(
                new BigDecimal("390000.00"), new BigDecimal("270000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
        Projection placeholder = new Projection(date, date,
                List.of(state(date, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)));
        SimulationResult result = new SimulationResult(placeholder, placeholder, comparison);

        ScenarioComparisonView view = SimulationResultFormatter.formatComparison(result);

        assertEquals("390,000.00", view.getBaselineNetWorthAfterFinalMonth());
        assertEquals("270,000.00", view.getScenarioNetWorthAfterFinalMonth());
        assertEquals("-120,000.00", view.getNetWorthImpact());
    }

    @Test
    void rejectsAProjectionWithNoSimulatedMonths() {
        LocalDate date = LocalDate.of(2026, 1, 1);
        Projection emptyBaseline = new Projection(date, date, List.of());
        ScenarioComparison comparison = new ScenarioComparison(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
        SimulationResult result = new SimulationResult(emptyBaseline, emptyBaseline, comparison);

        assertThrows(IllegalArgumentException.class, () -> SimulationResultFormatter.format(result));
    }

    // ===================== formatComparison — all five metrics (Milestone 6A) =====================

    /**
     * {@code ScenarioComparison} has, since the Investments milestone, already computed five
     * metric categories (net worth, investment value, loan balance, cash balance, total investment
     * contributions), each as baseline/scenario/difference plus a percentage change — but
     * {@link SimulationResultFormatter#formatComparison} previously read only the net worth triple
     * (see the two tests above this section). This test proves it now reads and formats all twenty
     * figures, with a deliberate mix of positive and negative differences across different metrics
     * (investment value and cash balance fall from baseline to scenario; net worth, loan balance,
     * and investment contributions rise) so both branches of the signed-money/signed-percentage
     * formatting are exercised across genuinely different metrics, not just net worth twice.
     */
    @Test
    void formatsAllFiveMetricCategoriesWithMixedPositiveAndNegativeDifferences() {
        ScenarioComparison comparison = new ScenarioComparison(
                new BigDecimal("300000.00"), new BigDecimal("360000.00"), // net worth: +60,000.00 / +20.00%
                new BigDecimal("500000.00"), new BigDecimal("450000.00"), // investment value: -50,000.00 / -10.00%
                new BigDecimal("100000.00"), new BigDecimal("120000.00"), // loan balance: +20,000.00 / +20.00%
                new BigDecimal("50000.00"), new BigDecimal("40000.00"),   // cash balance: -10,000.00 / -20.00%
                new BigDecimal("12000.00"), new BigDecimal("15000.00"));  // contributions: +3,000.00 / +25.00%
        LocalDate date = LocalDate.of(2026, 4, 1);
        Projection placeholder = new Projection(date, date,
                List.of(state(date, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)));
        SimulationResult result = new SimulationResult(placeholder, placeholder, comparison);

        ScenarioComparisonView view = SimulationResultFormatter.formatComparison(result);

        // net worth — unchanged fields, still correct alongside the newly added ones
        assertEquals("300,000.00", view.getBaselineNetWorthAfterFinalMonth());
        assertEquals("360,000.00", view.getScenarioNetWorthAfterFinalMonth());
        assertEquals("+60,000.00", view.getNetWorthImpact());
        assertEquals("+20.00%", view.getNetWorthPercentageChange());

        // investment value — a negative difference and a negative percentage change
        assertEquals("500,000.00", view.getBaselineInvestmentValueAfterFinalMonth());
        assertEquals("450,000.00", view.getScenarioInvestmentValueAfterFinalMonth());
        assertEquals("-50,000.00", view.getInvestmentValueImpact());
        assertEquals("-10.00%", view.getInvestmentValuePercentageChange());

        // loan balance
        assertEquals("100,000.00", view.getBaselineLoanBalanceAfterFinalMonth());
        assertEquals("120,000.00", view.getScenarioLoanBalanceAfterFinalMonth());
        assertEquals("+20,000.00", view.getLoanBalanceImpact());
        assertEquals("+20.00%", view.getLoanBalancePercentageChange());

        // cash balance — another negative difference/percentage
        assertEquals("50,000.00", view.getBaselineCashBalanceAfterFinalMonth());
        assertEquals("40,000.00", view.getScenarioCashBalanceAfterFinalMonth());
        assertEquals("-10,000.00", view.getCashBalanceImpact());
        assertEquals("-20.00%", view.getCashBalancePercentageChange());

        // total investment contributions
        assertEquals("12,000.00", view.getBaselineTotalInvestmentContributions());
        assertEquals("15,000.00", view.getScenarioTotalInvestmentContributions());
        assertEquals("+3,000.00", view.getInvestmentContributionImpact());
        assertEquals("+25.00%", view.getInvestmentContributionPercentageChange());
    }

    /**
     * {@link ScenarioComparison}'s percentage-change getters return {@link java.util.Optional#empty()}
     * whenever the corresponding baseline figure is exactly zero (see its own documentation) —
     * this test proves {@link SimulationResultFormatter#formatComparison} renders that as
     * {@code "n/a"} rather than a misleading number, for three independently zero-baseline metrics
     * (investment value, loan balance, cash balance all zero/zero here), while a genuinely
     * zero-<em>difference</em> but non-zero-baseline metric (net worth: 100,000.00 unchanged)
     * correctly still produces a present {@code "0.00%"} rather than {@code "n/a"} — proving the
     * two "zero" cases are not conflated. The investment-contributions metric's baseline is zero
     * but its scenario is not, so its absolute difference is a real, non-zero, positive figure
     * even though its percentage change is undefined.
     */
    @Test
    void formatsZeroBaselinePercentageChangeAsNotApplicable() {
        ScenarioComparison comparison = new ScenarioComparison(
                new BigDecimal("100000.00"), new BigDecimal("100000.00"), // net worth: no change at all
                BigDecimal.ZERO, BigDecimal.ZERO,                         // investment value: zero baseline
                BigDecimal.ZERO, BigDecimal.ZERO,                         // loan balance: zero baseline
                BigDecimal.ZERO, BigDecimal.ZERO,                         // cash balance: zero baseline
                BigDecimal.ZERO, new BigDecimal("5000.00"));              // contributions: zero baseline, non-zero scenario
        LocalDate date = LocalDate.of(2026, 4, 1);
        Projection placeholder = new Projection(date, date,
                List.of(state(date, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)));
        SimulationResult result = new SimulationResult(placeholder, placeholder, comparison);

        ScenarioComparisonView view = SimulationResultFormatter.formatComparison(result);

        assertEquals("0.00%", view.getNetWorthPercentageChange(),
                "a non-zero baseline with zero difference is a present 0.00%, not \"n/a\"");
        assertEquals("n/a", view.getInvestmentValuePercentageChange());
        assertEquals("n/a", view.getLoanBalancePercentageChange());
        assertEquals("n/a", view.getCashBalancePercentageChange());
        assertEquals("n/a", view.getInvestmentContributionPercentageChange(),
                "percentage change is undefined from a zero baseline even though the absolute difference below is not");
        assertEquals("+5,000.00", view.getInvestmentContributionImpact(),
                "the absolute difference is still a real, computable figure even when the percentage is undefined");
    }
}
