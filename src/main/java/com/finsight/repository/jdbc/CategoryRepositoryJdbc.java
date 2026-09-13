package com.finsight.repository.jdbc;

import com.finsight.domain.category.Category;
import com.finsight.repository.CategoryRepository;
import com.finsight.repository.RepositoryException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC/SQLite implementation of {@link CategoryRepository}, backed by the {@code categories}
 * table created by {@link SchemaInitializer}. Structured identically to
 * {@link AccountRepositoryJdbc}.
 *
 * <p>Every operation uses a {@link PreparedStatement} (never string-concatenated SQL) and scopes
 * its {@code PreparedStatement}/{@code ResultSet} with try-with-resources; the underlying
 * {@link java.sql.Connection}, supplied by a {@link SqliteConnectionProvider}, is not owned by
 * this class and is never closed here. Any {@link SQLException} is wrapped as a
 * {@link RepositoryException} — this class never lets a raw {@code SQLException} escape, keeping
 * {@link CategoryRepository}'s contract free of JDBC-specific checked exceptions.
 */
public final class CategoryRepositoryJdbc implements CategoryRepository {

    private static final String INSERT_SQL =
            "INSERT INTO categories (name, type) VALUES (?, ?)";
    private static final String SELECT_BY_ID_SQL =
            "SELECT id, name, type FROM categories WHERE id = ?";
    private static final String SELECT_ALL_SQL =
            "SELECT id, name, type FROM categories ORDER BY id";
    private static final String DELETE_SQL =
            "DELETE FROM categories WHERE id = ?";

    private final SqliteConnectionProvider connectionProvider;

    public CategoryRepositoryJdbc(SqliteConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider must not be null");
    }

    /**
     * Inserts {@code category}'s fields as a new row — {@code category.getId()} is never sent to
     * the database — and returns a new {@link Category} carrying the id SQLite generated for that
     * row, via {@link Category#withId(long)}. {@code category} itself is left unmodified.
     */
    @Override
    public Category save(Category category) {
        Objects.requireNonNull(category, "category must not be null");
        try (PreparedStatement statement =
                     connectionProvider.getConnection().prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, category.getName());
            statement.setString(2, category.getType().name());
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (!generatedKeys.next()) {
                    throw new RepositoryException("SQLite did not return a generated id for the new category");
                }
                long generatedId = generatedKeys.getLong(1);
                return category.withId(generatedId);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to save category: " + category, e);
        }
    }

    @Override
    public Optional<Category> findById(long id) {
        try (PreparedStatement statement = connectionProvider.getConnection().prepareStatement(SELECT_BY_ID_SQL)) {
            statement.setLong(1, id);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(CategoryMapper.fromRow(row));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find category by id " + id, e);
        }
    }

    @Override
    public List<Category> findAll() {
        try (PreparedStatement statement = connectionProvider.getConnection().prepareStatement(SELECT_ALL_SQL);
             ResultSet rows = statement.executeQuery()) {
            List<Category> categories = new ArrayList<>();
            while (rows.next()) {
                categories.add(CategoryMapper.fromRow(rows));
            }
            return List.copyOf(categories);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find all categories", e);
        }
    }

    /**
     * Deletes the category, if it exists. If a persisted transaction still references this
     * category via its (nullable) {@code category_id}, SQLite rejects the deletion as a foreign
     * key violation — surfaced here as a {@link RepositoryException} — rather than silently
     * cascading. Callers wanting to delete a still-referenced category must first re-categorize
     * or remove the transactions that reference it.
     */
    @Override
    public void delete(long id) {
        try (PreparedStatement statement = connectionProvider.getConnection().prepareStatement(DELETE_SQL)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to delete category " + id, e);
        }
    }
}
