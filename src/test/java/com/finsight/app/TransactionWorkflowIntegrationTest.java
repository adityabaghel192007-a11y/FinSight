package com.finsight.app;

import com.finsight.domain.account.Account;
import com.finsight.domain.category.Category;
import com.finsight.domain.category.CategoryType;
import com.finsight.domain.transaction.Transaction;
import com.finsight.domain.transaction.TransactionType;
import com.finsight.service.SimulationApplicationService;
import com.finsight.service.SimulationResult;
import com.finsight.simulation.model.MonthlyFinancialState;
import com.finsight.simulation.model.SimulationParameters;
import com.finsight.simulation.scenario.Scenario;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 5J's core integration tests: proves that a transaction recorded through the exact
 * same {@code TransactionApplicationService.recordExpense} call {@code FinSightApplication}'s
 * "Add Transaction" button triggers (a) genuinely persists across a separate
 * {@link CompositionRoot} lifecycle, is visible through the new
 * {@code FinancialDataApplicationService}, and (b) — the most important property this milestone
 * demonstrates — is automatically picked up by the very next baseline simulation with no new
 * calculation path and no UI-level arithmetic, because {@code SnapshotBuilder} already
 * consolidates every account's opening balance together with its own transactions via
 * {@code AccountBalanceCalculator} on every {@code runScenario} call.
 *
 * <p>Follows the same real-temp-file, two-{@link CompositionRoot}-lifecycle pattern already
 * established by {@code FinancialProfileUpdateWorkflowTest} for exactly this kind of
 * "does it survive a restart" proof, and reuses that test's demo-data arithmetic (150,000.00
 * opening cash, 60,000.00/40,000.00 default income/expenses) rather than editing the profile.
 */
class TransactionWorkflowIntegrationTest {

