package com.finsight.app;

import com.finsight.service.SimulationApplicationService;
import com.finsight.service.SimulationResult;
import com.finsight.simulation.comparison.ScenarioComparison;
import com.finsight.simulation.model.MonthlyFinancialState;
import com.finsight.simulation.model.SimulationParameters;
import com.finsight.simulation.scenario.ExpenseChangeEvent;
import com.finsight.simulation.scenario.Scenario;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Milestone 5F's core integration test: proves the exact scenario-comparison workflow
 * {@code FinSightApplication}'s "Run Scenario" button triggers actually works end to end against
 * a real, file-backed database seeded only by the existing, unmodified {@link DemoDataSeeder} —
 * no additional fake data, no mocks, no fakes. Exercises the real
 * {@link SimulationApplicationService}, the real {@code SimulationEngine} (both baseline and
 * scenario runs), and the real {@code ScenarioComparisonCalculator} — nothing here recomputes a
 * comparison figure itself; every assertion reads a value {@link SimulationResult} already
 * produced.
 *
 * <p>Uses the same fixed, deterministic default the UI pre-fills ("50000.00" new monthly
 * expenses — see {@code FinSightApplication.DEFAULT_NEW_MONTHLY_EXPENSES}) against the demo
 * profile's seeded 60,000.00 income / 40,000.00 expenses / 150,000.00 opening cash, so the exact
 * expected figures here are the same ones a person running the real GUI with its default value
 * would see.
 */
class ScenarioComparisonWorkflowTest {

    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 4, 1);
    private static final BigDecimal NEW_MONTHLY_EXPENSES = new BigDecimal("50000.00");

    private static SimulationParameters twelveMonthZeroGrowthParameters() {
        return new SimulationParameters(12, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE);
    }

    private static Scenario expenseIncreaseScenario() {
        return new Scenario(0L, "Increased monthly expenses",
                "Monthly expenses change to a new fixed amount, effective immediately.",
                AS_OF_DATE, List.of(new ExpenseChangeEvent(AS_OF_DATE, NEW_MONTHLY_EXPENSES)));
    }

    @Test
    void theExpenseIncreaseEventIsAppliedToEveryMonthOfTheScenarioProjection() throws IOException {
        Path tempDb = Files.createTempFile("finsight-scenario-comparison-workflow-test", ".db");
        Files.delete(tempDb);
        String jdbcUrl = "jdbc:sqlite:" + tempDb;
        try (CompositionRoot compositionRoot = new CompositionRoot(jdbcUrl)) {
            SimulationApplicationService simulationApplicationService = compositionRoot.getSimulationApplicationService();

            SimulationResult result = simulationApplicationService.runScenario(
                    AS_OF_DATE, twelveMonthZeroGrowthParameters(), expenseIncreaseScenario());

            List<MonthlyFinancialState> scenarioStates = result.getScenarioProjection().getStates();
            assertEquals(12, scenarioStates.size());
            for (MonthlyFinancialState state : scenarioStates) {
                assertEquals(0, NEW_MONTHLY_EXPENSES.compareTo(state.getMonthlyExpenses()),
                        "the ExpenseChangeEvent's effectiveDate (asOfDate) makes it effective from month 1 "
                                + "onward, so every one of the 12 simulated months must use the new expense figure");
            }
        } finally {
            Files.deleteIfExists(tempDb);
        }
    }

    @Test
    void theScenarioProjectionDiffersFromTheBaselineProjection() throws IOException {
        Path tempDb = Files.createTempFile("finsight-scenario-comparison-workflow-test", ".db");
        Files.delete(tempDb);
        String jdbcUrl = "jdbc:sqlite:" + tempDb;
        try (CompositionRoot compositionRoot = new CompositionRoot(jdbcUrl)) {
            SimulationApplicationService simulationApplicationService = compositionRoot.getSimulationApplicationService();

            SimulationResult result = simulationApplicationService.runScenario(
                    AS_OF_DATE, twelveMonthZeroGrowthParameters(), expenseIncreaseScenario());

            BigDecimal baselineFinalNetWorth = lastNetWorth(result.getBaselineProjection().getStates());
            BigDecimal scenarioFinalNetWorth = lastNetWorth(result.getScenarioProjection().getStates());

            assertNotEquals(0, baselineFinalNetWorth.compareTo(scenarioFinalNetWorth),
                    "a real expense increase must produce a genuinely different scenario outcome");
        } finally {
            Files.deleteIfExists(tempDb);
        }
    }

    /**
     * The full deterministic demo comparison, cross-checked against the exact figures independently
     * traced by hand: baseline flow is 60,000 − 40,000 = +20,000/month on a 150,000.00 opening
     * balance → month 12 = 390,000.00 (see the 5E discrepancy investigation). Scenario flow is
     * 60,000 − 50,000 = +10,000/month on the same opening balance → month 12 = 270,000.00. Every
     * figure asserted here is read from {@link SimulationResult#getComparison()} — the actual
     * {@code ScenarioComparisonCalculator} output — not recomputed in this test from the
     * projections.
     */
    @Test
    void producesTheExpectedDeterministicComparisonAgainstTheSeededDemoData() throws IOException {
        Path tempDb = Files.createTempFile("finsight-scenario-comparison-workflow-test", ".db");
        Files.delete(tempDb);
        String jdbcUrl = "jdbc:sqlite:" + tempDb;
        try (CompositionRoot compositionRoot = new CompositionRoot(jdbcUrl)) {
            SimulationApplicationService simulationApplicationService = compositionRoot.getSimulationApplicationService();

            SimulationResult result = simulationApplicationService.runScenario(
                    AS_OF_DATE, twelveMonthZeroGrowthParameters(), expenseIncreaseScenario());

            ScenarioComparison comparison = result.getComparison();
            assertEquals(0, new BigDecimal("390000.00").compareTo(comparison.getBaselineFinalNetWorth()));
            assertEquals(0, new BigDecimal("270000.00").compareTo(comparison.getScenarioFinalNetWorth()));
            assertEquals(0, new BigDecimal("-120000.00").compareTo(comparison.getNetWorthDifference()));
        } finally {
            Files.deleteIfExists(tempDb);
        }
    }

    private static BigDecimal lastNetWorth(List<MonthlyFinancialState> states) {
        return states.get(states.size() - 1).getNetWorth();
    }
}
