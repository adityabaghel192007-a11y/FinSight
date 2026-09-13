package com.finsight.simulation.scenario;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioTest {

    private static final LocalDate CREATED_AT = LocalDate.of(2026, 1, 1);

    private static IncomeChangeEvent incomeEvent() {
        return new IncomeChangeEvent(CREATED_AT.plusMonths(6), new BigDecimal("6000"));
    }

    private static OneTimeExpenseEvent expenseEvent() {
        return new OneTimeExpenseEvent(CREATED_AT.plusMonths(3), new BigDecimal("25000"));
    }

    private static Scenario scenario(long id, List<ScenarioEvent> events) {
        return new Scenario(id, "Job Change", "What if I switch jobs", CREATED_AT, events);
    }

    @Test
    void constructsWithValidArgumentsAndExposesAllFields() {
        List<ScenarioEvent> events = List.of(incomeEvent(), expenseEvent());
        Scenario s = scenario(1L, events);

        assertEquals(1L, s.getId());
        assertEquals("Job Change", s.getName());
        assertTrue(s.getDescription().isPresent());
        assertEquals("What if I switch jobs", s.getDescription().get());
        assertEquals(CREATED_AT, s.getCreatedAt());
        assertEquals(2, s.getEvents().size());
    }

    @Test
    void descriptionIsOptionalAndAbsentWhenNull() {
        Scenario s = new Scenario(1L, "No Description", null, CREATED_AT, List.of());

        assertTrue(s.getDescription().isEmpty());
    }

    @Test
    void allowsEmptyEventsList() {
        Scenario s = scenario(1L, List.of());

        assertTrue(s.getEvents().isEmpty());
    }

    @Test
    void rejectsNegativeId() {
        assertThrows(IllegalArgumentException.class,
                () -> new Scenario(-1L, "x", null, CREATED_AT, List.of()));
    }

    @Test
    void rejectsNullName() {
        assertThrows(NullPointerException.class,
                () -> new Scenario(1L, null, null, CREATED_AT, List.of()));
    }

    @Test
    void rejectsBlankName() {
        assertThrows(IllegalArgumentException.class,
                () -> new Scenario(1L, "   ", null, CREATED_AT, List.of()));
    }

    @Test
    void rejectsNullCreatedAt() {
        assertThrows(NullPointerException.class,
                () -> new Scenario(1L, "x", null, null, List.of()));
    }

    @Test
    void rejectsNullEvents() {
        assertThrows(NullPointerException.class,
                () -> new Scenario(1L, "x", null, CREATED_AT, null));
    }

    @Test
    void rejectsNullElementInEvents() {
        List<ScenarioEvent> withNull = new ArrayList<>();
        withNull.add(null);

        assertThrows(NullPointerException.class,
                () -> new Scenario(1L, "x", null, CREATED_AT, withNull));
    }

    @Test
    void doesNotMutateCallersListAfterConstruction() {
        List<ScenarioEvent> events = new ArrayList<>(List.of(incomeEvent()));
        Scenario s = scenario(1L, events);

        events.add(expenseEvent());

        assertEquals(1, s.getEvents().size());
    }

    @Test
    void returnedEventsListIsImmutable() {
        Scenario s = scenario(1L, List.of(incomeEvent()));

        assertThrows(UnsupportedOperationException.class, () -> s.getEvents().add(expenseEvent()));
    }

    @Test
    void equalityIsBasedOnIdOnly() {
        Scenario a = scenario(1L, List.of(incomeEvent()));
        Scenario sameId = new Scenario(1L, "Different Name", "different description",
                CREATED_AT.plusDays(10), List.of(expenseEvent()));
        Scenario differentId = scenario(2L, List.of(incomeEvent()));

        assertEquals(a, sameId);
        assertEquals(a.hashCode(), sameId.hashCode());
        assertNotEquals(a, differentId);
    }
}
