package com.finsight.repository.jdbc;

import com.finsight.domain.financialprofile.FinancialProfile;
import com.finsight.repository.FinancialProfileRepository;
import com.finsight.repository.RepositoryException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC/SQLite implementation of {@link FinancialProfileRepository}, backed by the
 * {@code financial_profile} table created by {@link SchemaInitializer}.
 *
 * <p>Every operation uses a {@link PreparedStatement} (never string-concatenated SQL) and scopes
 * its {@code PreparedStatement}/{@code ResultSet} with try-with-resources; the underlying
 * {@link java.sql.Connection}, supplied by a {@link SqliteConnectionProvider}, is not owned by
 * this class and is never closed here. Any {@link SQLException} is wrapped as a
 * {@link RepositoryException} — this class never lets a raw {@code SQLException} escape.
 *
 * <p>{@link #save(FinancialProfile)} enforces the singleton contract documented on
 * {@link FinancialProfileRepository}: it first checks whether a row already exists (via
 * {@link #find()}) and either inserts the first row or updates the existing one by its own id.
 * This check-then-act is not wrapped in an explicit transaction: FinSight uses a single
 * long-lived connection from a single-threaded desktop UI (see {@link SqliteConnectionProvider}),
 * matching every other repository in this codebase, none of which defends against concurrent
 * access from multiple connections either.
 */
public final class FinancialProfileRepositoryJdbc implements FinancialProfileRepository {

    private static final String SELECT_SQL =
            "SELECT id, monthly_income, monthly_expenses FROM financial_profile LIMIT 1";
    private static final String INSERT_SQL =
            "INSERT INTO financial_profile (monthly_income, monthly_expenses) VALUES (?, ?)";
    private static final String UPDATE_SQL =
            "UPDATE financial_profile SET monthly_income = ?, monthly_expenses = ? WHERE id = ?";

    private final SqliteConnectionProvider connectionProvider;

    public FinancialProfileRepositoryJdbc(SqliteConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider must not be null");
    }

    @Override
    public Optional<FinancialProfile> find() {
        try (PreparedStatement statement = connectionProvider.getConnection().prepareStatement(SELECT_SQL);
             ResultSet row = statement.executeQuery()) {
            if (!row.next()) {
                return Optional.empty();
            }
            return Optional.of(FinancialProfileMapper.fromRow(row));
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find the financial profile", e);
        }
    }

    /**
     * See {@link FinancialProfileRepository#save(FinancialProfile)} for the full contract. This
     * implementation reads the current state via {@link #find()} to decide whether to insert or
     * update; {@code profile}'s own {@code id} is never sent to the database in either case — an
     * update is always keyed by the existing row's id, never by whatever id {@code profile}
     * happens to carry.
     */
    @Override
    public FinancialProfile save(FinancialProfile profile) {
        Objects.requireNonNull(profile, "profile must not be null");
        Optional<FinancialProfile> existing = find();
        if (existing.isPresent()) {
            return update(existing.get().getId(), profile);
        }
        return insert(profile);
    }

    private FinancialProfile insert(FinancialProfile profile) {
        try (PreparedStatement statement =
                     connectionProvider.getConnection().prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, profile.getMonthlyIncome().toPlainString());
            statement.setString(2, profile.getMonthlyExpenses().toPlainString());
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (!generatedKeys.next()) {
                    throw new RepositoryException(
                            "SQLite did not return a generated id for the new financial profile");
                }
                long generatedId = generatedKeys.getLong(1);
                return profile.withId(generatedId);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to insert financial profile: " + profile, e);
        }
    }

    private FinancialProfile update(long existingId, FinancialProfile profile) {
        try (PreparedStatement statement = connectionProvider.getConnection().prepareStatement(UPDATE_SQL)) {
            statement.setString(1, profile.getMonthlyIncome().toPlainString());
            statement.setString(2, profile.getMonthlyExpenses().toPlainString());
            statement.setLong(3, existingId);
            int rowsUpdated = statement.executeUpdate();
            if (rowsUpdated != 1) {
                throw new RepositoryException(
                        "Expected to update exactly one financial profile row (id " + existingId
                                + "), but updated " + rowsUpdated);
            }
            return profile.withId(existingId);
        } catch (SQLException e) {
            throw new RepositoryException(
                    "Failed to update financial profile (id " + existingId + "): " + profile, e);
        }
    }
}
