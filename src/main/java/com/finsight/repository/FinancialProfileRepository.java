package com.finsight.repository;

import com.finsight.domain.financialprofile.FinancialProfile;

import java.util.Optional;

/**
 * Persistence contract for the single {@link FinancialProfile} row. Contains no reference to
 * JDBC, SQLite, or any other storage technology — implementations (see
 * {@code com.finsight.repository.jdbc}) depend on this interface and on the domain, never the
 * other way around.
 *
 * <p>This interface is deliberately shaped differently from every other repository in this
 * package ({@link AccountRepository}, {@link CategoryRepository}, {@link TransactionRepository},
 * and so on), which are all insert-only, immutable-history contracts with no {@code update}: a
 * {@link FinancialProfile} is not a historical ledger entry like a transaction or an investment —
 * it is a single current setting the user revises over time. There must never be more than one
 * {@code FinancialProfile} row, so "insert-only" would either force an unrequested history of past
 * baselines to accumulate, or require the caller to perform its own find-then-branch to decide
 * whether to insert or update. Neither fits, so this contract instead has exactly two operations:
 * reading the one row that may or may not exist yet, and an explicit upsert that owns the
 * insert-versus-update decision itself.
 *
 * <p>There is no general-purpose {@code update} here, and none is introduced on any other
 * repository by this class — {@link #save(FinancialProfile)} is a single, narrowly-scoped
 * operation for this one singleton row, not a generic update capability.
 *
 * <p>Every method that reaches the database can throw {@link RepositoryException} if the
 * underlying storage operation fails.
 */
public interface FinancialProfileRepository {

    /**
     * Returns the single persisted {@code FinancialProfile}, or {@link Optional#empty()} if the
     * user has not yet set one (for example, on a freshly created database).
     */
    Optional<FinancialProfile> find();

    /**
     * Persists {@code profile} as the one and only {@code FinancialProfile} row: if no row exists
     * yet, this inserts one and returns it with the id the database generated; if a row already
     * exists, this updates that existing row's {@code monthlyIncome}/{@code monthlyExpenses} in
     * place — keyed by the existing row's own id, not by any id carried on {@code profile} — and
     * returns the updated result. Either way, exactly one {@code FinancialProfile} row exists in
     * the database once this method returns, and there is never more than one.
     *
     * <p>{@code profile} itself is never modified; the caller's instance is left exactly as it
     * was.
     */
    FinancialProfile save(FinancialProfile profile);
}
