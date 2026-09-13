package com.finsight.simulation.model;

import com.finsight.domain.goal.Goal;
import com.finsight.domain.investment.Investment;
import com.finsight.domain.loan.Loan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * The immutable starting financial state a simulation begins from: a point-in-time picture of
 * cash, recurring income/expenses, and the holdings (investments, loans, goals) that participate
 * in the simulation.
 *
 * <p>This is a pure data holder — it contains no simulation logic. Computing how these values
 * evolve month to month is the {@code SimulationEngine}'s job, not this class's.
 *
 * <p>Instances are immutable and validated eagerly in the constructor. {@link #getInvestments()},
 * {@link #getLoans()}, and {@link #getGoals()} each return an unmodifiable view backed by a
 * defensive copy taken at construction time ({@link List#copyOf}), so the caller's original
 * lists can never be mutated through this class, and neither a null list nor a null element is
 * accepted.
 *
 * <p><b>Equality:</b> a snapshot, not a persisted entity — equality is based on all fields.
 * {@link BigDecimal} fields are compared using {@link BigDecimal#compareTo} (numeric value,
 * ignoring scale), matching the convention used elsewhere in this package
 * ({@code LoanPayment}, {@code InvestmentProjection}).
 */
public final class FinancialSnapshot {

    private final LocalDate asOfDate;
    private final BigDecimal cash;
    private final BigDecimal monthlyIncome;
    private final BigDecimal monthlyExpenses;
    private final List<Investment> investments;
    private final List<Loan> loans;
    private final List<Goal> goals;

    public FinancialSnapshot(
            LocalDate asOfDate,
            BigDecimal cash,
            BigDecimal monthlyIncome,
            BigDecimal monthlyExpenses,
            List<Investment> investments,
            List<Loan> loans,
            List<Goal> goals) {
        Objects.requireNonNull(asOfDate, "asOfDate must not be null");
        Objects.requireNonNull(cash, "cash must not be null");
        if (cash.signum() < 0) {
            throw new IllegalArgumentException("cash must be >= 0");
        }
        Objects.requireNonNull(monthlyIncome, "monthlyIncome must not be null");
        if (monthlyIncome.signum() < 0) {
            throw new IllegalArgumentException("monthlyIncome must be >= 0");
        }
        Objects.requireNonNull(monthlyExpenses, "monthlyExpenses must not be null");
        if (monthlyExpenses.signum() < 0) {
            throw new IllegalArgumentException("monthlyExpenses must be >= 0");
        }
        Objects.requireNonNull(investments, "investments must not be null");
        Objects.requireNonNull(loans, "loans must not be null");
        Objects.requireNonNull(goals, "goals must not be null");

        this.asOfDate = asOfDate;
        this.cash = cash;
        this.monthlyIncome = monthlyIncome;
        this.monthlyExpenses = monthlyExpenses;
        this.investments = List.copyOf(investments);
        this.loans = List.copyOf(loans);
        this.goals = List.copyOf(goals);
    }

    public LocalDate getAsOfDate() {
        return asOfDate;
    }

    public BigDecimal getCash() {
        return cash;
    }

    public BigDecimal getMonthlyIncome() {
        return monthlyIncome;
    }

    public BigDecimal getMonthlyExpenses() {
        return monthlyExpenses;
    }

    public List<Investment> getInvestments() {
        return investments;
    }

    public List<Loan> getLoans() {
        return loans;
    }

    public List<Goal> getGoals() {
        return goals;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FinancialSnapshot other)) {
            return false;
        }
        return asOfDate.equals(other.asOfDate)
                && cash.compareTo(other.cash) == 0
                && monthlyIncome.compareTo(other.monthlyIncome) == 0
                && monthlyExpenses.compareTo(other.monthlyExpenses) == 0
                && investments.equals(other.investments)
                && loans.equals(other.loans)
                && goals.equals(other.goals);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                asOfDate,
                cash.stripTrailingZeros(),
                monthlyIncome.stripTrailingZeros(),
                monthlyExpenses.stripTrailingZeros(),
                investments,
                loans,
                goals);
    }

    @Override
    public String toString() {
        return "FinancialSnapshot{asOfDate=" + asOfDate
                + ", cash=" + cash
                + ", monthlyIncome=" + monthlyIncome
                + ", monthlyExpenses=" + monthlyExpenses
                + ", investments=" + investments
                + ", loans=" + loans
                + ", goals=" + goals
                + '}';
    }
}
