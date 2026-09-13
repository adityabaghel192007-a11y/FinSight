package com.finsight.repository.jdbc;

import com.finsight.domain.loan.Loan;
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
 * Integration tests for {@link LoanRepositoryJdbc} against a real (but isolated, in-memory)
 * SQLite database — mirroring {@link AccountRepositoryJdbcTest}, {@link CategoryRepositoryJdbcTest},
 * {@link TransactionRepositoryJdbcTest}, and {@link InvestmentRepositoryJdbcTest}'s structure.
 *
 * <p>Every test opens its own fresh {@code jdbc:sqlite::memory:} database via
 * {@link #newRepository()} and initializes the schema on it; nothing here ever touches a file on
 * disk or a database another test (or the real application) might be using.
 */
class LoanRepositoryJdbcTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    private static final class TestDatabase implements AutoCloseable {
        private final SqliteConnectionProvider connectionProvider;
        private final LoanRepositoryJdbc repository;

        private TestDatabase() {
            this.connectionProvider = new SqliteConnectionProvider("jdbc:sqlite::memory:");
            SchemaInitializer.initialize(connectionProvider.getConnection());
            this.repository = new LoanRepositoryJdbc(connectionProvider);
        }

        @Override
        public void close() {
            connectionProvider.close();
        }
    }

    private static TestDatabase newRepository() {
        return new TestDatabase();
    }

    private static Loan newActiveLoan() {
        // id is a placeholder — save() ignores it and lets SQLite generate the real one.
        return new Loan(0L, "Home Loan", new BigDecimal("5000000"), new BigDecimal("0.085"), 240,
                START, new BigDecimal("5000000"), 240);
    }

    // ===================== CREATE =====================

    @Test
    void savingANewLoanGeneratesAnId() {
        try (TestDatabase db = newRepository()) {
            Loan saved = db.repository.save(newActiveLoan());

            assertTrue(saved.getId() > 0, "generated id should be a positive, valid row id");
        }
    }

    @Test
    void savingReturnsANewInstanceAndDoesNotMutateTheOriginal() {
        try (TestDatabase db = newRepository()) {
            Loan original = newActiveLoan();

            Loan saved = db.repository.save(original);

            // The original, caller-held instance is exactly as it was before saving.
            assertEquals(0L, original.getId());
            // The returned instance is a different object carrying the generated id.
            assertTrue(saved.getId() != original.getId());
            assertEquals(original.getName(), saved.getName());
        }
    }

    @Test
    void savingMultipleLoansProducesDistinctIds() {
        try (TestDatabase db = newRepository()) {
            Loan first = db.repository.save(newActiveLoan());
            Loan second = db.repository.save(newActiveLoan());
            Loan third = db.repository.save(newActiveLoan());

            Set<Long> ids = new HashSet<>(List.of(first.getId(), second.getId(), third.getId()));
            assertEquals(3, ids.size(), "every saved loan should receive a distinct id");
        }
    }

    // ===================== ROUND-TRIP =====================

    @Test
    void completeFieldRoundTrip() {
        try (TestDatabase db = newRepository()) {
            Loan toSave = new Loan(0L, "Home Loan", new BigDecimal("5000000"), new BigDecimal("0.085"),
                    240, START, new BigDecimal("5000000"), 240);
            Loan saved = db.repository.save(toSave);

            Loan retrieved = db.repository.findById(saved.getId()).orElseThrow();

            assertEquals(saved.getId(), retrieved.getId());
            assertEquals("Home Loan", retrieved.getName());
            assertEquals(0, new BigDecimal("5000000").compareTo(retrieved.getPrincipal()));
            assertEquals(0, new BigDecimal("0.085").compareTo(retrieved.getAnnualInterestRate()));
            assertEquals(240, retrieved.getTenureMonths());
            assertEquals(START, retrieved.getStartDate());
            assertEquals(0, new BigDecimal("5000000").compareTo(retrieved.getCurrentBalance()));
            assertEquals(240, retrieved.getRemainingTenureMonths());
        }
    }

    @Test
    void partiallyRepaidLoanRoundTripsCurrentStateIndependentlyOfOriginalTerms() {
        try (TestDatabase db = newRepository()) {
            // principal != currentBalance and tenureMonths != remainingTenureMonths, deliberately,
            // to prove current state is stored and read back as-is rather than derived.
            Loan toSave = new Loan(0L, "Home Loan", new BigDecimal("1000000"), new BigDecimal("0.09"),
                    60, START, new BigDecimal("720000"), 42);
            Loan saved = db.repository.save(toSave);

            Loan retrieved = db.repository.findById(saved.getId()).orElseThrow();

            assertEquals(0, new BigDecimal("1000000").compareTo(retrieved.getPrincipal()));
            assertEquals(60, retrieved.getTenureMonths());
            assertEquals(0, new BigDecimal("720000").compareTo(retrieved.getCurrentBalance()));
            assertEquals(42, retrieved.getRemainingTenureMonths());
            // Explicitly not reconstructed from the original terms.
            assertFalse(retrieved.getCurrentBalance().compareTo(retrieved.getPrincipal()) == 0);
            assertFalse(retrieved.getRemainingTenureMonths() == retrieved.getTenureMonths());
        }
    }

    @Test
    void zeroInterestLoanRoundTrips() {
        try (TestDatabase db = newRepository()) {
            Loan toSave = new Loan(0L, "Interest-Free Loan", BigDecimal.TEN, BigDecimal.ZERO, 12,
                    START, BigDecimal.TEN, 12);
            Loan saved = db.repository.save(toSave);

            Loan retrieved = db.repository.findById(saved.getId()).orElseThrow();

            assertEquals(0, BigDecimal.ZERO.compareTo(retrieved.getAnnualInterestRate()));
        }
    }

    @Test
    void fullyPaidLoanRoundTripsZeroBalanceAndZeroRemainingTenure() {
        try (TestDatabase db = newRepository()) {
            Loan toSave = new Loan(0L, "Paid Off Loan", new BigDecimal("50000"), new BigDecimal("0.10"),
                    12, START, BigDecimal.ZERO, 0);
            Loan saved = db.repository.save(toSave);

            Loan retrieved = db.repository.findById(saved.getId()).orElseThrow();

            assertEquals(0, BigDecimal.ZERO.compareTo(retrieved.getCurrentBalance()));
            assertEquals(0, retrieved.getRemainingTenureMonths());
            // Original terms are still intact even though the loan is fully paid off.
            assertEquals(0, new BigDecimal("50000").compareTo(retrieved.getPrincipal()));
            assertEquals(12, retrieved.getTenureMonths());
        }
    }

    @Test
    void oneMonthRemainingLoanRoundTrips() {
        try (TestDatabase db = newRepository()) {
            Loan toSave = new Loan(0L, "Almost Paid Off Loan", new BigDecimal("50000"), new BigDecimal("0.10"),
                    12, START, new BigDecimal("4123.45"), 1);
            Loan saved = db.repository.save(toSave);

            Loan retrieved = db.repository.findById(saved.getId()).orElseThrow();

            assertEquals(0, new BigDecimal("4123.45").compareTo(retrieved.getCurrentBalance()));
            assertEquals(1, retrieved.getRemainingTenureMonths());
        }
    }

    @Test
    void bigDecimalFieldsRoundTripWithoutLosingPrecision() {
        try (TestDatabase db = newRepository()) {
            // Representative values that a double/float round-trip would silently corrupt.
            Loan toSave = new Loan(0L, "Precision Check", new BigDecimal("1234567.89"),
                    new BigDecimal("0.0875"), 180, START, new BigDecimal("987654.32"), 150);
            Loan saved = db.repository.save(toSave);

            Loan retrieved = db.repository.findById(saved.getId()).orElseThrow();

            assertEquals(0, new BigDecimal("1234567.89").compareTo(retrieved.getPrincipal()));
            assertEquals(0, new BigDecimal("0.0875").compareTo(retrieved.getAnnualInterestRate()));
            assertEquals(0, new BigDecimal("987654.32").compareTo(retrieved.getCurrentBalance()));
        }
    }

    @Test
    void localDateRoundTripsExactly() {
        try (TestDatabase db = newRepository()) {
            LocalDate startDate = LocalDate.of(2023, 7, 4);
            Loan toSave = new Loan(0L, "Home Loan", BigDecimal.TEN, BigDecimal.ZERO, 12, startDate,
                    BigDecimal.TEN, 12);
            Loan saved = db.repository.save(toSave);

            Loan retrieved = db.repository.findById(saved.getId()).orElseThrow();

            assertEquals(startDate, retrieved.getStartDate());
        }
    }

    // ===================== READ =====================

    @Test
    void findByIdForAnExistingLoanReturnsIt() {
        try (TestDatabase db = newRepository()) {
            Loan saved = db.repository.save(newActiveLoan());

            Optional<Loan> result = db.repository.findById(saved.getId());

            assertTrue(result.isPresent());
            assertEquals(saved.getId(), result.get().getId());
        }
    }

    @Test
    void findByIdForAMissingIdReturnsEmpty() {
        try (TestDatabase db = newRepository()) {
            Optional<Loan> result = db.repository.findById(999_999L);

            assertFalse(result.isPresent());
        }
    }

    @Test
    void findAllReturnsEveryPersistedLoan() {
        try (TestDatabase db = newRepository()) {
            db.repository.save(newActiveLoan());
            db.repository.save(newActiveLoan());

            List<Loan> loans = db.repository.findAll();

            assertEquals(2, loans.size());
        }
    }

    @Test
    void findAllOnAnEmptyDatabaseReturnsAnEmptyList() {
        try (TestDatabase db = newRepository()) {
            List<Loan> loans = db.repository.findAll();

            assertTrue(loans.isEmpty());
        }
    }

    // ===================== DELETE =====================

    @Test
    void deletingALoanRemovesItFromFindByIdAndFindAll() {
        try (TestDatabase db = newRepository()) {
            Loan first = db.repository.save(newActiveLoan());
            Loan second = db.repository.save(newActiveLoan());

            db.repository.delete(first.getId());

            assertFalse(db.repository.findById(first.getId()).isPresent());
            List<Loan> remaining = db.repository.findAll();
            assertEquals(1, remaining.size());
            assertEquals(second.getId(), remaining.get(0).getId());
        }
    }

    @Test
    void deletingAMissingIdDoesNothing() {
        try (TestDatabase db = newRepository()) {
            db.repository.save(newActiveLoan());

            db.repository.delete(999_999L);

            assertEquals(1, db.repository.findAll().size());
        }
    }
}
