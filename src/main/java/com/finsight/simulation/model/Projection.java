package com.finsight.simulation.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * The complete output of a simulation run: an ordered sequence of {@link MonthlyFinancialState}
 * covering {@code [startDate, endDate]}.
 *
 * <p>This is a value/result object, not a database entity, and holds no simulation logic of its
 * own. It does not reconcile {@code endDate} against {@code states.size()} — producing a
 * self-consistent date range and state sequence is the {@code SimulationEngine}'s responsibility,
 * not something this class infers or enforces beyond the one basic invariant that
 * {@code endDate} is not before {@code startDate}.
 *
 * <p>Instances are immutable. {@link #getStates()} returns an unmodifiable view backed by a
 * defensive copy taken at construction time ({@link List#copyOf}), so the internal list can
 * never be mutated by callers, and neither the list nor any of its elements may be null.
 *
 * <p><b>Equality:</b> a result object, not a persisted entity — equality is based on all fields.
 * {@code states} equality relies on {@link MonthlyFinancialState}'s own {@code equals}.
 */
public final class Projection {

    private final LocalDate startDate;
    private final LocalDate endDate;
    private final List<MonthlyFinancialState> states;

    public Projection(LocalDate startDate, LocalDate endDate, List<MonthlyFinancialState> states) {
        Objects.requireNonNull(startDate, "startDate must not be null");
        Objects.requireNonNull(endDate, "endDate must not be null");
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must not be before startDate");
        }
        Objects.requireNonNull(states, "states must not be null");

        this.startDate = startDate;
        this.endDate = endDate;
        this.states = List.copyOf(states);
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public List<MonthlyFinancialState> getStates() {
        return states;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Projection other)) {
            return false;
        }
        return startDate.equals(other.startDate)
                && endDate.equals(other.endDate)
                && states.equals(other.states);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startDate, endDate, states);
    }

    @Override
    public String toString() {
        return "Projection{startDate=" + startDate
                + ", endDate=" + endDate
                + ", states=" + states
                + '}';
    }
}
