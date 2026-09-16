package com.finsight.service;

import com.finsight.domain.account.Account;
import com.finsight.domain.category.Category;
import com.finsight.domain.transaction.Transaction;
import com.finsight.repository.AccountRepository;
import com.finsight.repository.CategoryRepository;
import com.finsight.repository.TransactionRepository;
import com.finsight.simulation.calculator.AccountBalanceCalculator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Application-layer <b>read</b> access to the persisted {@link Account}, {@link Category}, and
 * {@link Transaction} data (Milestone 5J), for a UI that needs to display them without touching a
 * repository directly.
 *
 * <p>This class is deliberately read-only and deliberately separate from
 * {@link TransactionApplicationService}: that class's own documentation scopes it narrowly to the
 * three explicit write workflows ({@code recordIncome}, {@code recordExpense},
 * {@code recordTransfer}) it was introduced for, and every one of its existing tests, javadoc, and
 * transactional/validation behavior is about writing. Adding unrelated list-everything read
 * methods to it would blur that documented single responsibility for no benefit — the three
 * repositories this class needs are exactly the same three {@link TransactionApplicationService}
 * already depends on, wired from the very same {@link com.finsight.app.CompositionRoot} instances,
 * so nothing is duplicated by keeping the two classes separate.
 *
 * <p>{@link #getAccounts()}, {@link #getCategories()}, and {@link #getTransactions()} are direct,
 * unfiltered pass-throughs to the corresponding repository's own {@code findAll()} — no
 * calculation, aggregation, filtering, or sorting of any kind.
 *
 * <p><b>{@link #getAccountBalances()} (Milestone 5K)</b> is the one method here that does involve a
 * calculation, and it is deliberately not a calculation of this class's own: for each persisted
 * {@link Account} it fetches that account's transactions via
 * {@link TransactionRepository#findByAccountId(long)} and hands both straight to
 * {@link AccountBalanceCalculator#calculateBalance(Account, List)} — the exact same
 * already-existing, already-tested calculator {@code SnapshotBuilder} itself calls for the same
 * purpose (consolidating cash for a simulation), imported the same way
 * ({@code com.finsight.service} depending on {@code com.finsight.simulation.calculator} directly,
 * exactly as {@code SnapshotBuilder} already does — see its own imports). This class still performs
 * no arithmetic of its own: {@code openingBalance + income - expense} happens inside the calculator,
 * not here.
 */
public final class FinancialDataApplicationService {

    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;

    public FinancialDataApplicationService(
            AccountRepository accountRepository,
            CategoryRepository categoryRepository,
            TransactionRepository transactionRepository) {
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository must not be null");
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository must not be null");
        this.transactionRepository =
                Objects.requireNonNull(transactionRepository, "transactionRepository must not be null");
    }

    /** Every currently persisted {@link Account}, exactly as {@link AccountRepository#findAll()} returns it. */
    public List<Account> getAccounts() {
        return accountRepository.findAll();
    }

    /** Every currently persisted {@link Category}, exactly as {@link CategoryRepository#findAll()} returns it. */
    public List<Category> getCategories() {
        return categoryRepository.findAll();
    }

    /** Every currently persisted {@link Transaction}, exactly as {@link TransactionRepository#findAll()} returns it. */
    public List<Transaction> getTransactions() {
        return transactionRepository.findAll();
    }

    /**
     * Every currently persisted {@link Account}, each paired with its current balance — see
     * {@link AccountBalance} and this class's own top-level documentation for exactly how that
     * balance is computed and why it can be absent for a particular account. Accounts are returned
     * in the same order {@link #getAccounts()} would return them.
     */
    public List<AccountBalance> getAccountBalances() {
        List<AccountBalance> accountBalances = new ArrayList<>();
        for (Account account : getAccounts()) {
            List<Transaction> transactions = transactionRepository.findByAccountId(account.getId());
            BigDecimal currentBalance;
            try {
                currentBalance = AccountBalanceCalculator.calculateBalance(account, transactions);
            } catch (UnsupportedOperationException e) {
                // A TRANSFER transaction against this account — see AccountBalance's own
                // documentation for why this is represented as "absent" here rather than thrown,
                // and why that does not mean this class is guessing at or inventing a balance.
                currentBalance = null;
            }
            accountBalances.add(new AccountBalance(account, currentBalance));
        }
        return List.copyOf(accountBalances);
    }
}
