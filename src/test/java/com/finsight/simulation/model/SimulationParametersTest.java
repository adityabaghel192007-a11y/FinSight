package com.finsight.simulation.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimulationParametersTest {

    private static SimulationParameters valid() {
        return new SimulationParameters(24, new BigDecimal("0.05"), new BigDecimal("0.03"), new BigDecimal("83.00"));
    }

    @Test
    void constructsWithValidArguments() {
        SimulationParameters params = valid();

        assertEquals(24, params.getProjectionMonths());
        assertEquals(new BigDecimal("0.05"), params.getAnnualIncomeGrowthRate());
        assertEquals(new BigDecimal("0.03"), params.getAnnualExpenseGrowthRate());
        assertEquals(new BigDecimal("83.00"), params.getUsdToInrRate());
    }

    @Test
    void allowsZeroGrowthRates() {
        SimulationParameters params = new SimulationParameters(12, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83"));

        assertEquals(0, BigDecimal.ZERO.compareTo(params.getAnnualIncomeGrowthRate()));
        assertEquals(0, BigDecimal.ZERO.compareTo(params.getAnnualExpenseGrowthRate()));
    }

    @Test
    void rejectsZeroProjectionMonths() {
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationParameters(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE));
    }

    @Test
    void rejectsNegativeProjectionMonths() {
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationParameters(-1, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE));
    }

    @Test
    void rejectsNullIncomeGrowthRate() {
        assertThrows(NullPointerException.class,
                () -> new SimulationParameters(12, null, BigDecimal.ZERO, BigDecimal.ONE));
    }

    @Test
    void rejectsNegativeIncomeGrowthRate() {
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationParameters(12, new BigDecimal("-0.01"), BigDecimal.ZERO, BigDecimal.ONE));
    }

    @Test
    void rejectsNullExpenseGrowthRate() {
        assertThrows(NullPointerException.class,
                () -> new SimulationParameters(12, BigDecimal.ZERO, null, BigDecimal.ONE));
    }

    @Test
    void rejectsNegativeExpenseGrowthRate() {
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationParameters(12, BigDecimal.ZERO, new BigDecimal("-0.01"), BigDecimal.ONE));
    }

    @Test
    void rejectsNullUsdToInrRate() {
        assertThrows(NullPointerException.class,
                () -> new SimulationParameters(12, BigDecimal.ZERO, BigDecimal.ZERO, null));
    }

    @Test
    void rejectsZeroUsdToInrRate() {
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationParameters(12, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void rejectsNegativeUsdToInrRate() {
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationParameters(12, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("-1")));
    }

    @Test
    void equalityIsBasedOnCompleteStateIgnoringScale() {
        SimulationParameters a = valid();
        SimulationParameters b = new SimulationParameters(24, new BigDecimal("0.050"), new BigDecimal("0.030"), new BigDecimal("83"));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        SimulationParameters different = new SimulationParameters(12, new BigDecimal("0.05"), new BigDecimal("0.03"), new BigDecimal("83.00"));
        assertNotEquals(a, different);
    }
}
