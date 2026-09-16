package com.finsight.app;

import com.finsight.domain.financialprofile.FinancialProfile;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 5H's core integration test: proves that editing the {@link FinancialProfile} through
 * {@code FinancialProfileApplicationService} — the same workflow {@code FinSightApplication}'s
 * "Save Financial Profile" button triggers — genuinely persists across a separate
 * {@link CompositionRoot} lifecycle (simulating an application restart, exactly like
 * {@code FreshLaunchSimulationWorkflowTest}'s own relaunch test does for demo data), and that the
 * very next baseline simulation automatically picks up the new values with no UI code passing them
 * in directly.
 *
 * <p>Uses the exact acceptance-test figures from the milestone's own spec: income 70,000.00 /
 * expenses 45,000.00 against the demo profile's seeded 150,000.00 opening cash (net monthly flow
 * +25,000.00), which a 12-month, zero-growth baseline projection must resolve to month 1 =
 * 175,000.00 and month 12 = 450,000.00 — these are independently-derivable consequences of the
 * existing, unmodified simulation model, not hardcoded application behavior.
 */
class FinancialProfileUpdateWorkflowTest {

    @Test
    void anEditedProfileSurvivesARestartAndTheNextBaselineSimulationUsesIt() throws IOException {
        Path tempDb = Files.createTempFile("finsight-financial-profile-update-workflow-test", ".db");
        Files.delete(tempDb);
        String jdbcUrl = "jdbc:sqlite:" + tempDb;
        try {
            // First "launch": DemoDataSeeder seeds the default 60,000.00/40,000.00 profile, then
            // the user edits it to 70,000.00/45,000.00 and saves — exactly what "Save Financial
            // Profile" does.
            try (CompositionRoot firstLaunch = new CompositionRoot(jdbcUrl)) {
                FinancialProfile saved = firstLaunch.getFinancialProfileApplicationService()
                        .saveFinancialProfile("70000.00", "45000.00");
                assertEquals(0, new BigDecimal("70000.00").compareTo(saved.getMonthlyIncome()));
                assertEquals(0, new BigDecimal("45000.00").compareTo(saved.getMonthlyExpenses()));
            }

            // "Restart": a brand new CompositionRoot against the same file. DemoDataSeeder runs
            // again during construction, but must be a no-op since a profile already exists.
            try (CompositionRoot secondLaunch = new CompositionRoot(jdbcUrl)) {
                Optional<FinancialProfile> reloaded = secondLaunch.getFinancialProfileApplicationService().getFinancialProfile();
                assertTrue(reloaded.isPresent());
                assertEquals(0, new BigDecimal("70000.00").compareTo(reloaded.get().getMonthlyIncome()),
                        "the edited income must survive the restart, and DemoDataSeeder must not have reset it back to 60,000.00");
                assertEquals(0, new BigDecimal("45000.00").compareTo(reloaded.get().getMonthlyExpenses()),
                        "the edited expenses must survive the restart, and DemoDataSeeder must not have reset it back to 40,000.00");

                SimulationApplicationService simulationApplicationService = secondLaunch.getSimulationApplicationService();
                LocalDate asOfDate = LocalDate.of(2026, 4, 1);
                SimulationParameters parameters = new SimulationParameters(12, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE);
                Scenario baselineOnlyScenario = new Scenario(0L, "Baseline (no changes)", null, asOfDate, List.of());

                // Deliberately does NOT pass the edited income/expenses anywhere in this call —
                // SnapshotBuilder must pick them up on its own from the repository.
                SimulationResult result = simulationApplicationService.runScenario(asOfDate, parameters, baselineOnlyScenario);

                List<MonthlyFinancialState> states = result.getBaselineProjection().getStates();
                assertEquals(12, states.size());
                assertEquals(0, new BigDecimal("70000.00").compareTo(states.get(0).getMonthlyIncome()),
                        "the edited monthly income must reach the simulation through SnapshotBuilder, unchanged");
                assertEquals(0, new BigDecimal("45000.00").compareTo(states.get(0).getMonthlyExpenses()),
                        "the edited monthly expenses must reach the simulation through SnapshotBuilder, unchanged");
                assertEquals(0, new BigDecimal("175000.00").compareTo(states.get(0).getNetWorth()),
                        "month 1 = 150,000.00 opening cash + one month of +25,000.00 net flow");
                assertEquals(0, new BigDecimal("450000.00").compareTo(states.get(11).getNetWorth()),
                        "month 12 = 150,000.00 opening cash + twelve months of +25,000.00 net flow");
            }
        } finally {
            Files.deleteIfExists(tempDb);
        }
    }
}
