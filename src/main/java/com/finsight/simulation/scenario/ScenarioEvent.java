package com.finsight.simulation.scenario;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A single, strongly-typed change to apply to a baseline simulation from a given date onward (or,
 * for a one-time event, in the month that date falls in).
 *
 * <p>This is a sealed abstraction over the MVP event types — {@link IncomeChangeEvent},
 * {@link ExpenseChangeEvent}, {@link InvestmentContributionChangeEvent},
 * {@link InvestmentReturnChangeEvent}, and {@link OneTimeExpenseEvent} — rather than a generic
 * {@code Map<String, Object>} bag of fields: each concrete event carries exactly the strongly
 * typed data it needs (using {@link java.math.BigDecimal} for money/rates and {@link LocalDate}
 * for dates, matching the rest of this project), and the closed {@code permits} list means the
 * compiler — not a runtime check — enforces that only these five kinds of event exist.
 *
 * <p>Every event, regardless of kind, has an {@link #getEffectiveDate() effectiveDate}: the date
 * from which the change applies (for a one-time event, the date the outflow occurs). Beyond that,
 * this class intentionally holds no event-application logic — deciding how an event actually
 * changes a running simulation is the {@code SimulationEngine}'s job in a later milestone, not
 * something these data-only classes compute themselves.
 *
 * <p>Instances are immutable. As with the simulation model/calculator value objects elsewhere in
 * this project (e.g. {@code MonthlyFinancialState}), events carry no id of their own — a
 * {@code ScenarioEvent} is a value, not a persisted entity — so subclasses base equality on their
 * complete field set rather than on identity.
 */
public abstract sealed class ScenarioEvent
        permits IncomeChangeEvent, ExpenseChangeEvent, InvestmentContributionChangeEvent,
                InvestmentReturnChangeEvent, OneTimeExpenseEvent {

    private final LocalDate effectiveDate;

    ScenarioEvent(LocalDate effectiveDate) {
        Objects.requireNonNull(effectiveDate, "effectiveDate must not be null");
        this.effectiveDate = effectiveDate;
    }

    /**
     * The date from which this event's change applies (for a one-time event, the date the
     * outflow occurs). Never null.
     */
    public final LocalDate getEffectiveDate() {
        return effectiveDate;
    }
}
