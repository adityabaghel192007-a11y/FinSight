package com.finsight.repository;

import com.finsight.domain.goal.Goal;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for {@link Goal}. Contains no reference to JDBC, SQLite, or any other
 * storage technology — implementations (see {@code com.finsight.repository.jdbc}) depend on this
 * interface and on the domain, never the other way around.
 *
 * <p>Mirrors the shape established by {@link AccountRepository}, {@link CategoryRepository},
 * {@link TransactionRepository}, {@link InvestmentRepository}, and {@link LoanRepository}: create,
 * read (by id and all), and delete. There is deliberately no {@code update}, for the same reason
 * the other repositories have none — {@link Goal} has no way to distinguish "not yet saved" from
 * "persisted", since both are simply a {@code Goal} with some {@code id}. Nothing in this
 * milestone's vertical slice requires editing a persisted goal's fields (e.g. recording a new
 * {@code currentAmount} after a contribution) in place, so that distinction is left unintroduced
 * rather than guessed at.
 *
 * <p>This repository stores a {@link Goal}'s actual state only — it never persists a value
 * calculated by {@code GoalCalculator} (remaining amount, months remaining, required monthly
 * contribution); those remain derived on demand from the stored fields.
 *
 * <p>Every method that reaches the database can throw {@link RepositoryException} if the
 * underlying storage operation fails.
 */
public interface GoalRepository {

    /**
     * Persists {@code goal} as a new row and returns a new {@code Goal} carrying the id the
     * database generated for it. The {@code id} already present on {@code goal} is ignored — this
     * method only ever inserts, it never updates an existing row — and {@code goal} itself is
     * never modified; the caller's instance is left exactly as it was.
     */
    Goal save(Goal goal);

    /**
     * Looks up the goal with the given id, or {@link Optional#empty()} if no such goal is
     * currently persisted.
     */
    Optional<Goal> findById(long id);

    /**
     * Every currently persisted goal, in an unspecified but stable order (ascending by id). Empty
     * (never {@code null}) if none are persisted.
     */
    List<Goal> findAll();

    /**
     * Removes the goal with the given id, if one exists. Deleting an id that does not currently
     * exist is not an error — it simply leaves nothing to remove.
     */
    void delete(long id);
}
