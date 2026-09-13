package com.finsight.simulation.scenario;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A scenario event representing a single, non-recurring cash outflow occurring in the simulated
 * month that {@link #getEffectiveDate()} falls in (a large medical bill, a big purchase, a wedding,
 * ...) — as distinct from {@link ExpenseChangeEvent}, which changes the ongoing monthly baseline.
 *
 * <p>{@link #getAmount()} is a positive-magnitude outflow, matching how {@code MonthlyExpenses}
 * and {@code MonthlyFinancialState.getMonthlyExpenses()} elsewhere in this project also represent
 * expenses as positive amounts rather than signed values. Instances are immutable and validated
 * eagerly in the constructor.
 *
 * <p><b>Equality:</b> a value, not a persisted entity — equality is based on all fields, with
 * {@link BigDecimal} compared using {@link BigDecimal#compareTo} (numeric value, ignoring scale),
 * matching the convention used throughout {@code com.finsight.simulation}.
 */
public final class OneTimeExpenseEvent extends ScenarioEvent {

    private final BigDecimal amount;

    public OneTimeExpenseEvent(LocalDate effectiveDate, BigDecimal amount) {
        super(effectiveDate);
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be >= 0");
        }

        this.amount = amount;
    }

    /**
     * The one-time cash outflow amount, as a positive magnitude.
     */
    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OneTimeExpenseEvent other)) {
            return false;
        }
        return getEffectiveDate().equals(other.getEffectiveDate())
                && amount.compareTo(other.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getEffectiveDate(), amount.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return "OneTimeExpenseEvent{effectiveDate=" + getEffectiveDate()
                + ", amount=" + amount
                + '}';
    }
}
