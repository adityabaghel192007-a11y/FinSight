package com.finsight.service;

import com.finsight.domain.account.Account;
import com.finsight.domain.category.Category;
import com.finsight.domain.category.CategoryType;
import com.finsight.domain.transaction.Transaction;
import com.finsight.domain.transaction.TransactionType;
import com.finsight.repository.AccountRepository;
import com.finsight.repository.CategoryRepository;
import com.finsight.repository.TransactionRepository;
import com.finsight.repository.TransactionRunner;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Application-layer orchestration for the real, explicit user workflows this and the preceding
 * milestone add: recording income ({@link #recordIncome}), recording an expense
 * ({@link #recordExpense}), and recording a transfer between two accounts
 * ({@link #recordTransfer}). Coordinates {@link AccountRepository}, {@link CategoryRepository},
 * {@link TransactionRepository}, and {@link TransactionRunner} — depending on those interfaces
 * only, never on a JDBC implementation — to apply cross-entity business validation that no single
 * repository or domain constructor can express on its own (an account or category actually
 * existing; a category's type matching the workflow; a transfer's two accounts differing), then
 * persist the resulting {@link Transaction}(s).
 *
 * <p>Deliberately three explicit methods rather than one generic
 * {@code record(TransactionType type, ...)}: {@code recordIncome}, {@code recordExpense}, and
 * {@code recordTransfer} are the workflows a caller (eventually a JavaFX controller) actually has,
 * each with its own validation rule, and naming them explicitly keeps that intent obvious at the
 * call site rather than requiring the caller to pass the "correct" enum value (and, for a transfer,
 * two account ids in the right roles) and trust it is used correctly. This class does not compute
 * or return an account's balance — that remains
 * {@code AccountBalanceCalculator.calculateBalance(Account, List)}, called separately by whatever
 * needs it; {@code AccountBalanceCalculator} still does not attempt to infer {@code TRANSFER}
 * direction, and this class does not change that.
 *
 * <p><b>Validation performed here for {@code recordIncome}/{@code recordExpense}</b> (a failure
 * throws {@link ApplicationValidationException}, with a message meant to be shown to the user
 * as-is):
 * <ul>
 *   <li>the given {@code accountId} must refer to a persisted {@link Account};</li>
 *   <li>the given {@code categoryId} must refer to a persisted {@link Category};</li>
 *   <li>that category's {@link Category#getType()} must match the workflow — {@code INCOME} for
 *       {@link #recordIncome}, {@code EXPENSE} for {@link #recordExpense} — so a caller cannot
 *       accidentally record an income transaction against an expense category or vice versa.</li>
 * </ul>
 * {@code categoryId} is required (a plain {@code long}, not optional) for both workflows even
 * though {@link Transaction#getCategoryId()} is itself optional in the general domain model — these
 * two specific, explicit workflows always need a category to validate its type against, so leaving
 * it out would defeat the point of that validation. See {@link #recordTransfer} for the validation
 * it performs, which is different (no category is involved at all).
 *
 * <p>Everything else — {@code amount} must be strictly positive, {@code date} must not be
 * {@code null} — is exactly what {@link Transaction}'s own constructor already validates, so it is
 * deliberately not re-checked here; constructing a {@link Transaction} simply lets that validation
 * run once, in one place, and propagates its {@link IllegalArgumentException} or
 * {@link NullPointerException} unchanged.
 *
 * <p><b>Transaction boundary:</b> {@code recordIncome} and {@code recordExpense} each perform
 * exactly one write after two reads, and a single JDBC {@code INSERT} is already atomic on its
 * own, so neither uses {@link TransactionRunner#executeInTransaction(Runnable)}. {@code
 * recordTransfer} is different — it performs two writes (plus a narrow update, see its own
 * documentation) that must succeed or fail together — so it is the one workflow here that actually
 * uses that boundary. This class depends on {@link TransactionRunner} rather than on
 * {@code SqliteConnectionProvider} directly, keeping it free of any JDBC-specific dependency.
 *
 * <p>This class is otherwise stateless — it holds no data of its own beyond what is supplied at
 * construction — and reads but never modifies the {@link Account} or {@link Category} it looks up.
 */
public final class TransactionApplicationService {

    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionRunner transactionRunner;

    public TransactionApplicationService(
            AccountRepository accountRepository,
            CategoryRepository categoryRepository,
            TransactionRepository transactionRepository,
            TransactionRunner transactionRunner) {
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository must not be null");
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository must not be null");
        this.transactionRepository =
                Objects.requireNonNull(transactionRepository, "transactionRepository must not be null");
        this.transactionRunner = Objects.requireNonNull(transactionRunner, "transactionRunner must not be null");
    }

    /**
     * Records income against {@code accountId}: validates that the account and category exist and
     * that the category is an {@link CategoryType#INCOME} category, then persists a new
     * {@link TransactionType#INCOME} transaction with no {@code transferGroupId} and returns the
     * persisted instance (carrying the id {@link TransactionRepository#save} generated for it).
     *
     * @throws ApplicationValidationException if {@code accountId} or {@code categoryId} does not
     *     refer to a persisted {@link Account}/{@link Category}, or if the category's type is not
     *     {@code INCOME}
     * @throws NullPointerException if {@code amount} or {@code date} is {@code null}
     * @throws IllegalArgumentException if {@code amount} is not strictly positive
     * @throws com.finsight.repository.RepositoryException if persisting the transaction fails
     */
    public Transaction recordIncome(long accountId, BigDecimal amount, long categoryId, LocalDate date,
            String description) {
        return record(accountId, amount, categoryId, date, description, CategoryType.INCOME, TransactionType.INCOME);
    }

    /**
     * Records an expense against {@code accountId}: validates that the account and category exist
     * and that the category is an {@link CategoryType#EXPENSE} category, then persists a new
     * {@link TransactionType#EXPENSE} transaction with no {@code transferGroupId} and returns the
     * persisted instance (carrying the id {@link TransactionRepository#save} generated for it).
     *
     * @throws ApplicationValidationException if {@code accountId} or {@code categoryId} does not
     *     refer to a persisted {@link Account}/{@link Category}, or if the category's type is not
     *     {@code EXPENSE}
     * @throws NullPointerException if {@code amount} or {@code date} is {@code null}
     * @throws IllegalArgumentException if {@code amount} is not strictly positive
     * @throws com.finsight.repository.RepositoryException if persisting the transaction fails
     */
    public Transaction recordExpense(long accountId, BigDecimal amount, long categoryId, LocalDate date,
            String description) {
        return record(accountId, amount, categoryId, date, description, CategoryType.EXPENSE, TransactionType.EXPENSE);
    }

    /**
     * Records a transfer of {@code amount} from {@code sourceAccountId} to
     * {@code destinationAccountId} as exactly two {@link TransactionType#TRANSFER} transactions —
     * one leg against each account — sharing a single {@code transferGroupId}, persisted
     * atomically: either both legs are saved, or neither is. Neither leg carries a
     * {@code categoryId}: a transfer is neither an income nor an expense category's concern, so
     * {@link CategoryRepository} is never consulted here, unlike {@link #recordIncome}/
     * {@link #recordExpense}. Both legs carry exactly the {@code amount}, {@code date}, and
     * (optional) {@code description} the caller supplied — no automatic "Transfer from/to ..."
     * text is added to {@code description}.
     *
     * <p><b>How {@code transferGroupId} is chosen:</b> the source leg is inserted first, with a
     * temporary {@code null} {@code transferGroupId} (no shared value exists until something is
     * persisted). Its database-generated id then becomes the {@code transferGroupId} for both
     * legs: the source leg's row is corrected to carry it via
     * {@link TransactionRepository#assignTransferGroupId}, and the destination leg is inserted
     * with it directly. This reuses an id the persistence layer already guarantees to be unique —
     * the source leg's own row id — rather than inventing a second identifier scheme (a
     * timestamp, a UUID, a random value), matching this project's existing convention of plain
     * generated {@code long} ids everywhere else.
     *
     * <p><b>Atomicity:</b> the insert-source/assign-group-id/insert-destination sequence above
     * runs inside {@link TransactionRunner#executeInTransaction(Runnable)}. If any step fails —
     * including a {@link TransactionRepository#save} or
     * {@link TransactionRepository#assignTransferGroupId} call — every write already made during
     * this call is rolled back and the original failure propagates unchanged; neither leg is left
     * persisted. Deterministic validation (accounts differ and both exist) happens before this
     * transaction opens, so the transaction boundary protects only the writes themselves.
     *
     * @return a two-element list: the source leg followed by the destination leg, both carrying
     *     their generated ids and the shared {@code transferGroupId}
     * @throws ApplicationValidationException if {@code sourceAccountId} equals
     *     {@code destinationAccountId}, or if either does not refer to a persisted {@link Account}
     * @throws NullPointerException if {@code amount} or {@code date} is {@code null}
     * @throws IllegalArgumentException if {@code amount} is not strictly positive
     * @throws com.finsight.repository.RepositoryException if persisting either leg fails
     */
    public List<Transaction> recordTransfer(long sourceAccountId, long destinationAccountId, BigDecimal amount,
            LocalDate date, String description) {
        if (sourceAccountId == destinationAccountId) {
            throw new ApplicationValidationException(
                    "A transfer's source and destination accounts must differ (both were " + sourceAccountId + ")");
        }
        Account source = accountRepository.findById(sourceAccountId)
                .orElseThrow(() -> new ApplicationValidationException("Account not found: " + sourceAccountId));
        Account destination = accountRepository.findById(destinationAccountId)
                .orElseThrow(() -> new ApplicationValidationException("Account not found: " + destinationAccountId));

        // Constructing this transient instance validates amount/date exactly as Transaction's own
        // constructor always does, and does so before the transaction below opens (see the
        // class-level note on relying on that validation rather than duplicating it), keeping the
        // transaction boundary scoped to the writes themselves rather than to this validation.
        Transaction transientSourceLeg = new Transaction(
                0L, source.getId(), amount, TransactionType.TRANSFER, null, date, description, null);

        List<Transaction> legs = new ArrayList<>(2);
        transactionRunner.executeInTransaction(() -> {
            Transaction insertedSource = transactionRepository.save(transientSourceLeg);
            long transferGroupId = insertedSource.getId();
            transactionRepository.assignTransferGroupId(insertedSource.getId(), transferGroupId);

            Transaction destinationLeg = transactionRepository.save(new Transaction(
                    0L, destination.getId(), amount, TransactionType.TRANSFER, null, date, description,
                    transferGroupId));

            // The row assignTransferGroupId just corrected is not re-read from the database; this
            // constructs the equivalent, already-immutable Transaction representation directly,
            // matching insertedSource exactly except for transferGroupId — no setter is introduced
            // and insertedSource itself is left untouched.
            Transaction sourceLeg = new Transaction(insertedSource.getId(), source.getId(), amount,
                    TransactionType.TRANSFER, null, date, description, transferGroupId);

            legs.add(sourceLeg);
            legs.add(destinationLeg);
        });

        return List.copyOf(legs);
    }

    private Transaction record(long accountId, BigDecimal amount, long categoryId, LocalDate date,
            String description, CategoryType requiredCategoryType, TransactionType transactionType) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApplicationValidationException("Account not found: " + accountId));
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ApplicationValidationException("Category not found: " + categoryId));
        if (category.getType() != requiredCategoryType) {
            throw new ApplicationValidationException(
                    "Category " + categoryId + " is a " + category.getType() + " category; "
                            + transactionType + " transactions require a " + requiredCategoryType + " category");
        }

        // account is only used to establish that accountId is valid above; the transaction itself
        // is keyed by accountId, matching how every other repository/domain class in this codebase
        // stores foreign references as plain ids rather than embedded objects.
        Transaction transaction = new Transaction(
                0L, account.getId(), amount, transactionType, category.getId(), date, description, null);
        return transactionRepository.save(transaction);
    }
}
