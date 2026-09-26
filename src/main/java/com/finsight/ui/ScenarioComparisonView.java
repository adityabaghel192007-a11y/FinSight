package com.finsight.ui;

import java.util.Objects;

/**
 * A plain, already-formatted view of a {@code com.finsight.simulation.comparison.ScenarioComparison},
 * ready for a JavaFX control to display directly. Every field is a display string produced by
 * {@link SimulationResultFormatter#formatComparison}; this class holds no comparison logic or
 * arithmetic of its own — it is purely a carrier. Immutable, with value-based equality so it can
 * be asserted against directly in a test.
 *
 * <p><b>Milestone 6A ("Expose the full Scenario Comparison"):</b> {@code ScenarioComparison}
 * already computed five metric categories (net worth, investment value, loan balance, cash
 * balance, total investment contributions) — each as baseline/scenario/difference, plus an
 * {@code Optional<BigDecimal>} percentage change — since the Decision Lab reconnaissance
 * milestone, but this class previously surfaced only the net worth triple. This milestone widens
 * it to expose all twenty already-computed figures as display strings, following exactly the
 * three original net-worth fields' own naming and formatting conventions (an {@code AfterFinalMonth}
 * suffix for a final-month balance, an {@code Impact} suffix for a {@code scenario - baseline}
 * difference, a {@code PercentageChange} suffix for the corresponding percentage). No new
 * calculation is introduced anywhere in this class — every field is still read, unchanged, from an
 * already-computed {@code ScenarioComparison} figure.
 *
 * <p>{@code baselineTotalInvestmentContributions}/{@code scenarioTotalInvestmentContributions} are
 * named to match {@code ScenarioComparison}'s own getters rather than reusing the
 * {@code AfterFinalMonth} suffix, since that figure is a sum across every simulated month, not a
 * single final-month balance — the same distinction {@code ScenarioComparison} itself draws in its
 * own documentation.
 *
 * <p>A percentage-change field is {@code "n/a"} whenever the corresponding
 * {@code ScenarioComparison} percentage getter returned {@link java.util.Optional#empty()} (a
 * zero baseline) — see {@link SimulationResultFormatter} for where that string is produced. This
 * class does not decide that convention itself, only carries the already-decided string.
 */
public final class ScenarioComparisonView {

    // ===================== net worth =====================
    private final String baselineNetWorthAfterFinalMonth;
    private final String scenarioNetWorthAfterFinalMonth;
    private final String netWorthImpact;
    private final String netWorthPercentageChange;

    // ===================== investment value =====================
    private final String baselineInvestmentValueAfterFinalMonth;
    private final String scenarioInvestmentValueAfterFinalMonth;
    private final String investmentValueImpact;
    private final String investmentValuePercentageChange;

    // ===================== loan balance =====================
    private final String baselineLoanBalanceAfterFinalMonth;
    private final String scenarioLoanBalanceAfterFinalMonth;
    private final String loanBalanceImpact;
    private final String loanBalancePercentageChange;

    // ===================== cash balance =====================
    private final String baselineCashBalanceAfterFinalMonth;
    private final String scenarioCashBalanceAfterFinalMonth;
    private final String cashBalanceImpact;
    private final String cashBalancePercentageChange;

    // ===================== total investment contributions =====================
    private final String baselineTotalInvestmentContributions;
    private final String scenarioTotalInvestmentContributions;
    private final String investmentContributionImpact;
    private final String investmentContributionPercentageChange;

