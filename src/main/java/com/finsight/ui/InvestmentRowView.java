package com.finsight.ui;

import java.util.Objects;

/**
 * A plain, already-formatted view of one persisted {@code com.finsight.domain.investment.Investment},
 * ready for a JavaFX {@code TableView} row to display directly — one field per column, exactly the
 * same kind of carrier {@link TransactionRowView} already is for a {@code Transaction}.
 *
 * <p>Every field here is a display string; this class holds no monetary or business logic of its
 * own and performs no arithmetic — it is purely a carrier for strings {@link InvestmentFormatter}
 * has already produced (including resolving the investment's {@code accountId} to a human-readable
 * account name, and computing invested/current/gain-loss via the existing
 * {@code InvestmentCalculator}). Deliberately carries no database id: matching this milestone's
 * "don't expose internal database ids as the primary user-facing UX" constraint, nothing about this
 * carrier lets a JavaFX {@code TableView} display or sort by id. Immutable, with value-based
 * equality so it can be asserted against directly in a test.
 */
public final class InvestmentRowView {

    private final String name;
    private final String ticker;
    private final String assetType;
    private final String currency;
    private final String quantity;
    private final String averagePurchasePrice;
    private final String currentPrice;
    private final String investedValue;
    private final String currentValue;
    private final String gainLoss;
    private final String accountName;

    public InvestmentRowView(
            String name,
            String ticker,
            String assetType,
            String currency,
            String quantity,
            String averagePurchasePrice,
            String currentPrice,
            String investedValue,
            String currentValue,
            String gainLoss,
            String accountName) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.ticker = Objects.requireNonNull(ticker, "ticker must not be null");
        this.assetType = Objects.requireNonNull(assetType, "assetType must not be null");
        this.currency = Objects.requireNonNull(currency, "currency must not be null");
        this.quantity = Objects.requireNonNull(quantity, "quantity must not be null");
        this.averagePurchasePrice = Objects.requireNonNull(averagePurchasePrice, "averagePurchasePrice must not be null");
        this.currentPrice = Objects.requireNonNull(currentPrice, "currentPrice must not be null");
        this.investedValue = Objects.requireNonNull(investedValue, "investedValue must not be null");
        this.currentValue = Objects.requireNonNull(currentValue, "currentValue must not be null");
        this.gainLoss = Objects.requireNonNull(gainLoss, "gainLoss must not be null");
        this.accountName = Objects.requireNonNull(accountName, "accountName must not be null");
    }

    /** The investment's name, exactly as persisted. */
    public String getName() {
        return name;
    }

    /** The investment's ticker, or {@code "—"} if it has none. */
    public String getTicker() {
        return ticker;
    }

    /** e.g. {@code "ETF"}, {@code "GOLD"} — the investment's {@code AssetType}, exactly as persisted. */
    public String getAssetType() {
        return assetType;
    }

    /** e.g. {@code "INR"}, {@code "USD"} — the investment's own currency, exactly as persisted. */
    public String getCurrency() {
        return currency;
    }

    /** The investment's quantity, formatted as a plain decimal. */
    public String getQuantity() {
        return quantity;
    }

    /** {@code quantity * averagePurchasePrice}'s per-unit price, formatted as currency-prefixed money. */
    public String getAveragePurchasePrice() {
        return averagePurchasePrice;
    }

    /** The investment's current per-unit price, formatted as currency-prefixed money. */
    public String getCurrentPrice() {
        return currentPrice;
    }

    /** {@code InvestmentCalculator.calculateInvestedValue}, formatted as currency-prefixed money. */
    public String getInvestedValue() {
        return investedValue;
    }

    /** {@code InvestmentCalculator.calculateCurrentValue}, formatted as currency-prefixed money. */
    public String getCurrentValue() {
        return currentValue;
    }

    /** {@code InvestmentCalculator.calculateGainLoss}, formatted as signed, currency-prefixed money. */
    public String getGainLoss() {
        return gainLoss;
    }

    /** The name of the account this investment belongs to. */
    public String getAccountName() {
        return accountName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InvestmentRowView other)) {
            return false;
        }
        return name.equals(other.name)
                && ticker.equals(other.ticker)
                && assetType.equals(other.assetType)
                && currency.equals(other.currency)
                && quantity.equals(other.quantity)
                && averagePurchasePrice.equals(other.averagePurchasePrice)
                && currentPrice.equals(other.currentPrice)
                && investedValue.equals(other.investedValue)
                && currentValue.equals(other.currentValue)
                && gainLoss.equals(other.gainLoss)
                && accountName.equals(other.accountName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, ticker, assetType, currency, quantity, averagePurchasePrice,
                currentPrice, investedValue, currentValue, gainLoss, accountName);
    }

    @Override
    public String toString() {
        return "InvestmentRowView{name='" + name + '\''
                + ", ticker='" + ticker + '\''
                + ", assetType='" + assetType + '\''
                + ", currency='" + currency + '\''
                + ", quantity='" + quantity + '\''
                + ", averagePurchasePrice='" + averagePurchasePrice + '\''
                + ", currentPrice='" + currentPrice + '\''
                + ", investedValue='" + investedValue + '\''
                + ", currentValue='" + currentValue + '\''
                + ", gainLoss='" + gainLoss + '\''
                + ", accountName='" + accountName + '\''
                + '}';
    }
}
