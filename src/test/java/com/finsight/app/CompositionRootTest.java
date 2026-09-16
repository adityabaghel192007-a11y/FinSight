package com.finsight.app;

import com.finsight.domain.financialprofile.FinancialProfile;
import com.finsight.repository.RepositoryException;
import com.finsight.repository.jdbc.FinancialProfileRepositoryJdbc;
import com.finsight.repository.jdbc.SchemaInitializer;
import com.finsight.repository.jdbc.SqliteConnectionProvider;
import com.finsight.service.SimulationApplicationService;
import com.finsight.service.SimulationResult;
import com.finsight.service.TransactionApplicationService;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real, in-memory/real-SQLite integration tests for {@link CompositionRoot} — no mocked or faked
 * repository anywhere, matching this project's existing {@code *IntegrationTest} convention (see
 * e.g. {@code TransactionApplicationServiceIntegrationTest},
 * {@code SimulationApplicationServiceIntegrationTest}). Every assertion here is driven through
 * {@link CompositionRoot}'s own public accessors — {@link CompositionRoot#getSimulationApplicationService()}
 * and {@link CompositionRoot#getTransactionApplicationService()} — since it deliberately exposes
 * no repository or connection of its own (see its class-level Javadoc on avoiding speculative
 * accessors).
 */
class CompositionRootTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 4, 1);

    private static SimulationParameters parameters() {
        return new SimulationParameters(1, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE);
    }

    private static Scenario emptyScenario() {
        return new Scenario(1L, "Test scenario", null, AS_OF, List.of());
    }

    // ===================== 1. schema initialization =====================

    /**
     * {@link CompositionRoot}'s constructor must have actually run {@code SchemaInitializer} —
     * not merely opened a connection — before returning. This is proven with an existing, real
     * operation rather than by inspecting a field: {@code runScenario} against a freshly
     * constructed database must succeed and produce a real projection.
     *
     * <p><b>Changed by Milestone 5E:</b> before {@link DemoDataSeeder} existed, this test proved
     * schema initialization by asserting the <em>opposite</em> — that {@code runScenario} against
     * a still-empty database failed with {@code ApplicationValidationException} ("no
     * FinancialProfile recorded yet"), rather than a {@link RepositoryException} wrapping a
     * "no such table" {@code SQLException}. That premise no longer holds: {@link CompositionRoot}
     * now seeds a demo {@code FinancialProfile} (and one demo {@code Account}) into any database
     * that has never had a profile recorded — see {@link DemoDataSeeder} — so a fresh
     * {@code CompositionRoot} is no longer "empty" by the time this test can observe it. A
     * successful {@code runScenario} call is an equally direct proof of the same fact this test
     * has always verified: it could not succeed at all if the {@code financial_profile} table (or
     * any other table {@code SnapshotBuilder} reads) had never been created.
     */
    @Test
    void constructorInitializesTheSchemaAndSeedsDemoDataSoAFreshRunScenarioCallSucceeds() {
        try (CompositionRoot context = new CompositionRoot("jdbc:sqlite::memory:")) {
            SimulationResult result = context.getSimulationApplicationService()
                    .runScenario(AS_OF, parameters(), emptyScenario());

            assertNotNull(result);
            assertFalse(result.getBaselineProjection().getStates().isEmpty(),
                    "a real projection must have been produced, which is only possible once the "
                            + "schema exists and a FinancialProfile has been persisted into it");
        }
    }

    // ===================== 2. service wiring =====================

    /**
     * Proves {@code CompositionRoot -> repositories -> SnapshotBuilder ->
     * SimulationApplicationService} is genuinely wired, end to end, with a real scenario run that
     * actually succeeds — not merely one that fails validation. Since {@link CompositionRoot}
     * deliberately exposes no repository, the one persisted {@link FinancialProfile} row this
     * needs is seeded through a second, directly-constructed {@link FinancialProfileRepositoryJdbc}
     * against the same real SQLite database, before {@link CompositionRoot} itself opens its own
     * connection to it. A real temp file is used for this one test instead of
     * {@code jdbc:sqlite::memory:}: two separate {@code :memory:} connections are two entirely
     * separate, unshared databases, so seeding one would never be visible to the other.
     */
    @Test
    void servicesAreGenuinelyWiredThroughToARealSuccessfulScenarioRun() throws IOException {
        Path tempDb = Files.createTempFile("finsight-composition-root-test", ".db");
        String jdbcUrl = "jdbc:sqlite:" + tempDb;
        try {
            try (SqliteConnectionProvider seedConnection = new SqliteConnectionProvider(jdbcUrl)) {
                SchemaInitializer.initialize(seedConnection.getConnection());
                new FinancialProfileRepositoryJdbc(seedConnection)
                        .save(new FinancialProfile(0L, new BigDecimal("3000.00"), new BigDecimal("1200.00")));
            }

            try (CompositionRoot context = new CompositionRoot(jdbcUrl)) {
                SimulationApplicationService simulationApplicationService = context.getSimulationApplicationService();
                assertNotNull(simulationApplicationService);

                SimulationResult result = simulationApplicationService.runScenario(AS_OF, parameters(), emptyScenario());

                assertEquals(1, result.getBaselineProjection().getStates().size());
                BigDecimal month1Income = result.getBaselineProjection().getStates().get(0).getMonthlyIncome();
                assertEquals(0, new BigDecimal("3000.00").compareTo(month1Income),
                        "the profile seeded through a separate connection must be exactly what "
                                + "CompositionRoot's own internally-wired repositories read back");
            }
        } finally {
            Files.deleteIfExists(tempDb);
        }
    }

    /**
     * {@link CompositionRoot} deliberately exposes no {@code AccountRepository}/
     * {@code CategoryRepository} accessor (see its own Javadoc), and every real
     * {@link TransactionApplicationService} workflow ({@code recordIncome}, {@code recordExpense},
     * {@code recordTransfer}) requires at least one persisted {@link com.finsight.domain.account.Account}
     * to already exist, which cannot be created through {@link CompositionRoot}'s own exposed API
     * without expanding its accessor surface beyond what this milestone calls for. This test
     * therefore verifies construction only.
     */
    @Test
    void transactionApplicationServiceIsConstructed() {
        try (CompositionRoot context = new CompositionRoot("jdbc:sqlite::memory:")) {
            TransactionApplicationService transactionApplicationService = context.getTransactionApplicationService();

            assertNotNull(transactionApplicationService);
        }
    }

    // ===================== 3. resource lifecycle =====================

    @Test
    void closeClosesTheUnderlyingDatabaseConnection() {
        CompositionRoot context = new CompositionRoot("jdbc:sqlite::memory:");

        context.close();

        assertThrows(RepositoryException.class,
                () -> context.getSimulationApplicationService().runScenario(AS_OF, parameters(), emptyScenario()),
                "once closed, the underlying SqliteConnectionProvider's connection is closed, so "
                        + "any further repository operation through it must fail");
    }

    // ===================== 4. file-backed persistence (Milestone 5D) =====================
    //
    // Every test below uses a real temporary *file* JDBC URL, never DatabaseLocation's own
    // jdbcUrl() (which always resolves under the current user's real home directory) — these
    // tests must never create or touch the real ~/.finsight/finsight.db application database.

    /**
     * Proves {@link CompositionRoot} genuinely opens a file-backed SQLite database rather than
     * merely accepting a file URL without writing anything real to disk: after construction, the
     * database file itself must exist on disk and be non-empty (SQLite writes its header and the
     * schema this constructor initializes immediately, not lazily on first application write).
     */
    @Test
    void constructingAgainstAFileBackedUrlCreatesTheDatabaseFileOnDisk() throws IOException {
        Path tempDb = Files.createTempFile("finsight-composition-root-file-backed-test", ".db");
        Files.delete(tempDb); // SQLite must create this file itself, not merely open an existing one
        String jdbcUrl = "jdbc:sqlite:" + tempDb;
        try {
            try (CompositionRoot context = new CompositionRoot(jdbcUrl)) {
                assertNotNull(context.getSimulationApplicationService());
            }

            assertTrue(Files.exists(tempDb), "CompositionRoot must create the database file on disk");
            assertTrue(Files.size(tempDb) > 0,
                    "the created file must actually contain SQLite's header and the initialized schema, "
                            + "not be an empty placeholder");
        } finally {
            Files.deleteIfExists(tempDb);
        }
    }

    /**
     * Proves data survives across two entirely separate {@link CompositionRoot} lifecycles
     * (construct, use, close — then construct again) against the same file-backed database, which
     * is the whole point of moving off {@code jdbc:sqlite::memory:}: a real user closes and
     * reopens the application repeatedly and expects their data to still be there.
     *
     * <p>Sequence: a first {@link CompositionRoot} is constructed against a real temp file (this
     * alone initializes the schema on disk) and then closed immediately, with nothing else done
     * through it, to prove the schema itself is what persists, not merely an object held open in
     * memory. A {@link FinancialProfile} row is then written directly against that same file via a
     * separate, directly-constructed connection (proving the on-disk schema from the first
     * lifecycle is real and usable on its own). Finally, a <em>second</em>, independently
     * constructed {@link CompositionRoot} is opened against that same file and must read that row
     * back through a real, successful {@code runScenario} call — proving the second lifecycle's
     * own internally-wired repositories see exactly what an entirely separate prior lifecycle left
     * on disk.
     */
    @Test
    void dataPersistsAcrossSeparateCompositionRootLifecyclesAgainstTheSameFile() throws IOException {
        Path tempDb = Files.createTempFile("finsight-composition-root-persistence-test", ".db");
        Files.delete(tempDb);
        String jdbcUrl = "jdbc:sqlite:" + tempDb;
        try {
            try (CompositionRoot firstLifecycle = new CompositionRoot(jdbcUrl)) {
                assertNotNull(firstLifecycle.getSimulationApplicationService());
            }
            assertTrue(Files.exists(tempDb), "the first lifecycle must leave a real file behind after closing");

            try (SqliteConnectionProvider directConnection = new SqliteConnectionProvider(jdbcUrl)) {
                new FinancialProfileRepositoryJdbc(directConnection)
                        .save(new FinancialProfile(0L, new BigDecimal("3000.00"), new BigDecimal("1200.00")));
            }

            try (CompositionRoot secondLifecycle = new CompositionRoot(jdbcUrl)) {
                SimulationResult result = secondLifecycle.getSimulationApplicationService()
                        .runScenario(AS_OF, parameters(), emptyScenario());

                BigDecimal month1Income = result.getBaselineProjection().getStates().get(0).getMonthlyIncome();
                assertEquals(0, new BigDecimal("3000.00").compareTo(month1Income),
                        "a second, independently constructed CompositionRoot must read back exactly "
                                + "what an entirely separate, already-closed prior CompositionRoot "
                                + "lifecycle left persisted on disk");
            }
        } finally {
            Files.deleteIfExists(tempDb);
        }
    }
}
