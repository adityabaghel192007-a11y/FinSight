package com.finsight.service;

import com.finsight.common.FinancialPrecision;
import com.finsight.simulation.calculator.NetWorthCalculator;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A concise, point-in-time snapshot of the user's current financial position (Milestone 7A):
 * monthly income/expenses/savings, plus a headline current-position figure — cash, investments,
 * loans, and net worth — all already consolidated into INR.
 *
 * <p>This is a plain, immutable value holder — it contains no simulation logic, no repository
 * dependency, and no JavaFX dependency, matching the same "pure function of the values it's given"
 * spirit as {@link com.finsight.simulation.comparison.ScenarioComparison} and
 * {@code FinancialSnapshot}. Every figure here is computed elsewhere —
 * {@link FinancialOverviewApplicationService} is the one class responsible for retrieving and
 * consolidating them — this class only validates and holds the result.
 *
 * <p><b>{@code monthlySavings} is computed once, here, from {@code monthlyIncome} and
 * {@code monthlyExpenses}</b> — not accepted as an independent constructor parameter — so it can
 * never disagree with the two values it is derived from. This mirrors
 * {@code ScenarioComparison}'s own rule that every {@code *Difference} field is computed exactly
 * once, in its constructor, never re-derived or passed in separately.
 *
 * <p><b>{@code netWorth} is supplied by the caller, not recomputed here</b> — deliberately, so
 * this class never duplicates {@link NetWorthCalculator#calculateNetWorth}'s formula. The caller
 * ({@link FinancialOverviewApplicationService}) is expected to have obtained it by calling that
 * calculator directly with this same {@code cash}/{@code investments}/{@code loans}, but this
 * class does not itself re-derive or cross-check that arithmetic — doing so would mean this class
 * knows the net worth formula, which is exactly what {@link NetWorthCalculator} alone should own.
 *
 * <p><b>Which figures may be negative:</b> mirrors the convention already established by
 * {@code FinancialSnapshot} and {@code ScenarioComparison}. {@code monthlyIncome} and
 * {@code monthlyExpenses} come from an explicit user-supplied baseline and are validated
 * {@code >= 0}. {@code investments} and {@code loans} are consolidated totals of quantities that
 * can never legitimately be negative, so both are validated {@code >= 0} too — matching
 * {@code ScenarioComparison}'s identical rule for its own investment-value and loan-balance
 * fields. {@code monthlySavings}, {@code cash}, and {@code netWorth} may all legitimately be
 * negative (spending more than earning, an overdrawn consolidated balance, or liabilities
 * exceeding assets are all real, meaningful states this application surfaces rather than hides),
 * so none of the three is sign-restricted.
 *
 * <p>Instances are immutable and validated eagerly in the constructor. Every {@link BigDecimal}
 * field is rounded via {@link FinancialPrecision#roundMoney(BigDecimal)} at construction, matching
 * the money-rounding convention used by every calculator and value holder in this project.
 *
 * <p><b>Equality:</b> a calculated value, not a persisted entity — equality is based on all
 * fields, with {@link BigDecimal} fields compared using {@link BigDecimal#compareTo} (numeric
 * value, ignoring scale), matching the convention used by {@code FinancialSnapshot} and
 * {@code ScenarioComparison}.
 */
public final class FinancialOverviewData {

    private final BigDecimal monthlyIncome;
    private final BigDecimal monthlyExpenses;
    private final BigDecimal monthlySavings;
    private final BigDecimal cash;
    private final BigDecimal investments;
    private final BigDecimal loans;
    private final BigDecimal netWorth;

    /**
     * @param monthlyIncome the user's baseline monthly income, from {@code FinancialProfile};
     *     must be {@code >= 0}
     * @param monthlyExpenses the user's baseline monthly expenses, from {@code FinancialProfile};
     *     must be {@code >= 0}
     * @param cash every persisted account's balance, consolidated into INR; may be negative
     * @param investments every persisted investment's current value, consolidated into INR; must
     *     be {@code >= 0}
     * @param loans every persisted loan's current balance, summed; must be {@code >= 0}
     * @param netWorth {@code cash + investments - loans}, exactly as
     *     {@link NetWorthCalculator#calculateNetWorth} computed it — not recomputed here; may be
     *     negative
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code monthlyIncome}, {@code monthlyExpenses},
     *     {@code investments}, or {@code loans} is negative
     */
    public FinancialOverviewData(
            BigDecimal monthlyIncome,
            BigDecimal monthlyExpenses,
            BigDecimal cash,
            BigDecimal investments,
            BigDecimal loans,
            BigDecimal netWorth) {
        Objects.requireNonNull(monthlyIncome, "monthlyIncome must not be null");
        requireNonNegative(monthlyIncome, "monthlyIncome");
        Objects.requireNonNull(monthlyExpenses, "monthlyExpenses must not be null");
        requireNonNegative(monthlyExpenses, "monthlyExpenses");
        Objects.requireNonNull(cash, "cash must not be null");
        Objects.requireNonNull(investments, "investments must not be null");
        requireNonNegative(investments, "investments");
        Objects.requireNonNull(loans, "loans must not be null");
        requireNonNegative(loans, "loans");
        Objects.requireNonNull(netWorth, "netWorth must not be null");

        this.monthlyIncome = FinancialPrecision.roundMoney(monthlyIncome);
        this.monthlyExpenses = FinancialPrecision.roundMoney(monthlyExpenses);
        this.monthlySavings = FinancialPrecision.roundMoney(monthlyIncome.subtract(monthlyExpenses));
        this.cash = FinancialPrecision.roundMoney(cash);
        this.investments = FinancialPrecision.roundMoney(investments);
        this.loans = FinancialPrecision.roundMoney(loans);
        this.netWorth = FinancialPrecision.roundMoney(netWorth);
    }

    public BigDecimal getMonthlyIncome() {
        return monthlyIncome;
    }

    public BigDecimal getMonthlyExpenses() {
        return monthlyExpenses;
    }

    /** {@code monthlyIncome - monthlyExpenses}, computed once in this class's constructor. */
    public BigDecimal getMonthlySavings() {
        return monthlySavings;
    }

    public BigDecimal getCash() {
        return cash;
    }

    public BigDecimal getInvestments() {
        return investments;
    }

    public BigDecimal getLoans() {
        return loans;
    }

    public BigDecimal getNetWorth() {
        return netWorth;
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
        if (!(o instanceof FinancialOverviewData other)) {
            return false;
        }
        return monthlyIncome.compareTo(other.monthlyIncome) == 0
                && monthlyExpenses.compareTo(other.monthlyExpenses) == 0
                && monthlySavings.compareTo(other.monthlySavings) == 0
                && cash.compareTo(other.cash) == 0
                && investments.compareTo(other.investments) == 0
                && loans.compareTo(other.loans) == 0
                && netWorth.compareTo(other.netWorth) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                monthlyIncome.stripTrailingZeros(),
                monthlyExpenses.stripTrailingZeros(),
                monthlySavings.stripTrailingZeros(),
                cash.stripTrailingZeros(),
                investments.stripTrailingZeros(),
                loans.stripTrailingZeros(),
                netWorth.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return "FinancialOverviewData{monthlyIncome=" + monthlyIncome
                + ", monthlyExpenses=" + monthlyExpenses
                + ", monthlySavings=" + monthlySavings
                + ", cash=" + cash
                + ", investments=" + investments
                + ", loans=" + loans
                + ", netWorth=" + netWorth
                + '}';
    }
}
