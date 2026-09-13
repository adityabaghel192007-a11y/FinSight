package com.finsight.service;

import com.finsight.domain.account.Account;
import com.finsight.domain.account.AccountType;
import com.finsight.domain.account.Currency;
import com.finsight.domain.category.Category;
import com.finsight.domain.category.CategoryType;
import com.finsight.domain.transaction.Transaction;
import com.finsight.domain.transaction.TransactionType;
import com.finsight.repository.AccountRepository;
import com.finsight.repository.CategoryRepository;
import com.finsight.repository.RepositoryException;
import com.finsight.repository.TransactionRepository;
import com.finsight.repository.TransactionRunner;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TransactionApplicationService}, using small in-memory fakes of
 * {@link AccountRepository}, {@link CategoryRepository}, {@link TransactionRepository}, and
 * {@link TransactionRunner} rather than a real database or a mocking framework — the interfaces
 * are simple enough (create, read by id, list, delete) that a fake backed by a {@link HashMap} is
 * straightforward and keeps this test fast and JDBC-independent. The transfer-specific tests below
 * additionally use a small snapshot/restore fake ({@code RollingBackTransactionRunner}) that
 * mimics {@code SqliteConnectionProvider.executeInTransaction}'s commit/rollback contract closely
 * enough to prove the service's atomicity logic without a real database — real-database atomicity
 * is separately and more authoritatively proven in
 * {@link TransactionApplicationServiceIntegrationTest}.
 */
class TransactionApplicationServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 3, 1);

    // ===================== in-memory fakes =====================

    private static final class InMemoryAccountRepository implements AccountRepository {
        private final Map<Long, Account> accounts = new HashMap<>();
        private long nextId = 1;

        @Override
        public Account save(Account account) {
            long id = nextId++;
            Account saved = account.withId(id);
            accounts.put(id, saved);
            return saved;
        }

        @Override
        public Optional<Account> findById(long id) {
            return Optional.ofNullable(accounts.get(id));
        }

        @Override
        public List<Account> findAll() {
            return List.copyOf(accounts.values());
        }

        @Override
        public void delete(long id) {
            accounts.remove(id);
        }
    }

    private static final class InMemoryCategoryRepository implements CategoryRepository {
        private final Map<Long, Category> categories = new HashMap<>();
        private long nextId = 1;

        @Override
        public Category save(Category category) {
            long id = nextId++;
            Category saved = category.withId(id);
            categories.put(id, saved);
            return saved;
        }

        @Override
        public Optional<Category> findById(long id) {
            return Optional.ofNullable(categories.get(id));
        }

        @Override
        public List<Category> findAll() {
            return List.copyOf(categories.values());
        }

        @Override
        public void delete(long id) {
            categories.remove(id);
        }
    }

    private static final class InMemoryTransactionRepository implements TransactionRepository {
        private final Map<Long, Transaction> transactions = new HashMap<>();
        private long nextId = 1;

        @Override
        public Transaction save(Transaction transaction) {
            long id = nextId++;
            Transaction saved = transaction.withId(id);
            transactions.put(id, saved);
            return saved;
        }

        @Override
        public Optional<Transaction> findById(long id) {
            return Optional.ofNullable(transactions.get(id));
        }

        @Override
        public List<Transaction> findAll() {
            return List.copyOf(transactions.values());
        }

        @Override
        public List<Transaction> findByAccountId(long accountId) {
            List<Transaction> result = new ArrayList<>();
            for (Transaction transaction : transactions.values()) {
                if (transaction.getAccountId() == accountId) {
                    result.add(transaction);
                }
            }
            return List.copyOf(result);
        }

        @Override
        public void assignTransferGroupId(long transactionId, long transferGroupId) {
            Transaction existing = transactions.get(transactionId);
            if (existing == null) {
                throw new RepositoryException("no transaction with id " + transactionId);
            }
            Transaction updated = new Transaction(existing.getId(), existing.getAccountId(), existing.getAmount(),
                    existing.getType(), existing.getCategoryId().orElse(null), existing.getDate(),
                    existing.getDescription().orElse(null), transferGroupId);
            transactions.put(transactionId, updated);
        }

        @Override
        public void delete(long id) {
            transactions.remove(id);
        }

        /** Test-only support for {@link RollingBackTransactionRunner}; not part of any interface. */
        private Map<Long, Transaction> snapshot() {
            return new HashMap<>(transactions);
        }

        private void restore(Map<Long, Transaction> snapshot, long snapshotNextId) {
            transactions.clear();
            transactions.putAll(snapshot);
            nextId = snapshotNextId;
        }
    }

    /** A fake whose {@link #save} always fails, for proving a persistence failure propagates. */
    private static final class FailingTransactionRepository implements TransactionRepository {
        @Override
        public Transaction save(Transaction transaction) {
            throw new RepositoryException("simulated persistence failure");
        }

        @Override
        public Optional<Transaction> findById(long id) {
            return Optional.empty();
        }

        @Override
        public List<Transaction> findAll() {
            return List.of();
        }

        @Override
        public List<Transaction> findByAccountId(long accountId) {
            return List.of();
        }

        @Override
        public void assignTransferGroupId(long transactionId, long transferGroupId) {
            throw new RepositoryException("simulated persistence failure");
        }

        @Override
        public void delete(long id) {
            // no-op
        }
    }

    /**
     * Wraps an {@link InMemoryTransactionRepository}, delegating every call except {@link #save},
     * which fails on its {@code failOnCallNumber}-th invocation (1-based) with {@code failure} —
     * used to inject a deterministic mid-workflow failure into {@code recordTransfer}'s
     * insert-source/assign-group-id/insert-destination sequence, exactly at the second write.
     */
    private static final class FailOnNthSaveTransactionRepository implements TransactionRepository {
        private final InMemoryTransactionRepository delegate;
        private final int failOnCallNumber;
        private final RuntimeException failure;
        private int saveCallCount = 0;

        private FailOnNthSaveTransactionRepository(
                InMemoryTransactionRepository delegate, int failOnCallNumber, RuntimeException failure) {
            this.delegate = delegate;
            this.failOnCallNumber = failOnCallNumber;
            this.failure = failure;
        }

        @Override
        public Transaction save(Transaction transaction) {
            saveCallCount++;
            if (saveCallCount == failOnCallNumber) {
                throw failure;
            }
            return delegate.save(transaction);
        }

        @Override
        public Optional<Transaction> findById(long id) {
            return delegate.findById(id);
        }

        @Override
        public List<Transaction> findAll() {
            return delegate.findAll();
        }

        @Override
        public List<Transaction> findByAccountId(long accountId) {
            return delegate.findByAccountId(accountId);
        }

        @Override
        public void assignTransferGroupId(long transactionId, long transferGroupId) {
            delegate.assignTransferGroupId(transactionId, transferGroupId);
        }

        @Override
        public void delete(long id) {
            delegate.delete(id);
        }
    }

    /** Runs {@code work} directly, with no real atomicity — sufficient wherever a test never fails. */
    private static final class ImmediateTransactionRunner implements TransactionRunner {
        @Override
        public void executeInTransaction(Runnable work) {
            work.run();
        }
    }

    /**
     * A test-only stand-in for {@code SqliteConnectionProvider.executeInTransaction}: snapshots
     * {@code transactionRepository}'s state before running {@code work}, and restores it if
     * {@code work} throws, so the transfer-atomicity tests below can prove
     * {@code TransactionApplicationService.recordTransfer}'s rollback behavior without a real
     * database. Real-database rollback is separately and more authoritatively proven in
     * {@link TransactionApplicationServiceIntegrationTest}.
     */
    private static final class RollingBackTransactionRunner implements TransactionRunner {
        private final InMemoryTransactionRepository transactionRepository;

        private RollingBackTransactionRunner(InMemoryTransactionRepository transactionRepository) {
            this.transactionRepository = transactionRepository;
        }

        @Override
        public void executeInTransaction(Runnable work) {
            Map<Long, Transaction> snapshot = transactionRepository.snapshot();
            long snapshotNextId = transactionRepository.nextId;
            try {
                work.run();
            } catch (RuntimeException e) {
                transactionRepository.restore(snapshot, snapshotNextId);
                throw e;
            }
        }
    }

    // ===================== fixture =====================

    private static final class Fixture {
        private final InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
        private final InMemoryCategoryRepository categoryRepository = new InMemoryCategoryRepository();
        private final InMemoryTransactionRepository transactionRepository = new InMemoryTransactionRepository();
        private final TransactionApplicationService service = new TransactionApplicationService(
                accountRepository, categoryRepository, transactionRepository, new ImmediateTransactionRunner());

        private long newAccountId() {
            return accountRepository.save(
                    new Account(0L, "Checking", AccountType.BANK, Currency.INR, new BigDecimal("1000.00"))).getId();
        }

        private long newCategoryId(CategoryType type) {
            return categoryRepository.save(new Category(0L, "Category", type)).getId();
        }
    }

    // ===================== A/B: happy path =====================

    @Test
    void recordIncomeCreatesIncomeTransaction() {
        Fixture fx = new Fixture();
        long accountId = fx.newAccountId();
        long categoryId = fx.newCategoryId(CategoryType.INCOME);

        Transaction result = fx.service.recordIncome(
                accountId, new BigDecimal("500.00"), categoryId, DATE, "Salary");

        assertTrue(result.getId() > 0, "the returned transaction should carry a generated id");
        assertEquals(accountId, result.getAccountId());
        assertEquals(0, new BigDecimal("500.00").compareTo(result.getAmount()));
        assertEquals(TransactionType.INCOME, result.getType());
        assertEquals(Optional.of(categoryId), result.getCategoryId());
        assertEquals(DATE, result.getDate());
        assertEquals(Optional.of("Salary"), result.getDescription());
        assertTrue(result.getTransferGroupId().isEmpty());
    }

    @Test
    void recordExpenseCreatesExpenseTransaction() {
        Fixture fx = new Fixture();
        long accountId = fx.newAccountId();
        long categoryId = fx.newCategoryId(CategoryType.EXPENSE);

        Transaction result = fx.service.recordExpense(
                accountId, new BigDecimal("42.50"), categoryId, DATE, "Groceries");

        assertTrue(result.getId() > 0);
        assertEquals(accountId, result.getAccountId());
        assertEquals(0, new BigDecimal("42.50").compareTo(result.getAmount()));
        assertEquals(TransactionType.EXPENSE, result.getType());
        assertEquals(Optional.of(categoryId), result.getCategoryId());
        assertEquals(DATE, result.getDate());
        assertEquals(Optional.of("Groceries"), result.getDescription());
        assertTrue(result.getTransferGroupId().isEmpty());
    }

    // ===================== C/D: account must exist =====================

    @Test
    void incomeRequiresExistingAccount() {
        Fixture fx = new Fixture();
        long categoryId = fx.newCategoryId(CategoryType.INCOME);

        assertThrows(ApplicationValidationException.class,
                () -> fx.service.recordIncome(999_999L, new BigDecimal("100.00"), categoryId, DATE, null));
        assertTrue(fx.transactionRepository.findAll().isEmpty(),
                "nothing should be persisted when the account does not exist");
    }

    @Test
    void expenseRequiresExistingAccount() {
        Fixture fx = new Fixture();
        long categoryId = fx.newCategoryId(CategoryType.EXPENSE);

        assertThrows(ApplicationValidationException.class,
                () -> fx.service.recordExpense(999_999L, new BigDecimal("100.00"), categoryId, DATE, null));
        assertTrue(fx.transactionRepository.findAll().isEmpty());
    }

    // ===================== E/F: category must exist =====================

    @Test
    void incomeRequiresExistingCategory() {
        Fixture fx = new Fixture();
        long accountId = fx.newAccountId();

        assertThrows(ApplicationValidationException.class,
                () -> fx.service.recordIncome(accountId, new BigDecimal("100.00"), 999_999L, DATE, null));
        assertTrue(fx.transactionRepository.findAll().isEmpty());
    }

    @Test
    void expenseRequiresExistingCategory() {
        Fixture fx = new Fixture();
        long accountId = fx.newAccountId();

        assertThrows(ApplicationValidationException.class,
                () -> fx.service.recordExpense(accountId, new BigDecimal("100.00"), 999_999L, DATE, null));
        assertTrue(fx.transactionRepository.findAll().isEmpty());
    }

    // ===================== G/H: category type must match the workflow =====================

    @Test
    void incomeRequiresIncomeCategory() {
        Fixture fx = new Fixture();
        long accountId = fx.newAccountId();
        long expenseCategoryId = fx.newCategoryId(CategoryType.EXPENSE);

        assertThrows(ApplicationValidationException.class,
                () -> fx.service.recordIncome(accountId, new BigDecimal("100.00"), expenseCategoryId, DATE, null),
                "recording income against an EXPENSE category must be rejected");
        assertTrue(fx.transactionRepository.findAll().isEmpty());
    }

    @Test
    void expenseRequiresExpenseCategory() {
        Fixture fx = new Fixture();
        long accountId = fx.newAccountId();
        long incomeCategoryId = fx.newCategoryId(CategoryType.INCOME);

        assertThrows(ApplicationValidationException.class,
                () -> fx.service.recordExpense(accountId, new BigDecimal("100.00"), incomeCategoryId, DATE, null),
                "recording an expense against an INCOME category must be rejected");
        assertTrue(fx.transactionRepository.findAll().isEmpty());
    }

    // ===================== I: amount must be positive =====================

    @Test
    void amountMustBePositive() {
        Fixture fx = new Fixture();
        long accountId = fx.newAccountId();
        long categoryId = fx.newCategoryId(CategoryType.INCOME);

        assertThrows(IllegalArgumentException.class,
                () -> fx.service.recordIncome(accountId, BigDecimal.ZERO, categoryId, DATE, null));
        assertThrows(IllegalArgumentException.class,
                () -> fx.service.recordIncome(accountId, new BigDecimal("-10.00"), categoryId, DATE, null));
    }

    // ===================== J: null date rejected =====================

    @Test
    void nullDateIsRejected() {
        Fixture fx = new Fixture();
        long accountId = fx.newAccountId();
        long categoryId = fx.newCategoryId(CategoryType.INCOME);

        assertThrows(NullPointerException.class,
                () -> fx.service.recordIncome(accountId, new BigDecimal("100.00"), categoryId, null, null));
    }

    // ===================== K: optional description =====================

    @Test
    void optionalDescriptionIsPreservedWhenPresentAndAbsentWhenNull() {
        Fixture fx = new Fixture();
        long accountId = fx.newAccountId();
        long categoryId = fx.newCategoryId(CategoryType.INCOME);

        Transaction withDescription =
                fx.service.recordIncome(accountId, new BigDecimal("100.00"), categoryId, DATE, "Bonus");
        Transaction withoutDescription =
                fx.service.recordIncome(accountId, new BigDecimal("100.00"), categoryId, DATE, null);

        assertEquals(Optional.of("Bonus"), withDescription.getDescription());
        assertTrue(withoutDescription.getDescription().isEmpty());
    }

    // ===================== L: transferGroupId is always absent =====================

    @Test
    void recordedIncomeAndExpenseNeverCarryATransferGroupId() {
        Fixture fx = new Fixture();
        long accountId = fx.newAccountId();
        long incomeCategoryId = fx.newCategoryId(CategoryType.INCOME);
        long expenseCategoryId = fx.newCategoryId(CategoryType.EXPENSE);

        Transaction income = fx.service.recordIncome(accountId, new BigDecimal("100.00"), incomeCategoryId, DATE, null);
        Transaction expense = fx.service.recordExpense(accountId, new BigDecimal("50.00"), expenseCategoryId, DATE, null);

        assertTrue(income.getTransferGroupId().isEmpty());
        assertTrue(expense.getTransferGroupId().isEmpty());
    }

    // ===================== M: persistence failure propagates =====================

    @Test
    void persistenceFailureIsPropagatedRatherThanSwallowed() {
        InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
        InMemoryCategoryRepository categoryRepository = new InMemoryCategoryRepository();
        long accountId = accountRepository.save(
                new Account(0L, "Checking", AccountType.BANK, Currency.INR, BigDecimal.ZERO)).getId();
        long categoryId = categoryRepository.save(new Category(0L, "Salary", CategoryType.INCOME)).getId();
        TransactionApplicationService service = new TransactionApplicationService(
                accountRepository, categoryRepository, new FailingTransactionRepository(),
                new ImmediateTransactionRunner());

        assertThrows(RepositoryException.class,
                () -> service.recordIncome(accountId, new BigDecimal("100.00"), categoryId, DATE, null));
    }

    // ===================== N: no partial domain mutation =====================

    @Test
    void neitherAccountNorCategoryIsMutatedByRecordingATransaction() {
        Fixture fx = new Fixture();
        Account originalAccount =
                new Account(0L, "Checking", AccountType.BANK, Currency.INR, new BigDecimal("1000.00"));
        Account savedAccount = fx.accountRepository.save(originalAccount);
        Category originalCategory = new Category(0L, "Salary", CategoryType.INCOME);
        Category savedCategory = fx.categoryRepository.save(originalCategory);

        fx.service.recordIncome(savedAccount.getId(), new BigDecimal("100.00"), savedCategory.getId(), DATE, null);

        // The caller's original, pre-save instances are exactly as they were.
        assertEquals(0L, originalAccount.getId());
        assertEquals(0, new BigDecimal("1000.00").compareTo(originalAccount.getOpeningBalance()));
        assertEquals(0L, originalCategory.getId());
        assertEquals(CategoryType.INCOME, originalCategory.getType());

        // The persisted instances the repositories hold are also unchanged by the service call.
        Account reloadedAccount = fx.accountRepository.findById(savedAccount.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("1000.00").compareTo(reloadedAccount.getOpeningBalance()));
        Category reloadedCategory = fx.categoryRepository.findById(savedCategory.getId()).orElseThrow();
        assertEquals(CategoryType.INCOME, reloadedCategory.getType());
    }

    // ===================== recordTransfer =====================

    // ---- A: two transactions created, every field correct ----

    @Test
    void recordTransferCreatesTwoTransactions() {
        Fixture fx = new Fixture();
        long sourceId = fx.newAccountId();
        long destinationId = fx.newAccountId();

        List<Transaction> legs = fx.service.recordTransfer(
                sourceId, destinationId, new BigDecimal("300.00"), DATE, "Rent");

        assertEquals(2, legs.size());
        Transaction source = legs.get(0);
        Transaction destination = legs.get(1);

        assertEquals(sourceId, source.getAccountId());
        assertEquals(destinationId, destination.getAccountId());
        assertEquals(TransactionType.TRANSFER, source.getType());
        assertEquals(TransactionType.TRANSFER, destination.getType());
        assertEquals(0, new BigDecimal("300.00").compareTo(source.getAmount()));
        assertEquals(0, new BigDecimal("300.00").compareTo(destination.getAmount()));
        assertEquals(DATE, source.getDate());
        assertEquals(DATE, destination.getDate());
        assertEquals(Optional.of("Rent"), source.getDescription());
        assertEquals(Optional.of("Rent"), destination.getDescription());
        assertTrue(source.getCategoryId().isEmpty());
        assertTrue(destination.getCategoryId().isEmpty());
        assertTrue(source.getTransferGroupId().isPresent());
        assertTrue(destination.getTransferGroupId().isPresent());
        assertEquals(source.getTransferGroupId(), destination.getTransferGroupId());
    }

    // ---- B: the grouping strategy is exactly "the source leg's own generated id" ----

    @Test
    void generatedTransferGroupIdIsDerivedFromPersistedTransactionIdentity() {
        Fixture fx = new Fixture();
        long sourceId = fx.newAccountId();
        long destinationId = fx.newAccountId();

        List<Transaction> legs = fx.service.recordTransfer(
                sourceId, destinationId, new BigDecimal("50.00"), DATE, null);
        Transaction source = legs.get(0);

        assertEquals(Optional.of(source.getId()), source.getTransferGroupId(),
                "the source leg's own generated id must be the shared transferGroupId");
    }

    // ---- C: source and destination must differ ----

    @Test
    void sourceAndDestinationMustDiffer() {
        Fixture fx = new Fixture();
        long accountId = fx.newAccountId();

        assertThrows(ApplicationValidationException.class,
                () -> fx.service.recordTransfer(accountId, accountId, new BigDecimal("10.00"), DATE, null));
        assertTrue(fx.transactionRepository.findAll().isEmpty());
    }

    // ---- D/E: both accounts must exist ----

    @Test
    void sourceAccountMustExist() {
        Fixture fx = new Fixture();
        long destinationId = fx.newAccountId();

        assertThrows(ApplicationValidationException.class,
                () -> fx.service.recordTransfer(999_999L, destinationId, new BigDecimal("10.00"), DATE, null));
        assertTrue(fx.transactionRepository.findAll().isEmpty());
    }

    @Test
    void destinationAccountMustExist() {
        Fixture fx = new Fixture();
        long sourceId = fx.newAccountId();

        assertThrows(ApplicationValidationException.class,
                () -> fx.service.recordTransfer(sourceId, 999_999L, new BigDecimal("10.00"), DATE, null));
        assertTrue(fx.transactionRepository.findAll().isEmpty());
    }

    // ---- F: amount must be positive ----

    @Test
    void transferAmountMustBePositive() {
        Fixture fx = new Fixture();
        long sourceId = fx.newAccountId();
        long destinationId = fx.newAccountId();

        assertThrows(IllegalArgumentException.class,
                () -> fx.service.recordTransfer(sourceId, destinationId, BigDecimal.ZERO, DATE, null));
        assertThrows(IllegalArgumentException.class,
                () -> fx.service.recordTransfer(sourceId, destinationId, new BigDecimal("-5.00"), DATE, null));
        assertTrue(fx.transactionRepository.findAll().isEmpty());
    }

    // ---- G: null date rejected ----

    @Test
    void transferNullDateIsRejected() {
        Fixture fx = new Fixture();
        long sourceId = fx.newAccountId();
        long destinationId = fx.newAccountId();

        assertThrows(NullPointerException.class,
                () -> fx.service.recordTransfer(sourceId, destinationId, new BigDecimal("10.00"), null, null));
    }

    // ---- H: description is optional ----

    @Test
    void transferDescriptionIsOptional() {
        Fixture fx = new Fixture();
        long sourceId = fx.newAccountId();
        long destinationId = fx.newAccountId();

        List<Transaction> withDescription =
                fx.service.recordTransfer(sourceId, destinationId, new BigDecimal("10.00"), DATE, "Trip fund");
        List<Transaction> withoutDescription =
                fx.service.recordTransfer(sourceId, destinationId, new BigDecimal("10.00"), DATE, null);

        assertEquals(Optional.of("Trip fund"), withDescription.get(0).getDescription());
        assertEquals(Optional.of("Trip fund"), withDescription.get(1).getDescription());
        assertTrue(withoutDescription.get(0).getDescription().isEmpty());
        assertTrue(withoutDescription.get(1).getDescription().isEmpty());
    }

    // ---- I: never INCOME or EXPENSE ----

    @Test
    void transferDoesNotCreateIncomeOrExpense() {
        Fixture fx = new Fixture();
        long sourceId = fx.newAccountId();
        long destinationId = fx.newAccountId();

        List<Transaction> legs = fx.service.recordTransfer(
                sourceId, destinationId, new BigDecimal("10.00"), DATE, null);

        for (Transaction leg : legs) {
            assertEquals(TransactionType.TRANSFER, leg.getType());
        }
    }

    // ---- J: no category ----

    @Test
    void transferHasNoCategory() {
        Fixture fx = new Fixture();
        long sourceId = fx.newAccountId();
        long destinationId = fx.newAccountId();

        List<Transaction> legs = fx.service.recordTransfer(
                sourceId, destinationId, new BigDecimal("10.00"), DATE, null);

        for (Transaction leg : legs) {
            assertTrue(leg.getCategoryId().isEmpty());
        }
    }

    // ---- K: exactly two rows persisted ----

    @Test
    void exactlyTwoTransactionRecordsAreCreated() {
        Fixture fx = new Fixture();
        long sourceId = fx.newAccountId();
        long destinationId = fx.newAccountId();

        fx.service.recordTransfer(sourceId, destinationId, new BigDecimal("10.00"), DATE, null);

        assertEquals(2, fx.transactionRepository.findAll().size());
    }

    // ---- L: a RepositoryException on the second write rolls back the first ----

    @Test
    void repositoryFailureOnTheSecondWriteRollsBackTheFirst() {
        InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
        long sourceId = accountRepository.save(
                new Account(0L, "Source", AccountType.BANK, Currency.INR, new BigDecimal("1000.00"))).getId();
        long destinationId = accountRepository.save(
                new Account(0L, "Destination", AccountType.BANK, Currency.INR, BigDecimal.ZERO)).getId();
        InMemoryTransactionRepository backing = new InMemoryTransactionRepository();
        FailOnNthSaveTransactionRepository failing = new FailOnNthSaveTransactionRepository(
                backing, 2, new RepositoryException("simulated failure on second write"));
        TransactionApplicationService service = new TransactionApplicationService(
                accountRepository, new InMemoryCategoryRepository(), failing,
                new RollingBackTransactionRunner(backing));

        assertThrows(RepositoryException.class,
                () -> service.recordTransfer(sourceId, destinationId, new BigDecimal("100.00"), DATE, null));

        assertTrue(backing.findAll().isEmpty(),
                "the source leg's earlier successful write must be rolled back along with the failure");
    }

    // ---- M: a plain RuntimeException failure also rolls back ----

    @Test
    void runtimeFailureAfterTheFirstWriteRollsBack() {
        InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
        long sourceId = accountRepository.save(
                new Account(0L, "Source", AccountType.BANK, Currency.INR, new BigDecimal("1000.00"))).getId();
        long destinationId = accountRepository.save(
                new Account(0L, "Destination", AccountType.BANK, Currency.INR, BigDecimal.ZERO)).getId();
        InMemoryTransactionRepository backing = new InMemoryTransactionRepository();
        FailOnNthSaveTransactionRepository failing = new FailOnNthSaveTransactionRepository(
                backing, 2, new IllegalStateException("simulated runtime failure"));
        TransactionApplicationService service = new TransactionApplicationService(
                accountRepository, new InMemoryCategoryRepository(), failing,
                new RollingBackTransactionRunner(backing));

        assertThrows(IllegalStateException.class,
                () -> service.recordTransfer(sourceId, destinationId, new BigDecimal("100.00"), DATE, null));

        assertTrue(backing.findAll().isEmpty(),
                "a plain RuntimeException must roll back exactly like a RepositoryException");
    }

    // ---- N: a successful transfer commits both rows ----

    @Test
    void successfulTransferCommitsBothRows() {
        InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
        long sourceId = accountRepository.save(
                new Account(0L, "Source", AccountType.BANK, Currency.INR, new BigDecimal("1000.00"))).getId();
        long destinationId = accountRepository.save(
                new Account(0L, "Destination", AccountType.BANK, Currency.INR, BigDecimal.ZERO)).getId();
        InMemoryTransactionRepository backing = new InMemoryTransactionRepository();
        TransactionApplicationService service = new TransactionApplicationService(
                accountRepository, new InMemoryCategoryRepository(), backing,
                new RollingBackTransactionRunner(backing));

        service.recordTransfer(sourceId, destinationId, new BigDecimal("100.00"), DATE, null);

        assertEquals(2, backing.findAll().size());
    }
}
