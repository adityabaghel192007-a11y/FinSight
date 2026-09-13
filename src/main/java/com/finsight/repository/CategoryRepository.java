package com.finsight.repository;

import com.finsight.domain.category.Category;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for {@link Category}. Contains no reference to JDBC, SQLite, or any other
 * storage technology — implementations (see {@code com.finsight.repository.jdbc}) depend on this
 * interface and on the domain, never the other way around.
 *
 * <p>Mirrors the shape established by {@link AccountRepository}: create, read (by id and all),
 * and delete. There is deliberately no {@code update}, for the same reason {@code AccountRepository}
 * has none — {@link Category} has no way to distinguish "not yet saved" from "persisted", since
 * both are simply a {@code Category} with some {@code id}. Nothing in this milestone's vertical
 * slice requires renaming or re-typing a persisted category, so that distinction is left
 * unintroduced rather than guessed at.
 *
 * <p>{@code delete} is included because {@link Category} is referenced by {@code Transaction}
 * (optionally) and, in a later milestone, {@code Budget} — removing a category the application no
 * longer wants is a real operation, and its interaction with those foreign keys is exactly what
 * this milestone's persistence layer needs to prove out.
 *
 * <p>Every method that reaches the database can throw {@link RepositoryException} if the
 * underlying storage operation fails.
 */
public interface CategoryRepository {

    /**
     * Persists {@code category} as a new row and returns a new {@code Category} carrying the id
     * the database generated for it. The {@code id} already present on {@code category} is
     * ignored — this method only ever inserts, it never updates an existing row — and
     * {@code category} itself is never modified; the caller's instance is left exactly as it was.
     */
    Category save(Category category);

    /**
     * Looks up the category with the given id, or {@link Optional#empty()} if no such category is
     * currently persisted.
     */
    Optional<Category> findById(long id);

    /**
     * Every currently persisted category, in an unspecified but stable order (ascending by id).
     * Empty (never {@code null}) if none are persisted.
     */
    List<Category> findAll();

    /**
     * Removes the category with the given id, if one exists. Deleting an id that does not
     * currently exist is not an error — it simply leaves nothing to remove. Deleting a category
     * that a persisted transaction still references fails with {@link RepositoryException}
     * (foreign-key enforcement is enabled — see {@code SqliteConnectionProvider}); no cascade
     * behavior is applied.
     */
    void delete(long id);
}
