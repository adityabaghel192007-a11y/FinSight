package com.finsight.ui;

import com.finsight.service.SimulationResult;
import com.finsight.simulation.comparison.ScenarioComparison;
import com.finsight.simulation.model.MonthlyFinancialState;
import com.finsight.simulation.model.Projection;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Turns a {@link SimulationResult} — already fully computed by
 * {@code com.finsight.service.SimulationApplicationService} and the simulation layer beneath it —
 * into either a {@link SimulationResultView} (baseline only, via {@link #format}) or a
 * {@link ScenarioComparisonView} (baseline vs. scenario, via {@link #formatComparison}) that a
 * JavaFX control can display directly.
 *
 * <p>This class performs no financial calculation of its own: every figure it displays is read
 * directly off an existing {@link MonthlyFinancialState} or {@link ScenarioComparison} that
 * {@code SimulationEngine}/{@code ScenarioComparisonCalculator} already produced. Its only job is
 * presentation — extracting the right already-computed values, taking a plain difference between
 * two of them where display calls for one ({@link SimulationResultView#getNetWorthChange()},
 * {@link SimulationResultView#getMonthlySurplus()}), and formatting {@link BigDecimal} amounts and
 * the date range as readable text. If a future milestone needs a metric that is not already a
 * plain difference of two already-known values, it should come from the simulation/domain layer,
 * not be invented here.
 *
 * <p><b>{@link #format}</b> shows only the baseline projection — see its own documentation.
 * <b>{@link #formatComparison}</b> (added in Milestone 5F, widened in Milestone 6A) reads every
 * figure it shows directly from {@link SimulationResult#getComparison()} — the
 * {@link ScenarioComparison} that {@code ScenarioComparisonCalculator} already built from the
 * baseline and scenario {@link Projection}s — rather than re-deriving anything from either
 * {@code Projection} itself, so there is exactly one place ({@code ScenarioComparisonCalculator})
 * that ever computes a scenario-vs-baseline difference.
 *
 * <p><b>Milestone 6A ("Expose the full Scenario Comparison"):</b> {@link ScenarioComparison} has,
 * since the Investments milestone, already computed five metric categories (net worth, investment
 * value, loan balance, cash balance, total investment contributions) — each as
 * baseline/scenario/difference, plus an {@code Optional<BigDecimal>} percentage change — but
 * {@link #formatComparison} previously read only the net worth triple. This milestone widens it to
 * read and format all twenty already-computed figures, using exactly the same
 * {@link #formatMoney}/{@link #formatSignedMoney} helpers the net worth fields already used, plus
 * one new helper, {@link #formatPercentageChange(Optional)}, for the five percentage figures. No
 * new calculation is introduced: every percentage value formatted here was already computed by
 * {@link ScenarioComparison} itself; this method only decides how to render an
 * {@link Optional#empty()} percentage (as {@code "n/a"} — see that method's own documentation) and
 * how to render a present one (as signed, two-decimal text with a {@code "%"} suffix, matching the
 * same sign convention {@link #formatSignedMoney} already uses for money).
 *
 * <p>"Starting" and "ending" net worth/income/expenses in {@link SimulationResultView} refer to
 * the projection's first and last simulated month, respectively — <b>not</b> the pre-simulation
 * snapshot at {@code asOfDate} itself, since {@link Projection} never includes that point (see its
 * own documentation). This class does not relabel or reinterpret that; it only formats what
 * {@link Projection} (or, for the comparison, {@link ScenarioComparison}) already contains.
 */
public final class SimulationResultFormatter {

    private SimulationResultFormatter() {
        // stateless utility class; not instantiable
    }

    public static SimulationResultView format(SimulationResult result) {
        Objects.requireNonNull(result, "result must not be null");

        Projection baseline = result.getBaselineProjection();
        List<MonthlyFinancialState> states = baseline.getStates();
        if (states.isEmpty()) {
            throw new IllegalArgumentException(
                    "SimulationResultFormatter cannot format a projection with zero simulated months");
        }

        MonthlyFinancialState firstMonth = states.get(0);
        MonthlyFinancialState lastMonth = states.get(states.size() - 1);

        BigDecimal startingNetWorth = firstMonth.getNetWorth();
        BigDecimal endingNetWorth = lastMonth.getNetWorth();
        BigDecimal netWorthChange = endingNetWorth.subtract(startingNetWorth);
        // Milestone 5I: a metric card for the dashboard, computed the same way netWorthChange
        // above already is — a plain subtraction of two values MonthlyFinancialState already
        // computed, not a new financial calculation (see this class's and SimulationResultView's
        // own documentation).
        BigDecimal monthlySurplus = firstMonth.getMonthlyIncome().subtract(firstMonth.getMonthlyExpenses());

        String projectionPeriod = baseline.getStartDate() + " to " + baseline.getEndDate()
                + " (" + states.size() + (states.size() == 1 ? " month)" : " months)");

        return new SimulationResultView(
                projectionPeriod,
                formatMoney(startingNetWorth),
                formatMoney(endingNetWorth),
                formatSignedMoney(netWorthChange),
                formatMoney(firstMonth.getMonthlyIncome()),
                formatMoney(firstMonth.getMonthlyExpenses()),
                formatSignedMoney(monthlySurplus));
    }

    /**
     * Formats {@code result}'s already-computed {@link ScenarioComparison} — all five metric
     * categories (net worth, investment value, loan balance, cash balance, total investment
     * contributions), each as baseline/scenario/difference/percentage-change — for display. Reads
     * every value directly off {@code result.getComparison()} and performs no arithmetic of its
     * own beyond formatting; see this class's own "Milestone 6A" documentation above.
     */
    public static ScenarioComparisonView formatComparison(SimulationResult result) {
        Objects.requireNonNull(result, "result must not be null");

        ScenarioComparison comparison = result.getComparison();

        return new ScenarioComparisonView(
                // net worth
                formatMoney(comparison.getBaselineFinalNetWorth()),
                formatMoney(comparison.getScenarioFinalNetWorth()),
                formatSignedMoney(comparison.getNetWorthDifference()),
                formatPercentageChange(comparison.getNetWorthPercentageChange()),
                // investment value
                formatMoney(comparison.getBaselineFinalInvestmentValue()),
                formatMoney(comparison.getScenarioFinalInvestmentValue()),
                formatSignedMoney(comparison.getInvestmentValueDifference()),
                formatPercentageChange(comparison.getInvestmentValuePercentageChange()),
                // loan balance
                formatMoney(comparison.getBaselineFinalLoanBalance()),
                formatMoney(comparison.getScenarioFinalLoanBalance()),
                formatSignedMoney(comparison.getLoanBalanceDifference()),
                formatPercentageChange(comparison.getLoanBalancePercentageChange()),
                // cash balance
                formatMoney(comparison.getBaselineFinalCashBalance()),
                formatMoney(comparison.getScenarioFinalCashBalance()),
                formatSignedMoney(comparison.getCashBalanceDifference()),
                formatPercentageChange(comparison.getCashBalancePercentageChange()),
                // total investment contributions
                formatMoney(comparison.getBaselineTotalInvestmentContributions()),
                formatMoney(comparison.getScenarioTotalInvestmentContributions()),
                formatSignedMoney(comparison.getInvestmentContributionDifference()),
                formatPercentageChange(comparison.getInvestmentContributionPercentageChange()));
    }

    private static String formatMoney(BigDecimal amount) {
        return String.format("%,.2f", amount);
    }

    private static String formatSignedMoney(BigDecimal amount) {
        return (amount.signum() > 0 ? "+" : "") + formatMoney(amount);
    }

    /**
     * Formats a {@link ScenarioComparison} percentage-change figure as signed, two-decimal text
     * with a {@code "%"} suffix (e.g. {@code "+12.34%"}, {@code "-8.00%"}, {@code "0.00%"}) —
     * matching {@link #formatSignedMoney}'s own sign convention (a leading {@code '+'} only for a
     * strictly positive value; {@link BigDecimal}'s own formatting already supplies the leading
     * {@code '-'} for a negative one). {@link Optional#empty()} — which
     * {@link ScenarioComparison} returns whenever the corresponding baseline figure is exactly
     * zero, since "percent change from nothing" is undefined — is rendered as {@code "n/a"} rather
     * than a misleading number; this is a presentation choice only, not a change to
     * {@link ScenarioComparison}'s own decision to leave that case undefined.
     */
    private static String formatPercentageChange(Optional<BigDecimal> percentageChange) {
        return percentageChange.map(SimulationResultFormatter::formatSignedPercentage).orElse("n/a");
    }

    private static String formatSignedPercentage(BigDecimal percentage) {
        return (percentage.signum() > 0 ? "+" : "") + String.format("%.2f", percentage) + "%";
    }
}
