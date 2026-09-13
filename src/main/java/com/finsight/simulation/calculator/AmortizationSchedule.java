package com.finsight.simulation.calculator;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * The complete month-by-month payment schedule for a loan, as produced by
 * {@link LoanCalculator#calculateAmortizationSchedule}.
 *
 * <p>Instances are immutable. {@link #getPayments()} returns an unmodifiable view backed by a
 * defensive copy taken at construction time ({@link List#copyOf}), so the internal list can
 * never be mutated by callers, and neither {@code payments} nor any of its elements may be null.
 *
 * <p><b>Equality:</b> a calculated value, not a persisted entity — equality is based on all
 * fields. {@link #getMonthlyPayment()} is compared using {@link BigDecimal#compareTo} (numeric
 * value, ignoring scale), matching {@link LoanPayment}'s equality convention.
 */
public final class AmortizationSchedule {

    private final BigDecimal monthlyPayment;
    private final List<LoanPayment> payments;

    public AmortizationSchedule(BigDecimal monthlyPayment, List<LoanPayment> payments) {
        Objects.requireNonNull(monthlyPayment, "monthlyPayment must not be null");
        if (monthlyPayment.signum() < 0) {
            throw new IllegalArgumentException("monthlyPayment must be >= 0");
        }
        Objects.requireNonNull(payments, "payments must not be null");

        this.monthlyPayment = monthlyPayment;
        this.payments = List.copyOf(payments);
    }

    public BigDecimal getMonthlyPayment() {
        return monthlyPayment;
    }

    public List<LoanPayment> getPayments() {
        return payments;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AmortizationSchedule other)) {
            return false;
        }
        return monthlyPayment.compareTo(other.monthlyPayment) == 0
                && payments.equals(other.payments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(monthlyPayment.stripTrailingZeros(), payments);
    }

    @Override
    public String toString() {
        return "AmortizationSchedule{monthlyPayment=" + monthlyPayment
                + ", payments=" + payments
                + '}';
    }
}
