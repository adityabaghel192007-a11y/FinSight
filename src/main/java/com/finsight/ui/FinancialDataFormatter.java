package com.finsight.ui;

import com.finsight.domain.account.Account;
import com.finsight.domain.category.Category;
import com.finsight.domain.transaction.Transaction;
import com.finsight.domain.transaction.TransactionType;
import com.finsight.service.AccountBalance;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Turns persisted {@link Account} and {@link Transaction} data — read via
 * {@code com.finsight.service.FinancialDataApplicationService}, exactly as persisted — into display
 * strings/{@link TransactionRowView}s a JavaFX control can show directly, exactly the same kind of
 * presentation-only job {@link SimulationResultFormatter} already does for a simulation result.
 *
 * <p>This class performs no financial calculation whatsoever — not even the plain differences
 * {@link SimulationResultFormatter} allows itself. In particular, {@link #formatAccountBalance}
 * (Milestone 5K) only formats the {@link BigDecimal} an {@link AccountBalance} already carries —
 * itself already computed by {@code AccountBalanceCalculator} via
 * {@code FinancialDataApplicationService.getAccountBalances()}, never here. Its only jobs are
 * formatting {@link BigDecimal} amounts and {@link java.time.LocalDate}s as readable text, and
 * resolving a {@link Transaction}'s {@code categoryId}/{@code accountId} to the matching
 * {@link Category#getName()}/{@link Account#getName()} from an already-fetched list — a lookup,
 * not a calculation.
 */
public final class FinancialDataFormatter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    private FinancialDataFormatter() {
        // stateless utility class; not instantiable
    }

    /**
     * {@code accountBalance}'s already-computed current balance, formatted as plain (unsigned)
     * money — or, if {@link AccountBalance#getCurrentBalance()} is absent (see that class's own
     * documentation on why that can happen), a short, honest placeholder rather than a fabricated
     * number. This method computes nothing; it only formats or falls back.
     */
    public static String formatAccountBalance(AccountBalance accountBalance) {
        Objects.requireNonNull(accountBalance, "accountBalance must not be null");
        return accountBalance.getCurrentBalance()
                .map(FinancialDataFormatter::formatMoney)
                .orElse("Balance unavailable");
    }

    /** e.g. {@code "BANK · INR"} — {@code account}'s type and currency, exactly as persisted. */
    public static String formatAccountSubtitle(Account account) {
        Objects.requireNonNull(account, "account must not be null");
        return account.getType() + " · " + account.getCurrency();
    }

    /**
     * Converts every element of {@code transactions} into a {@link TransactionRowView}, resolving
     * each transaction's {@code categoryId} (if present) and {@code accountId} against
     * {@code categories}/{@code accounts} once via a lookup map built up front, rather than
     * scanning either list per transaction. A {@code categoryId} with no match in
     * {@code categories} (or none at all, e.g. a {@code TRANSFER}) displays as {@code "—"}; an
     * {@code accountId} with no match in {@code accounts} displays as {@code "Unknown account"} —
     * neither case is expected in practice, since both foreign keys are enforced at the database
     * level, but this method never throws for display purposes.
     */
    public static List<TransactionRowView> toTransactionRows(
            List<Transaction> transactions, List<Category> categories, List<Account> accounts) {
        Objects.requireNonNull(transactions, "transactions must not be null");
        Objects.requireNonNull(categories, "categories must not be null");
        Objects.requireNonNull(accounts, "accounts must not be null");

        Map<Long, String> categoryNamesById = new HashMap<>();
        for (Category category : categories) {
            categoryNamesById.put(category.getId(), category.getName());
        }
        Map<Long, String> accountNamesById = new HashMap<>();
        for (Account account : accounts) {
            accountNamesById.put(account.getId(), account.getName());
        }

        List<TransactionRowView> rows = new ArrayList<>(transactions.size());
        for (Transaction transaction : transactions) {
            String categoryName = transaction.getCategoryId()
                    .map(id -> categoryNamesById.getOrDefault(id, "—"))
                    .orElse("—");
            String accountName = accountNamesById.getOrDefault(transaction.getAccountId(), "Unknown account");

            rows.add(new TransactionRowView(
                    transaction.getDate().format(DATE_FORMAT),
                    formatType(transaction.getType()),
                    categoryName,
                    formatSignedAmount(transaction.getAmount(), transaction.getType()),
                    accountName));
        }
        return rows;
    }

    private static String formatType(TransactionType type) {
        return switch (type) {
            case INCOME -> "Income";
            case EXPENSE -> "Expense";
            case TRANSFER -> "Transfer";
        };
    }

    /**
     * {@code amount} (always stored strictly positive — see {@link Transaction}) prefixed with
     * {@code +} for {@link TransactionType#INCOME} or {@code -} for {@link TransactionType#EXPENSE},
     * matching {@code SimulationResultFormatter}'s existing signed-money convention. A
     * {@code TRANSFER}'s direction is not determinable from the current domain model (see
     * {@code AccountBalanceCalculator}'s own documentation), so it is shown unsigned rather than
     * guessing.
     */
    private static String formatSignedAmount(BigDecimal amount, TransactionType type) {
        String formatted = formatMoney(amount);
        return switch (type) {
            case INCOME -> "+" + formatted;
            case EXPENSE -> "-" + formatted;
            case TRANSFER -> formatted;
        };
    }

    private static String formatMoney(BigDecimal amount) {
        return String.format("%,.2f", amount);
    }
}
