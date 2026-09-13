# FinSight

FinSight is a Java-first desktop financial modeling and decision simulator. Rather than acting
primarily as an expense tracker, its central capability is letting a user model their current
financial situation — accounts, investments, loans, and goals — and explore how hypothetical
future decisions (a raise, a new loan, a change in investment contributions, a one-time expense)
would play out over time. Its signature feature, the Decision Lab, takes a financial baseline
plus a set of scenario assumptions and events, runs a deterministic monthly simulation, and
produces a projection that can be compared against the baseline or against other scenarios.

## Technology stack

- Java 21
- Maven
- JavaFX (UI)
- SQLite via JDBC (persistence)
- JUnit 5 (testing)

## Architecture

FinSight uses a layered design:

```
UI (JavaFX)
  -> application/service layer
    -> domain/simulation
      -> repository interfaces
        -> JDBC repository implementations
          -> SQLite
```

- **`com.finsight.ui`** — JavaFX views and controllers. Thin; no business logic.
- **`com.finsight.service`** — application/service layer coordinating use cases between the UI,
  the domain/simulation layer, and repositories.
- **`com.finsight.domain`** — core domain model (accounts, transactions, categories,
  investments, loans, goals, budgets, scenarios, scenario events). Plain Java, no framework
  dependencies.
- **`com.finsight.simulation`** — the deterministic monthly simulation engine that powers the
  Decision Lab. Framework-free and independently unit-testable: it does not depend on JavaFX or
  JDBC.
- **`com.finsight.repository`** — persistence-agnostic repository interfaces.
- **`com.finsight.repository.jdbc`** — JDBC/SQLite implementations of those interfaces.

## Status

This repository currently contains only the initial project bootstrap: Maven/JavaFX/JUnit 5
setup, the package skeleton described above, and a minimal application entry point. The domain
model, simulation engine, persistence layer, and UI screens have not been implemented yet.

## Building and running

```bash
# Run tests
mvn test

# Build
mvn package

# Launch the application
mvn javafx:run
```
