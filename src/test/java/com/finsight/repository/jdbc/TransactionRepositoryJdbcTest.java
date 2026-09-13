package com.finsight.repository.jdbc;

import com.finsight.domain.account.Account;
import com.finsight.domain.account.AccountType;
import com.finsight.domain.account.Currency;
import com.finsight.domain.category.Category;
import com.finsight.domain.category.CategoryType;
import com.finsight.domain.transaction.Transaction;
import com.finsight.domain.transaction.TransactionType;
import com.finsight.repository.RepositoryException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link TransactionRepositoryJdbc} against a real (but isolated,
 * in-memory) SQLite database — mirroring {@link AccountRepositoryJdbcTest} and
 * {@link CategoryRepositoryJdbcTest}'s structure, plus the nullable-column and foreign-key
 * behavior specific to {@code Transaction}.
 *
 * <p>Every test opens its own fresh {@code jdbc:sqlite::memory:} database via
 * {@link #newRepository()} and initializes the schema on it; nothing here ever touches a file on
 * disk or a database another test (or the real application) might be using.
 */
class TransactionRepositoryJdbcTest {

    private static final LocalDate DATE = LocalDate.of(2026, 1, 15);

    private static final class TestDatabase implements AutoCloseable {
        private final SqliteConnectionProvider connectionProvider;
        private final TransactionRepositoryJdbc repository;
        private final AccountRepositoryJdbc accountRepository;
        private final CategoryRepositoryJdbc categoryRepository;

        private TestDatabase() {
            this.connectionProvider = new SqliteConnectionProvider("jdbc:sqlite::memory:");
            SchemaInitializer.initialize(connectionProvider.getConnection());
            this.repository = new TransactionRepositoryJdbc(connectionProvider);
            this.accountRepository = new AccountRepositoryJdbc(connectionProvider);
            this.categoryRepository = new CategoryRepositoryJdbc(connectionProvider);
        }

        private long newAccountId() {
            return accountRepository.save(
                    new Account(0L, "Checking", AccountType.BANK, Currency.INR, BigDecimal.ZERO)).getId();
        }

        private long newCategoryId() {
            return categoryRepository.save(new Category(0L, "Groceries", CategoryType.EXPENSE)).getId();
        }

        @Override
        public void close() {
            connectionProvider.close();
        }
    }

    private static TestDatabase newRepository() {
        return new TestDatabase();
    }

    // ===================== CREATE =====================

    @Test
    void savingANewTransactionGeneratesAnId() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            Transaction saved = db.repository.save(new Transaction(
                    0L, accountId, new BigDecimal("50.00"), TransactionType.EXPENSE, null, DATE, null, null));

            assertTrue(saved.getId() > 0, "generated id should be a positive, valid row id");
        }
    }

    @Test
    void savingReturnsANewInstanceAndDoesNotMutateTheOriginal() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            Transaction original = new Transaction(
                    0L, accountId, new BigDecimal("50.00"), TransactionType.EXPENSE, null, DATE, null, null);

            Transaction saved = db.repository.save(original);

            assertEquals(0L, original.getId());
            assertTrue(saved.getId() != original.getId());
        }
    }

    @Test
    void savingMultipleTransactionsProducesDistinctIds() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            Transaction first = db.repository.save(new Transaction(
                    0L, accountId, new BigDecimal("10.00"), TransactionType.EXPENSE, null, DATE, null, null));
            Transaction second = db.repository.save(new Transaction(
                    0L, accountId, new BigDecimal("20.00"), TransactionType.INCOME, null, DATE, null, null));
            Transaction third = db.repository.save(new Transaction(
                    0L, accountId, new BigDecimal("30.00"), TransactionType.EXPENSE, null, DATE, null, null));

            Set<Long> ids = new HashSet<>(List.of(first.getId(), second.getId(), third.getId()));
            assertEquals(3, ids.size(), "every saved transaction should receive a distinct id");
        }
    }

    // ===================== ROUND-TRIP: FULL FIELD SET =====================

    @Test
    void transactionWithAllOptionalFieldsPresentRoundTripsEveryField() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            long categoryId = db.newCategoryId();
            Transaction saved = db.repository.save(new Transaction(
                    0L, accountId, new BigDecimal("123.45"), TransactionType.EXPENSE,
                    categoryId, DATE, "Weekly shop", 7L));

            Transaction retrieved = db.repository.findById(saved.getId()).orElseThrow();

            assertEquals(accountId, retrieved.getAccountId());
            assertEquals(0, new BigDecimal("123.45").compareTo(retrieved.getAmount()));
            assertEquals(TransactionType.EXPENSE, retrieved.getType());
            assertEquals(DATE, retrieved.getDate());
            assertEquals(Optional.of("Weekly shop"), retrieved.getDescription());
            assertEquals(Optional.of(categoryId), retrieved.getCategoryId());
            assertEquals(Optional.of(7L), retrieved.getTransferGroupId());
        }
    }

    @Test
    void transactionWithNoCategoryRoundTripsCategoryIdAsEmpty() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            Transaction saved = db.repository.save(new Transaction(
                    0L, accountId, new BigDecimal("10.00"), TransactionType.EXPENSE, null, DATE, "Cash tip", null));

            Transaction retrieved = db.repository.findById(saved.getId()).orElseThrow();

            assertTrue(retrieved.getCategoryId().isEmpty());
        }
    }

    @Test
    void transactionWithNoDescriptionRoundTripsDescriptionAsEmpty() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            long categoryId = db.newCategoryId();
            Transaction saved = db.repository.save(new Transaction(
                    0L, accountId, new BigDecimal("10.00"), TransactionType.EXPENSE, categoryId, DATE, null, null));

            Transaction retrieved = db.repository.findById(saved.getId()).orElseThrow();

            assertTrue(retrieved.getDescription().isEmpty());
        }
    }

    @Test
    void transactionWithNoTransferGroupIdRoundTripsAsEmpty() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            Transaction saved = db.repository.save(new Transaction(
                    0L, accountId, new BigDecimal("10.00"), TransactionType.EXPENSE, null, DATE, null, null));

            Transaction retrieved = db.repository.findById(saved.getId()).orElseThrow();

            assertTrue(retrieved.getTransferGroupId().isEmpty());
        }
    }

    @Test
    void transactionWithCategoryRoundTripsCategoryIdCorrectly() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            long categoryId = db.newCategoryId();
            Transaction saved = db.repository.save(new Transaction(
                    0L, accountId, new BigDecimal("10.00"), TransactionType.EXPENSE, categoryId, DATE, null, null));

            Transaction retrieved = db.repository.findById(saved.getId()).orElseThrow();

            assertEquals(Optional.of(categoryId), retrieved.getCategoryId());
        }
    }

    @Test
    void transactionWithDescriptionRoundTripsDescriptionCorrectly() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            Transaction saved = db.repository.save(new Transaction(
                    0L, accountId, new BigDecimal("10.00"), TransactionType.INCOME, null, DATE, "Freelance payment", null));

            Transaction retrieved = db.repository.findById(saved.getId()).orElseThrow();

            assertEquals(Optional.of("Freelance payment"), retrieved.getDescription());
        }
    }

    @Test
    void transferTransactionsSharingATransferGroupIdBothRoundTripItCorrectly() {
        try (TestDatabase db = newRepository()) {
            long fromAccount = db.newAccountId();
            long toAccount = db.newAccountId();
            long transferGroupId = 42L;

            Transaction legA = db.repository.save(new Transaction(
                    0L, fromAccount, new BigDecimal("20000"), TransactionType.TRANSFER, null, DATE, null, transferGroupId));
            Transaction legB = db.repository.save(new Transaction(
                    0L, toAccount, new BigDecimal("20000"), TransactionType.TRANSFER, null, DATE, null, transferGroupId));

            Transaction retrievedA = db.repository.findById(legA.getId()).orElseThrow();
            Transaction retrievedB = db.repository.findById(legB.getId()).orElseThrow();

            assertEquals(Optional.of(transferGroupId), retrievedA.getTransferGroupId());
            assertEquals(Optional.of(transferGroupId), retrievedB.getTransferGroupId());
            assertEquals(retrievedA.getTransferGroupId(), retrievedB.getTransferGroupId());
        }
    }

    @Test
    void bigDecimalAmountsRoundTripWithoutLosingPrecision() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            // Representative values that a double/float round-trip would silently corrupt.
            List<BigDecimal> amounts = List.of(
                    new BigDecimal("0.01"), new BigDecimal("19999.99"), new BigDecimal("100000.10"),
                    new BigDecimal("1234567.89"));

            for (BigDecimal amount : amounts) {
                Transaction saved = db.repository.save(new Transaction(
                        0L, accountId, amount, TransactionType.EXPENSE, null, DATE, null, null));

                Transaction retrieved = db.repository.findById(saved.getId()).orElseThrow();

                assertEquals(0, amount.compareTo(retrieved.getAmount()),
                        () -> amount + " did not round-trip exactly, got " + retrieved.getAmount());
            }
        }
    }

    @Test
    void localDateRoundTripsExactly() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            LocalDate date = LocalDate.of(2025, 12, 31);
            Transaction saved = db.repository.save(new Transaction(
                    0L, accountId, new BigDecimal("1.00"), TransactionType.INCOME, null, date, null, null));

            Transaction retrieved = db.repository.findById(saved.getId()).orElseThrow();

            assertEquals(date, retrieved.getDate());
        }
    }

    // ===================== READ =====================

    @Test
    void findByIdForAMissingIdReturnsEmpty() {
        try (TestDatabase db = newRepository()) {
            Optional<Transaction> result = db.repository.findById(999_999L);

            assertFalse(result.isPresent());
        }
    }

    @Test
    void findAllReturnsEveryPersistedTransaction() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            db.repository.save(new Transaction(
                    0L, accountId, new BigDecimal("10.00"), TransactionType.EXPENSE, null, DATE, null, null));
            db.repository.save(new Transaction(
                    0L, accountId, new BigDecimal("20.00"), TransactionType.INCOME, null, DATE, null, null));

            List<Transaction> transactions = db.repository.findAll();

            assertEquals(2, transactions.size());
        }
    }

    @Test
    void findAllOnAnEmptyDatabaseReturnsAnEmptyList() {
        try (TestDatabase db = newRepository()) {
            List<Transaction> transactions = db.repository.findAll();

            assertTrue(transactions.isEmpty());
        }
    }

    // ===================== FIND BY ACCOUNT ID =====================

    @Test
    void findByAccountIdReturnsOnlyMatchingTransactions() {
        try (TestDatabase db = newRepository()) {
            long accountA = db.newAccountId();
            long accountB = db.newAccountId();
            Transaction firstForA = db.repository.save(new Transaction(
                    0L, accountA, new BigDecimal("10.00"), TransactionType.EXPENSE, null, DATE, null, null));
            Transaction secondForA = db.repository.save(new Transaction(
                    0L, accountA, new BigDecimal("20.00"), TransactionType.INCOME, null, DATE, null, null));
            db.repository.save(new Transaction(
                    0L, accountB, new BigDecimal("30.00"), TransactionType.EXPENSE, null, DATE, null, null));

            List<Transaction> forA = db.repository.findByAccountId(accountA);

            assertEquals(2, forA.size());
            Set<Long> ids = new HashSet<>(List.of(forA.get(0).getId(), forA.get(1).getId()));
            assertEquals(Set.of(firstForA.getId(), secondForA.getId()), ids);
            for (Transaction transaction : forA) {
                assertEquals(accountA, transaction.getAccountId());
            }
        }
    }

    @Test
    void returnsEmptyListWhenAccountHasNoTransactions() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();

            List<Transaction> result = db.repository.findByAccountId(accountId);

            assertTrue(result.isEmpty());
        }
    }

    @Test
    void resultsAreDeterministicallyOrderedByAscendingId() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            Transaction first = db.repository.save(new Transaction(
                    0L, accountId, new BigDecimal("10.00"), TransactionType.EXPENSE, null, DATE, null, null));
            Transaction second = db.repository.save(new Transaction(
                    0L, accountId, new BigDecimal("20.00"), TransactionType.EXPENSE, null, DATE, null, null));
            Transaction third = db.repository.save(new Transaction(
                    0L, accountId, new BigDecimal("30.00"), TransactionType.EXPENSE, null, DATE, null, null));

            List<Transaction> result = db.repository.findByAccountId(accountId);

            assertEquals(List.of(first.getId(), second.getId(), third.getId()),
                    List.of(result.get(0).getId(), result.get(1).getId(), result.get(2).getId()));
        }
    }

    @Test
    void preservesAllTransactionFields() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            long categoryId = db.newCategoryId();
            Transaction saved = db.repository.save(new Transaction(
                    0L, accountId, new BigDecimal("123.45"), TransactionType.EXPENSE,
                    categoryId, DATE, "Weekly shop", 7L));

            Transaction retrieved = db.repository.findByAccountId(accountId).get(0);

            assertEquals(saved.getId(), retrieved.getId());
            assertEquals(accountId, retrieved.getAccountId());
            assertEquals(0, new BigDecimal("123.45").compareTo(retrieved.getAmount()));
            assertEquals(TransactionType.EXPENSE, retrieved.getType());
            assertEquals(Optional.of(categoryId), retrieved.getCategoryId());
            assertEquals(DATE, retrieved.getDate());
            assertEquals(Optional.of("Weekly shop"), retrieved.getDescription());
            assertEquals(Optional.of(7L), retrieved.getTransferGroupId());
        }
    }

    @Test
    void handlesNullableFields() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            db.repository.save(new Transaction(
                    0L, accountId, new BigDecimal("10.00"), TransactionType.EXPENSE, null, DATE, null, null));

            Transaction retrieved = db.repository.findByAccountId(accountId).get(0);

            assertTrue(retrieved.getCategoryId().isEmpty());
            assertTrue(retrieved.getDescription().isEmpty());
            assertTrue(retrieved.getTransferGroupId().isEmpty());
        }
    }

    @Test
    void supportsIncomeExpenseAndTransferTransactions() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            db.repository.save(new Transaction(
                    0L, accountId, new BigDecimal("10.00"), TransactionType.INCOME, null, DATE, null, null));
            db.repository.save(new Transaction(
                    0L, accountId, new BigDecimal("20.00"), TransactionType.EXPENSE, null, DATE, null, null));
            db.repository.save(new Transaction(
                    0L, accountId, new BigDecimal("30.00"), TransactionType.TRANSFER, null, DATE, null, 1L));

            List<Transaction> result = db.repository.findByAccountId(accountId);

            Set<TransactionType> types = new HashSet<>();
            for (Transaction transaction : result) {
                types.add(transaction.getType());
            }
            assertEquals(3, result.size());
            assertEquals(Set.of(TransactionType.INCOME, TransactionType.EXPENSE, TransactionType.TRANSFER), types);
        }
    }

    @Test
    void unknownAccountReturnsEmptyList() {
        try (TestDatabase db = newRepository()) {
            List<Transaction> result = db.repository.findByAccountId(999_999L);

            assertTrue(result.isEmpty());
        }
    }

    @Test
    void findByAccountIdDoesNotAffectExistingRepositoryBehavior() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            Transaction first = db.repository.save(new Transaction(
                    0L, accountId, new BigDecimal("10.00"), TransactionType.EXPENSE, null, DATE, null, null));
            Transaction second = db.repository.save(new Transaction(
                    0L, accountId, new BigDecimal("20.00"), TransactionType.INCOME, null, DATE, null, null));

            // Calling findByAccountId alongside the existing operations changes nothing about them.
            db.repository.findByAccountId(accountId);

            assertTrue(db.repository.findById(first.getId()).isPresent());
            assertEquals(2, db.repository.findAll().size());

            db.repository.delete(first.getId());

            assertFalse(db.repository.findById(first.getId()).isPresent());
            assertEquals(1, db.repository.findAll().size());
            assertEquals(1, db.repository.findByAccountId(accountId).size());
            assertEquals(second.getId(), db.repository.findByAccountId(accountId).get(0).getId());
        }
    }

    // ===================== ASSIGN TRANSFER GROUP ID =====================

    @Test
    void assignTransferGroupIdSetsOnlyThatColumn() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            long categoryId = db.newCategoryId();
            Transaction saved = db.repository.save(new Transaction(
                    0L, accountId, new BigDecimal("75.00"), TransactionType.TRANSFER,
                    categoryId, DATE, "Weekly shop", null));

            db.repository.assignTransferGroupId(saved.getId(), 42L);

            Transaction retrieved = db.repository.findById(saved.getId()).orElseThrow();
            assertEquals(Optional.of(42L), retrieved.getTransferGroupId());
            // every other field is exactly as it was before the assignment
            assertEquals(accountId, retrieved.getAccountId());
            assertEquals(0, new BigDecimal("75.00").compareTo(retrieved.getAmount()));
            assertEquals(TransactionType.TRANSFER, retrieved.getType());
            assertEquals(Optional.of(categoryId), retrieved.getCategoryId());
            assertEquals(DATE, retrieved.getDate());
            assertEquals(Optional.of("Weekly shop"), retrieved.getDescription());
        }
    }

    @Test
    void assignTransferGroupIdCanUseTheTransactionsOwnGeneratedIdAsTheGroupId() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            Transaction saved = db.repository.save(new Transaction(
                    0L, accountId, new BigDecimal("10.00"), TransactionType.TRANSFER, null, DATE, null, null));

            db.repository.assignTransferGroupId(saved.getId(), saved.getId());

            Transaction retrieved = db.repository.findById(saved.getId()).orElseThrow();
            assertEquals(Optional.of(saved.getId()), retrieved.getTransferGroupId());
        }
    }

    @Test
    void assignTransferGroupIdOnAMissingIdThrows() {
        try (TestDatabase db = newRepository()) {
            RepositoryException exception = assertThrows(RepositoryException.class,
                    () -> db.repository.assignTransferGroupId(999_999L, 1L));

            assertTrue(exception.getMessage().contains("999999") || exception.getMessage().contains("999_999"),
                    "the failure message should identify the missing transaction id");
        }
    }

    // ===================== FOREIGN KEYS =====================

    @Test
    void savingATransactionForANonexistentAccountFails() {
        try (TestDatabase db = newRepository()) {
            Transaction transaction = new Transaction(
                    0L, 999_999L, new BigDecimal("10.00"), TransactionType.EXPENSE, null, DATE, null, null);

            RepositoryException exception = assertThrows(RepositoryException.class,
                    () -> db.repository.save(transaction),
                    "saving a transaction with a non-existent account_id should be rejected");
            assertTrue(exception.getCause() instanceof SQLException);
        }
    }

    @Test
    void savingATransactionForANonexistentCategoryFails() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            Transaction transaction = new Transaction(
                    0L, accountId, new BigDecimal("10.00"), TransactionType.EXPENSE, 999_999L, DATE, null, null);

            RepositoryException exception = assertThrows(RepositoryException.class,
                    () -> db.repository.save(transaction),
                    "saving a transaction with a non-existent category_id should be rejected");
            assertTrue(exception.getCause() instanceof SQLException);
        }
    }

    @Test
    void savingAnUncategorizedTransactionWithNullCategoryIdSucceeds() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            Transaction transaction = new Transaction(
                    0L, accountId, new BigDecimal("10.00"), TransactionType.EXPENSE, null, DATE, null, null);

            Transaction saved = db.repository.save(transaction);

            assertTrue(saved.getId() > 0);
            assertTrue(db.repository.findById(saved.getId()).orElseThrow().getCategoryId().isEmpty());
        }
    }

    // ===================== DELETE =====================

    @Test
    void deletingATransactionRemovesItFromFindByIdAndFindAll() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            Transaction first = db.repository.save(new Transaction(
                    0L, accountId, new BigDecimal("10.00"), TransactionType.EXPENSE, null, DATE, null, null));
            Transaction second = db.repository.save(new Transaction(
                    0L, accountId, new BigDecimal("20.00"), TransactionType.INCOME, null, DATE, null, null));

            db.repository.delete(first.getId());

            assertFalse(db.repository.findById(first.getId()).isPresent());
            List<Transaction> remaining = db.repository.findAll();
            assertEquals(1, remaining.size());
            assertEquals(second.getId(), remaining.get(0).getId());
        }
    }

    @Test
    void deletingAMissingIdDoesNothing() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            db.repository.save(new Transaction(
                    0L, accountId, new BigDecimal("10.00"), TransactionType.EXPENSE, null, DATE, null, null));

            db.repository.delete(999_999L);

            assertEquals(1, db.repository.findAll().size());
        }
    }
}
