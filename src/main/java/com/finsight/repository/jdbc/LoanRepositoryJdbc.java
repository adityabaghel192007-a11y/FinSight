package com.finsight.repository.jdbc;

import com.finsight.domain.loan.Loan;
import com.finsight.repository.LoanRepository;
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
 * JDBC/SQLite implementation of {@link LoanRepository}, backed by the {@code loans} table created
 * by {@link SchemaInitializer}. Structured identically to {@link AccountRepositoryJdbc},
 * {@link CategoryRepositoryJdbc}, {@link TransactionRepositoryJdbc}, and
 * {@link InvestmentRepositoryJdbc}. {@code loans} has no foreign keys — a {@link Loan} does not
 * reference any other entity — so, unlike those repositories, nothing here can fail with a
 * foreign-key violation.
 *
 * <p>Every operation uses a {@link PreparedStatement} (never string-concatenated SQL) and scopes
 * its {@code PreparedStatement}/{@code ResultSet} with try-with-resources; the underlying
 * {@link java.sql.Connection}, supplied by a {@link SqliteConnectionProvider}, is not owned by
 * this class and is never closed here. Any {@link SQLException} is wrapped as a
 * {@link RepositoryException} — this class never lets a raw {@code SQLException} escape, keeping
 * {@link LoanRepository}'s contract free of JDBC-specific checked exceptions.
 */
public final class LoanRepositoryJdbc implements LoanRepository {

    private static final String INSERT_SQL =
            "INSERT INTO loans (name, principal, annual_interest_rate, tenure_months, start_date, "
                    + "current_balance, remaining_tenure_months) VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String SELECT_BY_ID_SQL =
            "SELECT id, name, principal, annual_interest_rate, tenure_months, start_date, "
                    + "current_balance, remaining_tenure_months FROM loans WHERE id = ?";
    private static final String SELECT_ALL_SQL =
            "SELECT id, name, principal, annual_interest_rate, tenure_months, start_date, "
                    + "current_balance, remaining_tenure_months FROM loans ORDER BY id";
    private static final String DELETE_SQL =
            "DELETE FROM loans WHERE id = ?";

    private final SqliteConnectionProvider connectionProvider;

    public LoanRepositoryJdbc(SqliteConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider must not be null");
    }

    /**
     * Inserts {@code loan}'s fields as a new row — {@code loan.getId()} is never sent to the
     * database — and returns a new {@link Loan} carrying the id SQLite generated for that row,
     * via {@link Loan#withId(long)}. {@code loan} itself is left unmodified. {@code principal},
     * {@code annualInterestRate}, and {@code currentBalance} are each stored via
     * {@link java.math.BigDecimal#toPlainString()}, never as a floating-point value;
     * {@code currentBalance} and {@code remainingTenureMonths} are stored exactly as given,
     * independently of {@code principal} and {@code tenureMonths}.
     */
    @Override
    public Loan save(Loan loan) {
        Objects.requireNonNull(loan, "loan must not be null");
        try (PreparedStatement statement =
                     connectionProvider.getConnection().prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, loan.getName());
            statement.setString(2, loan.getPrincipal().toPlainString());
            statement.setString(3, loan.getAnnualInterestRate().toPlainString());
            statement.setInt(4, loan.getTenureMonths());
            statement.setString(5, loan.getStartDate().toString());
            statement.setString(6, loan.getCurrentBalance().toPlainString());
            statement.setInt(7, loan.getRemainingTenureMonths());
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (!generatedKeys.next()) {
                    throw new RepositoryException("SQLite did not return a generated id for the new loan");
                }
                long generatedId = generatedKeys.getLong(1);
                return loan.withId(generatedId);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to save loan: " + loan, e);
        }
    }

    @Override
    public Optional<Loan> findById(long id) {
        try (PreparedStatement statement = connectionProvider.getConnection().prepareStatement(SELECT_BY_ID_SQL)) {
            statement.setLong(1, id);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(LoanMapper.fromRow(row));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find loan by id " + id, e);
        }
    }

    @Override
    public List<Loan> findAll() {
        try (PreparedStatement statement = connectionProvider.getConnection().prepareStatement(SELECT_ALL_SQL);
             ResultSet rows = statement.executeQuery()) {
            List<Loan> loans = new ArrayList<>();
            while (rows.next()) {
                loans.add(LoanMapper.fromRow(rows));
            }
            return List.copyOf(loans);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find all loans", e);
        }
    }

    @Override
    public void delete(long id) {
        try (PreparedStatement statement = connectionProvider.getConnection().prepareStatement(DELETE_SQL)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to delete loan " + id, e);
        }
    }
}
