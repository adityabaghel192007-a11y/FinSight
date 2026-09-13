package com.finsight.simulation.comparison;

import com.finsight.common.FinancialPrecision;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * The result of comparing a baseline {@code Projection} against a scenario {@code Projection}:
 * the final-state and cumulative-contribution figures from each side, plus the difference between
 * them for every metric.
 *
 * <p>This class holds no reference to {@code Projection} or {@code MonthlyFinancialState} at all
 * — it is a plain holder of the {@link BigDecimal} figures those classes ultimately produce, built
 * from whatever values a caller supplies. That keeps it usable anywhere two sets of financial
 * figures need comparing, and keeps it free of every layer above the simulation model (JavaFX,
 * {@code Scenario}, persistence, services) as well as of the model classes themselves — matching
 * the same "pure function of the values it's given" spirit as {@code LoanPayment} and
 * {@code InvestmentProjection}. Extracting those figures from two {@code Projection}s is
 * {@link ScenarioComparisonCalculator}'s job, not this class's.
 *
 * <p><b>Difference sign convention:</b> every {@code *Difference} field is computed as exactly
 * {@code scenario - baseline}, once, in this constructor — never re-derived or re-signed anywhere
 * else. A positive net-worth, investment-value, cash, or contribution difference therefore means
 * the scenario is higher than the baseline on that metric; a positive loan-balance difference
 * means the scenario carries <i>more</i> debt than the baseline (i.e. a negative loan-balance
 * difference is the "improvement" direction for that one metric, unlike the other four).
 *
 * <p><b>Which figures may be negative:</b> mirrors {@code MonthlyFinancialState}'s own rule, since
 * these figures are that class's final-month values. Net worth and cash balance may be negative
 * (a shortfall is a real, meaningful result this simulator surfaces rather than hides), so neither
 * they nor the differences derived from them are sign-restricted. Investment value, loan balance,
 * and total investment contributions can never legitimately be negative, so both the baseline and
 * scenario figures for each are validated {@code >= 0}; the differences derived from them, being a
 * subtraction of two non-negative numbers, are left unrestricted in sign.
 *
 * <p><b>Percentage change:</b> deliberately not stored as fields — see {@link #getNetWorthPercentageChange()}
 * and its siblings, computed on demand from the stored absolute figures rather than carried as
 * independent state that could ever drift from them. Percentage change is defined as
 * {@code (difference / |baseline|) * 100}; when {@code baseline} is exactly zero the ratio is
 * undefined, so {@link Optional#empty()} is returned rather than dividing by zero or reporting an
 * arbitrary sentinel.
 *
 * <p>Instances are immutable and validated eagerly in the constructor. Every {@link BigDecimal}
 * field is rounded via {@link FinancialPrecision#roundMoney(BigDecimal)} at construction, matching
 * the money-rounding convention used by every calculator in this project.
 *
 * <p><b>Equality:</b> a calculated value, not a persisted entity — equality is based on all
 * fields, with {@link BigDecimal} fields compared using {@link BigDecimal#compareTo} (numeric
 * value, ignoring scale), matching the convention used by {@code MonthlyFinancialState},
 * {@code FinancialSnapshot}, and {@code SimulationParameters}.
 */
public final class ScenarioComparison {

    private static final int PERCENTAGE_SCALE = 2;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final BigDecimal baselineFinalNetWorth;
    private final BigDecimal scenarioFinalNetWorth;
    private final BigDecimal netWorthDifference;

    private final BigDecimal baselineFinalInvestmentValue;
    private final BigDecimal scenarioFinalInvestmentValue;
    private final BigDecimal investmentValueDifference;

    private final BigDecimal baselineFinalLoanBalance;
    private final BigDecimal scenarioFinalLoanBalance;
    private final BigDecimal loanBalanceDifference;

    private final BigDecimal baselineFinalCashBalance;
    private final BigDecimal scenarioFinalCashBalance;
    private final BigDecimal cashBalanceDifference;

    private final BigDecimal baselineTotalInvestmentContributions;
    private final BigDecimal scenarioTotalInvestmentContributions;
    private final BigDecimal investmentContributionDifference;

    public ScenarioComparison(
            BigDecimal baselineFinalNetWorth,
            BigDecimal scenarioFinalNetWorth,
            BigDecimal baselineFinalInvestmentValue,
            BigDecimal scenarioFinalInvestmentValue,
            BigDecimal baselineFinalLoanBalance,
            BigDecimal scenarioFinalLoanBalance,
            BigDecimal baselineFinalCashBalance,
            BigDecimal scenarioFinalCashBalance,
            BigDecimal baselineTotalInvestmentContributions,
            BigDecimal scenarioTotalInvestmentContributions) {

        Objects.requireNonNull(baselineFinalNetWorth, "baselineFinalNetWorth must not be null");
        Objects.requireNonNull(scenarioFinalNetWorth, "scenarioFinalNetWorth must not be null");

        Objects.requireNonNull(baselineFinalInvestmentValue, "baselineFinalInvestmentValue must not be null");
        Objects.requireNonNull(scenarioFinalInvestmentValue, "scenarioFinalInvestmentValue must not be null");
        requireNonNegative(baselineFinalInvestmentValue, "baselineFinalInvestmentValue");
        requireNonNegative(scenarioFinalInvestmentValue, "scenarioFinalInvestmentValue");

        Objects.requireNonNull(baselineFinalLoanBalance, "baselineFinalLoanBalance must not be null");
        Objects.requireNonNull(scenarioFinalLoanBalance, "scenarioFinalLoanBalance must not be null");
        requireNonNegative(baselineFinalLoanBalance, "baselineFinalLoanBalance");
        requireNonNegative(scenarioFinalLoanBalance, "scenarioFinalLoanBalance");

        Objects.requireNonNull(baselineFinalCashBalance, "baselineFinalCashBalance must not be null");
        Objects.requireNonNull(scenarioFinalCashBalance, "scenarioFinalCashBalance must not be null");

        Objects.requireNonNull(baselineTotalInvestmentContributions, "baselineTotalInvestmentContributions must not be null");
        Objects.requireNonNull(scenarioTotalInvestmentContributions, "scenarioTotalInvestmentContributions must not be null");
        requireNonNegative(baselineTotalInvestmentContributions, "baselineTotalInvestmentContributions");
        requireNonNegative(scenarioTotalInvestmentContributions, "scenarioTotalInvestmentContributions");

        this.baselineFinalNetWorth = FinancialPrecision.roundMoney(baselineFinalNetWorth);
        this.scenarioFinalNetWorth = FinancialPrecision.roundMoney(scenarioFinalNetWorth);
        this.netWorthDifference = FinancialPrecision.roundMoney(scenarioFinalNetWorth.subtract(baselineFinalNetWorth));

        this.baselineFinalInvestmentValue = FinancialPrecision.roundMoney(baselineFinalInvestmentValue);
        this.scenarioFinalInvestmentValue = FinancialPrecision.roundMoney(scenarioFinalInvestmentValue);
        this.investmentValueDifference = FinancialPrecision.roundMoney(scenarioFinalInvestmentValue.subtract(baselineFinalInvestmentValue));

        this.baselineFinalLoanBalance = FinancialPrecision.roundMoney(baselineFinalLoanBalance);
        this.scenarioFinalLoanBalance = FinancialPrecision.roundMoney(scenarioFinalLoanBalance);
        this.loanBalanceDifference = FinancialPrecision.roundMoney(scenarioFinalLoanBalance.subtract(baselineFinalLoanBalance));

        this.baselineFinalCashBalance = FinancialPrecision.roundMoney(baselineFinalCashBalance);
        this.scenarioFinalCashBalance = FinancialPrecision.roundMoney(scenarioFinalCashBalance);
        this.cashBalanceDifference = FinancialPrecision.roundMoney(scenarioFinalCashBalance.subtract(baselineFinalCashBalance));

        this.baselineTotalInvestmentContributions = FinancialPrecision.roundMoney(baselineTotalInvestmentContributions);
        this.scenarioTotalInvestmentContributions = FinancialPrecision.roundMoney(scenarioTotalInvestmentContributions);
        this.investmentContributionDifference =
                FinancialPrecision.roundMoney(scenarioTotalInvestmentContributions.subtract(baselineTotalInvestmentContributions));
    }

    public BigDecimal getBaselineFinalNetWorth() {
        return baselineFinalNetWorth;
    }

    public BigDecimal getScenarioFinalNetWorth() {
        return scenarioFinalNetWorth;
    }

    public BigDecimal getNetWorthDifference() {
        return netWorthDifference;
    }

    public BigDecimal getBaselineFinalInvestmentValue() {
        return baselineFinalInvestmentValue;
    }

    public BigDecimal getScenarioFinalInvestmentValue() {
        return scenarioFinalInvestmentValue;
    }

    public BigDecimal getInvestmentValueDifference() {
        return investmentValueDifference;
    }

    public BigDecimal getBaselineFinalLoanBalance() {
        return baselineFinalLoanBalance;
    }

    public BigDecimal getScenarioFinalLoanBalance() {
        return scenarioFinalLoanBalance;
    }

    public BigDecimal getLoanBalanceDifference() {
        return loanBalanceDifference;
    }

    public BigDecimal getBaselineFinalCashBalance() {
        return baselineFinalCashBalance;
    }

    public BigDecimal getScenarioFinalCashBalance() {
        return scenarioFinalCashBalance;
    }

    public BigDecimal getCashBalanceDifference() {
        return cashBalanceDifference;
    }

    public BigDecimal getBaselineTotalInvestmentContributions() {
        return baselineTotalInvestmentContributions;
    }

    public BigDecimal getScenarioTotalInvestmentContributions() {
        return scenarioTotalInvestmentContributions;
    }

    public BigDecimal getInvestmentContributionDifference() {
        return investmentContributionDifference;
    }

    /**
     * {@code (netWorthDifference / |baselineFinalNetWorth|) * 100}, or {@link Optional#empty()}
     * when the baseline final net worth is exactly zero.
     */
    public Optional<BigDecimal> getNetWorthPercentageChange() {
        return percentageChange(baselineFinalNetWorth, netWorthDifference);
    }

    /**
     * {@code (investmentValueDifference / |baselineFinalInvestmentValue|) * 100}, or
     * {@link Optional#empty()} when the baseline final investment value is exactly zero.
     */
    public Optional<BigDecimal> getInvestmentValuePercentageChange() {
        return percentageChange(baselineFinalInvestmentValue, investmentValueDifference);
    }

    /**
     * {@code (loanBalanceDifference / |baselineFinalLoanBalance|) * 100}, or
     * {@link Optional#empty()} when the baseline final loan balance is exactly zero.
     */
    public Optional<BigDecimal> getLoanBalancePercentageChange() {
        return percentageChange(baselineFinalLoanBalance, loanBalanceDifference);
    }

    /**
     * {@code (cashBalanceDifference / |baselineFinalCashBalance|) * 100}, or
     * {@link Optional#empty()} when the baseline final cash balance is exactly zero.
     */
    public Optional<BigDecimal> getCashBalancePercentageChange() {
        return percentageChange(baselineFinalCashBalance, cashBalanceDifference);
    }

    /**
     * {@code (investmentContributionDifference / |baselineTotalInvestmentContributions|) * 100},
     * or {@link Optional#empty()} when the baseline total investment contributions figure is
     * exactly zero.
     */
    public Optional<BigDecimal> getInvestmentContributionPercentageChange() {
        return percentageChange(baselineTotalInvestmentContributions, investmentContributionDifference);
    }

    /**
     * {@code (difference / |baseline|) * 100}, computed at high precision
     * ({@link FinancialPrecision#CALCULATION_CONTEXT}) and rounded only once, to
     * {@link #PERCENTAGE_SCALE} decimal places, since it is the externally-visible result. Returns
     * {@link Optional#empty()} rather than dividing by zero when {@code baseline} is exactly zero —
     * there is no meaningful "percent change" from a baseline of nothing.
     */
    private static Optional<BigDecimal> percentageChange(BigDecimal baseline, BigDecimal difference) {
        if (baseline.signum() == 0) {
            return Optional.empty();
        }
        BigDecimal ratio = difference.divide(baseline.abs(), FinancialPrecision.CALCULATION_CONTEXT);
        return Optional.of(ratio.multiply(ONE_HUNDRED).setScale(PERCENTAGE_SCALE, FinancialPrecision.MONEY_ROUNDING));
    }

    private static void requireNonNegative(BigDecimal value, String name) {
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + " must be >= 0");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ScenarioComparison other)) {
            return false;
        }
        return baselineFinalNetWorth.compareTo(other.baselineFinalNetWorth) == 0
                && scenarioFinalNetWorth.compareTo(other.scenarioFinalNetWorth) == 0
                && netWorthDifference.compareTo(other.netWorthDifference) == 0
                && baselineFinalInvestmentValue.compareTo(other.baselineFinalInvestmentValue) == 0
                && scenarioFinalInvestmentValue.compareTo(other.scenarioFinalInvestmentValue) == 0
                && investmentValueDifference.compareTo(other.investmentValueDifference) == 0
                && baselineFinalLoanBalance.compareTo(other.baselineFinalLoanBalance) == 0
                && scenarioFinalLoanBalance.compareTo(other.scenarioFinalLoanBalance) == 0
                && loanBalanceDifference.compareTo(other.loanBalanceDifference) == 0
                && baselineFinalCashBalance.compareTo(other.baselineFinalCashBalance) == 0
                && scenarioFinalCashBalance.compareTo(other.scenarioFinalCashBalance) == 0
                && cashBalanceDifference.compareTo(other.cashBalanceDifference) == 0
                && baselineTotalInvestmentContributions.compareTo(other.baselineTotalInvestmentContributions) == 0
                && scenarioTotalInvestmentContributions.compareTo(other.scenarioTotalInvestmentContributions) == 0
                && investmentContributionDifference.compareTo(other.investmentContributionDifference) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                baselineFinalNetWorth.stripTrailingZeros(),
                scenarioFinalNetWorth.stripTrailingZeros(),
                netWorthDifference.stripTrailingZeros(),
                baselineFinalInvestmentValue.stripTrailingZeros(),
                scenarioFinalInvestmentValue.stripTrailingZeros(),
                investmentValueDifference.stripTrailingZeros(),
                baselineFinalLoanBalance.stripTrailingZeros(),
                scenarioFinalLoanBalance.stripTrailingZeros(),
                loanBalanceDifference.stripTrailingZeros(),
                baselineFinalCashBalance.stripTrailingZeros(),
                scenarioFinalCashBalance.stripTrailingZeros(),
                cashBalanceDifference.stripTrailingZeros(),
                baselineTotalInvestmentContributions.stripTrailingZeros(),
                scenarioTotalInvestmentContributions.stripTrailingZeros(),
                investmentContributionDifference.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return "ScenarioComparison{baselineFinalNetWorth=" + baselineFinalNetWorth
                + ", scenarioFinalNetWorth=" + scenarioFinalNetWorth
                + ", netWorthDifference=" + netWorthDifference
                + ", baselineFinalInvestmentValue=" + baselineFinalInvestmentValue
                + ", scenarioFinalInvestmentValue=" + scenarioFinalInvestmentValue
                + ", investmentValueDifference=" + investmentValueDifference
                + ", baselineFinalLoanBalance=" + baselineFinalLoanBalance
                + ", scenarioFinalLoanBalance=" + scenarioFinalLoanBalance
                + ", loanBalanceDifference=" + loanBalanceDifference
                + ", baselineFinalCashBalance=" + baselineFinalCashBalance
                + ", scenarioFinalCashBalance=" + scenarioFinalCashBalance
                + ", cashBalanceDifference=" + cashBalanceDifference
                + ", baselineTotalInvestmentContributions=" + baselineTotalInvestmentContributions
                + ", scenarioTotalInvestmentContributions=" + scenarioTotalInvestmentContributions
                + ", investmentContributionDifference=" + investmentContributionDifference
                + '}';
    }
}