    public ScenarioComparisonView(
            String baselineNetWorthAfterFinalMonth,
            String scenarioNetWorthAfterFinalMonth,
            String netWorthImpact,
            String netWorthPercentageChange,
            String baselineInvestmentValueAfterFinalMonth,
            String scenarioInvestmentValueAfterFinalMonth,
            String investmentValueImpact,
            String investmentValuePercentageChange,
            String baselineLoanBalanceAfterFinalMonth,
            String scenarioLoanBalanceAfterFinalMonth,
            String loanBalanceImpact,
            String loanBalancePercentageChange,
            String baselineCashBalanceAfterFinalMonth,
            String scenarioCashBalanceAfterFinalMonth,
            String cashBalanceImpact,
            String cashBalancePercentageChange,
            String baselineTotalInvestmentContributions,
            String scenarioTotalInvestmentContributions,
            String investmentContributionImpact,
            String investmentContributionPercentageChange) {
        this.baselineNetWorthAfterFinalMonth =
                Objects.requireNonNull(baselineNetWorthAfterFinalMonth, "baselineNetWorthAfterFinalMonth must not be null");
        this.scenarioNetWorthAfterFinalMonth =
                Objects.requireNonNull(scenarioNetWorthAfterFinalMonth, "scenarioNetWorthAfterFinalMonth must not be null");
        this.netWorthImpact = Objects.requireNonNull(netWorthImpact, "netWorthImpact must not be null");
        this.netWorthPercentageChange =
                Objects.requireNonNull(netWorthPercentageChange, "netWorthPercentageChange must not be null");

        this.baselineInvestmentValueAfterFinalMonth = Objects.requireNonNull(
                baselineInvestmentValueAfterFinalMonth, "baselineInvestmentValueAfterFinalMonth must not be null");
        this.scenarioInvestmentValueAfterFinalMonth = Objects.requireNonNull(
                scenarioInvestmentValueAfterFinalMonth, "scenarioInvestmentValueAfterFinalMonth must not be null");
        this.investmentValueImpact = Objects.requireNonNull(investmentValueImpact, "investmentValueImpact must not be null");
        this.investmentValuePercentageChange = Objects.requireNonNull(
                investmentValuePercentageChange, "investmentValuePercentageChange must not be null");

        this.baselineLoanBalanceAfterFinalMonth = Objects.requireNonNull(
                baselineLoanBalanceAfterFinalMonth, "baselineLoanBalanceAfterFinalMonth must not be null");
        this.scenarioLoanBalanceAfterFinalMonth = Objects.requireNonNull(
                scenarioLoanBalanceAfterFinalMonth, "scenarioLoanBalanceAfterFinalMonth must not be null");
        this.loanBalanceImpact = Objects.requireNonNull(loanBalanceImpact, "loanBalanceImpact must not be null");
        this.loanBalancePercentageChange =
                Objects.requireNonNull(loanBalancePercentageChange, "loanBalancePercentageChange must not be null");

        this.baselineCashBalanceAfterFinalMonth = Objects.requireNonNull(
                baselineCashBalanceAfterFinalMonth, "baselineCashBalanceAfterFinalMonth must not be null");
        this.scenarioCashBalanceAfterFinalMonth = Objects.requireNonNull(
                scenarioCashBalanceAfterFinalMonth, "scenarioCashBalanceAfterFinalMonth must not be null");
        this.cashBalanceImpact = Objects.requireNonNull(cashBalanceImpact, "cashBalanceImpact must not be null");
        this.cashBalancePercentageChange =
                Objects.requireNonNull(cashBalancePercentageChange, "cashBalancePercentageChange must not be null");

        this.baselineTotalInvestmentContributions = Objects.requireNonNull(
                baselineTotalInvestmentContributions, "baselineTotalInvestmentContributions must not be null");
        this.scenarioTotalInvestmentContributions = Objects.requireNonNull(
                scenarioTotalInvestmentContributions, "scenarioTotalInvestmentContributions must not be null");
        this.investmentContributionImpact =
                Objects.requireNonNull(investmentContributionImpact, "investmentContributionImpact must not be null");
        this.investmentContributionPercentageChange = Objects.requireNonNull(
                investmentContributionPercentageChange, "investmentContributionPercentageChange must not be null");
    }

    // ===================== net worth =====================

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

    /**
     * {@code ScenarioComparison.getNetWorthPercentageChange()}, formatted as a signed percentage,
     * or {@code "n/a"} when the baseline final net worth is exactly zero.
     */
    public String getNetWorthPercentageChange() {
        return netWorthPercentageChange;
    }

    // ===================== investment value =====================

