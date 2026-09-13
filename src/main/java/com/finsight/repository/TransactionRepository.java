package com.finsight.repository;

import com.finsight.domain.transaction.Transaction;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for {@link Transaction}. Contains no reference to JDBC, SQLite, or any
 * other storage technology — implementations (see {@code com.finsight.repository.jdbc}) depend on
 * this interface and on the domain, never the other way around.
 *
 * <p>Mirrors the shape established by {@link AccountRepository} and {@link CategoryRepository}:
 * create, read (by id and all), and delete. There is deliberately no {@code update}, for the same
 * reason the other repositories have none — {@link Transaction} has no way to distinguish "not
 * yet saved" from "persisted", since both are simply a {@code Transaction} with some {@code id}.
 * Nothing in this milestone's vertical slice requires editing a persisted transaction's fields in
 * place, so that distinction is left unintroduced rather than guessed at.
 *
 * <p>{@code delete} is included as a real, ordinary operation (removing a mistaken entry). It does
 * not need to touch the foreign keys {@code Transaction} itself holds ({@code account_id},
 * {@code category_id}) — deleting a transaction never violates a foreign key, since nothing
 * references a transaction by id. What deleting an {@link com.finsight.domain.account.Account} or
 * {@link com.finsight.domain.category.Category} that a transaction still references does is
 * documented on {@link AccountRepository#delete(long)} and {@link CategoryRepository#delete(long)}.
 *
 * <p>Every method that reaches the database can throw {@link RepositoryException} if the
 * underlying storage operation fails — including inserting a transaction whose
 * {@code accountId} or (when present) {@code categoryId} does not refer to a persisted row, since
 * foreign-key enforcement is enabled.
 */
public interface TransactionRepository {

    /**
     * Persists {@code transaction} as a new row and returns a new {@code Transaction} carrying
     * the id the database generated for it. The {@code id} already present on {@code transaction}
     * is ignored — this method only ever inserts, it never updates an existing row — and
     * {@code transaction} itself is never modified; the caller's instance is left exactly as it
     * was.
     */
    Transaction save(Transaction transaction);

    /**
     * Looks up the transaction with the given id, or {@link Optional#empty()} if no such
     * transaction is currently persisted.
     */
    Optional<Transaction> findById(long id);

    /**
     * Every currently persisted transaction, in an unspecified but stable order (ascending by
     * id). Empty (never {@code null}) if none are persisted.
     */
    List<Transaction> findAll();

    /**
     * Every currently persisted transaction whose {@code accountId} equals {@code accountId},
     * ordered ascending by id (the same stable order {@link #findAll()} uses). Empty (never
     * {@code null}) if the account has no transactions — including when {@code accountId} does not
     * refer to a persisted account at all. This method performs no lookup of its own against
     * {@link AccountRepository} to check that {@code accountId} exists; referential integrity for
     * {@code accountId} is already enforced when a transaction is {@link #save(Transaction) saved},
     * so an unknown id simply matches nothing here.
     */
    List<Transaction> findByAccountId(long accountId);

    /**
     * Sets the transaction with id {@code transactionId}'s {@code transferGroupId} to
     * {@code transferGroupId}, without touching any other column. This exists solely to support
     * an atomic transfer workflow (two {@code TRANSFER} transactions sharing one grouping value):
     * that shared value is derived from one of the two legs' own database-generated id, which is
     * only known after that leg has already been inserted, so it must be assigned in a second,
     * narrowly-scoped step rather than at insert time via {@link #save(Transaction)}.
     *
     * <p>This is deliberately not a general-purpose "update a transaction" operation — no other
     * field can be changed through this repository, matching the deliberate absence of a general
     * {@code update} method on this and every sibling repository interface (see the class-level
     * documentation). A caller that needs to change any other field still has none of that
     * capability here.
     *
     * @throws RepositoryException if no transaction with {@code transactionId} exists, or if the
     *     update otherwise fails
     */
    void assignTransferGroupId(long transactionId, long transferGroupId);

    /**
     * Removes the transaction with the given id, if one exists. Deleting an id that does not
     * currently exist is not an error — it simply leaves nothing to remove.
     */
    void delete(long id);
}
