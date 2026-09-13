package com.finsight.repository.jdbc;

import com.finsight.domain.investment.Investment;
import com.finsight.repository.InvestmentRepository;
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
 * JDBC/SQLite implementation of {@link InvestmentRepository}, backed by the {@code investments}
 * table created by {@link SchemaInitializer}. Structured identically to
 * {@link AccountRepositoryJdbc}, {@link CategoryRepositoryJdbc}, and
 * {@link TransactionRepositoryJdbc}.
 *
 * <p>Every operation uses a {@link PreparedStatement} (never string-concatenated SQL) and scopes
 * its {@code PreparedStatement}/{@code ResultSet} with try-with-resources; the underlying
 * {@link java.sql.Connection}, supplied by a {@link SqliteConnectionProvider}, is not owned by
 * this class and is never closed here. Any {@link SQLException} is wrapped as a
 * {@link RepositoryException} — this class never lets a raw {@code SQLException} escape, keeping
 * {@link InvestmentRepository}'s contract free of JDBC-specific checked exceptions. This includes
 * a foreign-key violation on insert (an {@code accountId} that does not refer to a persisted
 * row): SQLite reports that as a {@code SQLException}, which is wrapped the same way as any other
 * failure.
 */
public final class InvestmentRepositoryJdbc implements InvestmentRepository {

    private static final String INSERT_SQL =
            "INSERT INTO investments (account_id, name, ticker, asset_type, currency, quantity, "
                    + "average_purchase_price, current_price, expected_annual_return, monthly_contribution) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SELECT_BY_ID_SQL =
            "SELECT id, account_id, name, ticker, asset_type, currency, quantity, "
                    + "average_purchase_price, current_price, expected_annual_return, monthly_contribution "
                    + "FROM investments WHERE id = ?";
    private static final String SELECT_ALL_SQL =
            "SELECT id, account_id, name, ticker, asset_type, currency, quantity, "
                    + "average_purchase_price, current_price, expected_annual_return, monthly_contribution "
                    + "FROM investments ORDER BY id";
    private static final String DELETE_SQL =
            "DELETE FROM investments WHERE id = ?";

    private final SqliteConnectionProvider connectionProvider;

    public InvestmentRepositoryJdbc(SqliteConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider must not be null");
    }

    /**
     * Inserts {@code investment}'s fields as a new row — {@code investment.getId()} is never sent
     * to the database — and returns a new {@link Investment} carrying the id SQLite generated for
     * that row, via {@link Investment#withId(long)}. {@code investment} itself is left unmodified.
     * Every {@link java.math.BigDecimal} field is stored via
     * {@link java.math.BigDecimal#toPlainString()}, never as a floating-point value.
     */
    @Override
    public Investment save(Investment investment) {
        Objects.requireNonNull(investment, "investment must not be null");
        try (PreparedStatement statement =
                     connectionProvider.getConnection().prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, investment.getAccountId());
            statement.setString(2, investment.getName());
            statement.setString(3, investment.getTicker().orElse(null));
            statement.setString(4, investment.getAssetType().name());
            statement.setString(5, investment.getCurrency().name());
            statement.setString(6, investment.getQuantity().toPlainString());
            statement.setString(7, investment.getAveragePurchasePrice().toPlainString());
            statement.setString(8, investment.getCurrentPrice().toPlainString());
            statement.setString(9, investment.getExpectedAnnualReturn().toPlainString());
            statement.setString(10, investment.getMonthlyContribution().toPlainString());
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (!generatedKeys.next()) {
                    throw new RepositoryException("SQLite did not return a generated id for the new investment");
                }
                long generatedId = generatedKeys.getLong(1);
                return investment.withId(generatedId);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to save investment: " + investment, e);
        }
    }

    @Override
    public Optional<Investment> findById(long id) {
        try (PreparedStatement statement = connectionProvider.getConnection().prepareStatement(SELECT_BY_ID_SQL)) {
            statement.setLong(1, id);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(InvestmentMapper.fromRow(row));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find investment by id " + id, e);
        }
    }

    @Override
    public List<Investment> findAll() {
        try (PreparedStatement statement = connectionProvider.getConnection().prepareStatement(SELECT_ALL_SQL);
             ResultSet rows = statement.executeQuery()) {
            List<Investment> investments = new ArrayList<>();
            while (rows.next()) {
                investments.add(InvestmentMapper.fromRow(rows));
            }
            return List.copyOf(investments);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find all investments", e);
        }
    }

    @Override
    public void delete(long id) {
        try (PreparedStatement statement = connectionProvider.getConnection().prepareStatement(DELETE_SQL)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to delete investment " + id, e);
        }
    }
}
