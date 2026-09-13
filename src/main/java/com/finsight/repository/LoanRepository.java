package com.finsight.repository;

import com.finsight.domain.loan.Loan;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for {@link Loan}. Contains no reference to JDBC, SQLite, or any other
 * storage technology — implementations (see {@code com.finsight.repository.jdbc}) depend on this
 * interface and on the domain, never the other way around.
 *
 * <p>Mirrors the shape established by {@link AccountRepository}, {@link CategoryRepository},
 * {@link TransactionRepository}, and {@link InvestmentRepository}: create, read (by id and all),
 * and delete. There is deliberately no {@code update}, for the same reason the other repositories
 * have none — {@link Loan} has no way to distinguish "not yet saved" from "persisted", since both
 * are simply a {@code Loan} with some {@code id}. Nothing in this milestone's vertical slice
 * requires editing a persisted loan's fields (e.g. recording a new {@code currentBalance} after a
 * payment) in place, so that distinction is left unintroduced rather than guessed at.
 *
 * <p>Every method that reaches the database can throw {@link RepositoryException} if the
 * underlying storage operation fails.
 */
public interface LoanRepository {

    /**
     * Persists {@code loan} as a new row and returns a new {@code Loan} carrying the id the
     * database generated for it. The {@code id} already present on {@code loan} is ignored — this
     * method only ever inserts, it never updates an existing row — and {@code loan} itself is
     * never modified; the caller's instance is left exactly as it was.
     */
    Loan save(Loan loan);

    /**
     * Looks up the loan with the given id, or {@link Optional#empty()} if no such loan is
     * currently persisted.
     */
    Optional<Loan> findById(long id);

    /**
     * Every currently persisted loan, in an unspecified but stable order (ascending by id). Empty
     * (never {@code null}) if none are persisted.
     */
    List<Loan> findAll();

    /**
     * Removes the loan with the given id, if one exists. Deleting an id that does not currently
     * exist is not an error — it simply leaves nothing to remove.
     */
    void delete(long id);
}
