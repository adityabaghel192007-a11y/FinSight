package com.finsight.app;

import com.finsight.service.SimulationApplicationService;
import com.finsight.service.SimulationResult;
import com.finsight.simulation.model.MonthlyFinancialState;
import com.finsight.simulation.model.SimulationParameters;
import com.finsight.simulation.scenario.Scenario;
import com.finsight.ui.SimulationResultFormatter;
import com.finsight.ui.SimulationResultView;
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
 * Milestone 5E's core integration test: proves the exact workflow {@code FinSightApplication}
 * triggers from its "Run Simulation" button actually works end to end, starting from a completely
 * fresh, empty, file-backed database — the same starting condition a real first-time launch is in.
 *
 * <p>This intentionally exercises the real production path, with nothing faked or mocked:
 * {@link CompositionRoot} (which seeds demo data via {@link DemoDataSeeder} and wires every real
 * repository/service), a real temporary SQLite file (never {@code jdbc:sqlite::memory:}, and never
 * the real application database), the real {@link SimulationApplicationService}, and the real
 * {@link SimulationResultFormatter}. Nothing here touches JavaFX — that toolkit is not required to
 * verify that the UI-facing workflow itself is correct, only to verify that {@code Button}/{@code
 * Label} wiring around it doesn't crash (out of scope for a headless test — see
 * {@code FinSightApplicationTest}'s own documentation on why this project does not attempt
 * headless JavaFX testing).
 *
 * <p>Exact multi-month projection arithmetic is deliberately <b>not</b> re-asserted here — that is
 * already covered by {@code SimulationEngineTest} and {@code SimulationApplicationServiceTest}.
 * This test instead asserts the things specific to the 5E workflow: that a fresh launch succeeds
 * at all (it would fail with {@code ApplicationValidationException} before {@link DemoDataSeeder}
 * existed), that the deterministic demo income/expenses used by the seeded profile are exactly
 * what actually reaches the first simulated month, and that the formatter can turn the resulting
 * {@link SimulationResult} into a displayable view without error.
 */
class FreshLaunchSimulationWorkflowTest {

    @Test
    void aFreshFileBackedDatabaseCanRunTheSameBaselineWorkflowTheUiTriggers() throws IOException {
        Path tempDb = Files.createTempFile("finsight-fresh-launch-workflow-test", ".db");
        Files.delete(tempDb);
        String jdbcUrl = "jdbc:sqlite:" + tempDb;
        try {
            try (CompositionRoot compositionRoot = new CompositionRoot(jdbcUrl)) {
                SimulationApplicationService simulationApplicationService = compositionRoot.getSimulationApplicationService();

                LocalDate asOfDate = LocalDate.of(2026, 4, 1);
                SimulationParameters parameters = new SimulationParameters(12, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE);
                Scenario baselineOnlyScenario = new Scenario(0L, "Baseline (no changes)", null, asOfDate, List.of());

                SimulationResult result = simulationApplicationService.runScenario(asOfDate, parameters, baselineOnlyScenario);

                List<MonthlyFinancialState> states = result.getBaselineProjection().getStates();
                assertEquals(12, states.size(), "a fresh launch must produce the full 12-month projection, not fail partway");

                MonthlyFinancialState firstMonth = states.get(0);
                assertEquals(0, new BigDecimal("60000.00").compareTo(firstMonth.getMonthlyIncome()),
                        "the seeded demo profile's monthly income must reach the first simulated month unchanged");
                assertEquals(0, new BigDecimal("40000.00").compareTo(firstMonth.getMonthlyExpenses()),
                        "the seeded demo profile's monthly expenses must reach the first simulated month unchanged");
                assertTrue(firstMonth.getNetWorth().signum() > 0,
                        "the seeded demo account's opening balance plus a month of positive net cash flow "
                                + "must leave a positive net worth");

                SimulationResultView view = SimulationResultFormatter.format(result);
                assertEquals("60,000.00", view.getMonthlyIncome());
                assertEquals("40,000.00", view.getMonthlyExpenses());
                assertEquals("2026-04-01 to 2027-04-01 (12 months)", view.getProjectionPeriod());
            }
        } finally {
            Files.deleteIfExists(tempDb);
        }
    }

    @Test
    void launchingASecondTimeAgainstTheSameFileDoesNotDuplicateOrResetDemoData() throws IOException {
        Path tempDb = Files.createTempFile("finsight-fresh-launch-workflow-test-relaunch", ".db");
        Files.delete(tempDb);
        String jdbcUrl = "jdbc:sqlite:" + tempDb;
        try {
            LocalDate asOfDate = LocalDate.of(2026, 4, 1);
            SimulationParameters parameters = new SimulationParameters(12, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE);
            Scenario baselineOnlyScenario = new Scenario(0L, "Baseline (no changes)", null, asOfDate, List.of());

            BigDecimal firstLaunchNetWorth;
            try (CompositionRoot firstLaunch = new CompositionRoot(jdbcUrl)) {
                SimulationResult result = firstLaunch.getSimulationApplicationService()
                        .runScenario(asOfDate, parameters, baselineOnlyScenario);
                firstLaunchNetWorth = result.getBaselineProjection().getStates().get(0).getNetWorth();
            }

            try (CompositionRoot secondLaunch = new CompositionRoot(jdbcUrl)) {
                SimulationResult result = secondLaunch.getSimulationApplicationService()
                        .runScenario(asOfDate, parameters, baselineOnlyScenario);
                BigDecimal secondLaunchNetWorth = result.getBaselineProjection().getStates().get(0).getNetWorth();

                assertEquals(0, firstLaunchNetWorth.compareTo(secondLaunchNetWorth),
                        "relaunching against the same file must reuse the same persisted demo data, "
                                + "not seed a duplicate account that would double the projected net worth");
            }
        } finally {
            Files.deleteIfExists(tempDb);
        }
    }
}
