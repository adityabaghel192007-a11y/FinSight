package com.finsight.service;

import com.finsight.domain.account.Account;
import com.finsight.domain.transaction.TransactionType;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Pairs a persisted {@link Account} with its current balance — {@link Account#getOpeningBalance()}
 * combined with every one of its transactions via
 * {@code com.finsight.simulation.calculator.AccountBalanceCalculator} (Milestone 5K) — for a UI
 * that needs both together without recomputing anything itself. Returned by
 * {@link FinancialDataApplicationService#getAccountBalances()}; this class holds no calculation
 * logic of its own, it only carries an already-computed result.
 *
 * <p><b>{@link #getCurrentBalance()} is genuinely optional</b>, for exactly one reason:
 * {@code AccountBalanceCalculator} deliberately refuses (via {@code UnsupportedOperationException})
 * to compute a balance for an account with any {@link TransactionType#TRANSFER} transaction, since
 * transfer direction is not yet determinable from the current domain model (see that class's own
 * documentation). Rather than letting that exception propagate and prevent every other account's
 * balance from displaying too, {@link FinancialDataApplicationService#getAccountBalances()} catches
 * it per-account and represents that one account's balance as absent here — following this
 * codebase's established convention (see {@code Transaction}'s own documentation) of storing a
 * value that may legitimately be missing as a plain nullable field internally, exposed as
 * {@link Optional} so a caller cannot forget to handle the absent case. No current UI workflow can
 * actually create a {@code TRANSFER} transaction (see {@code FinSightApplication}'s own
 * documentation), so this case is not reachable through the application as it stands today, but
 * this class does not assume that will always remain true.
 */
public final class AccountBalance {

    private final Account account;
    private final BigDecimal currentBalance;

    /**
     * @param currentBalance the account's current balance, or {@code null} if
     *     {@code AccountBalanceCalculator} could not compute one (see this class's own
     *     documentation) — exposed through {@link #getCurrentBalance()} as an {@link Optional}
     */
    public AccountBalance(Account account, BigDecimal currentBalance) {
        this.account = Objects.requireNonNull(account, "account must not be null");
        this.currentBalance = currentBalance;
    }

    public Account getAccount() {
        return account;
    }

    /** The account's current balance, or {@link Optional#empty()} if it could not be computed. */
    public Optional<BigDecimal> getCurrentBalance() {
        return Optional.ofNullable(currentBalance);
    }
}
