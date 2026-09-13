package com.finsight.simulation.calculator;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * The result of projecting an investment's value forward month by month, as produced by
 * {@link InvestmentCalculator#projectValue}.
 *
 * <p>{@link #getInitialValue()} is the investment's current value before month 1 begins;
 * {@link #getMonthlyValues()} holds one entry per projected month, in order, each being the
 * value at the <em>end</em> of that month (after that month's growth and contribution).
 *
 * <p>Instances are immutable. {@link #getMonthlyValues()} returns an unmodifiable view backed by
 * a defensive copy taken at construction time ({@link List#copyOf}), so neither the list nor any
 * of its elements may be null, and the internal list can never be mutated by callers.
 *
 * <p><b>Equality:</b> a calculated value, not a persisted entity — equality is based on all
 * fields, with {@link BigDecimal} values compared using {@link BigDecimal#compareTo} (numeric
 * value, ignoring scale), matching the convention used by {@link LoanPayment} and
 * {@link AmortizationSchedule}.
 */
public final class InvestmentProjection {

    private final BigDecimal initialValue;
    private final List<BigDecimal> monthlyValues;

    public InvestmentProjection(BigDecimal initialValue, List<BigDecimal> monthlyValues) {
        Objects.requireNonNull(initialValue, "initialValue must not be null");
        if (initialValue.signum() < 0) {
            throw new IllegalArgumentException("initialValue must be >= 0");
        }
        Objects.requireNonNull(monthlyValues, "monthlyValues must not be null");

        this.monthlyValues = List.copyOf(monthlyValues);
        for (BigDecimal value : this.monthlyValues) {
            if (value.signum() < 0) {
                throw new IllegalArgumentException("monthlyValues must not contain negative values");
            }
        }
        this.initialValue = initialValue;
    }

    public BigDecimal getInitialValue() {
        return initialValue;
    }

    public List<BigDecimal> getMonthlyValues() {
        return monthlyValues;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InvestmentProjection other)) {
            return false;
        }
        if (initialValue.compareTo(other.initialValue) != 0) {
            return false;
        }
        if (monthlyValues.size() != other.monthlyValues.size()) {
            return false;
        }
        for (int i = 0; i < monthlyValues.size(); i++) {
            if (monthlyValues.get(i).compareTo(other.monthlyValues.get(i)) != 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(initialValue.stripTrailingZeros());
        for (BigDecimal value : monthlyValues) {
            result = 31 * result + value.stripTrailingZeros().hashCode();
        }
        return result;
    }

    @Override
    public String toString() {
        return "InvestmentProjection{initialValue=" + initialValue
                + ", monthlyValues=" + monthlyValues
                + '}';
    }
}
