package com.finsight.simulation.calculator;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One period (one month) of a loan {@link AmortizationSchedule}.
 *
 * <p>Instances are immutable and validated eagerly in the constructor. Unlike the domain
 * entities in {@code com.finsight.domain}, this is a calculated value, not a persisted entity
 * with its own identity — there is no id, and equality is based on all fields rather than on an
 * identity field.
 *
 * <p><b>Equality:</b> the {@link BigDecimal} fields are compared using {@link BigDecimal#compareTo}
 * (numeric value) rather than {@link BigDecimal#equals} (which also considers scale, so
 * {@code 100} and {@code 100.00} would otherwise be "not equal"). {@link #hashCode()} is kept
 * consistent with this by hashing each field's {@link BigDecimal#stripTrailingZeros()} form.
 */
public final class LoanPayment {

    private final int paymentNumber;
    private final BigDecimal payment;
    private final BigDecimal interest;
    private final BigDecimal principal;
    private final BigDecimal remainingBalance;

    public LoanPayment(
            int paymentNumber,
            BigDecimal payment,
            BigDecimal interest,
            BigDecimal principal,
            BigDecimal remainingBalance) {
        if (paymentNumber <= 0) {
            throw new IllegalArgumentException("paymentNumber must be > 0");
        }
        Objects.requireNonNull(payment, "payment must not be null");
        if (payment.signum() < 0) {
            throw new IllegalArgumentException("payment must be >= 0");
        }
        Objects.requireNonNull(interest, "interest must not be null");
        if (interest.signum() < 0) {
            throw new IllegalArgumentException("interest must be >= 0");
        }
        Objects.requireNonNull(principal, "principal must not be null");
        if (principal.signum() < 0) {
            throw new IllegalArgumentException("principal must be >= 0");
        }
        Objects.requireNonNull(remainingBalance, "remainingBalance must not be null");
        if (remainingBalance.signum() < 0) {
            throw new IllegalArgumentException("remainingBalance must be >= 0");
        }

        this.paymentNumber = paymentNumber;
        this.payment = payment;
        this.interest = interest;
        this.principal = principal;
        this.remainingBalance = remainingBalance;
    }

    public int getPaymentNumber() {
        return paymentNumber;
    }

    public BigDecimal getPayment() {
        return payment;
    }

    public BigDecimal getInterest() {
        return interest;
    }

    public BigDecimal getPrincipal() {
        return principal;
    }

    public BigDecimal getRemainingBalance() {
        return remainingBalance;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LoanPayment other)) {
            return false;
        }
        return paymentNumber == other.paymentNumber
                && payment.compareTo(other.payment) == 0
                && interest.compareTo(other.interest) == 0
                && principal.compareTo(other.principal) == 0
                && remainingBalance.compareTo(other.remainingBalance) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                paymentNumber,
                payment.stripTrailingZeros(),
                interest.stripTrailingZeros(),
                principal.stripTrailingZeros(),
                remainingBalance.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return "LoanPayment{paymentNumber=" + paymentNumber
                + ", payment=" + payment
                + ", interest=" + interest
                + ", principal=" + principal
                + ", remainingBalance=" + remainingBalance
                + '}';
    }
}
