package com.finsight.repository.jdbc;

import com.finsight.domain.goal.Goal;
import com.finsight.repository.GoalRepository;
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
 * JDBC/SQLite implementation of {@link GoalRepository}, backed by the {@code goals} table created
 * by {@link SchemaInitializer}. Structured identically to {@link AccountRepositoryJdbc},
 * {@link CategoryRepositoryJdbc}, {@link TransactionRepositoryJdbc},
 * {@link InvestmentRepositoryJdbc}, and {@link LoanRepositoryJdbc}. {@code goals} has no foreign
 * keys — a {@link Goal} does not reference any other entity — so, like {@code loans}, nothing
 * here can fail with a foreign-key violation.
 *
 * <p>Every operation uses a {@link PreparedStatement} (never string-concatenated SQL) and scopes
 * its {@code PreparedStatement}/{@code ResultSet} with try-with-resources; the underlying
 * {@link java.sql.Connection}, supplied by a {@link SqliteConnectionProvider}, is not owned by
 * this class and is never closed here. Any {@link SQLException} is wrapped as a
 * {@link RepositoryException} — this class never lets a raw {@code SQLException} escape, keeping
 * {@link GoalRepository}'s contract free of JDBC-specific checked exceptions.
 *
 * <p>This class persists exactly the fields on {@link Goal} and nothing else — it never computes
 * or stores a value that {@code GoalCalculator} would derive (remaining amount, months remaining,
 * required monthly contribution).
 */
public final class GoalRepositoryJdbc implements GoalRepository {

    private static final String INSERT_SQL =
            "INSERT INTO goals (name, target_amount, current_amount, target_date, monthly_contribution) "
                    + "VALUES (?, ?, ?, ?, ?)";
    private static final String SELECT_BY_ID_SQL =
            "SELECT id, name, target_amount, current_amount, target_date, monthly_contribution "
                    + "FROM goals WHERE id = ?";
    private static final String SELECT_ALL_SQL =
            "SELECT id, name, target_amount, current_amount, target_date, monthly_contribution "
                    + "FROM goals ORDER BY id";
    private static final String DELETE_SQL =
            "DELETE FROM goals WHERE id = ?";

    private final SqliteConnectionProvider connectionProvider;

    public GoalRepositoryJdbc(SqliteConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider must not be null");
    }

    /**
     * Inserts {@code goal}'s fields as a new row — {@code goal.getId()} is never sent to the
     * database — and returns a new {@link Goal} carrying the id SQLite generated for that row,
     * via {@link Goal#withId(long)}. {@code goal} itself is left unmodified. {@code targetAmount},
     * {@code currentAmount}, and {@code monthlyContribution} are each stored via
     * {@link java.math.BigDecimal#toPlainString()}, never as a floating-point value, and are
     * stored exactly as supplied — none is recalculated.
     */
    @Override
    public Goal save(Goal goal) {
        Objects.requireNonNull(goal, "goal must not be null");
        try (PreparedStatement statement =
                     connectionProvider.getConnection().prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, goal.getName());
            statement.setString(2, goal.getTargetAmount().toPlainString());
            statement.setString(3, goal.getCurrentAmount().toPlainString());
            statement.setString(4, goal.getTargetDate().toString());
            statement.setString(5, goal.getMonthlyContribution().toPlainString());
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (!generatedKeys.next()) {
                    throw new RepositoryException("SQLite did not return a generated id for the new goal");
                }
                long generatedId = generatedKeys.getLong(1);
                return goal.withId(generatedId);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to save goal: " + goal, e);
        }
    }

    @Override
    public Optional<Goal> findById(long id) {
        try (PreparedStatement statement = connectionProvider.getConnection().prepareStatement(SELECT_BY_ID_SQL)) {
            statement.setLong(1, id);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(GoalMapper.fromRow(row));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find goal by id " + id, e);
        }
    }

    @Override
    public List<Goal> findAll() {
        try (PreparedStatement statement = connectionProvider.getConnection().prepareStatement(SELECT_ALL_SQL);
             ResultSet rows = statement.executeQuery()) {
            List<Goal> goals = new ArrayList<>();
            while (rows.next()) {
                goals.add(GoalMapper.fromRow(rows));
            }
            return List.copyOf(goals);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find all goals", e);
        }
    }

    @Override
    public void delete(long id) {
        try (PreparedStatement statement = connectionProvider.getConnection().prepareStatement(DELETE_SQL)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to delete goal " + id, e);
        }
    }
}
