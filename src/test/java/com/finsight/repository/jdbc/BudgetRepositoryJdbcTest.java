package com.finsight.repository.jdbc;

import com.finsight.domain.budget.Budget;
import com.finsight.domain.category.Category;
import com.finsight.domain.category.CategoryType;
import com.finsight.repository.RepositoryException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link BudgetRepositoryJdbc} against a real (but isolated, in-memory)
 * SQLite database — mirroring {@link AccountRepositoryJdbcTest}, {@link CategoryRepositoryJdbcTest},
 * {@link TransactionRepositoryJdbcTest}, {@link InvestmentRepositoryJdbcTest},
 * {@link LoanRepositoryJdbcTest}, and {@link GoalRepositoryJdbcTest}'s structure, plus the
 * foreign-key behavior specific to {@code Budget}.
 *
 * <p>Every test opens its own fresh {@code jdbc:sqlite::memory:} database via
 * {@link #newRepository()} and initializes the schema on it; nothing here ever touches a file on
 * disk or a database another test (or the real application) might be using.
 */
class BudgetRepositoryJdbcTest {

    private static final YearMonth MONTH = YearMonth.of(2026, 3);

    private static final class TestDatabase implements AutoCloseable {
        private final SqliteConnectionProvider connectionProvider;
        private final BudgetRepositoryJdbc repository;
        private final CategoryRepositoryJdbc categoryRepository;

        private TestDatabase() {
            this.connectionProvider = new SqliteConnectionProvider("jdbc:sqlite::memory:");
            SchemaInitializer.initialize(connectionProvider.getConnection());
            this.repository = new BudgetRepositoryJdbc(connectionProvider);
            this.categoryRepository = new CategoryRepositoryJdbc(connectionProvider);
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
    void savingANewBudgetGeneratesAnId() {
        try (TestDatabase db = newRepository()) {
            long categoryId = db.newCategoryId();
            Budget saved = db.repository.save(new Budget(0L, categoryId, MONTH, new BigDecimal("15000")));

            assertTrue(saved.getId() > 0, "generated id should be a positive, valid row id");
        }
    }

    @Test
    void savingReturnsANewInstanceAndDoesNotMutateTheOriginal() {
        try (TestDatabase db = newRepository()) {
            long categoryId = db.newCategoryId();
            Budget original = new Budget(0L, categoryId, MONTH, new BigDecimal("15000"));

            Budget saved = db.repository.save(original);

            // The original, caller-held instance is exactly as it was before saving.
            assertEquals(0L, original.getId());
            // The returned instance is a different object carrying the generated id.
            assertTrue(saved.getId() != original.getId());
            assertEquals(original.getCategoryId(), saved.getCategoryId());
            assertEquals(original.getMonth(), saved.getMonth());
        }
    }

    @Test
    void savingMultipleBudgetsProducesDistinctIds() {
        try (TestDatabase db = newRepository()) {
            long categoryId = db.newCategoryId();
            Budget first = db.repository.save(new Budget(0L, categoryId, MONTH, new BigDecimal("1000")));
            Budget second = db.repository.save(new Budget(0L, categoryId, MONTH.plusMonths(1), new BigDecimal("2000")));
            Budget third = db.repository.save(new Budget(0L, categoryId, MONTH.plusMonths(2), new BigDecimal("3000")));

            Set<Long> ids = new HashSet<>(List.of(first.getId(), second.getId(), third.getId()));
            assertEquals(3, ids.size(), "every saved budget should receive a distinct id");
        }
    }

    // ===================== ROUND-TRIP =====================

    @Test
    void completeFieldRoundTrip() {
        try (TestDatabase db = newRepository()) {
            long categoryId = db.newCategoryId();
            Budget toSave = new Budget(0L, categoryId, MONTH, new BigDecimal("15000"));
            Budget saved = db.repository.save(toSave);

            Budget retrieved = db.repository.findById(saved.getId()).orElseThrow();

            assertEquals(saved.getId(), retrieved.getId());
            assertEquals(categoryId, retrieved.getCategoryId());
            assertEquals(MONTH, retrieved.getMonth());
            assertEquals(0, new BigDecimal("15000").compareTo(retrieved.getAmount()));
        }
    }

    @Test
    void bigDecimalAmountRoundTripsWithoutLosingPrecision() {
        try (TestDatabase db = newRepository()) {
            long categoryId = db.newCategoryId();
            Budget toSave = new Budget(0L, categoryId, MONTH, new BigDecimal("12345.67"));
            Budget saved = db.repository.save(toSave);

            Budget retrieved = db.repository.findById(saved.getId()).orElseThrow();

            assertEquals(0, new BigDecimal("12345.67").compareTo(retrieved.getAmount()));
        }
    }

    @Test
    void yearMonthRoundTripsExactlyAsYyyyMmTextNotConvertedThroughLocalDate() {
        try (TestDatabase db = newRepository()) {
            long categoryId = db.newCategoryId();
            YearMonth month = YearMonth.of(2026, 9);
            Budget toSave = new Budget(0L, categoryId, month, BigDecimal.ZERO);
            Budget saved = db.repository.save(toSave);

            // Confirm the raw stored representation is exactly "YYYY-MM" per the project convention.
            Optional<String> rawMonth = readRawMonthColumn(db, saved.getId());
            assertEquals(Optional.of("2026-09"), rawMonth);

            Budget retrieved = db.repository.findById(saved.getId()).orElseThrow();
            assertEquals(month, retrieved.getMonth());
        }
    }

    @Test
    void januaryMonthRoundTrips() {
        try (TestDatabase db = newRepository()) {
            long categoryId = db.newCategoryId();
            YearMonth january = YearMonth.of(2026, 1);
            Budget saved = db.repository.save(new Budget(0L, categoryId, january, BigDecimal.TEN));

            Budget retrieved = db.repository.findById(saved.getId()).orElseThrow();

            assertEquals(january, retrieved.getMonth());
        }
    }

    @Test
    void decemberMonthRoundTrips() {
        try (TestDatabase db = newRepository()) {
            long categoryId = db.newCategoryId();
            YearMonth december = YearMonth.of(2026, 12);
            Budget saved = db.repository.save(new Budget(0L, categoryId, december, BigDecimal.TEN));

            Budget retrieved = db.repository.findById(saved.getId()).orElseThrow();

            assertEquals(december, retrieved.getMonth());
        }
    }

    @Test
    void leapYearFebruaryRoundTrips() {
        try (TestDatabase db = newRepository()) {
            long categoryId = db.newCategoryId();
            YearMonth leapFebruary = YearMonth.of(2028, 2);
            Budget saved = db.repository.save(new Budget(0L, categoryId, leapFebruary, BigDecimal.TEN));

            Budget retrieved = db.repository.findById(saved.getId()).orElseThrow();

            assertEquals(leapFebruary, retrieved.getMonth());
            assertEquals(29, leapFebruary.lengthOfMonth(), "sanity check: 2028 is a leap year");
        }
    }

    @Test
    void differentCategoriesCanHaveBudgetsForTheSameMonth() {
        try (TestDatabase db = newRepository()) {
            long categoryA = db.newCategoryId();
            long categoryB = db.newCategoryId();

            Budget budgetA = db.repository.save(new Budget(0L, categoryA, MONTH, new BigDecimal("1000")));
            Budget budgetB = db.repository.save(new Budget(0L, categoryB, MONTH, new BigDecimal("2000")));

            assertTrue(budgetA.getId() != budgetB.getId());
            Budget retrievedA = db.repository.findById(budgetA.getId()).orElseThrow();
            Budget retrievedB = db.repository.findById(budgetB.getId()).orElseThrow();
            assertEquals(MONTH, retrievedA.getMonth());
            assertEquals(MONTH, retrievedB.getMonth());
            assertEquals(categoryA, retrievedA.getCategoryId());
            assertEquals(categoryB, retrievedB.getCategoryId());
        }
    }

    // ===================== READ =====================

    @Test
    void findByIdForAnExistingBudgetReturnsIt() {
        try (TestDatabase db = newRepository()) {
            long categoryId = db.newCategoryId();
            Budget saved = db.repository.save(new Budget(0L, categoryId, MONTH, BigDecimal.TEN));

            Optional<Budget> result = db.repository.findById(saved.getId());

            assertTrue(result.isPresent());
            assertEquals(saved.getId(), result.get().getId());
        }
    }

    @Test
    void findByIdForAMissingIdReturnsEmpty() {
        try (TestDatabase db = newRepository()) {
            Optional<Budget> result = db.repository.findById(999_999L);

            assertFalse(result.isPresent());
        }
    }

    @Test
    void findAllReturnsEveryPersistedBudget() {
        try (TestDatabase db = newRepository()) {
            long categoryId = db.newCategoryId();
            db.repository.save(new Budget(0L, categoryId, MONTH, BigDecimal.TEN));
            db.repository.save(new Budget(0L, categoryId, MONTH.plusMonths(1), BigDecimal.TEN));

            List<Budget> budgets = db.repository.findAll();

            assertEquals(2, budgets.size());
        }
    }

    @Test
    void findAllOnAnEmptyDatabaseReturnsAnEmptyList() {
        try (TestDatabase db = newRepository()) {
            List<Budget> budgets = db.repository.findAll();

            assertTrue(budgets.isEmpty());
        }
    }

    // ===================== DELETE =====================

    @Test
    void deletingABudgetRemovesItFromFindByIdAndFindAll() {
        try (TestDatabase db = newRepository()) {
            long categoryId = db.newCategoryId();
            Budget first = db.repository.save(new Budget(0L, categoryId, MONTH, BigDecimal.TEN));
            Budget second = db.repository.save(new Budget(0L, categoryId, MONTH.plusMonths(1), BigDecimal.TEN));

            db.repository.delete(first.getId());

            assertFalse(db.repository.findById(first.getId()).isPresent());
            List<Budget> remaining = db.repository.findAll();
            assertEquals(1, remaining.size());
            assertEquals(second.getId(), remaining.get(0).getId());
        }
    }

    @Test
    void deletingAMissingIdDoesNothing() {
        try (TestDatabase db = newRepository()) {
            long categoryId = db.newCategoryId();
            db.repository.save(new Budget(0L, categoryId, MONTH, BigDecimal.TEN));

            db.repository.delete(999_999L);

            assertEquals(1, db.repository.findAll().size());
        }
    }

    // ===================== FOREIGN KEYS =====================

    @Test
    void savingABudgetForAValidCategorySucceeds() {
        try (TestDatabase db = newRepository()) {
            long categoryId = db.newCategoryId();

            Budget saved = db.repository.save(new Budget(0L, categoryId, MONTH, BigDecimal.TEN));

            assertTrue(saved.getId() > 0);
        }
    }

    @Test
    void savingABudgetForANonexistentCategoryFails() {
        try (TestDatabase db = newRepository()) {
            Budget budget = new Budget(0L, 999_999L, MONTH, BigDecimal.TEN);

            RepositoryException exception = assertThrows(RepositoryException.class,
                    () -> db.repository.save(budget),
                    "saving a budget with a non-existent category_id should be rejected");
            assertTrue(exception.getCause() instanceof SQLException);
        }
    }

    @Test
    void deletingACategoryStillReferencedByABudgetFailsRatherThanCascading() {
        try (TestDatabase db = newRepository()) {
            long categoryId = db.newCategoryId();
            db.repository.save(new Budget(0L, categoryId, MONTH, BigDecimal.TEN));

            RepositoryException exception = assertThrows(RepositoryException.class,
                    () -> db.categoryRepository.delete(categoryId),
                    "deleting a category a budget still references should be rejected, not cascaded");
            assertTrue(exception.getCause() instanceof SQLException);
        }
    }

    /**
     * Reads the raw {@code month} column value directly (bypassing {@link BudgetMapper}) so a
     * test can confirm the on-disk text representation is exactly {@code YYYY-MM}, independent of
     * whatever {@link YearMonth#parse} would tolerate on the way back in.
     */
    private static Optional<String> readRawMonthColumn(TestDatabase db, long budgetId) {
        try (PreparedStatement statement = db.connectionProvider.getConnection().prepareStatement(
                "SELECT month FROM budgets WHERE id = ?")) {
            statement.setLong(1, budgetId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.ofNullable(row.getString("month"));
            }
        } catch (SQLException e) {
            throw new AssertionError("test setup failed: could not read raw month column", e);
        }
    }
}
