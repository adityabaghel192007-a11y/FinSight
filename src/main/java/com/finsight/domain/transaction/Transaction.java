package com.finsight.domain.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * A single financial transaction against an account.
 *
 * <p>{@link #getAmount()} is always strictly positive; it is {@link #getType()} that determines
 * what the amount means (income received, expense paid, or one leg of a transfer). Encoding
 * direction as a signed amount is deliberately avoided.
 *
 * <p>Instances are immutable and validated eagerly in the constructor. {@code categoryId},
 * {@code description}, and {@code transferGroupId} are genuinely optional (for example, a
 * transfer has no category, and a non-transfer transaction has no transfer group) and are
 * exposed as {@link Optional} even though they are stored as plain nullable fields internally.
 *
 * <p><b>Equality:</b> based solely on {@link #getId() id}, matching entity identity rather than
 * comparing every field — see the equivalent note on {@code Account} for the same caveat about
 * not-yet-persisted instances.
 *
 * <p><b>Persistence id assignment:</b> as with {@code Account}, this class has no notion of a
 * "transient" versus "persisted" instance. A repository that generates a real id for a
 * not-yet-saved transaction calls {@link #withId(long)} to obtain a new, separate
 * {@code Transaction} carrying that id, rather than mutating the instance it was given.
 *
 * <p>This class only models a transaction's data; it does not compute balances or aggregates.
 */
public final class Transaction {

    private final long id;
    private final long accountId;
    private final BigDecimal amount;
    private final TransactionType type;
    private final Long categoryId;
    private final LocalDate date;
    private final String description;
    private final Long transferGroupId;

    public Transaction(
            long id,
            long accountId,
            BigDecimal amount,
            TransactionType type,
            Long categoryId,
            LocalDate date,
            String description,
            Long transferGroupId) {
        if (id < 0) {
            throw new IllegalArgumentException("id must be >= 0");
        }
        if (accountId < 0) {
            throw new IllegalArgumentException("accountId must be >= 0");
        }
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be strictly > 0");
        }
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(date, "date must not be null");
        if (categoryId != null && categoryId < 0) {
            throw new IllegalArgumentException("categoryId must be >= 0 when present");
        }
        if (transferGroupId != null && transferGroupId < 0) {
            throw new IllegalArgumentException("transferGroupId must be >= 0 when present");
        }

        this.id = id;
        this.accountId = accountId;
        this.amount = amount;
        this.type = type;
        this.categoryId = categoryId;
        this.date = date;
        this.description = description;
        this.transferGroupId = transferGroupId;
    }

    public long getId() {
        return id;
    }

    public long getAccountId() {
        return accountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionType getType() {
        return type;
    }

    public Optional<Long> getCategoryId() {
        return Optional.ofNullable(categoryId);
    }

    public LocalDate getDate() {
        return date;
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    public Optional<Long> getTransferGroupId() {
        return Optional.ofNullable(transferGroupId);
    }

    /**
     * Returns a new {@code Transaction} identical to this one except for its {@code id}. Does not
     * modify this instance. Intended for a repository to attach a database-generated id to an
     * otherwise-already-built transaction after persisting it, without needing a mutable id field.
     */
    public Transaction withId(long id) {
        return new Transaction(
                id, this.accountId, this.amount, this.type, this.categoryId,
                this.date, this.description, this.transferGroupId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Transaction other)) {
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
        return "Transaction{id=" + id
                + ", accountId=" + accountId
                + ", amount=" + amount
                + ", type=" + type
                + ", categoryId=" + categoryId
                + ", date=" + date
                + ", description='" + description + '\''
                + ", transferGroupId=" + transferGroupId
                + '}';
    }
}
