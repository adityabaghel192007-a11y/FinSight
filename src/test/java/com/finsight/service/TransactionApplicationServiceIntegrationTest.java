package com.finsight.service;

import com.finsight.domain.account.Account;
import com.finsight.domain.account.AccountType;
import com.finsight.domain.account.Currency;
import com.finsight.domain.category.Category;
import com.finsight.domain.category.CategoryType;
import com.finsight.domain.transaction.Transaction;
import com.finsight.domain.transaction.TransactionType;
import com.finsight.repository.RepositoryException;
import com.finsight.repository.TransactionRepository;
import com.finsight.repository.jdbc.AccountRepositoryJdbc;
import com.finsight.repository.jdbc.CategoryRepositoryJdbc;
import com.finsight.repository.jdbc.SchemaInitializer;
import com.finsight.repository.jdbc.SqliteConnectionProvider;
import com.finsight.repository.jdbc.TransactionRepositoryJdbc;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves {@link TransactionApplicationService} actually integrates with the real persistence
 * layer — {@link AccountRepositoryJdbc}, {@link CategoryRepositoryJdbc}, and
 * {@link TransactionRepositoryJdbc} against a real (isolated, in-memory) SQLite database — rather
 * than only against the in-memory fakes in {@link TransactionApplicationServiceTest}. The service
 * itself remains JDBC-independent; this test only exercises it through JDBC-backed repositories.
 * {@link SqliteConnectionProvider} implements {@link com.finsight.repository.TransactionRunner}
 * directly, so it is passed to the service as both the connection provider repositories are built
 * on and the transaction-boundary dependency, with no separate adapter needed.
 */
class TransactionApplicationServiceIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2026, 3, 1);

    private static final class TestDatabase implements AutoCloseable {
        private final SqliteConnectionProvider connectionProvider;
        private final AccountRepositoryJdbc accountRepository;
        private final CategoryRepositoryJdbc categoryRepository;
        private final TransactionRepositoryJdbc transactionRepository;
        private final TransactionApplicationService service;

        private TestDatabase() {
            this.connectionProvider = new SqliteConnectionProvider("jdbc:sqlite::memory:");
            SchemaInitializer.initialize(connectionProvider.getConnection());
            this.accountRepository = new AccountRepositoryJdbc(connectionProvider);
            this.categoryRepository = new CategoryRepositoryJdbc(connectionProvider);
            this.transactionRepository = new TransactionRepositoryJdbc(connectionProvider);
            this.service = new TransactionApplicationService(
                    accountRepository, categoryRepository, transactionRepository, connectionProvider);
        }

        @Override
        public void close() {
            connectionProvider.close();
        }
    }

    /**
     * Wraps a real {@link TransactionRepositoryJdbc}, delegating every call except {@link #save},
     * which fails on its {@code failOnCallNumber}-th invocation (1-based) with {@code failure} —
     * the smallest seam needed to deterministically fail the second write of a real, two-write
     * {@code recordTransfer} call without weakening {@link TransactionApplicationService} or
     * {@link TransactionRepositoryJdbc} themselves. Reads always go straight to the real
     * repository, so what the test observes afterward (via a fresh, undecorated
     * {@link TransactionRepositoryJdbc} in each test) reflects exactly what SQLite itself
     * committed or rolled back.
     */
    private static final class FailOnNthSaveTransactionRepository implements TransactionRepository {
        private final TransactionRepositoryJdbc delegate;
        private final int failOnCallNumber;
        private int saveCallCount = 0;

        private FailOnNthSaveTransactionRepository(TransactionRepositoryJdbc delegate, int failOnCallNumber) {
            this.delegate = delegate;
            this.failOnCallNumber = failOnCallNumber;
        }

        @Override
        public Transaction save(Transaction transaction) {
            saveCallCount++;
            if (saveCallCount == failOnCallNumber) {
                throw new RepositoryException("simulated failure on write number " + failOnCallNumber);
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

    @Test
    void recordIncomeEndToEndPersistsAndIsRetrievableThroughTheRealRepositories() {
        try (TestDatabase db = new TestDatabase()) {
            Account account = db.accountRepository.save(
                    new Account(0L, "Checking", AccountType.BANK, Currency.INR, new BigDecimal("500.00")));
            Category salary = db.categoryRepository.save(new Category(0L, "Salary", CategoryType.INCOME));

            Transaction result = db.service.recordIncome(
                    account.getId(), new BigDecimal("2500.00"), salary.getId(), DATE, "Monthly salary");

            Optional<Transaction> byId = db.transactionRepository.findById(result.getId());
            assertTrue(byId.isPresent());
            Transaction retrieved = byId.orElseThrow();
            assertEquals(account.getId(), retrieved.getAccountId());
            assertEquals(TransactionType.INCOME, retrieved.getType());
            assertEquals(0, new BigDecimal("2500.00").compareTo(retrieved.getAmount()));
            assertEquals(Optional.of(salary.getId()), retrieved.getCategoryId());
            assertEquals(DATE, retrieved.getDate());
            assertEquals(Optional.of("Monthly salary"), retrieved.getDescription());
            assertTrue(retrieved.getTransferGroupId().isEmpty());

            List<Transaction> forAccount = db.transactionRepository.findByAccountId(account.getId());
            assertEquals(1, forAccount.size());
            assertEquals(result.getId(), forAccount.get(0).getId());
        }
    }

    @Test
    void recordExpenseEndToEndPersistsAndIsRetrievableThroughTheRealRepositories() {
        try (TestDatabase db = new TestDatabase()) {
            Account account = db.accountRepository.save(
                    new Account(0L, "Checking", AccountType.BANK, Currency.INR, new BigDecimal("500.00")));
            Category groceries = db.categoryRepository.save(new Category(0L, "Groceries", CategoryType.EXPENSE));

            Transaction result = db.service.recordExpense(
                    account.getId(), new BigDecimal("64.75"), groceries.getId(), DATE, "Weekly shop");

            Transaction retrieved = db.transactionRepository.findById(result.getId()).orElseThrow();
            assertEquals(account.getId(), retrieved.getAccountId());
            assertEquals(TransactionType.EXPENSE, retrieved.getType());
            assertEquals(0, new BigDecimal("64.75").compareTo(retrieved.getAmount()));
            assertEquals(Optional.of(groceries.getId()), retrieved.getCategoryId());
            assertEquals(Optional.of("Weekly shop"), retrieved.getDescription());
            assertTrue(retrieved.getTransferGroupId().isEmpty());

            List<Transaction> forAccount = db.transactionRepository.findByAccountId(account.getId());
            assertEquals(1, forAccount.size());
        }
    }

    @Test
    void wrongCategoryTypeIsRejectedEvenAgainstTheRealRepositories() {
        try (TestDatabase db = new TestDatabase()) {
            Account account = db.accountRepository.save(
                    new Account(0L, "Checking", AccountType.BANK, Currency.INR, BigDecimal.ZERO));
            Category groceries = db.categoryRepository.save(new Category(0L, "Groceries", CategoryType.EXPENSE));

            assertThrows(ApplicationValidationException.class,
                    () -> db.service.recordIncome(account.getId(), new BigDecimal("10.00"), groceries.getId(), DATE, null));
            assertTrue(db.transactionRepository.findAll().isEmpty(),
                    "the rejected attempt must not have persisted anything");
        }
    }

    // ===================== recordTransfer: real SQLite atomicity =====================

    @Test
    void successfulTransferEndToEndPersistsBothLegsWithASharedTransferGroupId() {
        try (TestDatabase db = new TestDatabase()) {
            Account source = db.accountRepository.save(
                    new Account(0L, "Source", AccountType.BANK, Currency.INR, new BigDecimal("1000.00")));
            Account destination = db.accountRepository.save(
                    new Account(0L, "Destination", AccountType.BANK, Currency.INR, BigDecimal.ZERO));

            List<Transaction> legs = db.service.recordTransfer(
                    source.getId(), destination.getId(), new BigDecimal("400.00"), DATE, "Move to savings");

            assertEquals(2, legs.size());
            assertEquals(2, db.transactionRepository.findAll().size());

            Transaction sourceLeg = db.transactionRepository.findById(legs.get(0).getId()).orElseThrow();
            Transaction destinationLeg = db.transactionRepository.findById(legs.get(1).getId()).orElseThrow();

            assertEquals(source.getId(), sourceLeg.getAccountId());
            assertEquals(destination.getId(), destinationLeg.getAccountId());
            assertEquals(TransactionType.TRANSFER, sourceLeg.getType());
            assertEquals(TransactionType.TRANSFER, destinationLeg.getType());
            assertEquals(0, new BigDecimal("400.00").compareTo(sourceLeg.getAmount()));
            assertEquals(0, new BigDecimal("400.00").compareTo(destinationLeg.getAmount()));
            assertEquals(Optional.of("Move to savings"), sourceLeg.getDescription());
            assertEquals(Optional.of("Move to savings"), destinationLeg.getDescription());
            assertTrue(sourceLeg.getCategoryId().isEmpty());
            assertTrue(destinationLeg.getCategoryId().isEmpty());
            assertTrue(sourceLeg.getTransferGroupId().isPresent());
            assertEquals(sourceLeg.getTransferGroupId(), destinationLeg.getTransferGroupId());
            assertEquals(Optional.of(sourceLeg.getId()), sourceLeg.getTransferGroupId());
        }
    }

    /**
     * The most important test in this milestone: proves the real
     * {@code SqliteConnectionProvider.executeInTransaction} boundary actually rolls back a
     * genuine SQLite write, not just an in-memory fake. {@link FailOnNthSaveTransactionRepository}
     * lets the source leg's insert succeed for real before the destination leg's insert fails,
     * so a passing test here means SQLite itself undid a write it had already made — the entire
     * reason Milestone 4A's transaction boundary exists.
     */
    @Test
    void failedSecondWriteRollsBackBothLegsInRealSqlite() {
        try (TestDatabase db = new TestDatabase()) {
            Account source = db.accountRepository.save(
                    new Account(0L, "Source", AccountType.BANK, Currency.INR, new BigDecimal("1000.00")));
            Account destination = db.accountRepository.save(
                    new Account(0L, "Destination", AccountType.BANK, Currency.INR, BigDecimal.ZERO));

            FailOnNthSaveTransactionRepository failing =
                    new FailOnNthSaveTransactionRepository(db.transactionRepository, 2);
            TransactionApplicationService failingService = new TransactionApplicationService(
                    db.accountRepository, db.categoryRepository, failing, db.connectionProvider);

            assertThrows(RepositoryException.class, () -> failingService.recordTransfer(
                    source.getId(), destination.getId(), new BigDecimal("250.00"), DATE, null));

            // Read back through the plain, undecorated repository: this reflects exactly what
            // SQLite committed, independent of the failing decorator used only to trigger this.
            assertTrue(db.transactionRepository.findByAccountId(source.getId()).isEmpty(),
                    "the source leg's earlier successful write must have been rolled back by SQLite");
            assertTrue(db.transactionRepository.findByAccountId(destination.getId()).isEmpty());
            assertTrue(db.transactionRepository.findAll().isEmpty(),
                    "no transaction row should remain after the failed atomic transfer");
        }
    }
}
