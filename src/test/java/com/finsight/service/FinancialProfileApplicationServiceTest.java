package com.finsight.service;

import com.finsight.domain.financialprofile.FinancialProfile;
import com.finsight.repository.jdbc.FinancialProfileRepositoryJdbc;
import com.finsight.repository.jdbc.SchemaInitializer;
import com.finsight.repository.jdbc.SqliteConnectionProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link FinancialProfileApplicationService}, against a real, isolated in-memory
 * SQLite database — matching this project's established {@code jdbc:sqlite::memory:} test
 * convention (see e.g. {@code DemoDataSeederTest}, {@code FinancialProfileRepositoryJdbcTest}).
 *
 * <p>The validation-rejection tests below all follow the same shape: save one known-good profile,
 * attempt an invalid save, then assert both that the invalid save threw and that the
 * <em>original</em> values are still exactly what {@link FinancialProfileApplicationService#getFinancialProfile()}
 * returns — proving invalid input never reaches {@code FinancialProfileRepository#save} at all,
 * not merely that it is reported as an error.
 */
class FinancialProfileApplicationServiceTest {

    private static final class TestDatabase implements AutoCloseable {
        private final SqliteConnectionProvider connectionProvider;
        private final FinancialProfileApplicationService service;

        private TestDatabase() {
            this.connectionProvider = new SqliteConnectionProvider("jdbc:sqlite::memory:");
            SchemaInitializer.initialize(connectionProvider.getConnection());
            this.service = new FinancialProfileApplicationService(new FinancialProfileRepositoryJdbc(connectionProvider));
        }

        @Override
        public void close() {
            connectionProvider.close();
        }
    }

    // ===================== getFinancialProfile() =====================

    @Test
    void getFinancialProfileOnAFreshDatabaseIsEmpty() {
        try (TestDatabase db = new TestDatabase()) {
            assertTrue(db.service.getFinancialProfile().isEmpty());
        }
    }

    // ===================== saving valid values =====================

    @Test
    void validValuesCanBeSaved() {
        try (TestDatabase db = new TestDatabase()) {
            FinancialProfile saved = db.service.saveFinancialProfile("70000.00", "45000.00");

            assertTrue(saved.getId() > 0);
            assertEquals(0, new BigDecimal("70000.00").compareTo(saved.getMonthlyIncome()));
            assertEquals(0, new BigDecimal("45000.00").compareTo(saved.getMonthlyExpenses()));
        }
    }

    @Test
    void savedValuesCanBeLoadedAgain() {
        try (TestDatabase db = new TestDatabase()) {
            db.service.saveFinancialProfile("70000.00", "45000.00");

            Optional<FinancialProfile> reloaded = db.service.getFinancialProfile();

            assertTrue(reloaded.isPresent());
            assertEquals(0, new BigDecimal("70000.00").compareTo(reloaded.get().getMonthlyIncome()));
            assertEquals(0, new BigDecimal("45000.00").compareTo(reloaded.get().getMonthlyExpenses()));
        }
    }

    @Test
    void savingATrimsSurroundingWhitespaceFromValidInput() {
        try (TestDatabase db = new TestDatabase()) {
            FinancialProfile saved = db.service.saveFinancialProfile("  70000.00 ", " 45000.00\t");

            assertEquals(0, new BigDecimal("70000.00").compareTo(saved.getMonthlyIncome()));
            assertEquals(0, new BigDecimal("45000.00").compareTo(saved.getMonthlyExpenses()));
        }
    }

    // ===================== rejection: negative amounts =====================

    @Test
    void negativeIncomeIsRejectedAndDoesNotOverwriteTheExistingProfile() {
        try (TestDatabase db = new TestDatabase()) {
            db.service.saveFinancialProfile("60000.00", "40000.00");

            assertThrows(IllegalArgumentException.class,
                    () -> db.service.saveFinancialProfile("-1000.00", "40000.00"));

            FinancialProfile stillPersisted = db.service.getFinancialProfile().orElseThrow();
            assertEquals(0, new BigDecimal("60000.00").compareTo(stillPersisted.getMonthlyIncome()),
                    "a rejected negative income must never overwrite the previously valid, persisted value");
            assertEquals(0, new BigDecimal("40000.00").compareTo(stillPersisted.getMonthlyExpenses()));
        }
    }

    @Test
    void negativeExpensesIsRejectedAndDoesNotOverwriteTheExistingProfile() {
        try (TestDatabase db = new TestDatabase()) {
            db.service.saveFinancialProfile("60000.00", "40000.00");

            assertThrows(IllegalArgumentException.class,
                    () -> db.service.saveFinancialProfile("60000.00", "-500.00"));

            FinancialProfile stillPersisted = db.service.getFinancialProfile().orElseThrow();
            assertEquals(0, new BigDecimal("60000.00").compareTo(stillPersisted.getMonthlyIncome()));
            assertEquals(0, new BigDecimal("40000.00").compareTo(stillPersisted.getMonthlyExpenses()),
                    "a rejected negative expenses value must never overwrite the previously valid, persisted value");
        }
    }

    // ===================== rejection: blank input =====================

    @Test
    void blankIncomeIsRejectedAndDoesNotOverwriteTheExistingProfile() {
        try (TestDatabase db = new TestDatabase()) {
            db.service.saveFinancialProfile("60000.00", "40000.00");

            ApplicationValidationException thrown = assertThrows(ApplicationValidationException.class,
                    () -> db.service.saveFinancialProfile("   ", "40000.00"));
            assertTrue(thrown.getMessage().toLowerCase().contains("blank"));

            FinancialProfile stillPersisted = db.service.getFinancialProfile().orElseThrow();
            assertEquals(0, new BigDecimal("60000.00").compareTo(stillPersisted.getMonthlyIncome()));
        }
    }

    @Test
    void blankExpensesIsRejectedAndDoesNotOverwriteTheExistingProfile() {
        try (TestDatabase db = new TestDatabase()) {
            db.service.saveFinancialProfile("60000.00", "40000.00");

            assertThrows(ApplicationValidationException.class,
                    () -> db.service.saveFinancialProfile("60000.00", ""));

            FinancialProfile stillPersisted = db.service.getFinancialProfile().orElseThrow();
            assertEquals(0, new BigDecimal("40000.00").compareTo(stillPersisted.getMonthlyExpenses()));
        }
    }

    // ===================== rejection: malformed numeric input =====================

    @Test
    void malformedNumericIncomeIsRejectedAndDoesNotOverwriteTheExistingProfile() {
        try (TestDatabase db = new TestDatabase()) {
            db.service.saveFinancialProfile("60000.00", "40000.00");

            ApplicationValidationException thrown = assertThrows(ApplicationValidationException.class,
                    () -> db.service.saveFinancialProfile("not-a-number", "40000.00"));
            assertTrue(thrown.getMessage().contains("valid number"));

            FinancialProfile stillPersisted = db.service.getFinancialProfile().orElseThrow();
            assertEquals(0, new BigDecimal("60000.00").compareTo(stillPersisted.getMonthlyIncome()));
        }
    }

    @Test
    void malformedNumericExpensesIsRejectedAndDoesNotOverwriteTheExistingProfile() {
        try (TestDatabase db = new TestDatabase()) {
            db.service.saveFinancialProfile("60000.00", "40000.00");

            assertThrows(ApplicationValidationException.class,
                    () -> db.service.saveFinancialProfile("60000.00", "forty thousand"));

            FinancialProfile stillPersisted = db.service.getFinancialProfile().orElseThrow();
            assertEquals(0, new BigDecimal("40000.00").compareTo(stillPersisted.getMonthlyExpenses()));
        }
    }

    // ===================== null rejection =====================

    @Test
    void rejectsANullMonthlyIncomeText() {
        try (TestDatabase db = new TestDatabase()) {
            assertThrows(NullPointerException.class, () -> db.service.saveFinancialProfile(null, "40000.00"));
            assertFalse(db.service.getFinancialProfile().isPresent(), "nothing should have been persisted");
        }
    }

    @Test
    void rejectsANullMonthlyExpensesText() {
        try (TestDatabase db = new TestDatabase()) {
            assertThrows(NullPointerException.class, () -> db.service.saveFinancialProfile("60000.00", null));
            assertFalse(db.service.getFinancialProfile().isPresent(), "nothing should have been persisted");
        }
    }

    // ===================== update semantics =====================

    @Test
    void savingASecondTimeUpdatesTheSameProfileRatherThanCreatingASecondOne() {
        try (TestDatabase db = new TestDatabase()) {
            FinancialProfile first = db.service.saveFinancialProfile("60000.00", "40000.00");
            FinancialProfile second = db.service.saveFinancialProfile("70000.00", "45000.00");

            assertEquals(first.getId(), second.getId(), "editing the profile must update the one existing row, not create a new one");
            FinancialProfile reloaded = db.service.getFinancialProfile().orElseThrow();
            assertEquals(0, new BigDecimal("70000.00").compareTo(reloaded.getMonthlyIncome()));
            assertEquals(0, new BigDecimal("45000.00").compareTo(reloaded.getMonthlyExpenses()));
        }
    }
}
