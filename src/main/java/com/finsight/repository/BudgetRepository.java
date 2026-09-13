package com.finsight.repository;

import com.finsight.domain.budget.Budget;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for {@link Budget}. Contains no reference to JDBC, SQLite, or any other
 * storage technology — implementations (see {@code com.finsight.repository.jdbc}) depend on this
 * interface and on the domain, never the other way around.
 *
 * <p>Mirrors the shape established by {@link AccountRepository}, {@link CategoryRepository},
 * {@link TransactionRepository}, {@link InvestmentRepository}, {@link LoanRepository}, and
 * {@link GoalRepository}: create, read (by id and all), and delete. There is deliberately no
 * {@code update}, for the same reason the other repositories have none — {@link Budget} has no
 * way to distinguish "not yet saved" from "persisted", since both are simply a {@code Budget}
 * with some {@code id}. Nothing in this milestone's vertical slice requires editing a persisted
 * budget's amount in place, so that distinction is left unintroduced rather than guessed at.
 *
 * <p>Every method that reaches the database can throw {@link RepositoryException} if the
 * underlying storage operation fails — including inserting a budget whose {@code categoryId} does
 * not refer to a persisted row, since foreign-key enforcement is enabled. This repository performs
 * no other validation of {@code categoryId} (for example, whether the referenced category is an
 * {@code EXPENSE} versus an {@code INCOME} category) — that belongs at a service/use-case layer,
 * not here, matching the note already on {@link Budget#getCategoryId()}.
 */
public interface BudgetRepository {

    /**
     * Persists {@code budget} as a new row and returns a new {@code Budget} carrying the id the
     * database generated for it. The {@code id} already present on {@code budget} is ignored —
     * this method only ever inserts, it never updates an existing row — and {@code budget} itself
     * is never modified; the caller's instance is left exactly as it was.
     */
    Budget save(Budget budget);

    /**
     * Looks up the budget with the given id, or {@link Optional#empty()} if no such budget is
     * currently persisted.
     */
    Optional<Budget> findById(long id);

    /**
     * Every currently persisted budget, in an unspecified but stable order (ascending by id).
     * Empty (never {@code null}) if none are persisted.
     */
    List<Budget> findAll();

    /**
     * Removes the budget with the given id, if one exists. Deleting an id that does not currently
     * exist is not an error — it simply leaves nothing to remove.
     */
    void delete(long id);
}
