package com.finsight.simulation.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The simulated financial state for one month — a single row of a {@link Projection}.
 *
 * <p>This is a plain simulation output/result model, not a database entity: it holds whatever
 * values the {@code SimulationEngine} computed for this month and validates them for internal
 * consistency, but it does not compute them itself (no derived-value logic beyond validation
 * belongs in this constructor).
 *
 * <p><b>Which fields may be negative, and why:</b> {@link #getInvestmentValue()},
 * {@link #getLoanBalance()}, {@link #getMonthlyIncome()}, {@link #getMonthlyExpenses()},
 * {@link #getInvestmentContribution()}, and {@link #getLoanPayment()} are validated {@code >= 0}
 * — none of these can meaningfully be negative (a loan balance or investment value below zero,
 * or a negative income/expense/contribution/payment amount, would be a bug in whatever produced
 * it). {@link #getCashBalance()}, {@link #getNetWorth()}, and {@link #getCumulativeSavings()} are
 * deliberately <b>not</b> floored at zero: this is a decision simulator, and a month where
 * expenses and loan payments exceed income — or where liabilities exceed assets — is exactly the
 * kind of shortfall the simulation exists to surface. Forcing these to be non-negative would
 * silently hide that signal.
 *
 * <p><b>Equality:</b> a calculated value, not a persisted entity — equality is based on all
 * fields, with {@link BigDecimal} fields compared using {@link BigDecimal#compareTo} (numeric
 * value, ignoring scale), matching the convention used by {@code LoanPayment} and
 * {@code InvestmentProjection}.
 */
public final class MonthlyFinancialState {

    private final LocalDate date;
    private final BigDecimal cashBalance;
    private final BigDecimal investmentValue;
    private final BigDecimal loanBalance;
    private final BigDecimal monthlyIncome;
    private final BigDecimal monthlyExpenses;
    private final BigDecimal investmentContribution;
    private final BigDecimal loanPayment;
    private final BigDecimal netWorth;
    private final BigDecimal cumulativeSavings;

    public MonthlyFinancialState(
            LocalDate date,
            BigDecimal cashBalance,
            BigDecimal investmentValue,
            BigDecimal loanBalance,
            BigDecimal monthlyIncome,
            BigDecimal monthlyExpenses,
            BigDecimal investmentContribution,
            BigDecimal loanPayment,
            BigDecimal netWorth,
            BigDecimal cumulativeSavings) {
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(cashBalance, "cashBalance must not be null");
        Objects.requireNonNull(investmentValue, "investmentValue must not be null");
        if (investmentValue.signum() < 0) {
            throw new IllegalArgumentException("investmentValue must be >= 0");
        }
        Objects.requireNonNull(loanBalance, "loanBalance must not be null");
        if (loanBalance.signum() < 0) {
            throw new IllegalArgumentException("loanBalance must be >= 0");
        }
        Objects.requireNonNull(monthlyIncome, "monthlyIncome must not be null");
        if (monthlyIncome.signum() < 0) {
            throw new IllegalArgumentException("monthlyIncome must be >= 0");
        }
        Objects.requireNonNull(monthlyExpenses, "monthlyExpenses must not be null");
        if (monthlyExpenses.signum() < 0) {
            throw new IllegalArgumentException("monthlyExpenses must be >= 0");
        }
        Objects.requireNonNull(investmentContribution, "investmentContribution must not be null");
        if (investmentContribution.signum() < 0) {
            throw new IllegalArgumentException("investmentContribution must be >= 0");
        }
        Objects.requireNonNull(loanPayment, "loanPayment must not be null");
        if (loanPayment.signum() < 0) {
            throw new IllegalArgumentException("loanPayment must be >= 0");
        }
        Objects.requireNonNull(netWorth, "netWorth must not be null");
        Objects.requireNonNull(cumulativeSavings, "cumulativeSavings must not be null");

        this.date = date;
        this.cashBalance = cashBalance;
        this.investmentValue = investmentValue;
        this.loanBalance = loanBalance;
        this.monthlyIncome = monthlyIncome;
        this.monthlyExpenses = monthlyExpenses;
        this.investmentContribution = investmentContribution;
        this.loanPayment = loanPayment;
        this.netWorth = netWorth;
        this.cumulativeSavings = cumulativeSavings;
    }

    public LocalDate getDate() {
        return date;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public BigDecimal getInvestmentValue() {
        return investmentValue;
    }

    public BigDecimal getLoanBalance() {
        return loanBalance;
    }

    public BigDecimal getMonthlyIncome() {
        return monthlyIncome;
    }

    public BigDecimal getMonthlyExpenses() {
        return monthlyExpenses;
    }

    public BigDecimal getInvestmentContribution() {
        return investmentContribution;
    }

    public BigDecimal getLoanPayment() {
        return loanPayment;
    }

    public BigDecimal getNetWorth() {
        return netWorth;
    }

    public BigDecimal getCumulativeSavings() {
        return cumulativeSavings;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MonthlyFinancialState other)) {
            return false;
        }
        return date.equals(other.date)
                && cashBalance.compareTo(other.cashBalance) == 0
                && investmentValue.compareTo(other.investmentValue) == 0
                && loanBalance.compareTo(other.loanBalance) == 0
                && monthlyIncome.compareTo(other.monthlyIncome) == 0
                && monthlyExpenses.compareTo(other.monthlyExpenses) == 0
                && investmentContribution.compareTo(other.investmentContribution) == 0
                && loanPayment.compareTo(other.loanPayment) == 0
                && netWorth.compareTo(other.netWorth) == 0
                && cumulativeSavings.compareTo(other.cumulativeSavings) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                date,
                cashBalance.stripTrailingZeros(),
                investmentValue.stripTrailingZeros(),
                loanBalance.stripTrailingZeros(),
                monthlyIncome.stripTrailingZeros(),
                monthlyExpenses.stripTrailingZeros(),
                investmentContribution.stripTrailingZeros(),
                loanPayment.stripTrailingZeros(),
                netWorth.stripTrailingZeros(),
                cumulativeSavings.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return "MonthlyFinancialState{date=" + date
                + ", cashBalance=" + cashBalance
                + ", investmentValue=" + investmentValue
                + ", loanBalance=" + loanBalance
                + ", monthlyIncome=" + monthlyIncome
                + ", monthlyExpenses=" + monthlyExpenses
                + ", investmentContribution=" + investmentContribution
                + ", loanPayment=" + loanPayment
                + ", netWorth=" + netWorth
                + ", cumulativeSavings=" + cumulativeSavings
                + '}';
    }
}
