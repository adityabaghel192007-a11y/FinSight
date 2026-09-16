package com.finsight.ui;

import com.finsight.service.SimulationResult;
import com.finsight.simulation.comparison.ScenarioComparison;
import com.finsight.simulation.model.MonthlyFinancialState;
import com.finsight.simulation.model.Projection;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

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
 * <b>{@link #formatComparison}</b> (added in Milestone 5F) reads every figure it shows directly
 * from {@link SimulationResult#getComparison()} — the {@link ScenarioComparison} that
 * {@code ScenarioComparisonCalculator} already built from the baseline and scenario
 * {@link Projection}s — rather than re-deriving anything from either {@code Projection} itself, so
 * there is exactly one place ({@code ScenarioComparisonCalculator}) that ever computes a
 * scenario-vs-baseline difference.
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
     * Formats {@code result}'s already-computed {@link ScenarioComparison} (baseline vs. scenario
     * final-month net worth, and their difference) for display. Reads only
     * {@link ScenarioComparison#getBaselineFinalNetWorth()},
     * {@link ScenarioComparison#getScenarioFinalNetWorth()}, and
     * {@link ScenarioComparison#getNetWorthDifference()} — every figure {@code
     * ScenarioComparisonCalculator} already derived from the two {@link Projection}s' final
     * simulated months — and performs no arithmetic of its own beyond formatting.
     */
    public static ScenarioComparisonView formatComparison(SimulationResult result) {
        Objects.requireNonNull(result, "result must not be null");

        ScenarioComparison comparison = result.getComparison();

        return new ScenarioComparisonView(
                formatMoney(comparison.getBaselineFinalNetWorth()),
                formatMoney(comparison.getScenarioFinalNetWorth()),
                formatSignedMoney(comparison.getNetWorthDifference()));
    }

    private static String formatMoney(BigDecimal amount) {
        return String.format("%,.2f", amount);
    }

    private static String formatSignedMoney(BigDecimal amount) {
        return (amount.signum() > 0 ? "+" : "") + formatMoney(amount);
    }
}
