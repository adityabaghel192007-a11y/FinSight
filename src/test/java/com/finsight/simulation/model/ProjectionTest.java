package com.finsight.simulation.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectionTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 3, 1);

    private static MonthlyFinancialState monthlyState(LocalDate date) {
        return new MonthlyFinancialState(date, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static List<MonthlyFinancialState> threeStates() {
        return List.of(
                monthlyState(START),
                monthlyState(LocalDate.of(2026, 2, 1)),
                monthlyState(END));
    }

    @Test
    void constructsWithValidArguments() {
        Projection projection = new Projection(START, END, threeStates());

        assertEquals(START, projection.getStartDate());
        assertEquals(END, projection.getEndDate());
        assertEquals(3, projection.getStates().size());
    }

    @Test
    void allowsEmptyStatesList() {
        Projection projection = new Projection(START, END, List.of());

        assertTrue(projection.getStates().isEmpty());
    }

    @Test
    void allowsStartDateEqualToEndDate() {
        Projection projection = new Projection(START, START, List.of(monthlyState(START)));

        assertEquals(START, projection.getStartDate());
        assertEquals(START, projection.getEndDate());
    }

    @Test
    void rejectsNullStartDate() {
        assertThrows(NullPointerException.class, () -> new Projection(null, END, threeStates()));
    }

    @Test
    void rejectsNullEndDate() {
        assertThrows(NullPointerException.class, () -> new Projection(START, null, threeStates()));
    }

    @Test
    void rejectsNullStates() {
        assertThrows(NullPointerException.class, () -> new Projection(START, END, null));
    }

    @Test
    void rejectsEndDateBeforeStartDate() {
        assertThrows(IllegalArgumentException.class,
                () -> new Projection(END, START, threeStates()));
    }

    @Test
    void rejectsNullElementsInStates() {
        List<MonthlyFinancialState> withNull = new ArrayList<>();
        withNull.add(null);

        assertThrows(NullPointerException.class, () -> new Projection(START, END, withNull));
    }

    @Test
    void doesNotMutateCallersListAfterConstruction() {
        List<MonthlyFinancialState> states = new ArrayList<>(List.of(monthlyState(START)));
        Projection projection = new Projection(START, END, states);

        states.add(monthlyState(END));

        assertEquals(1, projection.getStates().size());
    }

    @Test
    void returnedStatesListIsImmutable() {
        Projection projection = new Projection(START, END, threeStates());

        assertThrows(UnsupportedOperationException.class,
                () -> projection.getStates().add(monthlyState(END)));
    }

    @Test
    void equalityIsBasedOnCompleteState() {
        Projection a = new Projection(START, END, threeStates());
        Projection b = new Projection(START, END, threeStates());

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        Projection different = new Projection(START, END, List.of());
        assertNotEquals(a, different);
    }
}
