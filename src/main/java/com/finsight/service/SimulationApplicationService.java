package com.finsight.service;

import com.finsight.simulation.comparison.ScenarioComparison;
import com.finsight.simulation.comparison.ScenarioComparisonCalculator;
import com.finsight.simulation.engine.SimulationEngine;
import com.finsight.simulation.model.FinancialSnapshot;
import com.finsight.simulation.model.Projection;
import com.finsight.simulation.model.SimulationParameters;
import com.finsight.simulation.scenario.Scenario;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Application-layer orchestration for the complete "build a snapshot, run the baseline, run a
 * scenario against that same snapshot and assumptions, compare them" use case:
 * {@link SnapshotBuilder} &rarr; {@code SimulationEngine} &rarr; {@code ScenarioComparisonCalculator}.
 *
 * <p>This class performs no calculation of its own — it is orchestration only. {@code
 * SimulationEngine} and {@code ScenarioComparisonCalculator} are stateless, pure, static-method
 * utility classes (like every calculator in {@code com.finsight.simulation.calculator}), so, just
 * as {@link SnapshotBuilder} itself calls {@code AccountBalanceCalculator} directly rather than
 * having it injected, this class calls them directly rather than taking them as constructor
 * dependencies. {@link SnapshotBuilder} is the only injected dependency, since it is the one piece
 * of this workflow that genuinely needs to vary between contexts (a fake-repository-backed
 * instance in a unit test, a real JDBC-backed instance in production or an integration test) —
 * this class never constructs or depends on a repository or JDBC type itself.
 *
 * <p><b>FX rate ownership (locked architectural rule):</b> {@link SimulationParameters#getUsdToInrRate()}
 * is the single source of truth for the simulation's USD/INR rate. {@link #runScenario} passes
 * exactly that value to {@link SnapshotBuilder#build}, and passes the same {@code parameters}
 * instance, unchanged, to both {@code SimulationEngine} calls — so the snapshot's cash
 * consolidation and the simulation's own USD-denominated investment conversion always agree on one
 * rate. This class introduces no second FX-rate parameter, and derives no rate from system time,
 * database state, environment variables, or a live service.
 *
 * <p><b>Exactly one {@link FinancialSnapshot} is built per {@link #runScenario} call</b> and reused
 * for both the baseline and scenario simulations; this class never calls
 * {@link SnapshotBuilder#build} more than once per invocation, and never rebuilds the snapshot
 * between the baseline and scenario runs.
 *
 * <p>This class depends only on {@link SnapshotBuilder} (itself repository-interface-only — see
 * its own documentation) and on the simulation-layer types it orchestrates. It imports no JDBC
 * type, no repository implementation, and no JavaFX type, contains no financial formula of its
 * own, and never mutates any {@link FinancialSnapshot}, {@link SimulationParameters}, or
 * {@link Scenario} it is given or builds.
 */
public final class SimulationApplicationService {

    private final SnapshotBuilder snapshotBuilder;

    public SimulationApplicationService(SnapshotBuilder snapshotBuilder) {
        this.snapshotBuilder = Objects.requireNonNull(snapshotBuilder, "snapshotBuilder must not be null");
    }

    /**
     * Builds the financial snapshot as of {@code asOfDate} (consolidating cash using
     * {@code parameters.getUsdToInrRate()}), runs the baseline simulation against it, runs
     * {@code scenario} against that same snapshot and the same {@code parameters}, and returns
     * both projections together with the comparison between them.
     *
     * <p>Workflow, in order: build one snapshot &rarr; run baseline &rarr; run scenario &rarr;
     * compare &rarr; return. The snapshot is never rebuilt between the baseline and scenario runs.
     *
     * @param asOfDate the snapshot's point-in-time, passed straight through to
     *     {@link SnapshotBuilder#build} — never computed from system time here
     * @param parameters the simulation assumptions used, unchanged, for both the baseline and
     *     scenario runs; also the sole source of the FX rate used to build the snapshot
     * @param scenario the scenario to run against the baseline; only {@link Scenario#getEvents()}
     *     is used, passed to {@code SimulationEngine} exactly as given
     * @throws NullPointerException if {@code asOfDate}, {@code parameters}, or {@code scenario} is
     *     {@code null}
     * @throws ApplicationValidationException if {@link SnapshotBuilder#build} cannot produce a
     *     snapshot (e.g. no {@code FinancialProfile} has been recorded yet, or an account's
     *     currency is unsupported) — propagated unchanged
     * @throws IllegalArgumentException if {@code scenario}'s events conflict, or target an
     *     investment id absent from the built snapshot — propagated unchanged from
     *     {@code SimulationEngine.simulate}
     */
    public SimulationResult runScenario(LocalDate asOfDate, SimulationParameters parameters, Scenario scenario) {
        Objects.requireNonNull(asOfDate, "asOfDate must not be null");
        Objects.requireNonNull(parameters, "parameters must not be null");
        Objects.requireNonNull(scenario, "scenario must not be null");

        FinancialSnapshot snapshot = snapshotBuilder.build(asOfDate, parameters.getUsdToInrRate());

        Projection baselineProjection = SimulationEngine.simulate(snapshot, parameters);
        Projection scenarioProjection = SimulationEngine.simulate(snapshot, parameters, scenario.getEvents());

        ScenarioComparison comparison = ScenarioComparisonCalculator.compare(baselineProjection, scenarioProjection);

        return new SimulationResult(baselineProjection, scenarioProjection, comparison);
    }
}
