package com.finsight.ui;

import java.util.Objects;

/**
 * A plain, already-formatted view of one persisted {@code com.finsight.domain.transaction.Transaction},
 * ready for a JavaFX {@code TableView} row to display directly — one field per column
 * (Date/Type/Category/Amount/Account), exactly like {@link SimulationResultView} is one field per
 * metric card.
 *
 * <p>Every field here is a display string; this class holds no monetary or date logic of its own
 * and performs no arithmetic — it is purely a carrier for strings {@link FinancialDataFormatter}
 * has already produced (including resolving the transaction's {@code categoryId}/{@code accountId}
 * to a human-readable name). Immutable, with value-based equality so it can be asserted against
 * directly in a test.
 */
public final class TransactionRowView {

    private final String date;
    private final String type;
    private final String category;
    private final String amount;
    private final String account;

    public TransactionRowView(String date, String type, String category, String amount, String account) {
        this.date = Objects.requireNonNull(date, "date must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.category = Objects.requireNonNull(category, "category must not be null");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.account = Objects.requireNonNull(account, "account must not be null");
    }

    /** e.g. {@code "Mar 1, 2026"}. */
    public String getDate() {
        return date;
    }

    /** e.g. {@code "Income"}, {@code "Expense"}, or {@code "Transfer"}. */
    public String getType() {
        return type;
    }

    /** The transaction's category name, or a placeholder (e.g. {@code "—"}) if it has none. */
    public String getCategory() {
        return category;
    }

    /** The transaction's amount, formatted as signed money (see {@link FinancialDataFormatter}). */
    public String getAmount() {
        return amount;
    }

    /** The name of the account this transaction belongs to. */
    public String getAccount() {
        return account;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TransactionRowView other)) {
            return false;
        }
        return date.equals(other.date)
                && type.equals(other.type)
                && category.equals(other.category)
                && amount.equals(other.amount)
                && account.equals(other.account);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, type, category, amount, account);
    }

    @Override
    public String toString() {
        return "TransactionRowView{date='" + date + '\''
                + ", type='" + type + '\''
                + ", category='" + category + '\''
                + ", amount='" + amount + '\''
                + ", account='" + account + '\''
                + '}';
    }
}
