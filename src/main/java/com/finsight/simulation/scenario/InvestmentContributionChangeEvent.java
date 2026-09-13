package com.finsight.simulation.scenario;

import com.finsight.domain.investment.Investment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A scenario event that changes a specific investment's monthly contribution to a new absolute
 * amount from {@link #getEffectiveDate()} onward.
 *
 * <p>{@link #getInvestmentId()} identifies the target investment by id — matching
 * {@link Investment#getId()} — rather than embedding the {@link Investment} object itself, the
 * same way {@code Budget} and {@code Transaction} reference other entities by id rather than by
 * object reference. This class does not verify that the id refers to an investment that actually
 * exists in a given {@code FinancialSnapshot}; resolving it is the simulation layer's job when
 * this event is applied. {@link #getNewMonthlyContribution()} is the new contribution amount
 * itself, not a delta, for the same unambiguity reason as {@link IncomeChangeEvent}.
 *
 * <p>Instances are immutable and validated eagerly in the constructor.
 *
 * <p><b>Equality:</b> a value, not a persisted entity — equality is based on all fields, with
 * {@link BigDecimal} compared using {@link BigDecimal#compareTo} (numeric value, ignoring scale),
 * matching the convention used throughout {@code com.finsight.simulation}.
 */
public final class InvestmentContributionChangeEvent extends ScenarioEvent {

    private final long investmentId;
    private final BigDecimal newMonthlyContribution;

    public InvestmentContributionChangeEvent(LocalDate effectiveDate, long investmentId, BigDecimal newMonthlyContribution) {
        super(effectiveDate);
        if (investmentId < 0) {
            throw new IllegalArgumentException("investmentId must be >= 0");
        }
        Objects.requireNonNull(newMonthlyContribution, "newMonthlyContribution must not be null");
        if (newMonthlyContribution.signum() < 0) {
            throw new IllegalArgumentException("newMonthlyContribution must be >= 0");
        }

        this.investmentId = investmentId;
        this.newMonthlyContribution = newMonthlyContribution;
    }

    /**
     * The id of the {@link Investment} this event targets.
     */
    public long getInvestmentId() {
        return investmentId;
    }

    /**
     * The new monthly contribution amount for the targeted investment, effective from
     * {@link #getEffectiveDate()}.
     */
    public BigDecimal getNewMonthlyContribution() {
        return newMonthlyContribution;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InvestmentContributionChangeEvent other)) {
            return false;
        }
        return getEffectiveDate().equals(other.getEffectiveDate())
                && investmentId == other.investmentId
                && newMonthlyContribution.compareTo(other.newMonthlyContribution) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getEffectiveDate(), investmentId, newMonthlyContribution.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return "InvestmentContributionChangeEvent{effectiveDate=" + getEffectiveDate()
                + ", investmentId=" + investmentId
                + ", newMonthlyContribution=" + newMonthlyContribution
                + '}';
    }
}
