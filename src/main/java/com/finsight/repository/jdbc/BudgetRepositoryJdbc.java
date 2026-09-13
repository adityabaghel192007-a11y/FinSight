package com.finsight.repository.jdbc;

import com.finsight.domain.budget.Budget;
import com.finsight.repository.BudgetRepository;
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
 * JDBC/SQLite implementation of {@link BudgetRepository}, backed by the {@code budgets} table
 * created by {@link SchemaInitializer}. Structured identically to {@link AccountRepositoryJdbc},
 * {@link CategoryRepositoryJdbc}, {@link TransactionRepositoryJdbc},
 * {@link InvestmentRepositoryJdbc}, {@link LoanRepositoryJdbc}, and {@link GoalRepositoryJdbc}.
 *
 * <p>Every operation uses a {@link PreparedStatement} (never string-concatenated SQL) and scopes
 * its {@code PreparedStatement}/{@code ResultSet} with try-with-resources; the underlying
 * {@link java.sql.Connection}, supplied by a {@link SqliteConnectionProvider}, is not owned by
 * this class and is never closed here. Any {@link SQLException} is wrapped as a
 * {@link RepositoryException} — this class never lets a raw {@code SQLException} escape, keeping
 * {@link BudgetRepository}'s contract free of JDBC-specific checked exceptions. This includes a
 * foreign-key violation on insert (a {@code categoryId} that does not refer to a persisted row):
 * SQLite reports that as a {@code SQLException}, which is wrapped the same way as any other
 * failure. This class performs no additional validation of {@code categoryId} beyond what the
 * foreign key itself enforces — no check of the referenced category's type, for instance — that
 * responsibility, if it is ever needed, belongs to a higher layer.
 */
public final class BudgetRepositoryJdbc implements BudgetRepository {

    private static final String INSERT_SQL =
            "INSERT INTO budgets (category_id, month, amount) VALUES (?, ?, ?)";
    private static final String SELECT_BY_ID_SQL =
            "SELECT id, category_id, month, amount FROM budgets WHERE id = ?";
    private static final String SELECT_ALL_SQL =
            "SELECT id, category_id, month, amount FROM budgets ORDER BY id";
    private static final String DELETE_SQL =
            "DELETE FROM budgets WHERE id = ?";

    private final SqliteConnectionProvider connectionProvider;

    public BudgetRepositoryJdbc(SqliteConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider must not be null");
    }

    /**
     * Inserts {@code budget}'s fields as a new row — {@code budget.getId()} is never sent to the
     * database — and returns a new {@link Budget} carrying the id SQLite generated for that row,
     * via {@link Budget#withId(long)}. {@code budget} itself is left unmodified. {@code month} is
     * stored via {@link java.time.YearMonth#toString()} (the {@code YYYY-MM} form), and
     * {@code amount} via {@link java.math.BigDecimal#toPlainString()}, never as a floating-point
     * value.
     */
    @Override
    public Budget save(Budget budget) {
        Objects.requireNonNull(budget, "budget must not be null");
        try (PreparedStatement statement =
                     connectionProvider.getConnection().prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, budget.getCategoryId());
            statement.setString(2, budget.getMonth().toString());
            statement.setString(3, budget.getAmount().toPlainString());
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (!generatedKeys.next()) {
                    throw new RepositoryException("SQLite did not return a generated id for the new budget");
                }
                long generatedId = generatedKeys.getLong(1);
                return budget.withId(generatedId);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to save budget: " + budget, e);
        }
    }

    @Override
    public Optional<Budget> findById(long id) {
        try (PreparedStatement statement = connectionProvider.getConnection().prepareStatement(SELECT_BY_ID_SQL)) {
            statement.setLong(1, id);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(BudgetMapper.fromRow(row));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find budget by id " + id, e);
        }
    }

    @Override
    public List<Budget> findAll() {
        try (PreparedStatement statement = connectionProvider.getConnection().prepareStatement(SELECT_ALL_SQL);
             ResultSet rows = statement.executeQuery()) {
            List<Budget> budgets = new ArrayList<>();
            while (rows.next()) {
                budgets.add(BudgetMapper.fromRow(rows));
            }
            return List.copyOf(budgets);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find all budgets", e);
        }
    }

    @Override
    public void delete(long id) {
        try (PreparedStatement statement = connectionProvider.getConnection().prepareStatement(DELETE_SQL)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to delete budget " + id, e);
        }
    }
}
