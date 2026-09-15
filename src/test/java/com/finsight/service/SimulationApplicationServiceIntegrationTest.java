package com.finsight.service;

import com.finsight.domain.account.Account;
import com.finsight.domain.account.AccountType;
import com.finsight.domain.account.Currency;
import com.finsight.domain.financialprofile.FinancialProfile;
import com.finsight.domain.investment.AssetType;
import com.finsight.domain.investment.Investment;
import com.finsight.domain.transaction.Transaction;
import com.finsight.domain.transaction.TransactionType;
import com.finsight.repository.jdbc.AccountRepositoryJdbc;
import com.finsight.repository.jdbc.FinancialProfileRepositoryJdbc;
import com.finsight.repository.jdbc.GoalRepositoryJdbc;
import com.finsight.repository.jdbc.InvestmentRepositoryJdbc;
import com.finsight.repository.jdbc.LoanRepositoryJdbc;
import com.finsight.repository.jdbc.SchemaInitializer;
import com.finsight.repository.jdbc.SqliteConnectionProvider;
import com.finsight.repository.jdbc.TransactionRepositoryJdbc;
import com.finsight.simulation.model.MonthlyFinancialState;
import com.finsight.simulation.model.SimulationParameters;
import com.finsight.simulation.scenario.InvestmentContributionChangeEvent;
import com.finsight.simulation.scenario.Scenario;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves {@link SimulationApplicationService} actually integrates with the real persistence
 * layer — every {@code *RepositoryJdbc} class {@link SnapshotBuilder} depends on, against a real
 * (isolated, in-memory) SQLite database — rather than only against the in-memory fakes in
 * {@link SimulationApplicationServiceTest}. This is the one test in the codebase that exercises
 * the complete path: persisted {@link FinancialProfile}/{@link Account}/{@link Transaction}/
 * {@link Investment} rows &rarr; {@link SnapshotBuilder} &rarr;
 * {@link SimulationApplicationService} &rarr; {@code SimulationEngine} &rarr;
 * {@code ScenarioComparisonCalculator} &rarr; {@link SimulationResult}.
 *
 * <p>Matches the {@code TestDatabase} pattern already established by
 * {@link TransactionApplicationServiceIntegrationTest} and
 * {@code AccountBalanceCalculatorIntegrationTest}: an isolated in-memory SQLite database
 * ({@code jdbc:sqlite::memory:}), never the user's persistent application database.
 */
class SimulationApplicationServiceIntegrationTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 4, 1);

    private static final class TestDatabase implements AutoCloseable {
        private final SqliteConnectionProvider connectionProvider;
        private final FinancialProfileRepositoryJdbc financialProfileRepository;
        private final AccountRepositoryJdbc accountRepository;
        private final TransactionRepositoryJdbc transactionRepository;
        private final InvestmentRepositoryJdbc investmentRepository;
        private final SimulationApplicationService service;

        private TestDatabase() {
            this.connectionProvider = new SqliteConnectionProvider("jdbc:sqlite::memory:");
            SchemaInitializer.initialize(connectionProvider.getConnection());
            this.financialProfileRepository = new FinancialProfileRepositoryJdbc(connectionProvider);
            this.accountRepository = new AccountRepositoryJdbc(connectionProvider);
            this.transactionRepository = new TransactionRepositoryJdbc(connectionProvider);
            this.investmentRepository = new InvestmentRepositoryJdbc(connectionProvider);
            LoanRepositoryJdbc loanRepository = new LoanRepositoryJdbc(connectionProvider);
            GoalRepositoryJdbc goalRepository = new GoalRepositoryJdbc(connectionProvider);
            SnapshotBuilder snapshotBuilder = new SnapshotBuilder(
                    financialProfileRepository, accountRepository, transactionRepository,
                    investmentRepository, loanRepository, goalRepository);
            this.service = new SimulationApplicationService(snapshotBuilder);
        }

        @Override
        public void close() {
            connectionProvider.close();
        }
    }

    @Test
    void persistedFinancialStateReachesARealScenarioComparisonThroughRealSqlite() {
        try (TestDatabase db = new TestDatabase()) {
            db.financialProfileRepository.save(
                    new FinancialProfile(0L, new BigDecimal("3000.00"), new BigDecimal("1200.00")));
            Account checking = db.accountRepository.save(
                    new Account(0L, "Checking", AccountType.BANK, Currency.INR, new BigDecimal("10000.00")));
            db.transactionRepository.save(new Transaction(
                    0L, checking.getId(), new BigDecimal("500.00"), TransactionType.EXPENSE, null, AS_OF, null, null));
            Investment investment = db.investmentRepository.save(new Investment(
                    0L, checking.getId(), "Index Fund", null, AssetType.MUTUAL_FUND, Currency.INR,
                    BigDecimal.TEN, new BigDecimal("1000"), new BigDecimal("1100"),
                    new BigDecimal("0.12"), new BigDecimal("200.00")));

            SimulationParameters parameters = new SimulationParameters(6, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE);
            Scenario scenario = new Scenario(1L, "Boost SIP", null, AS_OF, List.of(
                    new InvestmentContributionChangeEvent(AS_OF.plusMonths(1), investment.getId(), new BigDecimal("500.00"))));

            SimulationResult result = db.service.runScenario(AS_OF, parameters, scenario);

            assertEquals(6, result.getBaselineProjection().getStates().size());
            assertEquals(6, result.getScenarioProjection().getStates().size());

            // Persisted checking balance 10000.00, minus the persisted on-asOfDate expense of
            // 500.00 (included, since it is not after asOfDate) = 9500.00 opening cash. Month 1's
            // cash balance is that opening cash plus the persisted profile's income/expenses,
            // minus the investment's own persisted monthlyContribution (200.00, unchanged in the
            // baseline run): 9500 + 3000 - 1200 - 200 = 11100.00.
            MonthlyFinancialState baselineMonth1 = result.getBaselineProjection().getStates().get(0);
            assertEquals(0, new BigDecimal("11100.00").compareTo(baselineMonth1.getCashBalance()));

            // The scenario's higher SIP contribution (500.00 vs the persisted 200.00) starting
            // month 1 must grow the investment more, and leave less month-1 cash, than the
            // baseline — proving persisted data really reached a distinguishable scenario run
            // through the real repositories, not just a baseline echo.
            MonthlyFinancialState scenarioMonth1 = result.getScenarioProjection().getStates().get(0);
            assertEquals(0, new BigDecimal("10800.00").compareTo(scenarioMonth1.getCashBalance()));

            BigDecimal baselineFinalInvestmentValue = result.getComparison().getBaselineFinalInvestmentValue();
            BigDecimal scenarioFinalInvestmentValue = result.getComparison().getScenarioFinalInvestmentValue();
            assertTrue(scenarioFinalInvestmentValue.compareTo(baselineFinalInvestmentValue) > 0,
                    "a higher scenario SIP contribution must grow the investment value more than the baseline");
        }
    }
}
