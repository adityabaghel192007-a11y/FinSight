package com.finsight.service;

import com.finsight.simulation.comparison.ScenarioComparison;
import com.finsight.simulation.model.Projection;

import java.util.Objects;

/**
 * The complete output of {@link SimulationApplicationService#runScenario}: the baseline
 * {@link Projection}, the scenario {@link Projection} run against the same financial snapshot and
 * assumptions, and the {@link ScenarioComparison} between them.
 *
 * <p>This is a plain result holder — like {@link Projection} and {@link ScenarioComparison}
 * themselves, it contains no calculation logic. It does not recompute or re-derive
 * {@code comparison} from {@code baselineProjection}/{@code scenarioProjection}; it merely carries
 * whatever the three values it is given are, exactly as
 * {@link SimulationApplicationService#runScenario} produced them.
 *
 * <p>Instances are immutable and validated eagerly in the constructor: none of the three fields
 * may be {@code null}. There are no setters and no mutable collections — every field is itself
 * an already-immutable value object.
 *
 * <p><b>Equality:</b> a result object, not a persisted entity — equality is based on all fields,
 * matching the convention used by {@link Projection} and {@link ScenarioComparison} themselves.
 */
public final class SimulationResult {

    private final Projection baselineProjection;
    private final Projection scenarioProjection;
    private final ScenarioComparison comparison;

    public SimulationResult(Projection baselineProjection, Projection scenarioProjection, ScenarioComparison comparison) {
        this.baselineProjection = Objects.requireNonNull(baselineProjection, "baselineProjection must not be null");
        this.scenarioProjection = Objects.requireNonNull(scenarioProjection, "scenarioProjection must not be null");
        this.comparison = Objects.requireNonNull(comparison, "comparison must not be null");
    }

    public Projection getBaselineProjection() {
        return baselineProjection;
    }

    public Projection getScenarioProjection() {
        return scenarioProjection;
    }

    public ScenarioComparison getComparison() {
        return comparison;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SimulationResult other)) {
            return false;
        }
        return baselineProjection.equals(other.baselineProjection)
                && scenarioProjection.equals(other.scenarioProjection)
                && comparison.equals(other.comparison);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baselineProjection, scenarioProjection, comparison);
    }

    @Override
    public String toString() {
        return "SimulationResult{baselineProjection=" + baselineProjection
                + ", scenarioProjection=" + scenarioProjection
                + ", comparison=" + comparison
                + '}';
    }
}
