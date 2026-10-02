# FinSight

FinSight is a Java-first personal financial modeling and decision-simulation application. It
lets a user record their actual financial position — accounts, transactions, investments, and
loans — and run a deterministic, multi-month simulation to see how that position evolves, with
or without a hypothetical future change (a raise, a new expense, a change in how much is
invested, and so on).

## Overview

FinSight follows one core idea:

```
Input -> Model -> Calculate -> Project -> Compare -> Understand
```

A user enters their real financial data (income, expenses, accounts, transactions, investments).
FinSight models that data as a financial snapshot, calculates a month-by-month projection from
it, and — through the Decision Lab — projects a second, hypothetical version of the same
timeline and compares the two side by side.

This is the key difference from a simple expense tracker: FinSight does not just record what
already happened. Its central feature is projecting what *could* happen under a set of stated
assumptions, and quantifying the difference between a baseline future and an alternative one.

## Features

Features below are ones actually wired into the running application (reachable from
`FinSightApplication`/`CompositionRoot`), not merely present as a domain class.

- **Financial Overview** — a summary card at the top of the application showing monthly income,
  monthly expenses, monthly savings, consolidated cash, consolidated investments, total loan
  balance, and net worth, all as of now.
- **Financial Profile** — lets the user set and edit their baseline monthly income and monthly
  expenses.
- **Accounts / cash balances** — displays each persisted account with its current balance
  (opening balance plus its recorded transactions).
- **Transactions** — lets the user record an income or expense transaction against an account and
  category, and view the resulting transaction history.
- **Investments** — lets the user record an investment (name, asset type, currency, quantity,
  purchase price, current price, expected annual return, monthly contribution) and view its
  invested value, current value, and gain/loss.
- **Financial Projection** — runs a fixed 12-month baseline projection and displays starting/
  ending net worth, net worth change, and monthly income/expenses/surplus, plus a chart.
- **Decision Lab** — lets the user build a scenario out of one or more events and run it against
  the same baseline, comparing the two (see below).
- **Multi-event scenarios** — a single scenario run can combine any number of the five supported
  event types.
- **Scenario comparison** — baseline vs. scenario net worth, investment value, loan balance, cash
  balance, and total investment contributions, each with an absolute and percentage impact.
- **SQLite persistence** — all of the above is persisted to a local SQLite database file.

Two domain areas exist in the codebase but are **not** currently reachable through the
application and are therefore not listed as features: **Goals** (a `Goal` domain class,
repository, and calculator exist, but `SimulationEngine` explicitly passes goals through
unread/unconsulted, and there is no UI to create one) and **Budgets** (a `Budget` domain class and
repository exist, but `CompositionRoot` deliberately does not wire a `BudgetRepository` into
anything, since nothing consumes it). See the Limitations section below.

## Decision Lab

The Decision Lab is FinSight's main feature: a side-by-side comparison of a baseline financial
future against a hypothetical one.

- **Baseline financial state** — built from whatever is currently persisted: the financial
  profile, every account's balance, every investment's current value, and every loan's current
  balance, as of the date the scenario is run.
- **Scenario assumptions** — a fixed projection horizon (12 months) and a fixed annual income/
  expense growth rate and USD→INR rate (both currently 0%/1, with no UI control to change them —
  see the Limitations section below), shared by the baseline and the scenario so both are computed
  from the same starting point and assumptions.
- **Deterministic monthly simulation** — the engine advances the baseline state one simulated
  month at a time with no randomness; the same inputs always produce the same projection.
- **Comparison between baseline and scenario** — after both projections are computed, the two are
  compared metric by metric and the difference is shown.

The Decision Lab supports five scenario event types, each a concrete class under
`com.finsight.simulation.scenario`:

1. **Income change** (`IncomeChangeEvent`) — changes monthly income from a given date onward.
2. **Expense change** (`ExpenseChangeEvent`) — changes monthly expenses from a given date onward.
3. **Investment contribution change** (`InvestmentContributionChangeEvent`) — changes a specific
   investment's monthly contribution from a given date onward.
4. **Investment return change** (`InvestmentReturnChangeEvent`) — changes a specific investment's
   expected annual return from a given date onward.
5. **One-time expense** (`OneTimeExpenseEvent`) — a single expense applied in one specific month.

A user can add any number and combination of these events to one scenario (for example, an
income raise together with an increased investment contribution) and run them together as a
single simulation, rather than one event at a time.

## Financial Model

- **Monthly cash flow** — each account's balance is its opening balance plus every recorded
  income transaction minus every recorded expense transaction (`AccountBalanceCalculator`).
