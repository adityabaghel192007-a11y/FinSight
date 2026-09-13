package com.finsight.simulation.scenario;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A scenario event that changes monthly expenses to a new absolute amount from
 * {@link #getEffectiveDate()} onward (a move to a cheaper city, a new recurring bill, ...).
 *
 * <p>{@link #getNewMonthlyExpenses()} is the new monthly expenses figure itself, not a delta —
 * matching {@link IncomeChangeEvent}'s same choice, for the same reason: the event's effect stays
 * unambiguous regardless of what the running expenses happened to be before it. Instances are
 * immutable and validated eagerly in the constructor.
 *
 * <p><b>Equality:</b> a value, not a persisted entity — equality is based on all fields, with
 * {@link BigDecimal} compared using {@link BigDecimal#compareTo} (numeric value, ignoring scale),
 * matching the convention used throughout {@code com.finsight.simulation}.
 */
public final class ExpenseChangeEvent extends ScenarioEvent {

    private final BigDecimal newMonthlyExpenses;

    public ExpenseChangeEvent(LocalDate effectiveDate, BigDecimal newMonthlyExpenses) {
        super(effectiveDate);
        Objects.requireNonNull(newMonthlyExpenses, "newMonthlyExpenses must not be null");
        if (newMonthlyExpenses.signum() < 0) {
            throw new IllegalArgumentException("newMonthlyExpenses must be >= 0");
        }

        this.newMonthlyExpenses = newMonthlyExpenses;
    }

    /**
     * The new monthly expenses figure, effective from {@link #getEffectiveDate()}.
     */
    public BigDecimal getNewMonthlyExpenses() {
        return newMonthlyExpenses;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ExpenseChangeEvent other)) {
            return false;
        }
        return getEffectiveDate().equals(other.getEffectiveDate())
                && newMonthlyExpenses.compareTo(other.newMonthlyExpenses) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getEffectiveDate(), newMonthlyExpenses.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return "ExpenseChangeEvent{effectiveDate=" + getEffectiveDate()
                + ", newMonthlyExpenses=" + newMonthlyExpenses
                + '}';
    }
}
