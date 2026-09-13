package com.finsight.domain.goal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A savings goal (e.g. a down payment, an emergency fund) tracked toward a target amount by a
 * target date.
 *
 * <p>Instances are immutable and validated eagerly in the constructor. Beyond the individual
 * field checks, {@link #getCurrentAmount()} must not exceed {@link #getTargetAmount()} — a goal
 * that is already past its target isn't a meaningful state for this model.
 *
 * <p><b>Equality:</b> based solely on {@link #getId() id}, matching entity identity rather than
 * comparing every field — see the equivalent note on {@code Account} for the same caveat about
 * not-yet-persisted instances.
 *
 * <p><b>Persistence id assignment:</b> as with {@code Account}, this class has no notion of a
 * "transient" versus "persisted" instance. A repository that generates a real id for a
 * not-yet-saved goal calls {@link #withId(long)} to obtain a new, separate {@code Goal} carrying
 * that id, rather than mutating the instance it was given.
 */
public final class Goal {

    private final long id;
    private final String name;
    private final BigDecimal targetAmount;
    private final BigDecimal currentAmount;
    private final LocalDate targetDate;
    private final BigDecimal monthlyContribution;

    public Goal(
            long id,
            String name,
            BigDecimal targetAmount,
            BigDecimal currentAmount,
            LocalDate targetDate,
            BigDecimal monthlyContribution) {
        if (id < 0) {
            throw new IllegalArgumentException("id must be >= 0");
        }
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(targetAmount, "targetAmount must not be null");
        if (targetAmount.signum() <= 0) {
            throw new IllegalArgumentException("targetAmount must be strictly > 0");
        }
        Objects.requireNonNull(currentAmount, "currentAmount must not be null");
        if (currentAmount.signum() < 0) {
            throw new IllegalArgumentException("currentAmount must be >= 0");
        }
        if (currentAmount.compareTo(targetAmount) > 0) {
            throw new IllegalArgumentException("currentAmount must not exceed targetAmount");
        }
        Objects.requireNonNull(targetDate, "targetDate must not be null");
        Objects.requireNonNull(monthlyContribution, "monthlyContribution must not be null");
        if (monthlyContribution.signum() < 0) {
            throw new IllegalArgumentException("monthlyContribution must be >= 0");
        }

        this.id = id;
        this.name = name;
        this.targetAmount = targetAmount;
        this.currentAmount = currentAmount;
        this.targetDate = targetDate;
        this.monthlyContribution = monthlyContribution;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getTargetAmount() {
        return targetAmount;
    }

    public BigDecimal getCurrentAmount() {
        return currentAmount;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public BigDecimal getMonthlyContribution() {
        return monthlyContribution;
    }

    /**
     * Returns a new {@code Goal} identical to this one except for its {@code id}. Does not modify
     * this instance. Intended for a repository to attach a database-generated id to an
     * otherwise-already-built goal after persisting it, without needing a mutable id field.
     */
    public Goal withId(long id) {
        return new Goal(id, this.name, this.targetAmount, this.currentAmount, this.targetDate, this.monthlyContribution);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Goal other)) {
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
        return "Goal{id=" + id
                + ", name='" + name + '\''
                + ", targetAmount=" + targetAmount
                + ", currentAmount=" + currentAmount
                + ", targetDate=" + targetDate
                + ", monthlyContribution=" + monthlyContribution
                + '}';
    }
}
