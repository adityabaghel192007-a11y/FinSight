package com.finsight.repository.jdbc;

import com.finsight.domain.goal.Goal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link GoalRepositoryJdbc} against a real (but isolated, in-memory)
 * SQLite database — mirroring {@link AccountRepositoryJdbcTest}, {@link CategoryRepositoryJdbcTest},
 * {@link TransactionRepositoryJdbcTest}, {@link InvestmentRepositoryJdbcTest}, and
 * {@link LoanRepositoryJdbcTest}'s structure.
 *
 * <p>Every test opens its own fresh {@code jdbc:sqlite::memory:} database via
 * {@link #newRepository()} and initializes the schema on it; nothing here ever touches a file on
 * disk or a database another test (or the real application) might be using.
 */
class GoalRepositoryJdbcTest {

    private static final LocalDate TARGET_DATE = LocalDate.of(2030, 6, 1);

    private static final class TestDatabase implements AutoCloseable {
        private final SqliteConnectionProvider connectionProvider;
        private final GoalRepositoryJdbc repository;

        private TestDatabase() {
            this.connectionProvider = new SqliteConnectionProvider("jdbc:sqlite::memory:");
            SchemaInitializer.initialize(connectionProvider.getConnection());
            this.repository = new GoalRepositoryJdbc(connectionProvider);
        }

        @Override
        public void close() {
            connectionProvider.close();
        }
    }

    private static TestDatabase newRepository() {
        return new TestDatabase();
    }

    private static Goal newActiveGoal() {
        // id is a placeholder — save() ignores it and lets SQLite generate the real one.
        return new Goal(0L, "Emergency Fund", new BigDecimal("500000"), new BigDecimal("100000"),
                TARGET_DATE, new BigDecimal("10000"));
    }

    // ===================== CREATE =====================

    @Test
    void savingANewGoalGeneratesAnId() {
        try (TestDatabase db = newRepository()) {
            Goal saved = db.repository.save(newActiveGoal());

            assertTrue(saved.getId() > 0, "generated id should be a positive, valid row id");
        }
    }

    @Test
    void savingReturnsANewInstanceAndDoesNotMutateTheOriginal() {
        try (TestDatabase db = newRepository()) {
            Goal original = newActiveGoal();

            Goal saved = db.repository.save(original);

            // The original, caller-held instance is exactly as it was before saving.
            assertEquals(0L, original.getId());
            // The returned instance is a different object carrying the generated id.
            assertTrue(saved.getId() != original.getId());
            assertEquals(original.getName(), saved.getName());
        }
    }

    @Test
    void savingMultipleGoalsProducesDistinctIds() {
        try (TestDatabase db = newRepository()) {
            Goal first = db.repository.save(newActiveGoal());
            Goal second = db.repository.save(newActiveGoal());
            Goal third = db.repository.save(newActiveGoal());

            Set<Long> ids = new HashSet<>(List.of(first.getId(), second.getId(), third.getId()));
            assertEquals(3, ids.size(), "every saved goal should receive a distinct id");
        }
    }

    // ===================== ROUND-TRIP =====================

    @Test
    void completeFieldRoundTrip() {
        try (TestDatabase db = newRepository()) {
            Goal toSave = new Goal(0L, "Emergency Fund", new BigDecimal("500000"), new BigDecimal("100000"),
                    TARGET_DATE, new BigDecimal("10000"));
            Goal saved = db.repository.save(toSave);

            Goal retrieved = db.repository.findById(saved.getId()).orElseThrow();

            assertEquals(saved.getId(), retrieved.getId());
            assertEquals("Emergency Fund", retrieved.getName());
            assertEquals(0, new BigDecimal("500000").compareTo(retrieved.getTargetAmount()));
            assertEquals(0, new BigDecimal("100000").compareTo(retrieved.getCurrentAmount()));
            assertEquals(TARGET_DATE, retrieved.getTargetDate());
            assertEquals(0, new BigDecimal("10000").compareTo(retrieved.getMonthlyContribution()));
        }
    }

    @Test
    void targetAmountRoundTripsWithoutLosingPrecision() {
        try (TestDatabase db = newRepository()) {
            Goal toSave = new Goal(0L, "Precision Check", new BigDecimal("1234567.89"),
                    BigDecimal.ZERO, TARGET_DATE, BigDecimal.ZERO);
            Goal saved = db.repository.save(toSave);

            Goal retrieved = db.repository.findById(saved.getId()).orElseThrow();

            assertEquals(0, new BigDecimal("1234567.89").compareTo(retrieved.getTargetAmount()));
        }
    }

    @Test
    void currentAmountRoundTripsWithoutLosingPrecision() {
        try (TestDatabase db = newRepository()) {
            Goal toSave = new Goal(0L, "Precision Check", new BigDecimal("999999.99"),
                    new BigDecimal("543210.17"), TARGET_DATE, BigDecimal.ZERO);
            Goal saved = db.repository.save(toSave);

            Goal retrieved = db.repository.findById(saved.getId()).orElseThrow();

            assertEquals(0, new BigDecimal("543210.17").compareTo(retrieved.getCurrentAmount()));
        }
    }

    @Test
    void monthlyContributionRoundTripsWithoutLosingPrecisionAndIsNotRecalculated() {
        try (TestDatabase db = newRepository()) {
            // A monthlyContribution value that does not correspond to any "neat" amortization of
            // (targetAmount - currentAmount) over the months until targetDate — proving the stored
            // value is read back exactly as supplied, never recomputed by GoalCalculator's logic.
            BigDecimal suppliedContribution = new BigDecimal("6789.13");
            Goal toSave = new Goal(0L, "Arbitrary Contribution", new BigDecimal("1000000"),
                    new BigDecimal("50000"), TARGET_DATE, suppliedContribution);
            Goal saved = db.repository.save(toSave);

            Goal retrieved = db.repository.findById(saved.getId()).orElseThrow();

            assertEquals(0, suppliedContribution.compareTo(retrieved.getMonthlyContribution()),
                    "monthlyContribution must be persisted and returned exactly as supplied, "
                            + "not recalculated from targetAmount/currentAmount/targetDate");
        }
    }

    @Test
    void targetDateRoundTripsExactly() {
        try (TestDatabase db = newRepository()) {
            LocalDate targetDate = LocalDate.of(2035, 12, 31);
            Goal toSave = new Goal(0L, "Retirement", BigDecimal.TEN, BigDecimal.ZERO, targetDate, BigDecimal.ZERO);
            Goal saved = db.repository.save(toSave);

            Goal retrieved = db.repository.findById(saved.getId()).orElseThrow();

            assertEquals(targetDate, retrieved.getTargetDate());
        }
    }

    @Test
    void goalWithZeroCurrentAmountRoundTrips() {
        try (TestDatabase db = newRepository()) {
            Goal toSave = new Goal(0L, "New Goal", BigDecimal.TEN, BigDecimal.ZERO, TARGET_DATE, BigDecimal.ZERO);
            Goal saved = db.repository.save(toSave);

            Goal retrieved = db.repository.findById(saved.getId()).orElseThrow();

            assertEquals(0, BigDecimal.ZERO.compareTo(retrieved.getCurrentAmount()));
        }
    }

    @Test
    void goalWithCurrentAmountEqualToTargetAmountRoundTrips() {
        try (TestDatabase db = newRepository()) {
            Goal toSave = new Goal(0L, "Almost Done", BigDecimal.TEN, BigDecimal.TEN, TARGET_DATE, BigDecimal.ZERO);
            Goal saved = db.repository.save(toSave);

            Goal retrieved = db.repository.findById(saved.getId()).orElseThrow();

            assertEquals(0, retrieved.getCurrentAmount().compareTo(retrieved.getTargetAmount()));
        }
    }

    // ===================== READ =====================

    @Test
    void findByIdForAnExistingGoalReturnsIt() {
        try (TestDatabase db = newRepository()) {
            Goal saved = db.repository.save(newActiveGoal());

            Optional<Goal> result = db.repository.findById(saved.getId());

            assertTrue(result.isPresent());
            assertEquals(saved.getId(), result.get().getId());
        }
    }

    @Test
    void findByIdForAMissingIdReturnsEmpty() {
        try (TestDatabase db = newRepository()) {
            Optional<Goal> result = db.repository.findById(999_999L);

            assertFalse(result.isPresent());
        }
    }

    @Test
    void findAllReturnsEveryPersistedGoal() {
        try (TestDatabase db = newRepository()) {
            db.repository.save(newActiveGoal());
            db.repository.save(newActiveGoal());

            List<Goal> goals = db.repository.findAll();

            assertEquals(2, goals.size());
        }
    }

    @Test
    void findAllOnAnEmptyDatabaseReturnsAnEmptyList() {
        try (TestDatabase db = newRepository()) {
            List<Goal> goals = db.repository.findAll();

            assertTrue(goals.isEmpty());
        }
    }

    // ===================== DELETE =====================

    @Test
    void deletingAGoalRemovesItFromFindByIdAndFindAll() {
        try (TestDatabase db = newRepository()) {
            Goal first = db.repository.save(newActiveGoal());
            Goal second = db.repository.save(newActiveGoal());

            db.repository.delete(first.getId());

            assertFalse(db.repository.findById(first.getId()).isPresent());
            List<Goal> remaining = db.repository.findAll();
            assertEquals(1, remaining.size());
            assertEquals(second.getId(), remaining.get(0).getId());
        }
    }

    @Test
    void deletingAMissingIdDoesNothing() {
        try (TestDatabase db = newRepository()) {
            db.repository.save(newActiveGoal());

            db.repository.delete(999_999L);

            assertEquals(1, db.repository.findAll().size());
        }
    }
}