- **Income/expense growth** — the simulation engine supports an annual income growth rate and an
  annual expense growth rate, each compounded once per simulated year
  (`incomeGrowthFactor = 1 + annualIncomeGrowthRate`, applied at month 13, 25, ...). The current
  UI always runs with both rates at 0%.
- **Investment compounding** — each investment's monthly rate is derived from its expected annual
  return: `monthlyRate = (1 + annualRate)^(1/12) - 1`. Each simulated month, a value is advanced
  as `newValue = beginningValue * (1 + monthlyRate) + monthlyContribution` — the contribution
  itself does not earn a return in the month it is added.
- **Investment contributions** — each investment's configured monthly contribution is added every
  simulated month as shown above, and can be changed mid-simulation by an
  `InvestmentContributionChangeEvent`.
- **Loan amortization** — each loan's EMI and full amortization schedule are computed up front
  from its *current* outstanding balance and *remaining* tenure, using the standard
  reducing-balance formula: `EMI = B * r * (1+r)^n / ((1+r)^n - 1)`, where `B` is the current
  balance, `r` is the monthly interest rate, and `n` is the remaining number of months. This
  schedule is actually consulted by the simulation engine every month to reduce the loan balance
  and deduct the EMI payment from cash.
- **Net worth** — `netWorth = cash + totalInvestmentValue - totalLoanBalance`
  (`NetWorthCalculator`), computed identically for the Financial Overview, the Financial
  Projection, and every month of a Decision Lab run.
- **Scenario comparison** — baseline and scenario are each reduced to the same five figures (net
  worth, investment value, loan balance, cash balance, total investment contributions) at the end
  of the projection horizon, and the scenario comparison reports each pair plus the absolute and
  percentage difference between them.

Loans are amortized and do affect every projection and the Financial Overview's loan/net-worth
figures, but there is currently no UI workflow to add a loan (see the Limitations section
below) — the simulation and overview calculations are implemented and
tested, independent of whether a loan is currently reachable from the UI.

## Architecture

```
JavaFX UI
    |
Application Services
    |
Domain / Simulation
    |
Repository Interfaces
    |
JDBC Implementations
    |
SQLite
```

- **`com.finsight.ui`** / **`com.finsight.app`** — JavaFX views (`DecisionLabView`,
  `FinancialOverviewView`, `ProjectionChartView`, ...) and the application entry point
  (`FinSightApplication`), which wires views to already-composed services via `CompositionRoot`.
  No business logic lives here.
- **`com.finsight.service`** — application services (`SimulationApplicationService`,
  `TransactionApplicationService`, `InvestmentApplicationService`,
  `FinancialOverviewApplicationService`, ...) that coordinate a use case between the UI, the
  domain/simulation layer, and the repositories. No JavaFX or JDBC type is referenced here.
- **`com.finsight.domain`** — the core domain model: accounts, transactions, categories,
  investments, loans, goals, budgets, financial profile, and scenario/event types. Plain,
  immutable Java objects with no framework dependency.
- **`com.finsight.simulation`** — the deterministic monthly simulation engine
  (`SimulationEngine`), its calculators (`AccountBalanceCalculator`, `InvestmentCalculator`,
  `LoanCalculator`, `NetWorthCalculator`, `GoalCalculator`), and the scenario comparison logic.
  Depends only on the domain layer — no JavaFX, no JDBC.
- **`com.finsight.repository`** — persistence-agnostic repository interfaces.
- **`com.finsight.repository.jdbc`** — SQLite/JDBC implementations of those interfaces.

The simulation engine is kept independent of JavaFX and JDBC so it can be unit-tested directly
with plain objects (no database, no UI toolkit) and so a financial calculation can never
accidentally depend on how it is displayed or stored.

## Tech Stack

- Java 21 (`maven.compiler.release`)
- JavaFX 21.0.4 (`javafx-controls`)
- Maven (build tool; `javafx-maven-plugin` for `mvn javafx:run`)
- SQLite via JDBC (`org.xerial:sqlite-jdbc` 3.42.0.0)
- JUnit 5.10.2 (`junit-jupiter`, test scope)

No other runtime dependency is declared in `pom.xml`.

## Project Structure

```
src/main/java/com/finsight/
  app/            Application entry point, composition root, demo data seeding, DB location
  ui/             JavaFX views for each section (Decision Lab, Financial Overview, charts, ...)
  service/        Application services coordinating UI <-> domain/simulation <-> repositories
  domain/         Core domain model (account, transaction, category, investment, loan, goal,
                  budget, financial profile), split one package per concept
  simulation/     Simulation engine, calculators, comparison logic, and scenario/event model
  repository/     Repository interfaces
  repository/jdbc/  SQLite/JDBC repository implementations and row mappers
  common/         Shared financial-precision/rounding policy

src/test/java/com/finsight/   Mirrors the main package structure (see Testing below)
src/main/resources/finsight.css   JavaFX stylesheet
```

