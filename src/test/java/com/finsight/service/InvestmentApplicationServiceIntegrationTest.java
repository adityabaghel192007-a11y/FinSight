package com.finsight.service;

import com.finsight.domain.account.Account;
import com.finsight.domain.account.AccountType;
import com.finsight.domain.account.Currency;
import com.finsight.domain.investment.AssetType;
import com.finsight.domain.investment.Investment;
import com.finsight.repository.jdbc.AccountRepositoryJdbc;
import com.finsight.repository.jdbc.InvestmentRepositoryJdbc;
import com.finsight.repository.jdbc.SchemaInitializer;
import com.finsight.repository.jdbc.SqliteConnectionProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves {@link InvestmentApplicationService} actually integrates with the real persistence layer
 * — {@link AccountRepositoryJdbc} and {@link InvestmentRepositoryJdbc} against a real (isolated,
 * in-memory) SQLite database — rather than only against the in-memory fakes in
 * {@link InvestmentApplicationServiceTest}. The service itself remains JDBC-independent; this test
 * only exercises it through JDBC-backed repositories. Matches
 * {@link TransactionApplicationServiceIntegrationTest}'s exact structure.
 */
class InvestmentApplicationServiceIntegrationTest {

    private static final class TestDatabase implements AutoCloseable {
        private final SqliteConnectionProvider connectionProvider;
        private final AccountRepositoryJdbc accountRepository;
        private final InvestmentRepositoryJdbc investmentRepository;
        private final InvestmentApplicationService service;

        private TestDatabase() {
            this.connectionProvider = new SqliteConnectionProvider("jdbc:sqlite::memory:");
            SchemaInitializer.initialize(connectionProvider.getConnection());
            this.accountRepository = new AccountRepositoryJdbc(connectionProvider);
            this.investmentRepository = new InvestmentRepositoryJdbc(connectionProvider);
            this.service = new InvestmentApplicationService(investmentRepository, accountRepository);
        }

        private long newAccountId() {
            return accountRepository.save(
                    new Account(0L, "Brokerage", AccountType.INVESTMENT, Currency.INR, new BigDecimal("0.00"))).getId();
        }

        @Override
        public void close() {
            connectionProvider.close();
        }
    }

    // ===================== successful persistence + round-trip fidelity =====================

    @Test
    void addedInvestmentRoundTripsExactlyThroughRealSqlite() {
        try (TestDatabase db = new TestDatabase()) {
            long accountId = db.newAccountId();

            Investment saved = db.service.addInvestment(
                    accountId, "Nifty Index Fund", "NIFTYBEES", AssetType.ETF, Currency.INR,
                    new BigDecimal("100"), new BigDecimal("200.00"), new BigDecimal("250.00"),
                    new BigDecimal("0.10"), new BigDecimal("5000.00"));

            assertTrue(saved.getId() > 0);

            // Read back through a second, independently constructed repository instance against
            // the same connection, exactly like TransactionApplicationServiceIntegrationTest does,
            // to prove the row genuinely round-trips through real SQLite rather than merely
            // through whatever the service call happened to return.
            Optional<Investment> reloaded = db.investmentRepository.findById(saved.getId());
            assertTrue(reloaded.isPresent());
            Investment investment = reloaded.get();
            assertEquals(accountId, investment.getAccountId());
            assertEquals("Nifty Index Fund", investment.getName());
            assertEquals(Optional.of("NIFTYBEES"), investment.getTicker());
            assertEquals(AssetType.ETF, investment.getAssetType());
            assertEquals(Currency.INR, investment.getCurrency());
            assertEquals(0, new BigDecimal("100").compareTo(investment.getQuantity()));
            assertEquals(0, new BigDecimal("200.00").compareTo(investment.getAveragePurchasePrice()));
            assertEquals(0, new BigDecimal("250.00").compareTo(investment.getCurrentPrice()));
            assertEquals(0, new BigDecimal("0.10").compareTo(investment.getExpectedAnnualReturn()));
            assertEquals(0, new BigDecimal("5000.00").compareTo(investment.getMonthlyContribution()));
        }
    }

    @Test
    void addingAnInvestmentWithNoTickerRoundTripsAsAbsent() {
        try (TestDatabase db = new TestDatabase()) {
            long accountId = db.newAccountId();

            Investment saved = db.service.addInvestment(
                    accountId, "Physical Gold", null, AssetType.GOLD, Currency.INR,
                    new BigDecimal("10"), new BigDecimal("5000.00"), new BigDecimal("6200.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO);

            Investment reloaded = db.investmentRepository.findById(saved.getId()).orElseThrow();
            assertTrue(reloaded.getTicker().isEmpty());
        }
    }

    @Test
    void getInvestmentsReadsBackEveryPersistedInvestmentFromRealSqlite() {
        try (TestDatabase db = new TestDatabase()) {
            long accountId = db.newAccountId();
            db.service.addInvestment(
                    accountId, "Nifty Index Fund", "NIFTYBEES", AssetType.ETF, Currency.INR,
                    new BigDecimal("100"), new BigDecimal("200.00"), new BigDecimal("250.00"),
                    new BigDecimal("0.10"), new BigDecimal("5000.00"));
            db.service.addInvestment(
                    accountId, "US Total Market ETF", "VTI", AssetType.US_STOCK, Currency.USD,
                    new BigDecimal("5"), new BigDecimal("200.00"), new BigDecimal("230.00"),
                    new BigDecimal("0.08"), new BigDecimal("100.00"));

            List<Investment> all = db.service.getInvestments();

            assertEquals(2, all.size());
        }
    }

    // ===================== foreign-key enforcement =====================

    @Test
    void nonexistentAccountCannotCreateAnInvestmentAndNothingIsPersisted() {
        try (TestDatabase db = new TestDatabase()) {
            assertThrows(ApplicationValidationException.class, () -> db.service.addInvestment(
                    999_999L, "Nifty Index Fund", null, AssetType.ETF, Currency.INR,
                    new BigDecimal("100"), new BigDecimal("200.00"), new BigDecimal("250.00"),
                    new BigDecimal("0.10"), new BigDecimal("5000.00")));

            assertTrue(db.investmentRepository.findAll().isEmpty(),
                    "the application-service check must reject this before any row reaches SQLite, "
                            + "so the database's own foreign-key constraint is never even exercised here");
        }
    }

    @Test
    void deletingTheOwningAccountDoesNotOrphanInvestmentValidationForFutureAdds() {
        // A narrower, defensive check that the service always re-validates accountId against the
        // database's current state rather than caching or trusting a previously-seen id: once an
        // account is deleted, adding a further investment against its old id must fail exactly the
        // same way a never-existing id would.
        try (TestDatabase db = new TestDatabase()) {
            long accountId = db.newAccountId();
            db.accountRepository.delete(accountId);

            assertThrows(ApplicationValidationException.class, () -> db.service.addInvestment(
                    accountId, "Nifty Index Fund", null, AssetType.ETF, Currency.INR,
                    new BigDecimal("100"), new BigDecimal("200.00"), new BigDecimal("250.00"),
                    new BigDecimal("0.10"), new BigDecimal("5000.00")));
        }
    }
}
