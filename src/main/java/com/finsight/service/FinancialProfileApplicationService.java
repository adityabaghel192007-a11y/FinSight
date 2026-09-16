package com.finsight.service;

import com.finsight.domain.financialprofile.FinancialProfile;
import com.finsight.repository.FinancialProfileRepository;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Application-layer orchestration for Milestone 5H's financial-profile editing workflow: reading
 * the single persisted {@link FinancialProfile} for display ({@link #getFinancialProfile()}), and
 * validating and saving a user-supplied replacement ({@link #saveFinancialProfile(String, String)}).
 *
 * <p>Depends only on {@link FinancialProfileRepository} — never on a JDBC type — matching every
 * other application service in this package ({@code SimulationApplicationService},
 * {@code TransactionApplicationService}). No existing service already owned
 * {@link FinancialProfile} operations (neither {@code SimulationApplicationService}, which only
 * reads a profile indirectly through {@code SnapshotBuilder}, nor {@code TransactionApplicationService},
 * which has no dependency on {@link FinancialProfileRepository} at all), so this is the smallest
 * new, focused application-level operation Milestone 5H's inspection step called for — not a
 * generic CRUD framework, and not a change to either existing service.
 *
 * <p><b>Where numeric parsing happens, and why:</b> unlike {@code TransactionApplicationService}
 * (which takes an already-typed {@link BigDecimal} amount, because nothing upstream of it ever
 * has a raw string to parse), this service accepts the raw text a user typed into an input field
 * directly, so that "blank input" and "malformed numeric input" are both real, application-layer
 * validation failures this class can be tested against directly — without needing a JavaFX
 * toolkit — rather than silently-untestable parsing logic duplicated inside
 * {@code FinSightApplication}. A blank or malformed value throws {@link ApplicationValidationException}
 * (this project's existing type for a user-input failure that is not a persistence failure — see
 * its own documentation), with a message meant to be shown to the user as-is, exactly like
 * {@code TransactionApplicationService}'s validation failures.
 *
 * <p><b>Where non-negativity is validated:</b> deliberately not re-checked in this class —
 * constructing a {@link FinancialProfile} already enforces {@code monthlyIncome >= 0} and
 * {@code monthlyExpenses >= 0}, throwing {@link IllegalArgumentException} otherwise, so
 * {@link #saveFinancialProfile(String, String)} simply lets that validation run once, in one
 * place, and propagates it unchanged — matching {@code TransactionApplicationService}'s own
 * documented reasoning for not re-checking what a domain constructor already validates.
 *
 * <p><b>Validation happens before any write:</b> both parsing and the {@link FinancialProfile}
 * constructor's own validation complete before {@link FinancialProfileRepository#save} is ever
 * called, so an invalid submission never reaches the repository and the previously persisted,
 * valid values are left completely untouched.
 */
public final class FinancialProfileApplicationService {

    private final FinancialProfileRepository financialProfileRepository;

    public FinancialProfileApplicationService(FinancialProfileRepository financialProfileRepository) {
        this.financialProfileRepository =
                Objects.requireNonNull(financialProfileRepository, "financialProfileRepository must not be null");
    }

    /**
     * The single persisted {@link FinancialProfile}, or {@link Optional#empty()} if none has ever
     * been recorded (in practice this should not happen once {@code DemoDataSeeder} has run, but
     * this method makes no assumption about that and simply reflects the repository).
     */
    public Optional<FinancialProfile> getFinancialProfile() {
        return financialProfileRepository.find();
    }

    /**
     * Validates and persists a new baseline {@link FinancialProfile}, replacing whichever one
     * currently exists (see {@link FinancialProfileRepository#save} for the exact singleton
     * upsert contract) — or inserting the first one, if none exists yet.
     *
     * @param monthlyIncomeText the monthly income the user typed, e.g. {@code "70000.00"}
     * @param monthlyExpensesText the monthly expenses the user typed, e.g. {@code "45000.00"}
     * @return the persisted {@link FinancialProfile}, carrying its row id
     * @throws NullPointerException if either argument is {@code null}
     * @throws ApplicationValidationException if either argument is blank, or is not a valid
     *     decimal number
     * @throws IllegalArgumentException if either parsed amount is negative (see
     *     {@link FinancialProfile}'s own constructor)
     * @throws com.finsight.repository.RepositoryException if persisting the profile fails
     */
    public FinancialProfile saveFinancialProfile(String monthlyIncomeText, String monthlyExpensesText) {
        BigDecimal monthlyIncome = parseAmount(monthlyIncomeText, "Monthly income");
        BigDecimal monthlyExpenses = parseAmount(monthlyExpensesText, "Monthly expenses");

        // id is always 0L here: FinancialProfileRepository#save ignores whatever id a transient
        // instance carries and keys the upsert by the existing row's own id (see its own
        // documentation), so there is nothing to look up first.
        FinancialProfile profile = new FinancialProfile(0L, monthlyIncome, monthlyExpenses);
        return financialProfileRepository.save(profile);
    }

    private static BigDecimal parseAmount(String text, String fieldName) {
        Objects.requireNonNull(text, fieldName + " must not be null");
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            throw new ApplicationValidationException(fieldName + " must not be blank");
        }
        try {
            return new BigDecimal(trimmed);
        } catch (NumberFormatException e) {
            throw new ApplicationValidationException(fieldName + " must be a valid number: \"" + text + "\"");
        }
    }
}
