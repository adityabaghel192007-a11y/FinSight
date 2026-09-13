package com.finsight.simulation.scenario;

import com.finsight.domain.investment.Investment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A scenario event that changes a specific investment's expected annual return to a new absolute
 * rate from {@link #getEffectiveDate()} onward (a shift to a more/less aggressive allocation, a
 * revised return assumption, ...).
 *
 * <p>{@link #getInvestmentId()} identifies the target investment by id — matching
 * {@link Investment#getId()} — the same way {@link InvestmentContributionChangeEvent} does; this
 * class likewise does not verify that the id refers to an investment that actually exists in a
 * given {@code FinancialSnapshot}. {@link #getNewExpectedAnnualReturn()} mirrors
 * {@link Investment#getExpectedAnnualReturn()}'s own meaning and validation (a non-negative annual
 * rate); how it is converted to a monthly rate remains the simulation layer's concern, via the
 * existing {@code InvestmentCalculator}, not something duplicated here.
 *
 * <p>Instances are immutable and validated eagerly in the constructor.
 *
 * <p><b>Equality:</b> a value, not a persisted entity — equality is based on all fields, with
 * {@link BigDecimal} compared using {@link BigDecimal#compareTo} (numeric value, ignoring scale),
 * matching the convention used throughout {@code com.finsight.simulation}.
 */
public final class InvestmentReturnChangeEvent extends ScenarioEvent {

    private final long investmentId;
    private final BigDecimal newExpectedAnnualReturn;

    public InvestmentReturnChangeEvent(LocalDate effectiveDate, long investmentId, BigDecimal newExpectedAnnualReturn) {
        super(effectiveDate);
        if (investmentId < 0) {
            throw new IllegalArgumentException("investmentId must be >= 0");
        }
        Objects.requireNonNull(newExpectedAnnualReturn, "newExpectedAnnualReturn must not be null");
        if (newExpectedAnnualReturn.signum() < 0) {
            throw new IllegalArgumentException("newExpectedAnnualReturn must be >= 0");
        }

        this.investmentId = investmentId;
        this.newExpectedAnnualReturn = newExpectedAnnualReturn;
    }

    /**
     * The id of the {@link Investment} this event targets.
     */
    public long getInvestmentId() {
        return investmentId;
    }

    /**
     * The new expected annual return for the targeted investment, effective from
     * {@link #getEffectiveDate()}.
     */
    public BigDecimal getNewExpectedAnnualReturn() {
        return newExpectedAnnualReturn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InvestmentReturnChangeEvent other)) {
            return false;
        }
        return getEffectiveDate().equals(other.getEffectiveDate())
                && investmentId == other.investmentId
                && newExpectedAnnualReturn.compareTo(other.newExpectedAnnualReturn) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getEffectiveDate(), investmentId, newExpectedAnnualReturn.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return "InvestmentReturnChangeEvent{effectiveDate=" + getEffectiveDate()
                + ", investmentId=" + investmentId
                + ", newExpectedAnnualReturn=" + newExpectedAnnualReturn
                + '}';
    }
}
