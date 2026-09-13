package com.finsight.repository;

import com.finsight.domain.account.Account;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for {@link Account}. Contains no reference to JDBC, SQLite, or any other
 * storage technology — implementations (see {@code com.finsight.repository.jdbc}) depend on this
 * interface and on the domain, never the other way around.
 *
 * <p>Only the operations this milestone's vertical slice actually needs are declared here:
 * creating a new account, reading one back (by id, or all of them), and removing one. There is
 * deliberately no {@code update}: {@link Account} has no way to distinguish "a fresh account not
 * yet saved" from "a persisted account whose fields should now overwrite the stored row" —
 * both are simply an {@code Account} with some {@code id} — so an update contract would either
 * have to invent a distinction the domain model doesn't make, or silently overwrite rows by id in
 * a way nothing in this milestone requires. Once a real use case for updating a persisted account
 * exists, that distinction can be introduced deliberately rather than guessed at here.
 *
 * <p>Every method that reaches the database can throw {@link RepositoryException} if the
 * underlying storage operation fails.
 */
public interface AccountRepository {

    /**
     * Persists {@code account} as a new row and returns a new {@code Account} carrying the id
     * the database generated for it. The {@code id} already present on {@code account} is
     * ignored — this method only ever inserts, it never updates an existing row — and
     * {@code account} itself is never modified; the caller's instance is left exactly as it was.
     */
    Account save(Account account);

    /**
     * Looks up the account with the given id, or {@link Optional#empty()} if no such account is
     * currently persisted.
     */
    Optional<Account> findById(long id);

    /**
     * Every currently persisted account, in an unspecified but stable order (ascending by id).
     * Empty (never {@code null}) if none are persisted.
     */
    List<Account> findAll();

    /**
     * Removes the account with the given id, if one exists. Deleting an id that does not
     * currently exist is not an error — it simply leaves nothing to remove.
     */
    void delete(long id);
}
