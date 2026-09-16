package com.finsight.ui;

import java.util.Objects;

/**
 * A plain, already-formatted view of a {@code com.finsight.service.SimulationResult}, ready for a
 * JavaFX control to display directly (a {@code Label}'s text, for example) without that UI code
 * needing to know anything about {@code BigDecimal} scale, rounding, or which
 * {@code MonthlyFinancialState} fields the figures came from.
 *
 * <p>Every field here is a display string, not a financial value: this class holds no monetary
 * logic of its own and performs no arithmetic — it is purely a carrier for strings
 * {@link SimulationResultFormatter} has already produced. Immutable, with value-based equality so
 * it can be asserted against directly in a test.
 *
 * <p><b>{@link #getMonthlySurplus()}</b> (added in Milestone 5I, for the dashboard's metric cards)
 * is the one exception to "no arithmetic" worth calling out explicitly — it is
 * {@code monthlyIncome - monthlyExpenses} for the same first simulated month
 * {@link #getMonthlyIncome()}/{@link #getMonthlyExpenses()} already describe, computed by
 * {@link SimulationResultFormatter} from the same two already-known {@code BigDecimal} values
 * before they are formatted to strings — exactly the same kind of presentation-layer subtraction
 * {@link #getNetWorthChange()} already performs (see {@link SimulationResultFormatter}'s own
 * documentation on why that does not count as "financial calculation" for this class's purposes).
 * It introduces no new domain concept and duplicates no simulation logic.
 */
public final class SimulationResultView {

    private final String projectionPeriod;
    private final String startingNetWorth;
    private final String endingNetWorth;
    private final String netWorthChange;
    private final String monthlyIncome;
    private final String monthlyExpenses;
    private final String monthlySurplus;

    public SimulationResultView(
            String projectionPeriod,
            String startingNetWorth,
            String endingNetWorth,
            String netWorthChange,
            String monthlyIncome,
            String monthlyExpenses,
            String monthlySurplus) {
        this.projectionPeriod = Objects.requireNonNull(projectionPeriod, "projectionPeriod must not be null");
        this.startingNetWorth = Objects.requireNonNull(startingNetWorth, "startingNetWorth must not be null");
        this.endingNetWorth = Objects.requireNonNull(endingNetWorth, "endingNetWorth must not be null");
        this.netWorthChange = Objects.requireNonNull(netWorthChange, "netWorthChange must not be null");
        this.monthlyIncome = Objects.requireNonNull(monthlyIncome, "monthlyIncome must not be null");
        this.monthlyExpenses = Objects.requireNonNull(monthlyExpenses, "monthlyExpenses must not be null");
        this.monthlySurplus = Objects.requireNonNull(monthlySurplus, "monthlySurplus must not be null");
    }

    /** e.g. {@code "2026-04-01 to 2027-03-01 (12 months)"}. */
    public String getProjectionPeriod() {
        return projectionPeriod;
    }

    /** The baseline projection's first simulated month's net worth, formatted as money. */
    public String getStartingNetWorth() {
        return startingNetWorth;
    }

    /** The baseline projection's final simulated month's net worth, formatted as money. */
    public String getEndingNetWorth() {
        return endingNetWorth;
    }

    /** {@code endingNetWorth - startingNetWorth} over the projection, formatted as signed money. */
    public String getNetWorthChange() {
        return netWorthChange;
    }

    /** The monthly income used for the first simulated month, formatted as money. */
    public String getMonthlyIncome() {
        return monthlyIncome;
    }

    /** The monthly expenses used for the first simulated month, formatted as money. */
    public String getMonthlyExpenses() {
        return monthlyExpenses;
    }

    /**
     * {@code monthlyIncome - monthlyExpenses} for the first simulated month, formatted as signed
     * money (see the class-level documentation for why this is not "new arithmetic" in the sense
     * the rest of this class's documentation warns against).
     */
    public String getMonthlySurplus() {
        return monthlySurplus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SimulationResultView other)) {
            return false;
        }
        return projectionPeriod.equals(other.projectionPeriod)
                && startingNetWorth.equals(other.startingNetWorth)
                && endingNetWorth.equals(other.endingNetWorth)
                && netWorthChange.equals(other.netWorthChange)
                && monthlyIncome.equals(other.monthlyIncome)
                && monthlyExpenses.equals(other.monthlyExpenses)
                && monthlySurplus.equals(other.monthlySurplus);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectionPeriod, startingNetWorth, endingNetWorth, netWorthChange,
                monthlyIncome, monthlyExpenses, monthlySurplus);
    }

    @Override
    public String toString() {
        return "SimulationResultView{projectionPeriod='" + projectionPeriod + '\''
                + ", startingNetWorth='" + startingNetWorth + '\''
                + ", endingNetWorth='" + endingNetWorth + '\''
                + ", netWorthChange='" + netWorthChange + '\''
                + ", monthlyIncome='" + monthlyIncome + '\''
                + ", monthlyExpenses='" + monthlyExpenses + '\''
                + ", monthlySurplus='" + monthlySurplus + '\''
                + '}';
    }
}