    @Test
    void addedTransactionSurvivesARestartAndIsVisibleThroughFinancialDataApplicationService() throws IOException {
        Path tempDb = Files.createTempFile("finsight-transaction-workflow-test", ".db");
        Files.delete(tempDb);
        String jdbcUrl = "jdbc:sqlite:" + tempDb;
        try {
            long accountId;
            long categoryId;

            // First "launch": DemoDataSeeder seeds the demo account plus the two demo categories
            // (Milestone 5J), then the user records a real expense — exactly what "Add
            // Transaction" does.
            try (CompositionRoot firstLaunch = new CompositionRoot(jdbcUrl)) {
                List<Account> accounts = firstLaunch.getFinancialDataApplicationService().getAccounts();
                assertEquals(1, accounts.size(), "DemoDataSeeder must have seeded exactly one account");
                Account demoAccount = accounts.get(0);
                accountId = demoAccount.getId();

                List<Category> categories = firstLaunch.getFinancialDataApplicationService().getCategories();
                Category groceries = categories.stream()
                        .filter(c -> c.getType() == CategoryType.EXPENSE)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("DemoDataSeeder must have seeded an EXPENSE category"));
                categoryId = groceries.getId();

                Transaction saved = firstLaunch.getTransactionApplicationService().recordExpense(
                        accountId, new BigDecimal("5000.00"), categoryId,
                        LocalDate.of(2026, 4, 1), "Groceries run");

                assertEquals(accountId, saved.getAccountId());
                assertEquals(TransactionType.EXPENSE, saved.getType());
                assertEquals(0, new BigDecimal("5000.00").compareTo(saved.getAmount()));
            }

            // "Restart": a brand new CompositionRoot against the same file. DemoDataSeeder runs
            // again during construction, but must be a no-op since a profile already exists — the
            // transaction must still be there, read back through FinancialDataApplicationService,
            // not a repository directly.
            try (CompositionRoot secondLaunch = new CompositionRoot(jdbcUrl)) {
                List<Transaction> transactions = secondLaunch.getFinancialDataApplicationService().getTransactions();

                assertEquals(1, transactions.size(),
                        "the transaction recorded before the restart must still be there, and "
                                + "DemoDataSeeder must not have created or removed any transaction");
                Transaction reloaded = transactions.get(0);
                assertEquals(accountId, reloaded.getAccountId());
                assertEquals(Long.valueOf(categoryId), reloaded.getCategoryId().orElseThrow());
                assertEquals(TransactionType.EXPENSE, reloaded.getType());
                assertEquals(0, new BigDecimal("5000.00").compareTo(reloaded.getAmount()));
                assertEquals(LocalDate.of(2026, 4, 1), reloaded.getDate());
                assertEquals("Groceries run", reloaded.getDescription().orElseThrow());
            }
        } finally {
            Files.deleteIfExists(tempDb);
        }
    }

    @Test
    void addingAnExpenseTransactionChangesTheNextBaselineSimulationsNetWorth() throws IOException {
        Path tempDb = Files.createTempFile("finsight-transaction-simulation-test", ".db");
        Files.delete(tempDb);
        String jdbcUrl = "jdbc:sqlite:" + tempDb;
        try (CompositionRoot root = new CompositionRoot(jdbcUrl)) {
            SimulationApplicationService simulationApplicationService = root.getSimulationApplicationService();
            LocalDate asOfDate = LocalDate.of(2026, 4, 1);
            SimulationParameters parameters = new SimulationParameters(1, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE);
            Scenario baselineOnlyScenario = new Scenario(0L, "Baseline (no changes)", null, asOfDate, List.of());

            // Before recording anything: 150,000.00 demo opening cash + one month of the demo
            // profile's +20,000.00 net flow (60,000.00 income - 40,000.00 expenses).
            SimulationResult before = simulationApplicationService.runScenario(asOfDate, parameters, baselineOnlyScenario);
            MonthlyFinancialState month1Before = before.getBaselineProjection().getStates().get(0);
            assertEquals(0, new BigDecimal("170000.00").compareTo(month1Before.getNetWorth()),
                    "month 1 before any transaction = 150,000.00 opening cash + one month of +20,000.00 net flow");

            // Record a real, persisted expense through the exact same TransactionApplicationService
            // call FinSightApplication's "Add Transaction" button makes — no simulation/domain
            // class is touched directly here.
            Account demoAccount = root.getFinancialDataApplicationService().getAccounts().get(0);
            Category groceries = root.getFinancialDataApplicationService().getCategories().stream()
                    .filter(c -> c.getType() == CategoryType.EXPENSE)
                    .findFirst()
                    .orElseThrow();
            root.getTransactionApplicationService().recordExpense(
                    demoAccount.getId(), new BigDecimal("5000.00"), groceries.getId(), asOfDate, "Groceries run");

            // Re-running the identical simulation call (same asOfDate, same parameters, same
            // baseline-only scenario) must now reflect the persisted transaction, because
            // SnapshotBuilder re-reads every account's transactions fresh on every call — this
            // proves the "UI -> service -> repository -> SnapshotBuilder -> SimulationEngine" flow
            // the milestone asks for, with no second calculation path introduced anywhere.
            SimulationResult after = simulationApplicationService.runScenario(asOfDate, parameters, baselineOnlyScenario);
            MonthlyFinancialState month1After = after.getBaselineProjection().getStates().get(0);
            assertEquals(0, new BigDecimal("165000.00").compareTo(month1After.getNetWorth()),
                    "the persisted 5,000.00 expense must reduce SnapshotBuilder's consolidated cash "
                            + "by exactly 5,000.00, with nothing else about the simulation changed");

            // Monthly income/expenses themselves are untouched — they still come only from
            // FinancialProfile (see SnapshotBuilder's own documentation), never derived from
            // transaction history.
            assertEquals(0, new BigDecimal("60000.00").compareTo(month1After.getMonthlyIncome()));
            assertEquals(0, new BigDecimal("40000.00").compareTo(month1After.getMonthlyExpenses()));
        } finally {
            Files.deleteIfExists(tempDb);
        }
    }
}
