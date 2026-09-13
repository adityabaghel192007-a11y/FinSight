package com.finsight.repository.jdbc;

import com.finsight.domain.account.Account;
import com.finsight.domain.account.AccountType;
import com.finsight.domain.account.Currency;
import com.finsight.domain.transaction.Transaction;
import com.finsight.domain.transaction.TransactionType;
import com.finsight.repository.RepositoryException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link SqliteConnectionProvider#executeInTransaction(Runnable)}, against a
 * real (but isolated, in-memory) SQLite database — the same style used by every
 * {@code *RepositoryJdbcTest} in this package.
 *
 * <p>These tests deliberately exercise the transaction boundary through real repositories
 * ({@link AccountRepositoryJdbc}, and {@link TransactionRepositoryJdbc} for the foreign-key case)
 * rather than raw SQL, since that is exactly how this mechanism is meant to be used: multiple
 * repository calls, sharing one {@link SqliteConnectionProvider}, wrapped as one atomic unit.
 */
class SqliteConnectionProviderTest {

    private static final class TestDatabase implements AutoCloseable {
        private final SqliteConnectionProvider connectionProvider;
        private final AccountRepositoryJdbc accountRepository;
        private final TransactionRepositoryJdbc transactionRepository;

        private TestDatabase() {
            this.connectionProvider = new SqliteConnectionProvider("jdbc:sqlite::memory:");
            SchemaInitializer.initialize(connectionProvider.getConnection());
            this.accountRepository = new AccountRepositoryJdbc(connectionProvider);
            this.transactionRepository = new TransactionRepositoryJdbc(connectionProvider);
        }

        @Override
        public void close() {
            connectionProvider.close();
        }
    }

    private static TestDatabase newDatabase() {
        return new TestDatabase();
    }

    private static Account transientAccount(String name) {
        // id is a placeholder — save() ignores it and lets SQLite generate the real one.
        return new Account(0L, name, AccountType.BANK, Currency.INR, new BigDecimal("100.00"));
    }

    /** A deliberately distinct exception type, so a test can assert the exact instance propagates. */
    private static final class DeliberateFailure extends RuntimeException {
        private DeliberateFailure(String message) {
            super(message);
        }
    }

    // ===================== 1. successful transaction commits =====================

    @Test
    void successfulTransactionCommitsAllWrites() {
        try (TestDatabase db = newDatabase()) {
            db.connectionProvider.executeInTransaction(() -> {
                db.accountRepository.save(transientAccount("Checking"));
                db.accountRepository.save(transientAccount("Savings"));
            });

            List<Account> accounts = db.accountRepository.findAll();
            assertEquals(2, accounts.size(), "both writes made inside the transaction should be committed");
        }
    }

    // ============ 2. failure rolls back an earlier successful write in the same transaction ============

    @Test
    void failureRollsBackAnEarlierSuccessfulWriteInTheSameTransaction() {
        try (TestDatabase db = newDatabase()) {
            assertThrows(DeliberateFailure.class, () -> db.connectionProvider.executeInTransaction(() -> {
                db.accountRepository.save(transientAccount("Checking")); // succeeds first...
                throw new DeliberateFailure("something went wrong after the first write");
            }));

            assertTrue(db.accountRepository.findAll().isEmpty(),
                    "the earlier successful write must be rolled back along with the failure");
        }
    }

    // ===================== 3. connection remains usable after rollback =====================

    @Test
    void connectionRemainsUsableAfterRollback() {
        try (TestDatabase db = newDatabase()) {
            assertThrows(DeliberateFailure.class, () -> db.connectionProvider.executeInTransaction(() -> {
                db.accountRepository.save(transientAccount("Checking"));
                throw new DeliberateFailure("boom");
            }));

            // The connection must still work normally for an unrelated, non-transactional operation.
            Account saved = db.accountRepository.save(transientAccount("Wallet"));
            assertTrue(saved.getId() > 0);
            assertEquals(1, db.accountRepository.findAll().size());
        }
    }

    // ================ 4. normal repository operations still work after a transaction ================

    @Test
    void normalRepositoryOperationsStillWorkAfterASuccessfulTransaction() {
        try (TestDatabase db = newDatabase()) {
            db.connectionProvider.executeInTransaction(() -> db.accountRepository.save(transientAccount("Checking")));

            // Plain, non-transactional repository calls after a committed transaction behave normally.
            Account saved = db.accountRepository.save(transientAccount("Savings"));
            Optional<Account> found = db.accountRepository.findById(saved.getId());

            assertTrue(found.isPresent());
            assertEquals(2, db.accountRepository.findAll().size());
        }
    }

    // ===================== 5. transaction state is restored correctly =====================