    /** The baseline projection's final simulated month's total investment value, formatted as money. */
    public String getBaselineInvestmentValueAfterFinalMonth() {
        return baselineInvestmentValueAfterFinalMonth;
    }

    /** The scenario projection's final simulated month's total investment value, formatted as money. */
    public String getScenarioInvestmentValueAfterFinalMonth() {
        return scenarioInvestmentValueAfterFinalMonth;
    }

    /** {@code ScenarioComparison.getInvestmentValueDifference()}, formatted as signed money. */
    public String getInvestmentValueImpact() {
        return investmentValueImpact;
    }

    /**
     * {@code ScenarioComparison.getInvestmentValuePercentageChange()}, formatted as a signed
     * percentage, or {@code "n/a"} when the baseline final investment value is exactly zero.
     */
    public String getInvestmentValuePercentageChange() {
        return investmentValuePercentageChange;
    }

    // ===================== loan balance =====================

    /** The baseline projection's final simulated month's total loan balance, formatted as money. */
    public String getBaselineLoanBalanceAfterFinalMonth() {
        return baselineLoanBalanceAfterFinalMonth;
    }

    /** The scenario projection's final simulated month's total loan balance, formatted as money. */
    public String getScenarioLoanBalanceAfterFinalMonth() {
        return scenarioLoanBalanceAfterFinalMonth;
    }

    /** {@code ScenarioComparison.getLoanBalanceDifference()}, formatted as signed money. */
    public String getLoanBalanceImpact() {
        return loanBalanceImpact;
    }

    /**
     * {@code ScenarioComparison.getLoanBalancePercentageChange()}, formatted as a signed
     * percentage, or {@code "n/a"} when the baseline final loan balance is exactly zero.
     */
    public String getLoanBalancePercentageChange() {
        return loanBalancePercentageChange;
    }

    // ===================== cash balance =====================

    /** The baseline projection's final simulated month's cash balance, formatted as money. */
    public String getBaselineCashBalanceAfterFinalMonth() {
        return baselineCashBalanceAfterFinalMonth;
    }

    /** The scenario projection's final simulated month's cash balance, formatted as money. */
    public String getScenarioCashBalanceAfterFinalMonth() {
        return scenarioCashBalanceAfterFinalMonth;
    }

    /** {@code ScenarioComparison.getCashBalanceDifference()}, formatted as signed money. */
    public String getCashBalanceImpact() {
        return cashBalanceImpact;
    }

    /**
     * {@code ScenarioComparison.getCashBalancePercentageChange()}, formatted as a signed
     * percentage, or {@code "n/a"} when the baseline final cash balance is exactly zero.
     */
    public String getCashBalancePercentageChange() {
        return cashBalancePercentageChange;
    }

    // ===================== total investment contributions =====================

    /** The baseline projection's total investment contributions summed across every simulated month, formatted as money. */
    public String getBaselineTotalInvestmentContributions() {
        return baselineTotalInvestmentContributions;
    }

    /** The scenario projection's total investment contributions summed across every simulated month, formatted as money. */
    public String getScenarioTotalInvestmentContributions() {
        return scenarioTotalInvestmentContributions;
    }

    /** {@code ScenarioComparison.getInvestmentContributionDifference()}, formatted as signed money. */
    public String getInvestmentContributionImpact() {
        return investmentContributionImpact;
    }

