package com.finsight.simulation.calculator;

import com.finsight.common.FinancialPrecision;
import com.finsight.domain.account.Account;
import com.finsight.domain.transaction.Transaction;
import com.finsight.domain.transaction.TransactionType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Computes an {@link Account}'s current balance from its opening balance plus the account's own
 * {@link Transaction}s:
 *
 * <pre>
 *     balance = openingBalance + income - expense
 * </pre>
 *
 * <p>This class is stateless: it holds no account or transaction data of its own, only a pure
 * function of an {@link Account} and a {@link List} of {@link Transaction}s the caller has already
 * retrieved — typically via {@code TransactionRepository.findByAccountId(account.getId())}. It
 * depends on nothing outside the JDK and {@code com.finsight.domain} — no JavaFX, no JDBC, no
 * persistence — so it is unit-testable with plain objects, exactly like {@link NetWorthCalculator}
 * and every other calculator in this package. Retrieving the transactions is the repository's job;
 * this class only ever calculates from a list already in hand, keeping calculation strictly above
 * the persistence layer.
 *
 * <p><b>{@code TRANSFER} is deliberately not yet supported.</b> A {@link Transaction} records only
 * an {@code accountId}, an {@code amount} (always positive), a {@link TransactionType}, and a
 * grouping {@code transferGroupId} — nothing on the transaction itself, or anywhere else in the
 * current domain model, says whether a given {@code TRANSFER} leg represents money entering or
 * leaving the account it belongs to (a transfer is two {@code TRANSFER} rows sharing a
 * {@code transferGroupId}, with no source/destination distinction between them). Guessing a
 * direction — for example, from the relative order of the two legs' ids or account ids — would be
 * inventing a semantic the model does not actually guarantee, so this class refuses to guess: see
 * {@link #calculateBalance(Account, List)}. A future transfer-workflow milestone is expected to
 * establish a real source/destination invariant when transfers are actually created, at which point
 * this class can be extended to account for transfers correctly instead of working around a guess
 * made here.
 *
 * <p><b>Rounding:</b> the running total is accumulated at full {@link BigDecimal} precision — plain
 * addition/subtraction of already-2-decimal-scale monetary amounts loses nothing — and rounded via
 * {@link FinancialPrecision#roundMoney(BigDecimal)} only once, on the final result, matching every
 * other calculator in this package.
 */
public final class AccountBalanceCalculator {

    private AccountBalanceCalculator() {
        // stateless utility class; not instantiable
    }

    /**
     * {@code account.getOpeningBalance()}, adjusted by every transaction in {@code transactions}:
     * {@code +amount} for {@link TransactionType#INCOME}, {@code -amount} for
     * {@link TransactionType#EXPENSE}. Rounded to 2 decimal places via
     * {@link FinancialPrecision#roundMoney(BigDecimal)}. The result may be negative — a negative
     * balance is not rejected here; it is a legitimate (if undesirable) real-world state, and
     * flagging or preventing it is a concern for a higher layer, not this calculation.
     *
     * <p>{@code transactions} is expected to already be exactly the transactions belonging to
     * {@code account} — precisely what {@code TransactionRepository.findByAccountId(account.getId())}
     * returns. Every element's {@link Transaction#getAccountId()} must equal {@code account.getId()},
     * or this method throws {@link IllegalArgumentException} rather than silently folding in a
     * transaction that belongs to a different account. Neither {@code account} nor any element of
     * {@code transactions} is read more than once or modified in any way.
     *
     * @throws NullPointerException if {@code account}, {@code transactions}, or any element of
     *     {@code transactions} is {@code null}
     * @throws IllegalArgumentException if any transaction's {@code accountId} does not equal
     *     {@code account.getId()}
     * @throws UnsupportedOperationException if {@code transactions} contains a
     *     {@link TransactionType#TRANSFER} transaction — see the class-level documentation for why
     *     this class does not yet attempt to account for transfers
     */
    public static BigDecimal calculateBalance(Account account, List<Transaction> transactions) {
        Objects.requireNonNull(account, "account must not be null");
        Objects.requireNonNull(transactions, "transactions must not be null");

        BigDecimal balance = account.getOpeningBalance();
        for (Transaction transaction : transactions) {
            Objects.requireNonNull(transaction, "transactions must not contain null elements");
            if (transaction.getAccountId() != account.getId()) {
                throw new IllegalArgumentException(
                        "transaction " + transaction.getId() + " belongs to account "
                                + transaction.getAccountId() + ", not account " + account.getId());
            }

            switch (transaction.getType()) {
                case INCOME -> balance = balance.add(transaction.getAmount());
                case EXPENSE -> balance = balance.subtract(transaction.getAmount());
                case TRANSFER -> throw new UnsupportedOperationException(
                        "AccountBalanceCalculator cannot yet account for TRANSFER transaction "
                                + transaction.getId() + ": transfer direction (money entering versus leaving "
                                + "this account) is not determinable from the current domain model. This is "
                                + "deliberately deferred until a transfer workflow milestone establishes "
                                + "source/destination semantics; see the class-level documentation.");
            }
        }

        return FinancialPrecision.roundMoney(balance);
    }
}
