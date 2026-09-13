package com.finsight.domain.account;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A financial account (a bank account, a cash wallet, an investment account, ...).
 *
 * <p>Instances are immutable. All constructor arguments are validated eagerly; invalid input
 * (nulls, a blank name, a negative id, a negative opening balance) fails fast rather than being
 * normalized or silently accepted.
 *
 * <p><b>Equality:</b> {@code Account} is an entity, so {@link #equals(Object)} and
 * {@link #hashCode()} are based solely on {@link #getId() id}, not on every field — two
 * instances with the same id are considered the same account even if other fields differ.
 * This is only meaningful once an account has been assigned a real, persisted id; comparing two
 * not-yet-persisted instances that happen to share a placeholder id is not reliable, but
 * assigning real ids is a persistence-layer concern for a later milestone.
 *
 * <p><b>Persistence id assignment:</b> this class has no notion of a "transient" versus
 * "persisted" instance — every {@code Account} always carries some {@code id}, assigned by
 * whoever constructs it. When a repository persists a not-yet-saved account and the database
 * generates its real id, the repository does not (and cannot, since this class has no setters)
 * mutate the instance it was given; instead it calls {@link #withId(long)} to obtain a new,
 * separate {@code Account} carrying the generated id, leaving the original instance untouched.
 */
public final class Account {

    private final long id;
    private final String name;
    private final AccountType type;
    private final Currency currency;
    private final BigDecimal openingBalance;

    public Account(long id, String name, AccountType type, Currency currency, BigDecimal openingBalance) {
        if (id < 0) {
            throw new IllegalArgumentException("id must be >= 0");
        }
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(openingBalance, "openingBalance must not be null");
        if (openingBalance.signum() < 0) {
            throw new IllegalArgumentException("openingBalance must be >= 0");
        }

        this.id = id;
        this.name = name;
        this.type = type;
        this.currency = currency;
        this.openingBalance = openingBalance;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public AccountType getType() {
        return type;
    }

    public Currency getCurrency() {
        return currency;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    /**
     * Returns a new {@code Account} identical to this one except for its {@code id}. Does not
     * modify this instance. Intended for a repository to attach a database-generated id to an
     * otherwise-already-built account after persisting it, without needing a mutable id field.
     */
    public Account withId(long id) {
        return new Account(id, this.name, this.type, this.currency, this.openingBalance);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Account other)) {
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
        return "Account{id=" + id
                + ", name='" + name + '\''
                + ", type=" + type
                + ", currency=" + currency
                + ", openingBalance=" + openingBalance
                + '}';
    }
}
