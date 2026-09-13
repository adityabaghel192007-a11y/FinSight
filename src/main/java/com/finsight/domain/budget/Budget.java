package com.finsight.domain.budget;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Objects;

/**
 * A budgeted amount for one category in one calendar month.
 *
 * <p>Instances are immutable and validated eagerly in the constructor. {@link #getCategoryId()}
 * is a foreign key into the category domain and is only checked for shape here (non-negative);
 * whether it refers to a category that actually exists is a repository/service-layer concern for
 * a later milestone.
 *
 * <p><b>Equality:</b> based solely on {@link #getId() id}, matching entity identity rather than
 * comparing every field — see the equivalent note on {@code Account} for the same caveat about
 * not-yet-persisted instances.
 *
 * <p><b>Persistence id assignment:</b> as with {@code Account}, this class has no notion of a
 * "transient" versus "persisted" instance. A repository that generates a real id for a
 * not-yet-saved budget calls {@link #withId(long)} to obtain a new, separate {@code Budget}
 * carrying that id, rather than mutating the instance it was given.
 */
public final class Budget {

    private final long id;
    private final long categoryId;
    private final YearMonth month;
    private final BigDecimal amount;

    public Budget(long id, long categoryId, YearMonth month, BigDecimal amount) {
        if (id < 0) {
            throw new IllegalArgumentException("id must be >= 0");
        }
        if (categoryId < 0) {
            throw new IllegalArgumentException("categoryId must be >= 0");
        }
        Objects.requireNonNull(month, "month must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be >= 0");
        }

        this.id = id;
        this.categoryId = categoryId;
        this.month = month;
        this.amount = amount;
    }

    public long getId() {
        return id;
    }

    public long getCategoryId() {
        return categoryId;
    }

    public YearMonth getMonth() {
        return month;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Returns a new {@code Budget} identical to this one except for its {@code id}. Does not
     * modify this instance. Intended for a repository to attach a database-generated id to an
     * otherwise-already-built budget after persisting it, without needing a mutable id field.
     */
    public Budget withId(long id) {
        return new Budget(id, this.categoryId, this.month, this.amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Budget other)) {
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
        return "Budget{id=" + id
                + ", categoryId=" + categoryId
                + ", month=" + month
                + ", amount=" + amount
                + '}';
    }
}
