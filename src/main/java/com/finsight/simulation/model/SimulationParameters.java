package com.finsight.simulation.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The assumptions that control a simulation run, independent of any particular
 * {@link FinancialSnapshot}.
 *
 * <p>Deliberately does not repeat each investment's expected return — that already lives on
 * {@code Investment.getExpectedAnnualReturn()} and belongs there, not duplicated here.
 *
 * <p>Instances are immutable and validated eagerly in the constructor.
 *
 * <p><b>Equality:</b> a plain assumptions holder, not a persisted entity — equality is based on
 * all fields, with {@link BigDecimal} fields compared using {@link BigDecimal#compareTo}
 * (numeric value, ignoring scale), matching the convention used elsewhere in this package.
 */
public final class SimulationParameters {

    private final int projectionMonths;
    private final BigDecimal annualIncomeGrowthRate;
    private final BigDecimal annualExpenseGrowthRate;
    private final BigDecimal usdToInrRate;

    public SimulationParameters(
            int projectionMonths,
            BigDecimal annualIncomeGrowthRate,
            BigDecimal annualExpenseGrowthRate,
            BigDecimal usdToInrRate) {
        if (projectionMonths <= 0) {
            throw new IllegalArgumentException("projectionMonths must be > 0");
        }
        Objects.requireNonNull(annualIncomeGrowthRate, "annualIncomeGrowthRate must not be null");
        if (annualIncomeGrowthRate.signum() < 0) {
            throw new IllegalArgumentException("annualIncomeGrowthRate must be >= 0");
        }
        Objects.requireNonNull(annualExpenseGrowthRate, "annualExpenseGrowthRate must not be null");
        if (annualExpenseGrowthRate.signum() < 0) {
            throw new IllegalArgumentException("annualExpenseGrowthRate must be >= 0");
        }
        Objects.requireNonNull(usdToInrRate, "usdToInrRate must not be null");
        if (usdToInrRate.signum() <= 0) {
            throw new IllegalArgumentException("usdToInrRate must be strictly > 0");
        }

        this.projectionMonths = projectionMonths;
        this.annualIncomeGrowthRate = annualIncomeGrowthRate;
        this.annualExpenseGrowthRate = annualExpenseGrowthRate;
        this.usdToInrRate = usdToInrRate;
    }

    public int getProjectionMonths() {
        return projectionMonths;
    }

    public BigDecimal getAnnualIncomeGrowthRate() {
        return annualIncomeGrowthRate;
    }

    public BigDecimal getAnnualExpenseGrowthRate() {
        return annualExpenseGrowthRate;
    }

    public BigDecimal getUsdToInrRate() {
        return usdToInrRate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SimulationParameters other)) {
            return false;
        }
        return projectionMonths == other.projectionMonths
                && annualIncomeGrowthRate.compareTo(other.annualIncomeGrowthRate) == 0
                && annualExpenseGrowthRate.compareTo(other.annualExpenseGrowthRate) == 0
                && usdToInrRate.compareTo(other.usdToInrRate) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                projectionMonths,
                annualIncomeGrowthRate.stripTrailingZeros(),
                annualExpenseGrowthRate.stripTrailingZeros(),
                usdToInrRate.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return "SimulationParameters{projectionMonths=" + projectionMonths
                + ", annualIncomeGrowthRate=" + annualIncomeGrowthRate
                + ", annualExpenseGrowthRate=" + annualExpenseGrowthRate
                + ", usdToInrRate=" + usdToInrRate
                + '}';
    }
}
