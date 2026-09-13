package com.finsight.simulation.scenario;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A scenario event that changes monthly income to a new absolute amount from
 * {@link #getEffectiveDate()} onward (a raise, a job change, ...).
 *
 * <p>{@link #getNewMonthlyIncome()} is the new monthly income figure itself, not a delta — this
 * keeps the event's effect unambiguous regardless of what the running income happened to be
 * before it. Instances are immutable and validated eagerly in the constructor.
 *
 * <p><b>Equality:</b> a value, not a persisted entity — equality is based on all fields, with
 * {@link BigDecimal} compared using {@link BigDecimal#compareTo} (numeric value, ignoring scale),
 * matching the convention used throughout {@code com.finsight.simulation}.
 */
public final class IncomeChangeEvent extends ScenarioEvent {

    private final BigDecimal newMonthlyIncome;

    public IncomeChangeEvent(LocalDate effectiveDate, BigDecimal newMonthlyIncome) {
        super(effectiveDate);
        Objects.requireNonNull(newMonthlyIncome, "newMonthlyIncome must not be null");
        if (newMonthlyIncome.signum() < 0) {
            throw new IllegalArgumentException("newMonthlyIncome must be >= 0");
        }

        this.newMonthlyIncome = newMonthlyIncome;
    }

    /**
     * The new monthly income figure, effective from {@link #getEffectiveDate()}.
     */
    public BigDecimal getNewMonthlyIncome() {
        return newMonthlyIncome;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IncomeChangeEvent other)) {
            return false;
        }
        return getEffectiveDate().equals(other.getEffectiveDate())
                && newMonthlyIncome.compareTo(other.newMonthlyIncome) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getEffectiveDate(), newMonthlyIncome.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return "IncomeChangeEvent{effectiveDate=" + getEffectiveDate()
                + ", newMonthlyIncome=" + newMonthlyIncome
                + '}';
    }
}
