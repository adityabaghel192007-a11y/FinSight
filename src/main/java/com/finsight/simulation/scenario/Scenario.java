package com.finsight.simulation.scenario;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A named, saved collection of {@link ScenarioEvent}s — a "what if" hypothesis a user has built up
 * (e.g. "what if I switch jobs and increase my SIP") — that can later be handed to the
 * {@code SimulationEngine} alongside a {@code FinancialSnapshot} and {@code SimulationParameters}.
 *
 * <p>Unlike the individual {@link ScenarioEvent}s it holds, a {@code Scenario} is a named,
 * identifiable thing a user creates, saves, and revisits — much closer in shape to the entities in
 * {@code com.finsight.domain} (an {@code Account}, a {@code Goal}) than to the plain calculated
 * value objects in this package's sibling packages. It carries an id and a creation date for that
 * reason, even though it currently lives alongside the simulation types it is built from rather
 * than in {@code com.finsight.domain}, since scenario modeling is a simulation-layer concern.
 *
 * <p>{@link #getDescription()} is genuinely optional — a scenario's {@link #getName()} alone may
 * be enough to identify it — and is exposed as an {@link Optional}, matching how
 * {@code Transaction} handles its own optional {@code description} field. {@link #getCreatedAt()}
 * is always supplied by the caller, never computed internally via {@code LocalDate.now()}, keeping
 * construction as deterministic as everything else in this project.
 *
 * <p>Instances are immutable and validated eagerly in the constructor. {@link #getEvents()}
 * returns an unmodifiable view backed by a defensive copy taken at construction time
 * ({@link List#copyOf}), so the internal list can never be mutated by callers, and neither the
 * list nor any of its elements may be null.
 *
 * <p>This class holds no event-application logic — building the events themselves and later
 * applying them to a simulation are separate concerns handled elsewhere.
 *
 * <p><b>Equality:</b> {@code Scenario} is an entity (it has an id a user assigns and revisits), so
 * {@link #equals(Object)} and {@link #hashCode()} are based solely on {@link #getId() id}, not on
 * every field — matching the convention used throughout {@code com.finsight.domain} (e.g.
 * {@code Account}, {@code Goal}). This is only meaningful once a scenario has been assigned a
 * real, persisted id; comparing two not-yet-persisted instances that happen to share a placeholder
 * id is not reliable, but assigning real ids is a persistence-layer concern for a later milestone.
 */
public final class Scenario {

    private final long id;
    private final String name;
    private final String description;
    private final LocalDate createdAt;
    private final List<ScenarioEvent> events;

    public Scenario(long id, String name, String description, LocalDate createdAt, List<ScenarioEvent> events) {
        if (id < 0) {
            throw new IllegalArgumentException("id must be >= 0");
        }
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(events, "events must not be null");

        this.id = id;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
        this.events = List.copyOf(events);
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    public LocalDate getCreatedAt() {
        return createdAt;
    }

    public List<ScenarioEvent> getEvents() {
        return events;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Scenario other)) {
            return false;
        }
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "Scenario{id=" + id
                + ", name='" + name + '\''
                + ", description='" + description + '\''
                + ", createdAt=" + createdAt
                + ", events=" + events
                + '}';
    }
}
