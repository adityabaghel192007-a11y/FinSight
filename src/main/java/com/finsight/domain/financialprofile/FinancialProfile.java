package com.finsight.domain.financialprofile;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The user's explicit, self-reported baseline monthly income and monthly expenses.
 *
 * <p>This is not derived from {@link com.finsight.domain.transaction.Transaction} history —
 * transactions are dated individual records with no recurring-transaction model, so "monthly"
 * income/expenses is not a fact that can be computed from them without inventing an unrequested
 * recurrence semantic. Instead, this is a plain user-owned assumption, analogous to how
 * {@code ScenarioEvent} subtypes (for example {@code IncomeChangeEvent}) already store absolute
 * replacement values the user supplies directly rather than deltas derived from history.
 *
 * <p>Deliberately minimal: only {@code monthlyIncome} and {@code monthlyExpenses}. There is no
 * {@code asOfDate} or similar timestamp field — nothing in the current design requires one, and
 * one can be added later if a concrete need for it emerges.
 *
 * <p>Instances are immutable and validated eagerly in the constructor, using the same
 * non-negativity rule {@code FinancialSnapshot}'s own constructor already enforces for these two
 * values (zero is allowed — for example, someone between jobs may genuinely have zero income —
 * but a negative amount is not a valid monetary value here).
 *
 * <p><b>Equality:</b> based solely on {@link #getId() id}, matching entity identity rather than
 * comparing every field — see the equivalent note on {@code Account} for the same caveat about
 * not-yet-persisted instances.
 *
 * <p><b>Persistence id assignment:</b> as with {@code Account}, this class has no notion of a
 * "transient" versus "persisted" instance. A repository that generates a real id for a
 * not-yet-saved profile, or that reads back the one existing row, calls {@link #withId(long)} to
 * obtain a new, separate {@code FinancialProfile} carrying that id, rather than mutating the
 * instance it was given.
 *
 * <p><b>Singleton persistence:</b> exactly one {@code FinancialProfile} row is ever meant to
 * exist. This class itself has no way to enforce that — it is a plain value holder like every
 * other domain entity — so that guarantee is the responsibility of
 * {@code FinancialProfileRepository#save}, documented there.
 */
public final class FinancialProfile {

    private final long id;
    private final BigDecimal monthlyIncome;
    private final BigDecimal monthlyExpenses;

    public FinancialProfile(long id, BigDecimal monthlyIncome, BigDecimal monthlyExpenses) {
        if (id < 0) {
            throw new IllegalArgumentException("id must be >= 0");
        }
        Objects.requireNonNull(monthlyIncome, "monthlyIncome must not be null");
        if (monthlyIncome.signum() < 0) {
            throw new IllegalArgumentException("monthlyIncome must be >= 0");
        }
        Objects.requireNonNull(monthlyExpenses, "monthlyExpenses must not be null");
        if (monthlyExpenses.signum() < 0) {
            throw new IllegalArgumentException("monthlyExpenses must be >= 0");
        }

        this.id = id;
        this.monthlyIncome = monthlyIncome;
        this.monthlyExpenses = monthlyExpenses;
    }

    public long getId() {
        return id;
    }

    public BigDecimal getMonthlyIncome() {
        return monthlyIncome;
    }

    public BigDecimal getMonthlyExpenses() {
        return monthlyExpenses;
    }

    /**
     * Returns a new {@code FinancialProfile} identical to this one except for its {@code id}.
     * Does not modify this instance. Intended for a repository to attach the single row's id
     * (whether newly generated or already existing) to an otherwise-already-built profile,
     * without needing a mutable id field.
     */
    public FinancialProfile withId(long id) {
        return new FinancialProfile(id, this.monthlyIncome, this.monthlyExpenses);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FinancialProfile other)) {
            return false;
        }
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "FinancialProfile{id=" + id
                + ", monthlyIncome=" + monthlyIncome
                + ", monthlyExpenses=" + monthlyExpenses
                + '}';
    }
}
