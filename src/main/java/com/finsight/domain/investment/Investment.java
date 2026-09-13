package com.finsight.domain.investment;

import com.finsight.domain.account.Currency;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * A holding in an investment account (a stock position, a mutual fund, a fixed deposit, ...).
 *
 * <p>Instances are immutable and validated eagerly in the constructor. {@link #getTicker()} is
 * genuinely optional — not every asset (a fixed deposit, physical gold) has one — and is stored
 * as a nullable field but exposed as an {@link Optional}, matching how {@code Transaction}
 * handles its optional fields. Every other field is required, including the {@link BigDecimal}
 * ones: {@link #getQuantity()} must be strictly positive, the remaining monetary/rate fields
 * must be present and non-negative.
 *
 * <p>This class only models an investment's data; it does not compute market value or
 * projected growth.
 *
 * <p><b>Equality:</b> based solely on {@link #getId() id}, matching entity identity rather than
 * comparing every field — see the equivalent note on {@code Account} for the same caveat about
 * not-yet-persisted instances.
 *
 * <p><b>Persistence id assignment:</b> as with {@code Account}, this class has no notion of a
 * "transient" versus "persisted" instance. A repository that generates a real id for a
 * not-yet-saved investment calls {@link #withId(long)} to obtain a new, separate
 * {@code Investment} carrying that id, rather than mutating the instance it was given.
 */
public final class Investment {

    private final long id;
    private final long accountId;
    private final String name;
    private final String ticker;
    private final AssetType assetType;
    private final Currency currency;
    private final BigDecimal quantity;
    private final BigDecimal averagePurchasePrice;
    private final BigDecimal currentPrice;
    private final BigDecimal expectedAnnualReturn;
    private final BigDecimal monthlyContribution;

    public Investment(
            long id,
            long accountId,
            String name,
            String ticker,
            AssetType assetType,
            Currency currency,
            BigDecimal quantity,
            BigDecimal averagePurchasePrice,
            BigDecimal currentPrice,
            BigDecimal expectedAnnualReturn,
            BigDecimal monthlyContribution) {
        if (id < 0) {
            throw new IllegalArgumentException("id must be >= 0");
        }
        if (accountId < 0) {
            throw new IllegalArgumentException("accountId must be >= 0");
        }
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(assetType, "assetType must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(quantity, "quantity must not be null");
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be strictly > 0");
        }
        Objects.requireNonNull(averagePurchasePrice, "averagePurchasePrice must not be null");
        if (averagePurchasePrice.signum() < 0) {
            throw new IllegalArgumentException("averagePurchasePrice must be >= 0");
        }
        Objects.requireNonNull(currentPrice, "currentPrice must not be null");
        if (currentPrice.signum() < 0) {
            throw new IllegalArgumentException("currentPrice must be >= 0");
        }
        Objects.requireNonNull(expectedAnnualReturn, "expectedAnnualReturn must not be null");
        if (expectedAnnualReturn.signum() < 0) {
            throw new IllegalArgumentException("expectedAnnualReturn must be >= 0");
        }
        Objects.requireNonNull(monthlyContribution, "monthlyContribution must not be null");
        if (monthlyContribution.signum() < 0) {
            throw new IllegalArgumentException("monthlyContribution must be >= 0");
        }

        this.id = id;
        this.accountId = accountId;
        this.name = name;
        this.ticker = ticker;
        this.assetType = assetType;
        this.currency = currency;
        this.quantity = quantity;
        this.averagePurchasePrice = averagePurchasePrice;
        this.currentPrice = currentPrice;
        this.expectedAnnualReturn = expectedAnnualReturn;
        this.monthlyContribution = monthlyContribution;
    }

    public long getId() {
        return id;
    }

    public long getAccountId() {
        return accountId;
    }

    public String getName() {
        return name;
    }

    public Optional<String> getTicker() {
        return Optional.ofNullable(ticker);
    }

    public AssetType getAssetType() {
        return assetType;
    }

    public Currency getCurrency() {
        return currency;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getAveragePurchasePrice() {
        return averagePurchasePrice;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public BigDecimal getExpectedAnnualReturn() {
        return expectedAnnualReturn;
    }

    public BigDecimal getMonthlyContribution() {
        return monthlyContribution;
    }

    /**
     * Returns a new {@code Investment} identical to this one except for its {@code id}. Does not
     * modify this instance. Intended for a repository to attach a database-generated id to an
     * otherwise-already-built investment after persisting it, without needing a mutable id field.
     */
    public Investment withId(long id) {
        return new Investment(
                id, this.accountId, this.name, this.ticker, this.assetType, this.currency,
                this.quantity, this.averagePurchasePrice, this.currentPrice,
                this.expectedAnnualReturn, this.monthlyContribution);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Investment other)) {
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
        return "Investment{id=" + id
                + ", accountId=" + accountId
                + ", name='" + name + '\''
                + ", ticker='" + ticker + '\''
                + ", assetType=" + assetType
                + ", currency=" + currency
                + ", quantity=" + quantity
                + ", averagePurchasePrice=" + averagePurchasePrice
                + ", currentPrice=" + currentPrice
                + ", expectedAnnualReturn=" + expectedAnnualReturn
                + ", monthlyContribution=" + monthlyContribution
                + '}';
    }
}
