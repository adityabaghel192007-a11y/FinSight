package com.finsight.repository.jdbc;

import com.finsight.domain.financialprofile.FinancialProfile;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link FinancialProfileRepositoryJdbc} against a real (but isolated,
 * in-memory) SQLite database.
 *
 * <p>The focus here is different from a typical repository test like
 * {@code AccountRepositoryJdbcTest}: beyond basic save/find round-tripping, these tests exist to
 * prove the singleton contract documented on {@link com.finsight.repository.FinancialProfileRepository}
 * actually holds against a real database — a second {@link #save} call updates the one existing
 * row in place rather than inserting a second one, and does so keyed by the existing row's own id
 * rather than whatever id the caller happens to pass in.
 */
class FinancialProfileRepositoryJdbcTest {

    private static final class TestDatabase implements AutoCloseable {
        private final SqliteConnectionProvider connectionProvider;
        private final FinancialProfileRepositoryJdbc repository;

        private TestDatabase() {
            this.connectionProvider = new SqliteConnectionProvider("jdbc:sqlite::memory:");
            SchemaInitializer.initialize(connectionProvider.getConnection());
            this.repository = new FinancialProfileRepositoryJdbc(connectionProvider);
        }

        private int countRows() {
            try (PreparedStatement statement =
                         connectionProvider.getConnection().prepareStatement("SELECT COUNT(*) FROM financial_profile");
                 ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void close() {
            connectionProvider.close();
        }
    }

    private static TestDatabase newRepository() {
        return new TestDatabase();
    }

    // ===================== find() on an empty database =====================

    @Test
    void findOnAFreshDatabaseReturnsEmpty() {
        try (TestDatabase db = newRepository()) {
            Optional<FinancialProfile> result = db.repository.find();

            assertFalse(result.isPresent());
        }
    }

    // ===================== save(): first call inserts =====================

    @Test
    void firstSaveInsertsARowWithAGeneratedId() {
        try (TestDatabase db = newRepository()) {
            FinancialProfile saved = db.repository.save(
                    new FinancialProfile(0L, new BigDecimal("5000.00"), new BigDecimal("3200.00")));

            assertTrue(saved.getId() > 0, "generated id should be a positive, valid row id");
            assertEquals(0, new BigDecimal("5000.00").compareTo(saved.getMonthlyIncome()));
            assertEquals(0, new BigDecimal("3200.00").compareTo(saved.getMonthlyExpenses()));
            assertEquals(1, db.countRows());
        }
    }

    @Test
    void firstSaveReturnsANewInstanceAndDoesNotMutateTheOriginal() {
        try (TestDatabase db = newRepository()) {
            FinancialProfile original = new FinancialProfile(0L, new BigDecimal("5000.00"), new BigDecimal("3200.00"));

            FinancialProfile saved = db.repository.save(original);

            assertEquals(0L, original.getId());
            assertTrue(saved.getId() != original.getId());
        }
    }

    @Test
    void afterFirstSaveFindReturnsTheSameValues() {
        try (TestDatabase db = newRepository()) {
            FinancialProfile saved = db.repository.save(
                    new FinancialProfile(0L, new BigDecimal("4500.50"), new BigDecimal("2100.25")));

            Optional<FinancialProfile> found = db.repository.find();

            assertTrue(found.isPresent());
            assertEquals(saved.getId(), found.get().getId());
            assertEquals(0, new BigDecimal("4500.50").compareTo(found.get().getMonthlyIncome()));
            assertEquals(0, new BigDecimal("2100.25").compareTo(found.get().getMonthlyExpenses()));
        }
    }

    // ===================== save(): second call updates in place =====================

    @Test
    void secondSaveUpdatesTheExistingRowRatherThanInsertingASecondOne() {
        try (TestDatabase db = newRepository()) {
            FinancialProfile first = db.repository.save(
                    new FinancialProfile(0L, new BigDecimal("5000.00"), new BigDecimal("3200.00")));

            FinancialProfile second = db.repository.save(
                    new FinancialProfile(0L, new BigDecimal("6000.00"), new BigDecimal("3400.00")));

            assertEquals(first.getId(), second.getId(),
                    "the second save should update the same row, not create a new one");
            assertEquals(1, db.countRows(), "there must never be more than one financial profile row");

            Optional<FinancialProfile> found = db.repository.find();
            assertTrue(found.isPresent());
            assertEquals(0, new BigDecimal("6000.00").compareTo(found.get().getMonthlyIncome()));
            assertEquals(0, new BigDecimal("3400.00").compareTo(found.get().getMonthlyExpenses()));
        }
    }

    @Test
    void secondSaveIsKeyedByTheExistingRowsIdRegardlessOfTheIdCarriedByTheArgument() {
        try (TestDatabase db = newRepository()) {
            FinancialProfile first = db.repository.save(
                    new FinancialProfile(0L, new BigDecimal("5000.00"), new BigDecimal("3200.00")));

            // Deliberately pass a bogus, unrelated id on the argument: save() must ignore it and
            // still update the one existing row, not attempt to update a different (nonexistent)
            // row or insert a new one.
            FinancialProfile updated = db.repository.save(
                    new FinancialProfile(999_999L, new BigDecimal("7000.00"), new BigDecimal("3500.00")));

            assertEquals(first.getId(), updated.getId());
            assertEquals(1, db.countRows());
        }
    }

    @Test
    void manySavesStillLeaveExactlyOneRow() {
        try (TestDatabase db = newRepository()) {
            long firstId = db.repository.save(new FinancialProfile(0L, BigDecimal.ZERO, BigDecimal.ZERO)).getId();

            for (int i = 1; i <= 5; i++) {
                FinancialProfile result = db.repository.save(
                        new FinancialProfile(0L, new BigDecimal(i * 100), new BigDecimal(i * 50)));
                assertEquals(firstId, result.getId());
            }

            assertEquals(1, db.countRows());
        }
    }

    @Test
    void secondSaveReturnsANewInstanceAndDoesNotMutateTheOriginal() {
        try (TestDatabase db = newRepository()) {
            db.repository.save(new FinancialProfile(0L, new BigDecimal("5000.00"), new BigDecimal("3200.00")));
            FinancialProfile updateArgument = new FinancialProfile(0L, new BigDecimal("6000.00"), new BigDecimal("3400.00"));

            FinancialProfile result = db.repository.save(updateArgument);

            assertEquals(0L, updateArgument.getId(), "the argument passed to save() must be left untouched");
            assertTrue(result.getId() > 0);
        }
    }
}
