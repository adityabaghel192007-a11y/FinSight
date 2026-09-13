package com.finsight.repository.jdbc;

import com.finsight.domain.category.Category;
import com.finsight.domain.category.CategoryType;
import com.finsight.domain.transaction.Transaction;
import com.finsight.domain.transaction.TransactionType;
import com.finsight.repository.RepositoryException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
 * Integration tests for {@link CategoryRepositoryJdbc} against a real (but isolated, in-memory)
 * SQLite database — mirroring {@link AccountRepositoryJdbcTest}'s structure and coverage.
 *
 * <p>Every test opens its own fresh {@code jdbc:sqlite::memory:} database via
 * {@link #newRepository()} and initializes the schema on it; nothing here ever touches a file on
 * disk or a database another test (or the real application) might be using.
 */
class CategoryRepositoryJdbcTest {

    private static final class TestDatabase implements AutoCloseable {
        private final SqliteConnectionProvider connectionProvider;
        private final CategoryRepositoryJdbc repository;

        private TestDatabase() {
            this.connectionProvider = new SqliteConnectionProvider("jdbc:sqlite::memory:");
            SchemaInitializer.initialize(connectionProvider.getConnection());
            this.repository = new CategoryRepositoryJdbc(connectionProvider);
        }

        @Override
        public void close() {
            connectionProvider.close();
        }
    }

    private static TestDatabase newRepository() {
        return new TestDatabase();
    }

    private static Category transientCategory(String name) {
        // id is a placeholder — save() ignores it and lets SQLite generate the real one.
        return new Category(0L, name, CategoryType.EXPENSE);
    }

    // ===================== CREATE =====================

    @Test
    void savingANewCategoryGeneratesAnId() {
        try (TestDatabase db = newRepository()) {
            Category saved = db.repository.save(transientCategory("Groceries"));

            assertTrue(saved.getId() > 0, "generated id should be a positive, valid row id");
        }
    }

    @Test
    void savingMultipleCategoriesProducesDistinctIds() {
        try (TestDatabase db = newRepository()) {
            Category first = db.repository.save(transientCategory("Groceries"));
            Category second = db.repository.save(transientCategory("Salary"));
            Category third = db.repository.save(transientCategory("Rent"));

            Set<Long> ids = new HashSet<>(List.of(first.getId(), second.getId(), third.getId()));
            assertEquals(3, ids.size(), "every saved category should receive a distinct id");
        }
    }

    @Test
    void savingReturnsANewInstanceAndDoesNotMutateTheOriginal() {
        try (TestDatabase db = newRepository()) {
            Category original = transientCategory("Groceries");

            Category saved = db.repository.save(original);

            // The original, caller-held instance is exactly as it was before saving.
            assertEquals(0L, original.getId());
            // The returned instance is a different object carrying the generated id.
            assertTrue(saved.getId() != original.getId());
            assertEquals(original.getName(), saved.getName());
            assertEquals(original.getType(), saved.getType());
        }
    }

    // ===================== READ =====================

    @Test
    void savedCategoryCanBeRetrievedByIdWithMatchingValues() {
        try (TestDatabase db = newRepository()) {
            Category toSave = new Category(0L, "Salary", CategoryType.INCOME);
            Category saved = db.repository.save(toSave);

            Optional<Category> retrieved = db.repository.findById(saved.getId());

            assertTrue(retrieved.isPresent());
            Category category = retrieved.get();
            assertEquals(saved.getId(), category.getId());
            assertEquals("Salary", category.getName());
            assertEquals(CategoryType.INCOME, category.getType());
        }
    }

    @Test
    void findByIdForAMissingIdReturnsEmpty() {
        try (TestDatabase db = newRepository()) {
            Optional<Category> result = db.repository.findById(999_999L);

            assertFalse(result.isPresent());
        }
    }

    @Test
    void findAllReturnsEveryPersistedCategory() {
        try (TestDatabase db = newRepository()) {
            db.repository.save(transientCategory("Groceries"));
            db.repository.save(transientCategory("Salary"));

            List<Category> categories = db.repository.findAll();

            assertEquals(2, categories.size());
        }
    }

    @Test
    void findAllOnAnEmptyDatabaseReturnsAnEmptyList() {
        try (TestDatabase db = newRepository()) {
            List<Category> categories = db.repository.findAll();

            assertTrue(categories.isEmpty());
        }
    }

    @Test
    void categoryTypeRoundTripsThroughSqliteCorrectly() {
        try (TestDatabase db = newRepository()) {
            for (CategoryType type : CategoryType.values()) {
                Category saved = db.repository.save(new Category(0L, "Category-" + type, type));

                Category retrieved = db.repository.findById(saved.getId()).orElseThrow();

                assertEquals(type, retrieved.getType(), () -> "CategoryType " + type + " did not round-trip correctly");
            }
        }
    }

    // ===================== DELETE =====================

    @Test
    void deletingACategoryRemovesItFromFindByIdAndFindAll() {
        try (TestDatabase db = newRepository()) {
            Category first = db.repository.save(transientCategory("Groceries"));
            Category second = db.repository.save(transientCategory("Salary"));

            db.repository.delete(first.getId());

            assertFalse(db.repository.findById(first.getId()).isPresent());
            List<Category> remaining = db.repository.findAll();
            assertEquals(1, remaining.size());
            assertEquals(second.getId(), remaining.get(0).getId());
        }
    }

    @Test
    void deletingAMissingIdDoesNothing() {
        try (TestDatabase db = newRepository()) {
            db.repository.save(transientCategory("Groceries"));

            db.repository.delete(999_999L);

            assertEquals(1, db.repository.findAll().size());
        }
    }

    @Test
    void deletingACategoryStillReferencedByATransactionFailsRatherThanCascading() {
        try (TestDatabase db = newRepository()) {
            Category category = db.repository.save(transientCategory("Groceries"));
            long accountId = insertAccount(db, "Checking");

            TransactionRepositoryJdbc transactionRepository = new TransactionRepositoryJdbc(db.connectionProvider);
            transactionRepository.save(new Transaction(
                    0L, accountId, new BigDecimal("10.00"), TransactionType.EXPENSE,
                    category.getId(), LocalDate.of(2026, 1, 1), null, null));

            RepositoryException exception = assertThrows(RepositoryException.class,
                    () -> db.repository.delete(category.getId()),
                    "deleting a category a transaction still references should be rejected, not cascaded");
            assertTrue(exception.getCause() instanceof SQLException);
        }
    }

    /**
     * Inserts an account row directly (bypassing {@code AccountRepositoryJdbc}) so tests in this
     * class can set up a real, valid {@code account_id} to satisfy the foreign key on
     * {@code transactions} without depending on the account repository.
     */
    private static long insertAccount(TestDatabase db, String name) {
        try (PreparedStatement statement = db.connectionProvider.getConnection().prepareStatement(
                "INSERT INTO accounts (name, type, currency, opening_balance) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name);
            statement.setString(2, "BANK");
            statement.setString(3, "INR");
            statement.setString(4, "0.00");
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                generatedKeys.next();
                return generatedKeys.getLong(1);
            }
        } catch (SQLException e) {
            throw new AssertionError("test setup failed: could not insert account", e);
        }
    }
}
