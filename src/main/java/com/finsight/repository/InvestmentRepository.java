package com.finsight.repository;

import com.finsight.domain.investment.Investment;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for {@link Investment}. Contains no reference to JDBC, SQLite, or any
 * other storage technology — implementations (see {@code com.finsight.repository.jdbc}) depend on
 * this interface and on the domain, never the other way around.
 *
 * <p>Mirrors the shape established by {@link AccountRepository}, {@link CategoryRepository}, and
 * {@link TransactionRepository}: create, read (by id and all), and delete. There is deliberately
 * no {@code update}, for the same reason the other repositories have none — {@link Investment}
 * has no way to distinguish "not yet saved" from "persisted", since both are simply an
 * {@code Investment} with some {@code id}. Nothing in this milestone's vertical slice requires
 * editing a persisted investment's fields (e.g. its current price) in place, so that distinction
 * is left unintroduced rather than guessed at.
 *
 * <p>Every method that reaches the database can throw {@link RepositoryException} if the
 * underlying storage operation fails — including inserting an investment whose {@code accountId}
 * does not refer to a persisted row, since foreign-key enforcement is enabled.
 */
public interface InvestmentRepository {

    /**
     * Persists {@code investment} as a new row and returns a new {@code Investment} carrying the
     * id the database generated for it. The {@code id} already present on {@code investment} is
     * ignored — this method only ever inserts, it never updates an existing row — and
     * {@code investment} itself is never modified; the caller's instance is left exactly as it
     * was.
     */
    Investment save(Investment investment);

    /**
     * Looks up the investment with the given id, or {@link Optional#empty()} if no such
     * investment is currently persisted.
     */
    Optional<Investment> findById(long id);

    /**
     * Every currently persisted investment, in an unspecified but stable order (ascending by id).
     * Empty (never {@code null}) if none are persisted.
     */
    List<Investment> findAll();

    /**
     * Removes the investment with the given id, if one exists. Deleting an id that does not
     * currently exist is not an error — it simply leaves nothing to remove.
     */
    void delete(long id);
}
