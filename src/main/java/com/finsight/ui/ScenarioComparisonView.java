package com.finsight.ui;

import java.util.Objects;

/**
 * A plain, already-formatted view of a {@code com.finsight.simulation.comparison.ScenarioComparison},
 * ready for a JavaFX control to display directly. Every field is a display string produced by
 * {@link SimulationResultFormatter#formatComparison}; this class holds no comparison logic or
 * arithmetic of its own — it is purely a carrier. Immutable, with value-based equality so it can
 * be asserted against directly in a test.
 */
public final class ScenarioComparisonView {

    private final String baselineNetWorthAfterFinalMonth;
    private final String scenarioNetWorthAfterFinalMonth;
    private final String netWorthImpact;

    public ScenarioComparisonView(
            String baselineNetWorthAfterFinalMonth,
            String scenarioNetWorthAfterFinalMonth,
            String netWorthImpact) {
        this.baselineNetWorthAfterFinalMonth =
                Objects.requireNonNull(baselineNetWorthAfterFinalMonth, "baselineNetWorthAfterFinalMonth must not be null");
        this.scenarioNetWorthAfterFinalMonth =
                Objects.requireNonNull(scenarioNetWorthAfterFinalMonth, "scenarioNetWorthAfterFinalMonth must not be null");
        this.netWorthImpact = Objects.requireNonNull(netWorthImpact, "netWorthImpact must not be null");
    }

    /** The baseline projection's final simulated month's net worth, formatted as money. */
    public String getBaselineNetWorthAfterFinalMonth() {
        return baselineNetWorthAfterFinalMonth;
    }

    /** The scenario projection's final simulated month's net worth, formatted as money. */
    public String getScenarioNetWorthAfterFinalMonth() {
        return scenarioNetWorthAfterFinalMonth;
    }

    /** {@code ScenarioComparison.getNetWorthDifference()} (scenario − baseline), formatted as signed money. */
    public String getNetWorthImpact() {
        return netWorthImpact;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ScenarioComparisonView other)) {
            return false;
        }
        return baselineNetWorthAfterFinalMonth.equals(other.baselineNetWorthAfterFinalMonth)
                && scenarioNetWorthAfterFinalMonth.equals(other.scenarioNetWorthAfterFinalMonth)
                && netWorthImpact.equals(other.netWorthImpact);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baselineNetWorthAfterFinalMonth, scenarioNetWorthAfterFinalMonth, netWorthImpact);
    }

    @Override
    public String toString() {
        return "ScenarioComparisonView{baselineNetWorthAfterFinalMonth='" + baselineNetWorthAfterFinalMonth + '\''
                + ", scenarioNetWorthAfterFinalMonth='" + scenarioNetWorthAfterFinalMonth + '\''
                + ", netWorthImpact='" + netWorthImpact + '\''
                + '}';
    }
}
