package com.finsight.app;

import com.finsight.domain.account.Account;
import com.finsight.domain.account.Currency;
import com.finsight.domain.investment.AssetType;
import com.finsight.domain.investment.Investment;
import com.finsight.service.InvestmentApplicationService;
import com.finsight.service.SimulationApplicationService;
import com.finsight.service.SimulationResult;
import com.finsight.simulation.comparison.ScenarioComparison;
import com.finsight.simulation.model.MonthlyFinancialState;
import com.finsight.simulation.model.SimulationParameters;
import com.finsight.simulation.scenario.IncomeChangeEvent;
import com.finsight.simulation.scenario.InvestmentContributionChangeEvent;
import com.finsight.simulation.scenario.OneTimeExpenseEvent;
import com.finsight.simulation.scenario.Scenario;
import com.finsight.simulation.scenario.ScenarioEvent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 6B's required {@link CompositionRoot}-backed integration test: proves a single
 * {@link Scenario} carrying three <em>heterogeneous</em> {@link ScenarioEvent}s — an
 * {@link IncomeChangeEvent}, a {@link OneTimeExpenseEvent}, and an
 * {@link InvestmentContributionChangeEvent} — runs end to end through the exact,
 * unmodified production path the Decision Lab (see {@code com.finsight.ui.DecisionLabView}) uses:
 * {@link CompositionRoot} &rarr; real repositories against a real (temporary, file-backed) SQLite
 * database &rarr; {@link SimulationApplicationService#runScenario} &rarr; {@code SimulationEngine}
 * &rarr; {@link ScenarioComparison}.
 *
 * <p>This is deliberately <b>not</b> a duplicate of any existing engine test: {@code
 * SimulationEngineTest}/{@code ScenarioApplicationTest} already prove the engine itself handles
 * arbitrary combinations of heterogeneous events (see e.g.
 * {@code netWorthInvariantHoldsWithAllFiveEventTypesCombined}), and
 * {@code SimulationApplicationServiceIntegrationTest} already proves a single-event scenario
 * reaches a real database. What only this test proves is the thing specific to the Decision Lab
 * milestone: that a real {@link CompositionRoot} (the exact object
 * {@code FinSightApplication}/{@code DecisionLabView} share) can carry <em>three</em> events of
 * three different types, targeting three different properties, through one scenario run, and that
 * the resulting {@link ScenarioComparison} reflects all three changes correctly. Nothing here
 * touches JavaFX or {@code DecisionLabView} itself — that view is a thin, hand-verified wrapper
 * around exactly this call (build a {@link Scenario} from
 * {@code List.copyOf(pendingEvents)} and call {@code runScenario}); this test is the
 * non-UI proof that the call it makes actually works.
 */
class DecisionLabMultiEventScenarioIntegrationTest {

    @Test
    void aScenarioWithAnIncomeChangeAOneTimeExpenseAndAnInvestmentContributionChangeRunsEndToEnd()
            throws IOException {
        Path tempDb = Files.createTempFile("finsight-decision-lab-multi-event-test", ".db");
        Files.delete(tempDb);
        String jdbcUrl = "jdbc:sqlite:" + tempDb;
        try {
            try (CompositionRoot compositionRoot = new CompositionRoot(jdbcUrl)) {
                // CompositionRoot seeds exactly one demo Account (see DemoDataSeeder) into a fresh
                // database — that seeded account is what the investment below is attached to,
                // since CompositionRoot deliberately exposes no way to create an Account directly.
                List<Account> accounts = compositionRoot.getFinancialDataApplicationService().getAccounts();
                assertEquals(1, accounts.size(), "a fresh database must have exactly the one seeded demo account");
                Account demoAccount = accounts.get(0);

                InvestmentApplicationService investmentApplicationService =
                        compositionRoot.getInvestmentApplicationService();
                Investment investment = investmentApplicationService.addInvestment(
                        demoAccount.getId(), "Index Fund", null, AssetType.MUTUAL_FUND, Currency.INR,
                        BigDecimal.TEN, new BigDecimal("1000.00"), new BigDecimal("1100.00"),
                        new BigDecimal("0.12"), new BigDecimal("200.00"));

                LocalDate asOfDate = LocalDate.of(2026, 4, 1);
                SimulationParameters parameters =
                        new SimulationParameters(6, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE);

                // Three different event types, targeting three different properties, at three
                // different effective dates — exactly the shape of scenario the Decision Lab is
                // built to let a user assemble one event at a time.
                List<ScenarioEvent> events = List.of(
                        new IncomeChangeEvent(asOfDate.plusMonths(1), new BigDecimal("90000.00")),
                        new InvestmentContributionChangeEvent(
                                asOfDate.plusMonths(2), investment.getId(), new BigDecimal("500.00")),
                        new OneTimeExpenseEvent(asOfDate.plusMonths(3), new BigDecimal("20000.00")));
                Scenario multiEventScenario = new Scenario(0L, "Decision Lab Scenario", null, asOfDate, events);

                SimulationResult result = compositionRoot.getSimulationApplicationService()
                        .runScenario(asOfDate, parameters, multiEventScenario);

                assertNotNull(result);
                List<MonthlyFinancialState> baselineStates = result.getBaselineProjection().getStates();
                List<MonthlyFinancialState> scenarioStates = result.getScenarioProjection().getStates();
                assertEquals(6, baselineStates.size());
                assertEquals(6, scenarioStates.size());

                // 1. The income change (effective asOfDate.plusMonths(1)) is active from the very
                // first simulated month onward, in the scenario projection only.
                assertEquals(0, new BigDecimal("60000.00").compareTo(baselineStates.get(0).getMonthlyIncome()),
                        "the baseline projection must never see any of the scenario's events");
                assertEquals(0, new BigDecimal("90000.00").compareTo(scenarioStates.get(0).getMonthlyIncome()),
                        "the income change must be active from its effective month onward in the scenario");
                assertEquals(0, new BigDecimal("90000.00").compareTo(scenarioStates.get(5).getMonthlyIncome()),
                        "the income change must remain active for the rest of the projection");

                // 2. All three events' combined effect on cash, by the third simulated month
                // (states.get(2), the same month the one-time expense is due), matches hand-worked
                // arithmetic exactly. The demo account's seeded opening balance (150,000.00, no
                // prior transactions) is the starting cash for both projections.
                //   Baseline (unaffected by any event): +60,000 income -40,000 expenses -200
                //   investment contribution, every month for 3 months:
                //     150,000.00 + 3 x (60,000 - 40,000 - 200) = 150,000.00 + 59,400.00 = 209,400.00
                //   Scenario: month 1 (income already changed to 90,000; contribution still 200) =
                //   +49,800; month 2 (contribution now 500) = +49,500; month 3 (one-time expense of
                //   20,000 also due) = +29,500:
                //     150,000.00 + 49,800.00 + 49,500.00 + 29,500.00 = 278,800.00
                // Getting this exact combined figure right is only possible if all three events —
                // of three different types, at three different effective dates — were genuinely
                // applied together by the real SimulationEngine this CompositionRoot-backed call
                // reached, not just one or two of them.
                assertEquals(0, new BigDecimal("209400.00").compareTo(baselineStates.get(2).getCashBalance()),
                        "the baseline's cash balance must be unaffected by any of the scenario's events");
                assertEquals(0, new BigDecimal("278800.00").compareTo(scenarioStates.get(2).getCashBalance()),
                        "the scenario's cash balance must reflect the combined effect of all three events");

                // 3. The investment contribution change (effective asOfDate.plusMonths(2), i.e.
                // states.get(1) onward) must grow the scenario's investment value beyond the
                // baseline's by the end of the projection.
                ScenarioComparison comparison = result.getComparison();
                assertNotNull(comparison);
                assertTrue(comparison.getScenarioFinalInvestmentValue()
                                .compareTo(comparison.getBaselineFinalInvestmentValue()) > 0,
                        "a higher scenario SIP contribution must grow the final investment value "
                                + "beyond the baseline's unchanged contribution");

                // 4. All three changes together must leave the scenario's final net worth
                // different from the baseline's — proving the comparison reflects the combined
                // effect of all three events, not just one of them.
                assertTrue(comparison.getNetWorthDifference().signum() != 0,
                        "three combined event effects must not net out to exactly zero net worth impact");
            }
        } finally {
            Files.deleteIfExists(tempDb);
        }
    }
}