    @Test
    void autoCommitIsRestoredAfterASuccessfulTransaction() throws SQLException {
        try (TestDatabase db = newDatabase()) {
            assertTrue(db.connectionProvider.getConnection().getAutoCommit(),
                    "auto-commit should be enabled before any transaction runs");

            db.connectionProvider.executeInTransaction(() -> db.accountRepository.save(transientAccount("Checking")));

            assertTrue(db.connectionProvider.getConnection().getAutoCommit(),
                    "auto-commit should be restored to enabled after a committed transaction");
        }
    }

    @Test
    void autoCommitIsRestoredAfterAFailedTransaction() throws SQLException {
        try (TestDatabase db = newDatabase()) {
            assertThrows(DeliberateFailure.class, () -> db.connectionProvider.executeInTransaction(() -> {
                throw new DeliberateFailure("boom");
            }));

            assertTrue(db.connectionProvider.getConnection().getAutoCommit(),
                    "auto-commit should be restored to enabled even after a rolled-back transaction");
        }
    }

    // ===================== 6. foreign key enforcement remains enabled =====================

    @Test
    void foreignKeyEnforcementRemainsEnabledInsideATransaction() {
        try (TestDatabase db = newDatabase()) {
            Transaction withInvalidAccount = new Transaction(0L, 999_999L, new BigDecimal("50.00"),
                    TransactionType.EXPENSE, null, LocalDate.of(2026, 1, 1), null, null);

            // A foreign-key violation (accountId referencing no row) surfaces the same way inside a
            // transaction as it does outside one: as a RepositoryException from the repository call.
            assertThrows(RepositoryException.class,
                    () -> db.connectionProvider.executeInTransaction(() -> db.transactionRepository.save(withInvalidAccount)));

            assertTrue(db.transactionRepository.findAll().isEmpty(),
                    "the rejected insert should not have been persisted");

            // Foreign key enforcement is still active afterward too, outside any transaction.
            assertThrows(RepositoryException.class, () -> db.transactionRepository.save(withInvalidAccount));
        }
    }

    // =============== 7. appropriate exception propagation/wrapping ===============

    @Test
    void aRuntimeExceptionFromWorkPropagatesUnchanged() {
        try (TestDatabase db = newDatabase()) {
            DeliberateFailure original = new DeliberateFailure("the exact instance must propagate");

            DeliberateFailure thrown = assertThrows(DeliberateFailure.class,
                    () -> db.connectionProvider.executeInTransaction(() -> {
                        throw original;
                    }));

            assertSame(original, thrown,
                    "a failure thrown by work already has a meaningful type and must not be re-wrapped");
        }
    }

    @Test
    void aRepositoryExceptionFromAFailedWriteInsideWorkPropagatesUnchanged() {
        try (TestDatabase db = newDatabase()) {
            Transaction withInvalidAccount = new Transaction(0L, 999_999L, new BigDecimal("50.00"),
                    TransactionType.EXPENSE, null, LocalDate.of(2026, 1, 1), null, null);

            // The RepositoryException a repository already throws for a failed write is exactly what
            // escapes executeInTransaction — not a RepositoryException wrapping a RepositoryException.
            RepositoryException thrown = assertThrows(RepositoryException.class,
                    () -> db.connectionProvider.executeInTransaction(() -> db.transactionRepository.save(withInvalidAccount)));

            assertFalse(thrown.getCause() instanceof RepositoryException,
                    "the failure should not be wrapped a second time");
        }
    }

    // =============== 8. nested transaction behavior is explicit and tested ===============

    @Test
    void nestedTransactionInvocationIsRejected() {
        try (TestDatabase db = newDatabase()) {
            assertThrows(IllegalStateException.class, () -> db.connectionProvider.executeInTransaction(
                    () -> db.connectionProvider.executeInTransaction(() -> { })));
        }
    }

    @Test
    void aRejectedNestedTransactionRollsBackTheOuterTransactionAndLeavesTheConnectionUsable() throws SQLException {
        try (TestDatabase db = newDatabase()) {
            assertThrows(IllegalStateException.class, () -> db.connectionProvider.executeInTransaction(() -> {
                db.accountRepository.save(transientAccount("Checking")); // succeeds first...
                db.connectionProvider.executeInTransaction(() -> { }); // ...then the nested call is rejected
            }));

            assertTrue(db.accountRepository.findAll().isEmpty(),
                    "the outer transaction's earlier write must be rolled back too");
            assertTrue(db.connectionProvider.getConnection().getAutoCommit(),
                    "auto-commit should be restored even after a rejected nested transaction");

            // The connection is left perfectly usable — including for a fresh, successful transaction.
            db.connectionProvider.executeInTransaction(() -> db.accountRepository.save(transientAccount("Wallet")));
            assertEquals(1, db.accountRepository.findAll().size());
        }
    }
}
