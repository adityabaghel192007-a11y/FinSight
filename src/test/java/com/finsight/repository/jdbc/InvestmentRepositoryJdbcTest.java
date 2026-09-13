package com.finsight.repository.jdbc;

import com.finsight.domain.account.Account;
import com.finsight.domain.account.AccountType;
import com.finsight.domain.account.Currency;
import com.finsight.domain.investment.AssetType;
import com.finsight.domain.investment.Investment;
import com.finsight.repository.RepositoryException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link InvestmentRepositoryJdbc} against a real (but isolated,
 * in-memory) SQLite database — mirroring {@link AccountRepositoryJdbcTest},
 * {@link CategoryRepositoryJdbcTest}, and {@link TransactionRepositoryJdbcTest}'s structure.
 *
 * <p>Every test opens its own fresh {@code jdbc:sqlite::memory:} database via
 * {@link #newRepository()} and initializes the schema on it; nothing here ever touches a file on
 * disk or a database another test (or the real application) might be using.
 */
class InvestmentRepositoryJdbcTest {

    private static final class TestDatabase implements AutoCloseable {
        private final SqliteConnectionProvider connectionProvider;
        private final InvestmentRepositoryJdbc repository;
        private final AccountRepositoryJdbc accountRepository;

        private TestDatabase() {
            this.connectionProvider = new SqliteConnectionProvider("jdbc:sqlite::memory:");
            SchemaInitializer.initialize(connectionProvider.getConnection());
            this.repository = new InvestmentRepositoryJdbc(connectionProvider);
            this.accountRepository = new AccountRepositoryJdbc(connectionProvider);
        }

        private long newAccountId() {
            return accountRepository.save(
                    new Account(0L, "Brokerage", AccountType.INVESTMENT, Currency.INR, BigDecimal.ZERO)).getId();
        }

        @Override
        public void close() {
            connectionProvider.close();
        }
    }

    private static TestDatabase newRepository() {
        return new TestDatabase();
    }

    private static Investment transientInvestment(long accountId, String name) {
        // id is a placeholder — save() ignores it and lets SQLite generate the real one.
        return new Investment(
                0L, accountId, name, "NIFTYBEES", AssetType.ETF, Currency.INR,
                new BigDecimal("100"), new BigDecimal("200.00"), new BigDecimal("250.00"),
                new BigDecimal("0.12"), new BigDecimal("5000"));
    }

    // ===================== CREATE =====================

    @Test
    void savingANewInvestmentGeneratesAnId() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            Investment saved = db.repository.save(transientInvestment(accountId, "Nifty 50 Index Fund"));

            assertTrue(saved.getId() > 0, "generated id should be a positive, valid row id");
        }
    }

    @Test
    void savingReturnsANewInstanceAndDoesNotMutateTheOriginal() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            Investment original = transientInvestment(accountId, "Nifty 50 Index Fund");

            Investment saved = db.repository.save(original);

            // The original, caller-held instance is exactly as it was before saving.
            assertEquals(0L, original.getId());
            // The returned instance is a different object carrying the generated id.
            assertTrue(saved.getId() != original.getId());
            assertEquals(original.getName(), saved.getName());
            assertEquals(original.getAccountId(), saved.getAccountId());
        }
    }

    @Test
    void savingMultipleInvestmentsProducesDistinctIds() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            Investment first = db.repository.save(transientInvestment(accountId, "Nifty 50 Index Fund"));
            Investment second = db.repository.save(transientInvestment(accountId, "Gold ETF"));
            Investment third = db.repository.save(transientInvestment(accountId, "S&P 500 Index Fund"));

            Set<Long> ids = new HashSet<>(List.of(first.getId(), second.getId(), third.getId()));
            assertEquals(3, ids.size(), "every saved investment should receive a distinct id");
        }
    }

    // ===================== ROUND-TRIP =====================

    @Test
    void savedInvestmentCanBeRetrievedByIdWithEveryFieldMatching() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            Investment toSave = new Investment(
                    0L, accountId, "Nifty 50 Index Fund", "NIFTYBEES", AssetType.ETF, Currency.INR,
                    new BigDecimal("100.5"), new BigDecimal("212.34"), new BigDecimal("256.78"),
                    new BigDecimal("0.125"), new BigDecimal("5500.00"));
            Investment saved = db.repository.save(toSave);

            Investment retrieved = db.repository.findById(saved.getId()).orElseThrow();

            assertEquals(saved.getId(), retrieved.getId());
            assertEquals(accountId, retrieved.getAccountId());
            assertEquals("Nifty 50 Index Fund", retrieved.getName());
            assertEquals(Optional.of("NIFTYBEES"), retrieved.getTicker());
            assertEquals(AssetType.ETF, retrieved.getAssetType());
            assertEquals(Currency.INR, retrieved.getCurrency());
            assertEquals(0, new BigDecimal("100.5").compareTo(retrieved.getQuantity()));
            assertEquals(0, new BigDecimal("212.34").compareTo(retrieved.getAveragePurchasePrice()));
            assertEquals(0, new BigDecimal("256.78").compareTo(retrieved.getCurrentPrice()));
            assertEquals(0, new BigDecimal("0.125").compareTo(retrieved.getExpectedAnnualReturn()));
            assertEquals(0, new BigDecimal("5500.00").compareTo(retrieved.getMonthlyContribution()));
        }
    }

    @Test
    void investmentWithNoTickerRoundTripsTickerAsEmpty() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            Investment toSave = new Investment(
                    0L, accountId, "Fixed Deposit", null, AssetType.FIXED_DEPOSIT, Currency.INR,
                    BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("0.07"), BigDecimal.ZERO);

            Investment saved = db.repository.save(toSave);
            Investment retrieved = db.repository.findById(saved.getId()).orElseThrow();

            assertTrue(retrieved.getTicker().isEmpty());
        }
    }

    @Test
    void everyAssetTypeRoundTripsThroughSqliteCorrectly() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            for (AssetType assetType : AssetType.values()) {
                Investment saved = db.repository.save(new Investment(
                        0L, accountId, "Holding-" + assetType, null, assetType, Currency.INR,
                        BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

                Investment retrieved = db.repository.findById(saved.getId()).orElseThrow();

                assertEquals(assetType, retrieved.getAssetType(),
                        () -> "AssetType " + assetType + " did not round-trip correctly");
            }
        }
    }

    @Test
    void bothCurrenciesRoundTripThroughSqliteCorrectly() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            for (Currency currency : Currency.values()) {
                Investment saved = db.repository.save(new Investment(
                        0L, accountId, "Holding-" + currency, null, AssetType.OTHER, currency,
                        BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

                Investment retrieved = db.repository.findById(saved.getId()).orElseThrow();

                assertEquals(currency, retrieved.getCurrency(),
                        () -> "Currency " + currency + " did not round-trip correctly");
            }
        }
    }

    @Test
    void bigDecimalFieldsRoundTripWithoutLosingPrecision() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            // Representative values that a double/float round-trip would silently corrupt.
            Investment toSave = new Investment(
                    0L, accountId, "Precision Check", "PRCN", AssetType.MUTUAL_FUND, Currency.USD,
                    new BigDecimal("1234.56789"), new BigDecimal("19999.99"),
                    new BigDecimal("100000.10"), new BigDecimal("0.0725"), new BigDecimal("1234567.89"));

            Investment saved = db.repository.save(toSave);
            Investment retrieved = db.repository.findById(saved.getId()).orElseThrow();

            assertEquals(0, new BigDecimal("1234.56789").compareTo(retrieved.getQuantity()));
            assertEquals(0, new BigDecimal("19999.99").compareTo(retrieved.getAveragePurchasePrice()));
            assertEquals(0, new BigDecimal("100000.10").compareTo(retrieved.getCurrentPrice()));
            assertEquals(0, new BigDecimal("0.0725").compareTo(retrieved.getExpectedAnnualReturn()));
            assertEquals(0, new BigDecimal("1234567.89").compareTo(retrieved.getMonthlyContribution()));
        }
    }

    // ===================== READ =====================

    @Test
    void findByIdForAMissingIdReturnsEmpty() {
        try (TestDatabase db = newRepository()) {
            Optional<Investment> result = db.repository.findById(999_999L);

            assertFalse(result.isPresent());
        }
    }

    @Test
    void findAllReturnsEveryPersistedInvestment() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            db.repository.save(transientInvestment(accountId, "Nifty 50 Index Fund"));
            db.repository.save(transientInvestment(accountId, "Gold ETF"));

            List<Investment> investments = db.repository.findAll();

            assertEquals(2, investments.size());
        }
    }

    @Test
    void findAllOnAnEmptyDatabaseReturnsAnEmptyList() {
        try (TestDatabase db = newRepository()) {
            List<Investment> investments = db.repository.findAll();

            assertTrue(investments.isEmpty());
        }
    }

    // ===================== DELETE =====================

    @Test
    void deletingAnInvestmentRemovesItFromFindByIdAndFindAll() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            Investment first = db.repository.save(transientInvestment(accountId, "Nifty 50 Index Fund"));
            Investment second = db.repository.save(transientInvestment(accountId, "Gold ETF"));

            db.repository.delete(first.getId());

            assertFalse(db.repository.findById(first.getId()).isPresent());
            List<Investment> remaining = db.repository.findAll();
            assertEquals(1, remaining.size());
            assertEquals(second.getId(), remaining.get(0).getId());
        }
    }

    @Test
    void deletingAMissingIdDoesNothing() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            db.repository.save(transientInvestment(accountId, "Nifty 50 Index Fund"));

            db.repository.delete(999_999L);

            assertEquals(1, db.repository.findAll().size());
        }
    }

    // ===================== FOREIGN KEYS =====================

    @Test
    void savingAnInvestmentForAValidAccountSucceeds() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();

            Investment saved = db.repository.save(transientInvestment(accountId, "Nifty 50 Index Fund"));

            assertTrue(saved.getId() > 0);
        }
    }

    @Test
    void savingAnInvestmentForANonexistentAccountFails() {
        try (TestDatabase db = newRepository()) {
            Investment investment = transientInvestment(999_999L, "Nifty 50 Index Fund");

            RepositoryException exception = assertThrows(RepositoryException.class,
                    () -> db.repository.save(investment),
                    "saving an investment with a non-existent account_id should be rejected");
            assertTrue(exception.getCause() instanceof SQLException);
        }
    }

    @Test
    void deletingAnAccountStillReferencedByAnInvestmentFailsRatherThanCascading() {
        try (TestDatabase db = newRepository()) {
            long accountId = db.newAccountId();
            db.repository.save(transientInvestment(accountId, "Nifty 50 Index Fund"));

            RepositoryException exception = assertThrows(RepositoryException.class,
                    () -> db.accountRepository.delete(accountId),
                    "deleting an account an investment still references should be rejected, not cascaded");
            assertTrue(exception.getCause() instanceof SQLException);
        }
    }
}
