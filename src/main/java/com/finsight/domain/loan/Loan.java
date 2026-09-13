package com.finsight.domain.loan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A loan (a home loan, a car loan, ...) taken against the user's finances.
 *
 * <p>This class represents both a loan's <b>original</b> terms and its <b>current</b> outstanding
 * state, and the two must not be conflated:
 * <ul>
 *   <li>{@link #getPrincipal()} and {@link #getTenureMonths()} are the loan's original terms as
 *       taken out — the amount originally borrowed and the original total tenure. These never
 *       change for the life of the loan, however many payments have since been made.</li>
 *   <li>{@link #getCurrentBalance()} and {@link #getRemainingTenureMonths()} are the loan's state
 *       as of this snapshot — the outstanding principal balance still owed, and the number of
 *       months remaining to pay it off. These shrink as the loan is paid down.</li>
 * </ul>
 *
 * <p>For example, a loan with {@code principal = 1,000,000}, {@code tenureMonths = 60},
 * {@code currentBalance = 720,000}, {@code remainingTenureMonths = 42} was originally a ₹10L loan
 * over 60 months, and currently has ₹7.2L outstanding with 42 months left to pay it off. A
 * brand-new loan with no payments made yet has {@code currentBalance == principal} and
 * {@code remainingTenureMonths == tenureMonths}.
 *
 * <p>Instances are immutable and validated eagerly in the constructor. Deliberately does
 * <b>not</b> store an EMI: the EMI is a derived value computed from the loan's <em>current</em>
 * state ({@link #getCurrentBalance()}, {@link #getAnnualInterestRate()}, and
 * {@link #getRemainingTenureMonths()}) using the standard reducing-balance formula, by
 * {@code LoanCalculator} — never stored as state here.
 *
 * <p><b>Validation of the original/current relationship:</b> beyond each field's own range check,
 * {@link #getCurrentBalance()} must not exceed {@link #getPrincipal()} (a loan can only be paid
 * down, never grow beyond what was originally borrowed — this model has no concept of negative
 * amortization) and {@link #getRemainingTenureMonths()} must not exceed {@link #getTenureMonths()}
 * (time remaining can never exceed the original term). A strictly positive
 * {@link #getCurrentBalance()} requires at least one month of {@link #getRemainingTenureMonths()}
 * — a positive outstanding balance with zero months left to pay it in has no amortization
 * schedule that could resolve it, so that combination is rejected rather than silently producing
 * an incorrect (undefined) EMI. A fully paid-off loan is represented as
 * {@code currentBalance == 0 && remainingTenureMonths == 0}.
 *
 * <p><b>Equality:</b> based solely on {@link #getId() id}, matching entity identity rather than
 * comparing every field — see the equivalent note on {@code Account} for the same caveat about
 * not-yet-persisted instances. This applies to every field, including {@link #getCurrentBalance()}
 * and {@link #getRemainingTenureMonths()}: two {@code Loan} instances with the same id are equal
 * regardless of what either field holds.
 *
 * <p><b>Persistence id assignment:</b> as with {@code Account}, this class has no notion of a
 * "transient" versus "persisted" instance. A repository that generates a real id for a
 * not-yet-saved loan calls {@link #withId(long)} to obtain a new, separate {@code Loan} carrying
 * that id, rather than mutating the instance it was given.
 */
public final class Loan {

    private final long id;
    private final String name;
    private final BigDecimal principal;
    private final BigDecimal annualInterestRate;
    private final int tenureMonths;
    private final LocalDate startDate;
    private final BigDecimal currentBalance;
    private final int remainingTenureMonths;

    public Loan(
            long id,
            String name,
            BigDecimal principal,
            BigDecimal annualInterestRate,
            int tenureMonths,
            LocalDate startDate,
            BigDecimal currentBalance,
            int remainingTenureMonths) {
        if (id < 0) {
            throw new IllegalArgumentException("id must be >= 0");
        }
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(principal, "principal must not be null");
        if (principal.signum() <= 0) {
            throw new IllegalArgumentException("principal must be strictly > 0");
        }
        Objects.requireNonNull(annualInterestRate, "annualInterestRate must not be null");
        if (annualInterestRate.signum() < 0) {
            throw new IllegalArgumentException("annualInterestRate must be >= 0");
        }
        if (tenureMonths <= 0) {
            throw new IllegalArgumentException("tenureMonths must be strictly > 0");
        }
        Objects.requireNonNull(startDate, "startDate must not be null");
        Objects.requireNonNull(currentBalance, "currentBalance must not be null");
        if (currentBalance.signum() < 0) {
            throw new IllegalArgumentException("currentBalance must be >= 0");
        }
        if (currentBalance.compareTo(principal) > 0) {
            throw new IllegalArgumentException("currentBalance must not exceed principal");
        }
        if (remainingTenureMonths < 0) {
            throw new IllegalArgumentException("remainingTenureMonths must be >= 0");
        }
        if (remainingTenureMonths > tenureMonths) {
            throw new IllegalArgumentException("remainingTenureMonths must not exceed tenureMonths");
        }
        if (remainingTenureMonths == 0 && currentBalance.signum() > 0) {
            throw new IllegalArgumentException(
                    "remainingTenureMonths must be > 0 when currentBalance is > 0 "
                            + "(a positive balance with zero months remaining cannot be amortized)");
        }

        this.id = id;
        this.name = name;
        this.principal = principal;
        this.annualInterestRate = annualInterestRate;
        this.tenureMonths = tenureMonths;
        this.startDate = startDate;
        this.currentBalance = currentBalance;
        this.remainingTenureMonths = remainingTenureMonths;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /**
     * The original amount borrowed. Never changes for the life of the loan — see
     * {@link #getCurrentBalance()} for the outstanding amount as of this snapshot.
     */
    public BigDecimal getPrincipal() {
        return principal;
    }

    public BigDecimal getAnnualInterestRate() {
        return annualInterestRate;
    }

    /**
     * The original total tenure, in months, as taken out. Never changes for the life of the
     * loan — see {@link #getRemainingTenureMonths()} for the months left as of this snapshot.
     */
    public int getTenureMonths() {
        return tenureMonths;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    /**
     * The outstanding principal balance still owed, as of this snapshot. {@code 0} means the
     * loan is fully paid off. Never exceeds {@link #getPrincipal()}.
     */
    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    /**
     * The number of months remaining, as of this snapshot, to pay off {@link #getCurrentBalance()}.
     * {@code 0} means the loan is fully paid off. Never exceeds {@link #getTenureMonths()}.
     */
    public int getRemainingTenureMonths() {
        return remainingTenureMonths;
    }

    /**
     * Returns a new {@code Loan} identical to this one except for its {@code id}. Does not modify
     * this instance. Intended for a repository to attach a database-generated id to an
     * otherwise-already-built loan after persisting it, without needing a mutable id field.
     */
    public Loan withId(long id) {
        return new Loan(
                id, this.name, this.principal, this.annualInterestRate, this.tenureMonths,
                this.startDate, this.currentBalance, this.remainingTenureMonths);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Loan other)) {
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
        return "Loan{id=" + id
                + ", name='" + name + '\''
                + ", principal=" + principal
                + ", annualInterestRate=" + annualInterestRate
                + ", tenureMonths=" + tenureMonths
                + ", startDate=" + startDate
                + ", currentBalance=" + currentBalance
                + ", remainingTenureMonths=" + remainingTenureMonths
                + '}';
    }
}