    /**
     * {@code ScenarioComparison.getInvestmentContributionPercentageChange()}, formatted as a
     * signed percentage, or {@code "n/a"} when the baseline total investment contributions figure
     * is exactly zero.
     */
    public String getInvestmentContributionPercentageChange() {
        return investmentContributionPercentageChange;
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
                && netWorthImpact.equals(other.netWorthImpact)
                && netWorthPercentageChange.equals(other.netWorthPercentageChange)
                && baselineInvestmentValueAfterFinalMonth.equals(other.baselineInvestmentValueAfterFinalMonth)
                && scenarioInvestmentValueAfterFinalMonth.equals(other.scenarioInvestmentValueAfterFinalMonth)
                && investmentValueImpact.equals(other.investmentValueImpact)
                && investmentValuePercentageChange.equals(other.investmentValuePercentageChange)
                && baselineLoanBalanceAfterFinalMonth.equals(other.baselineLoanBalanceAfterFinalMonth)
                && scenarioLoanBalanceAfterFinalMonth.equals(other.scenarioLoanBalanceAfterFinalMonth)
                && loanBalanceImpact.equals(other.loanBalanceImpact)
                && loanBalancePercentageChange.equals(other.loanBalancePercentageChange)
                && baselineCashBalanceAfterFinalMonth.equals(other.baselineCashBalanceAfterFinalMonth)
                && scenarioCashBalanceAfterFinalMonth.equals(other.scenarioCashBalanceAfterFinalMonth)
                && cashBalanceImpact.equals(other.cashBalanceImpact)
                && cashBalancePercentageChange.equals(other.cashBalancePercentageChange)
                && baselineTotalInvestmentContributions.equals(other.baselineTotalInvestmentContributions)
                && scenarioTotalInvestmentContributions.equals(other.scenarioTotalInvestmentContributions)
                && investmentContributionImpact.equals(other.investmentContributionImpact)
                && investmentContributionPercentageChange.equals(other.investmentContributionPercentageChange);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                baselineNetWorthAfterFinalMonth,
                scenarioNetWorthAfterFinalMonth,
                netWorthImpact,
                netWorthPercentageChange,
                baselineInvestmentValueAfterFinalMonth,
                scenarioInvestmentValueAfterFinalMonth,
                investmentValueImpact,
                investmentValuePercentageChange,
                baselineLoanBalanceAfterFinalMonth,
                scenarioLoanBalanceAfterFinalMonth,
                loanBalanceImpact,
                loanBalancePercentageChange,
                baselineCashBalanceAfterFinalMonth,
                scenarioCashBalanceAfterFinalMonth,
                cashBalanceImpact,
                cashBalancePercentageChange,
                baselineTotalInvestmentContributions,
                scenarioTotalInvestmentContributions,
                investmentContributionImpact,
                investmentContributionPercentageChange);
    }

    @Override
    public String toString() {
        return "ScenarioComparisonView{baselineNetWorthAfterFinalMonth='" + baselineNetWorthAfterFinalMonth + '\''
                + ", scenarioNetWorthAfterFinalMonth='" + scenarioNetWorthAfterFinalMonth + '\''
                + ", netWorthImpact='" + netWorthImpact + '\''
                + ", netWorthPercentageChange='" + netWorthPercentageChange + '\''
                + ", baselineInvestmentValueAfterFinalMonth='" + baselineInvestmentValueAfterFinalMonth + '\''
                + ", scenarioInvestmentValueAfterFinalMonth='" + scenarioInvestmentValueAfterFinalMonth + '\''
                + ", investmentValueImpact='" + investmentValueImpact + '\''
                + ", investmentValuePercentageChange='" + investmentValuePercentageChange + '\''
                + ", baselineLoanBalanceAfterFinalMonth='" + baselineLoanBalanceAfterFinalMonth + '\''
                + ", scenarioLoanBalanceAfterFinalMonth='" + scenarioLoanBalanceAfterFinalMonth + '\''
                + ", loanBalanceImpact='" + loanBalanceImpact + '\''
                + ", loanBalancePercentageChange='" + loanBalancePercentageChange + '\''
                + ", baselineCashBalanceAfterFinalMonth='" + baselineCashBalanceAfterFinalMonth + '\''
                + ", scenarioCashBalanceAfterFinalMonth='" + scenarioCashBalanceAfterFinalMonth + '\''
                + ", cashBalanceImpact='" + cashBalanceImpact + '\''
                + ", cashBalancePercentageChange='" + cashBalancePercentageChange + '\''
                + ", baselineTotalInvestmentContributions='" + baselineTotalInvestmentContributions + '\''
                + ", scenarioTotalInvestmentContributions='" + scenarioTotalInvestmentContributions + '\''
                + ", investmentContributionImpact='" + investmentContributionImpact + '\''
                + ", investmentContributionPercentageChange='" + investmentContributionPercentageChange + '\''
                + '}';
    }
}
