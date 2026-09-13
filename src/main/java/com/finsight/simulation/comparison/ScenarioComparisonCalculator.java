package com.finsight.simulation.comparison;

import com.finsight.common.FinancialPrecision;
import com.finsight.simulation.model.MonthlyFinancialState;
import com.finsight.simulation.model.Projection;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Builds a {@link ScenarioComparison} from a baseline {@link Projection} and a scenario
 * {@link Projection} — typically the two outputs of {@code SimulationEngine.simulate}, one with
 * no scenario events and one with them, though this class does not know or care where either
 * {@code Projection} came from.
 *
 * <p>This class is stateless: it holds no projection data of its own, only a pure function of the
 * two {@link Projection}s it is given. It depends on nothing outside the JDK and
 * {@code com.finsight.simulation.model} — no JavaFX, no {@code Scenario}, no JDBC, no persistence,
 * no service layer — so it can be unit-tested in isolation, matching every other calculator in
 * this project. It does not modify or re-validate either {@link Projection} or the
 * {@link MonthlyFinancialState}s it reads from them; those are already guaranteed valid by their
 * own constructors.
 *
 * <p><b>Final-state metrics</b> (net worth, investment value, loan balance, cash balance) are read
 * from the last element of {@link Projection#getStates()} — the last simulated month — for each
 * side.
 *
 * <p><b>Cumulative metrics</b> (total investment contributions) are the sum of
 * {@link MonthlyFinancialState#getInvestmentContribution()} across every state in the projection,
 * for each side. Since every contribution is already a non-negative, 2-decimal monetary value,
 * summing them loses no precision; the total is (re-)rounded only for consistency with every other
 * monetary value this project produces.
 *
 * <p><b>Validation:</b> {@link Projection} itself does not forbid an empty {@code states} list (it
 * only requires {@code endDate >= startDate}), but a comparison has no "final month" to read from
 * an empty projection, so this class rejects that case itself rather than let it surface later as
 * an unchecked {@code IndexOutOfBoundsException}. In ordinary use — a {@code Projection} produced
 * by {@code SimulationEngine} — this can never happen, since {@code SimulationParameters} already
 * requires {@code projectionMonths > 0}.
 */
public final class ScenarioComparisonCalculator {

    private ScenarioComparisonCalculator() {
        // stateless utility class; not instantiable
    }

    /**
     * Compares {@code scenario} against {@code baseline}, producing a {@link ScenarioComparison}
     * whose every {@code *Difference} figure is {@code scenario - baseline} for that metric (see
     * {@link ScenarioComparison} for the full sign convention).
     */
    public static ScenarioComparison compare(Projection baseline, Projection scenario) {
        Objects.requireNonNull(baseline, "baseline must not be null");
        Objects.requireNonNull(scenario, "scenario must not be null");

        MonthlyFinancialState baselineFinal = finalStateOf(baseline, "baseline");
        MonthlyFinancialState scenarioFinal = finalStateOf(scenario, "scenario");

        return new ScenarioComparison(
                baselineFinal.getNetWorth(), scenarioFinal.getNetWorth(),
                baselineFinal.getInvestmentValue(), scenarioFinal.getInvestmentValue(),
                baselineFinal.getLoanBalance(), scenarioFinal.getLoanBalance(),
                baselineFinal.getCashBalance(), scenarioFinal.getCashBalance(),
                sumInvestmentContributions(baseline), sumInvestmentContributions(scenario));
    }

    private static MonthlyFinancialState finalStateOf(Projection projection, String label) {
        List<MonthlyFinancialState> states = projection.getStates();
        if (states.isEmpty()) {
            throw new IllegalArgumentException(
                    label + " projection must contain at least one MonthlyFinancialState to compare");
        }
        return states.get(states.size() - 1);
    }

    private static BigDecimal sumInvestmentContributions(Projection projection) {
        BigDecimal total = BigDecimal.ZERO;
        for (MonthlyFinancialState state : projection.getStates()) {
            total = total.add(state.getInvestmentContribution());
        }
        return FinancialPrecision.roundMoney(total);
    }
}