## Testing

```
770 tests
0 failures
0 errors
0 skipped
```

Tests are organized to mirror the main package structure and cover:

- domain model validation (e.g. `AccountTest`, `TransactionTest`, `LoanTest`, `InvestmentTest`)
- calculators (`AccountBalanceCalculatorTest`, `InvestmentCalculatorTest`, `LoanCalculatorTest`,
  `NetWorthCalculatorTest`, `GoalCalculatorTest`)
- the simulation engine and scenario events (`SimulationEngineTest`, `ScenarioApplicationTest`,
  and one test per event type)
- repositories/JDBC against a real SQLite database (`*RepositoryJdbcTest`,
  `SchemaInitializerTest`)
- application services, both with in-memory fakes and against a real database
  (`*ApplicationServiceTest` / `*ApplicationServiceIntegrationTest`)
- end-to-end workflows through `CompositionRoot` (`FreshLaunchSimulationWorkflowTest`,
  `TransactionWorkflowIntegrationTest`, `DecisionLabMultiEventScenarioIntegrationTest`, ...)
- UI formatting logic that has no JavaFX dependency (`SimulationResultFormatterTest`,
  `InvestmentFormatterTest`, `FinancialDataFormatterTest`, ...)

There are no TestFX or other headless-JavaFX UI tests — JavaFX view classes themselves are not
exercised by the automated suite.

## Running the Application

Requires Java 21 (the project is built and compiled against `maven.compiler.release=21`).

```bash
# Run the full test suite
mvn clean test

# Launch the application
mvn javafx:run
```

The application stores its data in a single SQLite file under the current user's home directory
(`~/.finsight/finsight.db`), created automatically on first launch.

## Financial Safety / Scope

FinSight is an educational/personal software modeling project, not a financial advisory tool and
not a substitute for professional financial advice. Every projection it produces is only as
accurate as the income, expense, investment, and loan figures and growth/return assumptions the
user supplies — it does not verify those assumptions against reality, and it does not guarantee
any projected outcome.

## Limitations

- No live market data, pricing feed, or exchange-rate source — every price, return, and FX rate
  (currently a fixed 1.0 USD→INR) is a value the user enters directly.
- No bank or brokerage account integration — accounts, transactions, and investments are entered
  manually.
- No authentication and no cloud backend — FinSight is a single-user desktop application backed
  by a local SQLite file.
- Every projection assumption (growth rates, expected returns, scenario events) is user-provided;
  the annual income/expense growth rate the engine supports has no UI control yet and is always
  run at 0%.
- `Goal` and `Budget` exist as domain/persistence classes but are not yet reachable from any UI
  workflow, and goals are not consulted by the simulation.
- There is no UI workflow to add a loan, even though loan amortization is implemented and used by
  the simulation engine and the Financial Overview.
- No automated UI (TestFX/headless JavaFX) test coverage.

## Screenshots

Screenshots can be added here after the final GUI capture.

## Design / Engineering Goals

- **Clear separation of concerns** — JavaFX views never call a repository or the simulation
  engine directly; every call goes through an application service.
- **Immutable domain/model objects** — `Account`, `Transaction`, `Investment`, `Loan`,
  `FinancialProfile`, and the simulation's own model types have no setters; a persisted id is
  attached via a `withId(long)` method that returns a new instance.
- **Deterministic simulation** — `SimulationEngine` takes no system clock or random input beyond
  what is explicitly passed to it, so the same inputs always produce the same projection.
- **Testability** — the simulation/calculator layer depends on nothing beyond the domain model,
  so it is unit-tested with plain objects; application services are tested against both
  in-memory fakes and a real SQLite database.
- **`BigDecimal` for financial precision** — every monetary figure is a `BigDecimal`, rounded
  through one shared policy (`FinancialPrecision`), never a `double` or `float`.
- **Repository abstraction** — every persistence operation goes through a repository interface;
  `com.finsight.service` and `com.finsight.simulation` have no JDBC dependency.
- **Simple JavaFX presentation** — one stylesheet, no MVC framework, no additional UI libraries;
  views are plain `Node` subclasses built once and updated through a small public surface.

## Future Improvements

- Expose `Goal` and `Budget` through the UI so they are usable, not just persisted.
- Add a UI workflow to create and edit loans.
- Let the user configure the annual income/expense growth rate and USD→INR rate instead of a
  fixed 0%/1.0.
- Allow editing or deleting an existing account, transaction, or investment (currently add-only).
