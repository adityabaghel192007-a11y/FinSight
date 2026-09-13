package com.finsight.simulation.engine;

import com.finsight.common.FinancialPrecision;
import com.finsight.domain.investment.Investment;
import com.finsight.domain.loan.Loan;
import com.finsight.simulation.calculator.AmortizationSchedule;
import com.finsight.simulation.calculator.InvestmentCalculator;
import com.finsight.simulation.calculator.LoanCalculator;
import com.finsight.simulation.calculator.LoanPayment;
import com.finsight.simulation.calculator.NetWorthCalculator;
import com.finsight.simulation.model.FinancialSnapshot;
import com.finsight.simulation.model.MonthlyFinancialState;
import com.finsight.simulation.model.Projection;
import com.finsight.simulation.model.SimulationParameters;
import com.finsight.simulation.scenario.ExpenseChangeEvent;
import com.finsight.simulation.scenario.IncomeChangeEvent;
import com.finsight.simulation.scenario.InvestmentContributionChangeEvent;
import com.finsight.simulation.scenario.InvestmentReturnChangeEvent;
import com.finsight.simulation.scenario.OneTimeExpenseEvent;
import com.finsight.simulation.scenario.ScenarioEvent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Runs the deterministic month-by-month financial simulation: given a starting
 * {@link FinancialSnapshot}, a set of {@link SimulationParameters}, and (optionally) a list of
 * {@link ScenarioEvent}s, produces the resulting {@link Projection} — how cash, investments,
 * loans, net worth, and cumulative savings evolve one simulated month at a time.
 *
 * <p>This class is stateless, like the calculators in {@code com.finsight.simulation.calculator}:
 * it holds no data between calls, only a pure function of its inputs. Every {@link #simulate} call
 * allocates its own local working state (running per-investment values, precomputed amortization
 * schedules, running income/expense/cash/cumulative-savings trackers, and the event bookkeeping
 * described below); nothing is cached on or shared with the {@link Investment}, {@link Loan},
 * {@link FinancialSnapshot}, {@link ScenarioEvent}, or any list they expose, and none of those
 * inputs is ever mutated. Because of this, calling {@link #simulate} twice with equal inputs always
 * produces an equal {@link Projection} — there is no {@link LocalDate#now()}, no randomness, no
 * live data, and no other non-deterministic source anywhere in this class.
 *
 * <h2>Timeline</h2>
 * <p>The simulation starts at {@link FinancialSnapshot#getAsOfDate()}. That starting point itself
 * is never emitted as a {@link MonthlyFinancialState} — the returned {@link Projection} has
 * exactly {@link SimulationParameters#getProjectionMonths()} states, one per simulated month, each
 * dated {@code asOfDate.plusMonths(monthIndex)} for {@code monthIndex} running {@code 1..
 * projectionMonths}. {@code Projection.getStartDate()} is {@code asOfDate}; {@code
 * Projection.getEndDate()} is the date of the final simulated month.
 *
 * <h2>Annual income/expense growth convention</h2>
 * <p>{@link SimulationParameters#getAnnualIncomeGrowthRate()} and
 * {@link SimulationParameters#getAnnualExpenseGrowthRate()} are annual assumptions, never applied
 * every month. A "simulated year" is a block of exactly 12 simulated months: months 1-12 use the
 * snapshot's original monthly income/expenses unchanged; entering month 13 — and every 12th
 * simulated month after that (25, 37, ...) — multiplies the <em>current</em> running income and
 * expenses by {@code (1 + rate)} exactly once, before that month's cash flow is computed. Growth
 * compounds year over year (each year's figure is derived from the previous year's value, not the
 * original), and income growth and expense growth are applied independently of each other. A
 * scenario {@link IncomeChangeEvent}/{@link ExpenseChangeEvent} due in the same month as a growth
 * application is applied afterward (see "Scenario event application" below), so the event's
 * explicit replacement value supersedes that month's growth result and becomes the new baseline
 * subsequent years' growth compounds from.
 *
 * <h2>Investment handling</h2>
 * <p>Each {@link Investment}'s own-currency value is tracked internally, starting from
 * {@link InvestmentCalculator#calculateCurrentValue(Investment)}. Each simulated month it advances
 * via {@link InvestmentCalculator#compoundOneMonth(BigDecimal, BigDecimal, BigDecimal)} — the same
 * shared one-month operation {@link InvestmentCalculator#projectValue} uses — with the investment's
 * own monthly rate ({@link InvestmentCalculator#calculateMonthlyRate(Investment)} initially,
 * derived from {@link Investment#getExpectedAnnualReturn()}) and that month's contribution
 * (initially {@link Investment#getMonthlyContribution()}); the contribution does not itself earn a
 * return in the month it is added. Unlike {@link InvestmentCalculator#projectValue}, this class
 * calls that shared operation once per simulated month rather than for a whole horizon up front,
 * because each simulated month also needs that month's contribution amount on its own (to convert
 * to INR for cash-flow accounting), because the rate and contribution can each change mid-projection
 * via a scenario event, and because investments and loans must advance together, one simulated
 * month at a time. The running value stays in the investment's own currency (INR
 * or USD) and is converted to INR only when consolidating {@link MonthlyFinancialState} totals,
 * via {@link InvestmentCalculator#convertToInr}, using {@link SimulationParameters#getUsdToInrRate()}.
 * That FX rate is a reporting-time assumption only — it never modifies the underlying
 * USD-denominated running value, and scenario return/contribution changes do not alter it either.
 *
 * <h2>Loan handling</h2>
 * <p>Each {@link Loan}'s full {@link AmortizationSchedule} is computed once, up front, via
 * {@link LoanCalculator#calculateAmortizationSchedule} — the EMI formula itself is never
 * duplicated here. That schedule is computed from the loan's <em>current</em> state —
 * {@link Loan#getCurrentBalance()} amortized over {@link Loan#getRemainingTenureMonths()} — not
 * its original {@link Loan#getPrincipal()}/{@link Loan#getTenureMonths()}, so simulated month 1 is
 * payment 1 of a schedule that pays off exactly what the loan actually owes as of the snapshot,
 * over exactly the months actually left to pay it. {@link Loan#getStartDate()} plays no role in
 * this arithmetic; it is descriptive metadata only, and the schedule's alignment to real time
 * comes entirely from {@code currentBalance}/{@code remainingTenureMonths} already being as of the
 * snapshot's {@code asOfDate}. Once a loan's schedule is exhausted (a simulated month beyond
 * {@link Loan#getRemainingTenureMonths()}), its payment and balance are both zero for every
 * remaining month — and because every {@link LoanPayment#getRemainingBalance()} is itself
 * validated {@code >= 0}, the aggregated loan balance can never go negative. There are no loan
 * scenario events yet; loans are unaffected by any {@link ScenarioEvent}.
 *
 * <h2>Goals</h2>
 * <p>{@link FinancialSnapshot#getGoals()} passes through unread. {@link MonthlyFinancialState} has
 * no goal-progress field to populate, so there is nothing for this engine to compute or store per
 * month without inventing new state. Goals are neither consulted for simulation logic nor mutated.
 *
 * <h2>Scenario event application</h2>
 * <p>Every {@link ScenarioEvent} carries an {@code effectiveDate}. Per the class's own contract,
 * that is the date from which its change applies — resolved here as: <em>an event takes effect
 * starting with the first simulated month whose date is not before the event's
 * {@code effectiveDate}</em>. This one rule, applied uniformly, is what makes a past-dated event
 * (on or before {@code asOfDate}) active from month 1 (since month 1's date is always after
 * {@code asOfDate}), and what makes an event dated after the last simulated month have no effect
 * at all (no simulated month's date ever reaches it) — neither case needs special-casing.
 * Internally, each event type's instances are grouped, sorted ascending by {@code effectiveDate},
 * and walked forward by a small advancing-pointer helper ({@code EventCursor}) in lockstep with
 * the month loop, so no event is ever re-examined once consumed and no date math is duplicated.
 *
 * <ul>
 *   <li>{@link IncomeChangeEvent} / {@link ExpenseChangeEvent} <b>replace</b> the running monthly
 *       income/expenses with an absolute new value (not a delta). If more than one such event
 *       becomes due in the same simulated month, the one with the latest {@code effectiveDate}
 *       (chronologically most recent) wins for that month, matching how they would apply if the
 *       month grid were finer-grained.</li>
 *   <li>{@link InvestmentContributionChangeEvent} / {@link InvestmentReturnChangeEvent} likewise
 *       replace the targeted {@link Investment}'s running monthly contribution / monthly rate
 *       (the latter recomputed via {@link InvestmentCalculator#calculateMonthlyRate(BigDecimal)}
 *       from the event's new annual return, never duplicated by hand). The target is resolved by
 *       {@link Investment#getId()} against {@link FinancialSnapshot#getInvestments()}; an event
 *       naming an id absent from the snapshot is rejected up front (see below) rather than
 *       silently ignored.</li>
 *   <li>{@link OneTimeExpenseEvent} is different in kind: it is <b>additive</b>, not a
 *       replacement, and fires exactly once — in the single simulated month its {@code
 *       effectiveDate} resolves to — never persisting beyond it. All one-time expenses due in the
 *       same month are summed into that month's "other outflows" (see "Cash flow" below).</li>
 * </ul>
 *
 * <p><b>Conflict validation:</b> for the four <em>replacing</em> event types, two events sharing
 * both the same target property (income; expenses; a given investment's contribution; a given
 * investment's return) and the same {@code effectiveDate} are irreconcilably ambiguous — nothing
 * in this milestone's model says which one should win — so {@link #simulate(FinancialSnapshot,
 * SimulationParameters, List)} rejects the whole event list with {@link IllegalArgumentException}
 * rather than silently picking one based on list order. {@link OneTimeExpenseEvent} is exempt from
 * this check: multiple one-time expenses sharing a date are independent, additive outflows (e.g.
 * a car repair and a medical bill in the same month), not a conflict. Different properties may
 * always change on the same date (e.g. an income change and an SIP change together).
 *
 * <h2>Cash flow</h2>
 * <p>Each month: {@code endingCash = beginningCash + income - expenses - investmentContributions
 * - loanPayments - otherOutflows}. {@code otherOutflows} is the sum of that month's due
 * {@link OneTimeExpenseEvent} amounts (zero when none are due, which is always the case in the
 * baseline two-argument simulation). There is no MVP event type that produces a cash inflow, so
 * "other inflows" is always zero and is omitted from the arithmetic rather than kept as a
 * permanently-zero variable. Investment gains are not cash income, and loan interest is not
 * subtracted separately from cash — it is already contained within each loan's payment. Cash is
 * never floored at zero: a shortfall month is a real result this simulator exists to surface,
 * matching {@link MonthlyFinancialState}'s own documented policy on {@code cashBalance}.
 *
 * <h2>Net worth</h2>
 * <p>Unchanged: {@code netWorth = cash + totalInvestmentValue - totalLoanBalance}, via
 * {@link NetWorthCalculator}. Because a {@link OneTimeExpenseEvent}'s effect is folded into
 * {@code cash} before net worth is computed, no change to {@link MonthlyFinancialState} or to this
 * formula was needed to account for it.
 *
 * <h2>Cumulative savings</h2>
 * <p>{@code cumulativeSavings} accumulates {@code income - expenses} across every simulated month
 * so far — unchanged from the baseline definition, and deliberately still excluding investment
 * contributions and loan payments, for the same reason as before: it represents cumulative income
 * minus cumulative <em>regular</em> living expenses, not cash actually retained. A
 * {@link OneTimeExpenseEvent} is, by design, <b>not</b> subtracted from {@code cumulativeSavings}
 * either: it is a one-off "other outflow", not a regular monthly expense, so folding it in here
 * would silently change what this figure has always meant. It does still reduce {@code
 * cashBalance} (via "other outflows" above), so its effect on the household's actual cash position
 * is fully visible — just not through this particular metric.
 */
public final class SimulationEngine {

    private static final int MONTHS_PER_YEAR = 12;

    private SimulationEngine() {
        // stateless utility class; not instantiable
    }

    /**
     * Runs the baseline deterministic simulation (no scenario events) and returns the resulting
     * {@link Projection}. Neither {@code snapshot} nor {@code parameters} — nor anything either
     * exposes — is mutated. Equivalent to {@code simulate(snapshot, parameters, List.of())}.
     */
    public static Projection simulate(FinancialSnapshot snapshot, SimulationParameters parameters) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(parameters, "parameters must not be null");
        return simulateInternal(snapshot, parameters, List.of());
    }

    /**
     * Runs the deterministic simulation with the given scenario events applied — see the
     * class-level "Scenario event application" section for exactly how each event type is
     * resolved and applied. An empty {@code events} list produces exactly the {@link Projection}
     * that {@link #simulate(FinancialSnapshot, SimulationParameters)} would. Neither
     * {@code snapshot}, {@code parameters}, nor {@code events} — nor anything any of them expose —
     * is mutated.
     *
     * @throws NullPointerException if {@code events} (or any element of it) is null
     * @throws IllegalArgumentException if two events of the same replacing type target the same
     *         property on the same {@code effectiveDate} (see class-level "Conflict validation"),
     *         or if an {@link InvestmentContributionChangeEvent}/{@link InvestmentReturnChangeEvent}
     *         targets an investment id not present in {@code snapshot}
     */
    public static Projection simulate(FinancialSnapshot snapshot, SimulationParameters parameters, List<ScenarioEvent> events) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(parameters, "parameters must not be null");
        Objects.requireNonNull(events, "events must not be null");
        return simulateInternal(snapshot, parameters, List.copyOf(events));
    }

    private static Projection simulateInternal(FinancialSnapshot snapshot, SimulationParameters parameters, List<ScenarioEvent> events) {
        List<Investment> investments = snapshot.getInvestments();
        List<Loan> loans = snapshot.getLoans();
        int investmentCount = investments.size();
        int loanCount = loans.size();

        Map<Long, Integer> investmentIndexById = indexInvestmentsById(investments);
        validateNoConflicts(events);
        validateInvestmentTargetsExist(events, investmentIndexById);

        // --- Categorize events by type/target, sorted ascending by effectiveDate. ---
        List<IncomeChangeEvent> incomeEvents = new ArrayList<>();
        List<ExpenseChangeEvent> expenseEvents = new ArrayList<>();
        List<OneTimeExpenseEvent> oneTimeExpenseEvents = new ArrayList<>();
        List<List<InvestmentContributionChangeEvent>> contributionEventsByInvestment = emptyListPerInvestment(investmentCount);
        List<List<InvestmentReturnChangeEvent>> returnEventsByInvestment = emptyListPerInvestment(investmentCount);

        for (ScenarioEvent event : events) {
            switch (event) {
                case IncomeChangeEvent e -> incomeEvents.add(e);
                case ExpenseChangeEvent e -> expenseEvents.add(e);
                case OneTimeExpenseEvent e -> oneTimeExpenseEvents.add(e);
                case InvestmentContributionChangeEvent e ->
                        contributionEventsByInvestment.get(investmentIndexById.get(e.getInvestmentId())).add(e);
                case InvestmentReturnChangeEvent e ->
                        returnEventsByInvestment.get(investmentIndexById.get(e.getInvestmentId())).add(e);
            }
        }
        sortByEffectiveDate(incomeEvents);
        sortByEffectiveDate(expenseEvents);
        sortByEffectiveDate(oneTimeExpenseEvents);
        contributionEventsByInvestment.forEach(SimulationEngine::sortByEffectiveDate);
        returnEventsByInvestment.forEach(SimulationEngine::sortByEffectiveDate);

        EventCursor<IncomeChangeEvent> incomeCursor = new EventCursor<>(incomeEvents);
        EventCursor<ExpenseChangeEvent> expenseCursor = new EventCursor<>(expenseEvents);
        EventCursor<OneTimeExpenseEvent> oneTimeExpenseCursor = new EventCursor<>(oneTimeExpenseEvents);
        List<EventCursor<InvestmentContributionChangeEvent>> contributionCursors = new ArrayList<>(investmentCount);
        List<EventCursor<InvestmentReturnChangeEvent>> returnCursors = new ArrayList<>(investmentCount);
        for (int i = 0; i < investmentCount; i++) {
            contributionCursors.add(new EventCursor<>(contributionEventsByInvestment.get(i)));
            returnCursors.add(new EventCursor<>(returnEventsByInvestment.get(i)));
        }

        // --- Per-investment running state; monthly rate and contribution are each overridable
        //     from their effective month onward, but the Investment object itself never changes. ---
        BigDecimal[] investmentValues = new BigDecimal[investmentCount];
        BigDecimal[] investmentMonthlyRates = new BigDecimal[investmentCount];
        BigDecimal[] investmentContributions = new BigDecimal[investmentCount];
        for (int i = 0; i < investmentCount; i++) {
            Investment investment = investments.get(i);
            investmentValues[i] = InvestmentCalculator.calculateCurrentValue(investment);
            investmentMonthlyRates[i] = InvestmentCalculator.calculateMonthlyRate(investment);
            investmentContributions[i] = investment.getMonthlyContribution();
        }

        AmortizationSchedule[] loanSchedules = new AmortizationSchedule[loanCount];
        for (int i = 0; i < loanCount; i++) {
            loanSchedules[i] = LoanCalculator.calculateAmortizationSchedule(loans.get(i));
        }

        BigDecimal currentIncome = snapshot.getMonthlyIncome();
        BigDecimal currentExpenses = snapshot.getMonthlyExpenses();
        BigDecimal cash = snapshot.getCash();
        BigDecimal cumulativeSavings = FinancialPrecision.roundMoney(BigDecimal.ZERO);

        BigDecimal usdToInrRate = parameters.getUsdToInrRate();
        BigDecimal incomeGrowthFactor = BigDecimal.ONE.add(parameters.getAnnualIncomeGrowthRate());
        BigDecimal expenseGrowthFactor = BigDecimal.ONE.add(parameters.getAnnualExpenseGrowthRate());
        int projectionMonths = parameters.getProjectionMonths();
        LocalDate asOfDate = snapshot.getAsOfDate();

        List<MonthlyFinancialState> states = new ArrayList<>(projectionMonths);

        for (int monthIndex = 1; monthIndex <= projectionMonths; monthIndex++) {
            LocalDate simulatedDate = asOfDate.plusMonths(monthIndex);

            // Annual growth: applied once at the start of each new simulated year (month 13,
            // 25, ... — see the class-level "Annual income/expense growth convention" section).
            if (monthIndex > 1 && (monthIndex - 1) % MONTHS_PER_YEAR == 0) {
                currentIncome = FinancialPrecision.roundMoney(currentIncome.multiply(incomeGrowthFactor));
                currentExpenses = FinancialPrecision.roundMoney(currentExpenses.multiply(expenseGrowthFactor));
            }

            // Scenario overrides: an event due this month replaces growth's result for this
            // month and becomes the new running baseline (see "Scenario event application").
            List<IncomeChangeEvent> dueIncome = incomeCursor.advanceTo(simulatedDate);
            if (!dueIncome.isEmpty()) {
                currentIncome = lastOf(dueIncome).getNewMonthlyIncome();
            }
            List<ExpenseChangeEvent> dueExpense = expenseCursor.advanceTo(simulatedDate);
            if (!dueExpense.isEmpty()) {
                currentExpenses = lastOf(dueExpense).getNewMonthlyExpenses();
            }

            BigDecimal monthlyIncome = currentIncome;
            BigDecimal monthlyExpenses = currentExpenses;

            BigDecimal otherOutflows = FinancialPrecision.roundMoney(BigDecimal.ZERO);
            for (OneTimeExpenseEvent due : oneTimeExpenseCursor.advanceTo(simulatedDate)) {
                otherOutflows = otherOutflows.add(due.getAmount());
            }
            otherOutflows = FinancialPrecision.roundMoney(otherOutflows);

            BigDecimal totalInvestmentValue = FinancialPrecision.roundMoney(BigDecimal.ZERO);
            BigDecimal totalInvestmentContribution = FinancialPrecision.roundMoney(BigDecimal.ZERO);
            for (int i = 0; i < investmentCount; i++) {
                Investment investment = investments.get(i);

                List<InvestmentReturnChangeEvent> dueReturn = returnCursors.get(i).advanceTo(simulatedDate);
                if (!dueReturn.isEmpty()) {
                    investmentMonthlyRates[i] = InvestmentCalculator.calculateMonthlyRate(lastOf(dueReturn).getNewExpectedAnnualReturn());
                }
                List<InvestmentContributionChangeEvent> dueContribution = contributionCursors.get(i).advanceTo(simulatedDate);
                if (!dueContribution.isEmpty()) {
                    investmentContributions[i] = lastOf(dueContribution).getNewMonthlyContribution();
                }

                BigDecimal contribution = FinancialPrecision.roundMoney(investmentContributions[i]);
                BigDecimal newValue = InvestmentCalculator.compoundOneMonth(
                        investmentValues[i], investmentMonthlyRates[i], contribution);
                investmentValues[i] = newValue;

                totalInvestmentValue = totalInvestmentValue.add(
                        InvestmentCalculator.convertToInr(investment, newValue, usdToInrRate));
                totalInvestmentContribution = totalInvestmentContribution.add(
                        InvestmentCalculator.convertToInr(investment, contribution, usdToInrRate));
            }
            totalInvestmentValue = FinancialPrecision.roundMoney(totalInvestmentValue);
            totalInvestmentContribution = FinancialPrecision.roundMoney(totalInvestmentContribution);

            BigDecimal totalLoanBalance = FinancialPrecision.roundMoney(BigDecimal.ZERO);
            BigDecimal totalLoanPayment = FinancialPrecision.roundMoney(BigDecimal.ZERO);
            for (int i = 0; i < loanCount; i++) {
                List<LoanPayment> payments = loanSchedules[i].getPayments();
                if (monthIndex <= payments.size()) {
                    LoanPayment payment = payments.get(monthIndex - 1);
                    totalLoanPayment = totalLoanPayment.add(payment.getPayment());
                    totalLoanBalance = totalLoanBalance.add(payment.getRemainingBalance());
                }
                // else: this loan's schedule has already ended — it contributes a zero payment
                // and zero balance for this and every remaining simulated month.
            }
            totalLoanBalance = FinancialPrecision.roundMoney(totalLoanBalance);
            totalLoanPayment = FinancialPrecision.roundMoney(totalLoanPayment);

            cash = FinancialPrecision.roundMoney(cash.add(monthlyIncome)
                    .subtract(monthlyExpenses)
                    .subtract(totalInvestmentContribution)
                    .subtract(totalLoanPayment)
                    .subtract(otherOutflows));

            BigDecimal netWorth = NetWorthCalculator.calculateNetWorth(cash, totalInvestmentValue, totalLoanBalance);

            BigDecimal monthlySavings = monthlyIncome.subtract(monthlyExpenses);
            cumulativeSavings = FinancialPrecision.roundMoney(cumulativeSavings.add(monthlySavings));

            states.add(new MonthlyFinancialState(
                    simulatedDate,
                    cash,
                    totalInvestmentValue,
                    totalLoanBalance,
                    monthlyIncome,
                    monthlyExpenses,
                    totalInvestmentContribution,
                    totalLoanPayment,
                    netWorth,
                    cumulativeSavings));
        }

        LocalDate endDate = asOfDate.plusMonths(projectionMonths);
        return new Projection(asOfDate, endDate, states);
    }

    private static Map<Long, Integer> indexInvestmentsById(List<Investment> investments) {
        Map<Long, Integer> index = new HashMap<>();
        for (int i = 0; i < investments.size(); i++) {
            index.put(investments.get(i).getId(), i);
        }
        return index;
    }

    private static void validateInvestmentTargetsExist(List<ScenarioEvent> events, Map<Long, Integer> investmentIndexById) {
        for (ScenarioEvent event : events) {
            if (event instanceof InvestmentContributionChangeEvent e && !investmentIndexById.containsKey(e.getInvestmentId())) {
                throw new IllegalArgumentException(
                        "InvestmentContributionChangeEvent targets investment id " + e.getInvestmentId()
                                + ", which is not present in the given FinancialSnapshot");
            }
            if (event instanceof InvestmentReturnChangeEvent e && !investmentIndexById.containsKey(e.getInvestmentId())) {
                throw new IllegalArgumentException(
                        "InvestmentReturnChangeEvent targets investment id " + e.getInvestmentId()
                                + ", which is not present in the given FinancialSnapshot");
            }
        }
    }

    /**
     * Rejects the event list if two events of the same <em>replacing</em> type target the same
     * property (income; expenses; a given investment's contribution; a given investment's return)
     * on the same {@code effectiveDate} — see the class-level "Conflict validation" section.
     * {@link OneTimeExpenseEvent}s are exempt: they are additive, so sharing a date is not a
     * conflict.
     */
    private static void validateNoConflicts(List<ScenarioEvent> events) {
        Set<LocalDate> incomeDates = new HashSet<>();
        Set<LocalDate> expenseDates = new HashSet<>();
        Map<Long, Set<LocalDate>> contributionDatesByInvestment = new HashMap<>();
        Map<Long, Set<LocalDate>> returnDatesByInvestment = new HashMap<>();

        for (ScenarioEvent event : events) {
            switch (event) {
                case IncomeChangeEvent e -> requireNoConflict(incomeDates, e.getEffectiveDate(), "IncomeChangeEvent");
                case ExpenseChangeEvent e -> requireNoConflict(expenseDates, e.getEffectiveDate(), "ExpenseChangeEvent");
                case InvestmentContributionChangeEvent e -> requireNoConflict(
                        contributionDatesByInvestment.computeIfAbsent(e.getInvestmentId(), id -> new HashSet<>()),
                        e.getEffectiveDate(),
                        "InvestmentContributionChangeEvent for investment " + e.getInvestmentId());
                case InvestmentReturnChangeEvent e -> requireNoConflict(
                        returnDatesByInvestment.computeIfAbsent(e.getInvestmentId(), id -> new HashSet<>()),
                        e.getEffectiveDate(),
                        "InvestmentReturnChangeEvent for investment " + e.getInvestmentId());
                case OneTimeExpenseEvent e -> {
                    // Additive, not a replacement: sharing an effectiveDate with another
                    // OneTimeExpenseEvent is not a conflict (see class-level Javadoc).
                }
            }
        }
    }

    private static void requireNoConflict(Set<LocalDate> seenEffectiveDates, LocalDate effectiveDate, String description) {
        if (!seenEffectiveDates.add(effectiveDate)) {
            throw new IllegalArgumentException(
                    "Conflicting scenario events: more than one " + description
                            + " event has effectiveDate " + effectiveDate
                            + "; same-property, same-date changes are ambiguous and must be resolved by the caller.");
        }
    }

    private static <T> List<List<T>> emptyListPerInvestment(int investmentCount) {
        List<List<T>> lists = new ArrayList<>(investmentCount);
        for (int i = 0; i < investmentCount; i++) {
            lists.add(new ArrayList<>());
        }
        return lists;
    }

    private static <T extends ScenarioEvent> void sortByEffectiveDate(List<T> events) {
        events.sort(Comparator.comparing(ScenarioEvent::getEffectiveDate));
    }

    private static <T> T lastOf(List<T> list) {
        return list.get(list.size() - 1);
    }

    /**
     * A sorted-by-{@code effectiveDate} list of events, walked forward once by an advancing
     * pointer as the month loop progresses. {@link #advanceTo} returns every event that has newly
     * become due (effectiveDate not after the given month's date) since the last call, in
     * ascending {@code effectiveDate} order, and never returns them again — so a month with
     * nothing newly due gets an empty list, a past-dated event is returned on the very first call
     * (month 1's date is always after any {@code asOfDate}-or-earlier {@code effectiveDate}), and
     * an event dated after the last simulated month is simply never returned at all. Construction
     * requires the given list to already be sorted ascending by {@code effectiveDate}.
     */
    private static final class EventCursor<T extends ScenarioEvent> {
        private final List<T> events;
        private int nextIndex;

        EventCursor(List<T> events) {
            this.events = events;
        }

        List<T> advanceTo(LocalDate monthDate) {
            if (nextIndex >= events.size() || events.get(nextIndex).getEffectiveDate().isAfter(monthDate)) {
                return List.of();
            }
            List<T> due = new ArrayList<>();
            while (nextIndex < events.size() && !events.get(nextIndex).getEffectiveDate().isAfter(monthDate)) {
                due.add(events.get(nextIndex));
                nextIndex++;
            }
            return due;
        }
    }
}
