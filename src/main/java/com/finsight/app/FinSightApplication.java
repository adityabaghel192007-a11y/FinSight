package com.finsight.app;

import com.finsight.domain.account.Account;
import com.finsight.domain.category.Category;
import com.finsight.domain.category.CategoryType;
import com.finsight.domain.financialprofile.FinancialProfile;
import com.finsight.domain.transaction.Transaction;
import com.finsight.domain.transaction.TransactionType;
import com.finsight.service.AccountBalance;
import com.finsight.service.SimulationApplicationService;
import com.finsight.service.SimulationResult;
import com.finsight.simulation.model.SimulationParameters;
import com.finsight.simulation.scenario.ExpenseChangeEvent;
import com.finsight.simulation.scenario.Scenario;
import com.finsight.ui.FinancialDataFormatter;
import com.finsight.ui.ProjectionChartView;
import com.finsight.ui.ScenarioComparisonView;
import com.finsight.ui.SimulationResultFormatter;
import com.finsight.ui.SimulationResultView;
import com.finsight.ui.TransactionRowView;

import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Application entry point.
 *
 * <p>This class is a thin JavaFX lifecycle shell over {@link CompositionRoot}:
 * <ul>
 *   <li>{@link #init()} constructs the {@link CompositionRoot} — database setup belongs here,
 *       not in {@link #start}, because {@code init()} runs before the JavaFX Application Thread
 *       starts, while {@code start(Stage)} runs on it;</li>
 *   <li>{@link #start(Stage)} builds a single-screen dashboard, top to bottom: a header, a
 *       "Financial Profile" card, a "Financial Projection" card (metrics + chart), and a
 *       "Scenario Simulator" card. The profile card goes through
 *       {@link CompositionRoot#getFinancialProfileApplicationService()}; the other two go through
 *       {@link CompositionRoot#getSimulationApplicationService()} — this class never touches a
 *       repository, {@code SimulationEngine}, {@code ScenarioComparisonCalculator}, or the
 *       database directly. The chart (a {@link ProjectionChartView}) plots the same
 *       baseline/scenario {@code Projection}s the metrics above and below it already display, so
 *       nothing is calculated twice. No business or financial logic lives here — this class only
 *       wires buttons to already-composed application services and displays already-computed,
 *       already-formatted values;</li>
 *   <li>{@link #stop()} closes the {@link CompositionRoot}, releasing the database connection it
 *       owns.</li>
 * </ul>
 *
 * <p><b>Milestone 5I</b> restructured this class's {@code start(Stage)} from one flat
 * {@code VBox} into a {@code BorderPane} (fixed header, scrollable content) with each of the
 * three workflows in its own visually distinct "card" (a {@code VBox} styled via the
 * {@code section-card} CSS class), and introduced {@code /finsight.css}
 * ({@code src/main/resources/finsight.css}) for all colors, spacing, and typography. This was a
 * presentation-only change: every method below that talks to a {@link CompositionRoot} service
 * ({@link #runBaselineSimulation()}, {@link #runExpenseIncreaseScenario(BigDecimal)}) is
 * byte-for-byte the same orchestration Milestones 5E–5H already established, and every existing
 * validation/error path (thrown before any repository write — see
 * {@code FinancialProfileApplicationService}) is preserved exactly, just displayed through
 * restyled {@link Label}s instead of plain ones. A few small private helper methods
 * ({@link #metricCard}, {@link #showStatus}, {@link #applySemanticValueStyle}) factor out the
 * repeated card-building/status-styling code introduced by this restructuring; none of them
 * contain financial logic.
 *
 * <p>The JDBC URL passed to {@link CompositionRoot} comes from {@link DatabaseLocation#jdbcUrl()}
 * — a real, file-backed SQLite database under the current user's home directory, not the
 * in-memory placeholder used by earlier milestones. See {@link DatabaseLocation} for exactly
 * where that file lives and why.
 *
 * <p><b>The baseline run</b> triggered by "Run Simulation" is a fixed, deterministic 12-month
 * projection: zero assumed income/expense growth, and a {@link Scenario} with no events. On
 * success, the baseline projection's 12 monthly states are also handed to
 * {@link ProjectionChartView#showBaseline}, which plots them without recomputing anything.
 *
 * <p><b>The scenario run</b> (Milestone 5F) triggered by "Run Scenario" uses the same 12-month,
 * zero-growth {@link SimulationParameters}, but with a {@link Scenario} carrying exactly one
 * {@link ExpenseChangeEvent}, effective immediately, replacing monthly expenses with the amount
 * the user enters — {@code ExpenseChangeEvent} is the only scenario event type usable against the
 * current demo data without changing {@link DemoDataSeeder}: the two investment-targeting event
 * types require an investment id already present in the snapshot, and the seeded demo data has no
 * investments. The text field is pre-filled with a fixed, deterministic default (see
 * {@link #DEFAULT_NEW_MONTHLY_EXPENSES}) so the workflow is repeatable out of the box, but remains
 * editable. On success, both the baseline and scenario projections from this same run are handed
 * to {@link ProjectionChartView#showBaseline}/{@link ProjectionChartView#showScenario} —
 * refreshing the baseline series too (with data that is, by construction of
 * {@code SimulationApplicationService.runScenario}, built from the very same snapshot as the
 * scenario) keeps both chart series drawn from one shared timeline, exactly as Milestone 5G
 * requires. If validation fails (a malformed or negative amount) before the service is even
 * called, neither chart method runs, so the chart is left exactly as it was — never partially
 * updated.
 *
 * <p>Running {@code SimulationEngine}/{@code ScenarioComparisonCalculator} against this codebase's
 * realistic data sizes is a fast, synchronous, in-memory computation (no I/O in the simulation
 * loop itself — see {@code SimulationEngine}'s own documentation), so both runs happen directly on
 * the JavaFX Application Thread here; nothing about either is expensive enough to justify a
 * background thread for this milestone.
 *
 * <p><b>The Financial Profile section (Milestone 5H)</b> lets the user edit the same
 * {@code monthlyIncome}/{@code monthlyExpenses} {@link FinancialProfile} that {@code SnapshotBuilder}
 * already reads on every simulation/scenario run. Its two text fields are pre-filled from
 * {@link CompositionRoot#getFinancialProfileApplicationService()}{@code .getFinancialProfile()} when
 * the screen is built; "Save Financial Profile" hands the raw field text straight to
 * {@code FinancialProfileApplicationService.saveFinancialProfile(String, String)}, which owns all
 * parsing and validation (blank, malformed, or negative input) and throws before writing anything
 * invalid — this class performs no numeric parsing or validation of its own here, only displays
 * whatever succeeds or whatever error message a failure carries. Saving does not automatically
 * re-run the baseline: since {@code SnapshotBuilder} re-reads {@code FinancialProfileRepository}
 * fresh on every {@code runScenario} call, the very next "Run Simulation" or "Run Scenario" click
 * automatically picks up the new values with no further wiring — this class never passes the
 * edited amounts into either simulation call directly.
 *
 * <p><b>The Financial Data section (Milestone 5J)</b> displays persisted accounts and
 * transactions and lets the user add a new one, entirely through
 * {@link CompositionRoot#getFinancialDataApplicationService()} (read) and
 * {@link CompositionRoot#getTransactionApplicationService()} (write via the existing
 * {@code recordIncome}/{@code recordExpense}) — never a repository directly. Because
 * {@code SnapshotBuilder} already consolidates every account's opening balance plus its own
 * transactions (income minus expense, via {@code AccountBalanceCalculator}) into the snapshot's
 * cash figure on every {@code runScenario} call, a transaction added here is automatically
 * reflected the very next time "Run Simulation" or "Run Scenario" is clicked — exactly the same
 * "no further wiring needed" property the Financial Profile section above already relies on.
 *
 * <p><b>The Accounts cards display each account's current balance (Milestone 5K)</b> — opening
 * balance combined with its transactions — via
 * {@link CompositionRoot#getFinancialDataApplicationService()}{@code .getAccountBalances()}, which
 * itself delegates to the existing, already-tested {@code AccountBalanceCalculator} (the same
 * calculator {@code SnapshotBuilder} already uses for the simulation's cash figure). This class
 * still calculates no balance of its own: {@link FinancialDataFormatter#formatAccountBalance}
 * only formats the {@link com.finsight.service.AccountBalance} already handed to it. Because
 * {@code AccountBalanceCalculator} cannot yet determine a {@code TRANSFER}'s direction, an
 * account with a transfer against it is displayed with a "Balance unavailable" placeholder rather
 * than a guessed number (see {@link com.finsight.service.AccountBalance}'s own documentation) —
 * no current workflow in this UI can actually create a transfer, so this is defensive rather than
 * an active limitation of the accounts shown today. Adding a transaction refreshes both the
 * transaction list and every account's displayed balance immediately, with no restart required.
 */
public class FinSightApplication extends Application {

    private static final int PROJECTION_MONTHS = 12;

    /**
     * The scenario input field's pre-filled default: a fixed, deterministic new monthly expense
     * figure, chosen to be meaningfully higher than the demo profile's seeded 40,000.00 (see
     * {@link DemoDataSeeder}) so the baseline-vs-scenario comparison is obviously different by
     * default, without this class reading the seeded profile itself to compute a delta.
     */
    private static final String DEFAULT_NEW_MONTHLY_EXPENSES = "50000.00";

    private CompositionRoot compositionRoot;

    @Override
    public void init() {
        compositionRoot = new CompositionRoot(DatabaseLocation.jdbcUrl());
    }

    @Override
    public void start(Stage primaryStage) {
        VBox header = buildHeader();
        VBox profileSection = buildProfileSection();
        VBox financialDataSection = buildFinancialDataSection();

        // ---------- Financial Projection section ----------

        Label projectionSectionLabel = new Label("Financial Projection");
        projectionSectionLabel.getStyleClass().add("section-heading");

        Label simulationStatusLabel = new Label(
                "Click \"Run Simulation\" to see a " + PROJECTION_MONTHS + "-month financial projection.");
        simulationStatusLabel.getStyleClass().add("section-description");
        simulationStatusLabel.setWrapText(true);

        Label projectionPeriodCaption = new Label();
        projectionPeriodCaption.getStyleClass().add("field-label");
        projectionPeriodCaption.setVisible(false);
        projectionPeriodCaption.setManaged(false);

        Label startingNetWorthValue = new Label();
        Label endingNetWorthValue = new Label();
        Label netWorthChangeValue = new Label();
        Label incomeValue = new Label();
        Label expensesValue = new Label();
        Label surplusValue = new Label();

        // Milestone 5I micro-polish: two fixed rows of three cards each (rather than one FlowPane
        // left to wrap on its own) gives a deterministic 3x2 grid instead of the width-dependent
        // 4-then-2 wrap the single FlowPane produced. Each card is given equal, growable width so
        // the two rows line up as a clean grid rather than being left-packed.
        VBox startingNetWorthCard = metricCard("Net worth after month 1", startingNetWorthValue);
        VBox endingNetWorthCard = metricCard("Net worth after month " + PROJECTION_MONTHS, endingNetWorthValue);
        VBox netWorthChangeCard = metricCard("Net worth change", netWorthChangeValue);
        VBox incomeCard = metricCard("Monthly income", incomeValue);
        VBox expensesCard = metricCard("Monthly expenses", expensesValue);
        VBox surplusCard = metricCard("Monthly surplus", surplusValue);
        for (VBox card : List.of(
                startingNetWorthCard, endingNetWorthCard, netWorthChangeCard, incomeCard, expensesCard, surplusCard)) {
            HBox.setHgrow(card, Priority.ALWAYS);
            card.setMaxWidth(Double.MAX_VALUE);
        }

        HBox metricsRowTop = new HBox(16, startingNetWorthCard, endingNetWorthCard, netWorthChangeCard);
        HBox metricsRowBottom = new HBox(16, incomeCard, expensesCard, surplusCard);
        VBox metricsRow = new VBox(16, metricsRowTop, metricsRowBottom);
        metricsRow.setVisible(false);
        metricsRow.setManaged(false);

        ProjectionChartView projectionChartView = new ProjectionChartView();
        projectionChartView.getStyleClass().add("projection-chart");
        projectionChartView.setPrefHeight(340);
        projectionChartView.setMaxWidth(Double.MAX_VALUE);
        projectionChartView.setVisible(false);
        projectionChartView.setManaged(false);

        StackPane chartContainer = new StackPane(projectionChartView);
        chartContainer.getStyleClass().add("chart-container");

        Button runSimulationButton = new Button("Run Simulation");
        runSimulationButton.getStyleClass().add("primary-button");
        runSimulationButton.setOnAction(event -> {
            try {
                SimulationResult result = runBaselineSimulation();
                SimulationResultView view = SimulationResultFormatter.format(result);

                projectionPeriodCaption.setText("Projection period: " + view.getProjectionPeriod());
                startingNetWorthValue.setText(view.getStartingNetWorth());
                endingNetWorthValue.setText(view.getEndingNetWorth());
                netWorthChangeValue.setText(view.getNetWorthChange());
                applySemanticValueStyle(netWorthChangeValue, view.getNetWorthChange());
                incomeValue.setText(view.getMonthlyIncome());
                expensesValue.setText(view.getMonthlyExpenses());
                surplusValue.setText(view.getMonthlySurplus());
                applySemanticValueStyle(surplusValue, view.getMonthlySurplus());

                showStatus(simulationStatusLabel,
                        "Showing a " + PROJECTION_MONTHS + "-month baseline projection as of " + LocalDate.now() + ".",
                        true);
                setVisibleAndManaged(projectionPeriodCaption, true);
                setVisibleAndManaged(metricsRow, true);
                setVisibleAndManaged(projectionChartView, true);

                projectionChartView.showBaseline(result.getBaselineProjection());
            } catch (RuntimeException e) {
                setVisibleAndManaged(projectionPeriodCaption, false);
                setVisibleAndManaged(metricsRow, false);
                setVisibleAndManaged(projectionChartView, false);
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                showStatus(simulationStatusLabel, "Could not run the simulation: " + reason, false);
            }
        });

        VBox projectionSection = new VBox(16,
                projectionSectionLabel, runSimulationButton, simulationStatusLabel,
                projectionPeriodCaption, metricsRow, chartContainer);
        projectionSection.getStyleClass().add("section-card");

        // ---------- Scenario Simulator section ----------

        Label scenarioSectionLabel = new Label("Scenario Simulator");
        scenarioSectionLabel.getStyleClass().add("section-heading");

        Label scenarioDescription = new Label("What happens if your monthly expenses change?");
        scenarioDescription.getStyleClass().add("section-description");

        Label newExpensesFieldLabel = new Label("New monthly expenses (₹)");
        newExpensesFieldLabel.getStyleClass().add("field-label");
        TextField newMonthlyExpensesField = new TextField(DEFAULT_NEW_MONTHLY_EXPENSES);
        newMonthlyExpensesField.getStyleClass().add("finsight-text-field");
        newMonthlyExpensesField.setPrefColumnCount(12);
        VBox newExpensesFieldBox = new VBox(4, newExpensesFieldLabel, newMonthlyExpensesField);

        Button runScenarioButton = new Button("Run Scenario");
        runScenarioButton.getStyleClass().add("primary-button");

        HBox scenarioInputRow = new HBox(16, newExpensesFieldBox, runScenarioButton);
        scenarioInputRow.setAlignment(Pos.BOTTOM_LEFT);

        Label scenarioStatusLabel = new Label(
                "Enter a new monthly expense amount and click \"Run Scenario\" to compare it against the baseline.");
        scenarioStatusLabel.getStyleClass().add("section-description");
        scenarioStatusLabel.setWrapText(true);

        Label baselineFinalNetWorthValue = new Label();
        Label scenarioFinalNetWorthValue = new Label();
        Label netWorthImpactValue = new Label();

        FlowPane comparisonRow = new FlowPane(16, 16,
                metricCard("Baseline — net worth after month " + PROJECTION_MONTHS, baselineFinalNetWorthValue),
                metricCard("Scenario — net worth after month " + PROJECTION_MONTHS, scenarioFinalNetWorthValue),
                metricCard("Net worth impact", netWorthImpactValue));
        comparisonRow.setVisible(false);
        comparisonRow.setManaged(false);

        runScenarioButton.setOnAction(event -> {
            try {
                BigDecimal newMonthlyExpenses = new BigDecimal(newMonthlyExpensesField.getText().trim());
                if (newMonthlyExpenses.signum() < 0) {
                    throw new IllegalArgumentException("New monthly expenses must not be negative.");
                }

                SimulationResult result = runExpenseIncreaseScenario(newMonthlyExpenses);
                ScenarioComparisonView view = SimulationResultFormatter.formatComparison(result);

                baselineFinalNetWorthValue.setText(view.getBaselineNetWorthAfterFinalMonth());
                scenarioFinalNetWorthValue.setText(view.getScenarioNetWorthAfterFinalMonth());
                netWorthImpactValue.setText(view.getNetWorthImpact());
                applySemanticValueStyle(netWorthImpactValue, view.getNetWorthImpact());

                showStatus(scenarioStatusLabel, "Comparing the baseline against monthly expenses of "
                        + newMonthlyExpensesField.getText().trim() + " from today onward.", true);
                setVisibleAndManaged(comparisonRow, true);

                // Refresh both series from this one result so baseline and scenario always share
                // the same underlying snapshot/timeline, per Milestone 5G's requirement — this is
                // not a second calculation, just re-plotting the same runScenario() output that
                // formatComparison() above already used.
                projectionChartView.showBaseline(result.getBaselineProjection());
                projectionChartView.showScenario(result.getScenarioProjection());
                setVisibleAndManaged(projectionPeriodCaption, true);
                setVisibleAndManaged(metricsRow, true);
                setVisibleAndManaged(projectionChartView, true);
            } catch (RuntimeException e) {
                // Deliberately does not touch projectionChartView here: whatever it was showing
                // before this failed attempt (baseline only, or baseline + a prior scenario)
                // remains exactly as it was, rather than being cleared or partially updated.
                setVisibleAndManaged(comparisonRow, false);
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                showStatus(scenarioStatusLabel, "Could not run the scenario: " + reason, false);
            }
        });

        VBox scenarioSection = new VBox(16,
                scenarioSectionLabel, scenarioDescription, scenarioInputRow, scenarioStatusLabel, comparisonRow);
        scenarioSection.getStyleClass().add("section-card");

        // ---------- Assemble the page ----------

        VBox content = new VBox(24, profileSection, financialDataSection, projectionSection, scenarioSection);
        content.setMaxWidth(880);
        content.setPadding(new Insets(28, 32, 40, 32));

        StackPane centeringPane = new StackPane(content);
        centeringPane.setAlignment(Pos.TOP_CENTER);

        ScrollPane scrollPane = new ScrollPane(centeringPane);
        scrollPane.setFitToWidth(true);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-background");
        root.setTop(header);
        root.setCenter(scrollPane);

        Scene scene = new Scene(root, 960, 840);
        URL cssUrl = getClass().getResource("/finsight.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        primaryStage.setTitle("FinSight");
        primaryStage.setMinWidth(760);
        primaryStage.setMinHeight(600);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /** The fixed application header: title and one-line subtitle, in their own bordered strip. */
    private static VBox buildHeader() {
        Label titleLabel = new Label("FinSight");
        titleLabel.getStyleClass().add("app-title");

        Label subtitleLabel = new Label("Personal Financial Decision Simulator");
        subtitleLabel.getStyleClass().add("app-subtitle");

        VBox header = new VBox(4, titleLabel, subtitleLabel);
        header.getStyleClass().add("app-header");
        header.setPadding(new Insets(22, 32, 22, 32));
        return header;
    }

    /**
     * Builds the "Financial Profile" card: exactly Milestone 5H's editing workflow (pre-filled
     * fields, a save button that delegates entirely to
     * {@code FinancialProfileApplicationService.saveFinancialProfile(String, String)}, and a
     * status message), restyled. No parsing or validation happens in this method — a
     * {@link RuntimeException} from the service is simply caught and its message displayed.
     */
    private VBox buildProfileSection() {
        Label profileSectionLabel = new Label("Financial Profile");
        profileSectionLabel.getStyleClass().add("section-heading");

        Label incomeFieldLabel = new Label("Monthly income (₹)");
        incomeFieldLabel.getStyleClass().add("field-label");
        TextField monthlyIncomeField = new TextField();
        monthlyIncomeField.getStyleClass().add("finsight-text-field");
        monthlyIncomeField.setPrefColumnCount(12);
        VBox incomeFieldBox = new VBox(4, incomeFieldLabel, monthlyIncomeField);

        Label expensesFieldLabel = new Label("Monthly expenses (₹)");
        expensesFieldLabel.getStyleClass().add("field-label");
        TextField monthlyExpensesField = new TextField();
        monthlyExpensesField.getStyleClass().add("finsight-text-field");
        monthlyExpensesField.setPrefColumnCount(12);
        VBox expensesFieldBox = new VBox(4, expensesFieldLabel, monthlyExpensesField);

        Button saveProfileButton = new Button("Save Financial Profile");
        saveProfileButton.getStyleClass().add("primary-button");

        HBox profileFormRow = new HBox(16, incomeFieldBox, expensesFieldBox, saveProfileButton);
        profileFormRow.setAlignment(Pos.BOTTOM_LEFT);

        Label profileStatusLabel = new Label();
        profileStatusLabel.getStyleClass().add("status-message");
        profileStatusLabel.setWrapText(true);

        // Pre-fill from whatever is currently persisted (DemoDataSeeder guarantees a profile
        // exists by the time this runs, but this handles an empty result gracefully instead of
        // assuming that).
        Optional<FinancialProfile> currentProfile = compositionRoot.getFinancialProfileApplicationService().getFinancialProfile();
        currentProfile.ifPresentOrElse(
                profile -> {
                    monthlyIncomeField.setText(profile.getMonthlyIncome().toPlainString());
                    monthlyExpensesField.setText(profile.getMonthlyExpenses().toPlainString());
                },
                () -> showStatus(profileStatusLabel, "No financial profile is recorded yet.", false));

        saveProfileButton.setOnAction(event -> {
            try {
                FinancialProfile saved = compositionRoot.getFinancialProfileApplicationService()
                        .saveFinancialProfile(monthlyIncomeField.getText(), monthlyExpensesField.getText());

                // Reflects back exactly what was persisted, rather than leaving whatever raw text
                // the user typed in place.
                monthlyIncomeField.setText(saved.getMonthlyIncome().toPlainString());
                monthlyExpensesField.setText(saved.getMonthlyExpenses().toPlainString());

                showStatus(profileStatusLabel,
                        "Financial profile saved. Click \"Run Simulation\" to see the updated projection.", true);
            } catch (RuntimeException e) {
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                showStatus(profileStatusLabel, "Could not save the financial profile: " + reason, false);
            }
        });

        VBox profileSection = new VBox(16, profileSectionLabel, profileFormRow, profileStatusLabel);
        profileSection.getStyleClass().add("section-card");
        return profileSection;
    }

    /**
     * Builds the "Financial Data" card (Milestone 5J): a compact accounts display, a persisted
     * transaction list, and an "Add Transaction" form — reusing the existing 5I visual language
     * ({@code section-card}, {@code metric-card} via {@link #metricCard}, {@code field-label},
     * {@code finsight-text-field}, {@code primary-button}, {@code status-message}) rather than
     * introducing any new CSS class.
     *
     * <p>Every piece of data shown here is read through
     * {@link CompositionRoot#getFinancialDataApplicationService()} (never a repository directly),
     * and "Add Transaction" delegates entirely to the existing
     * {@code TransactionApplicationService.recordIncome}/{@code recordExpense} — this method parses
     * only the date and amount text fields (matching the exact pattern the Scenario section's
     * amount field already established: a plain {@link LocalDate#parse}/{@code new BigDecimal(...)}
     * call whose {@link RuntimeException} is caught below and shown as-is), and performs the "a
     * type/category/account must actually be selected" empty-field check the milestone spec
     * explicitly allows the UI to do — every other validation rule (amount positive, category
     * exists and matches the selected type, account exists) remains
     * {@code TransactionApplicationService}'s and {@link Transaction}'s own responsibility, invoked
     * and never duplicated here.
     *
     * <p>Accounts are shown with their current balance (Milestone 5K) — opening balance combined
     * with transactions via {@code AccountBalanceCalculator}, reached through
     * {@link CompositionRoot#getFinancialDataApplicationService()}{@code .getAccountBalances()} and
     * formatted via {@link FinancialDataFormatter#formatAccountBalance(com.finsight.service.AccountBalance)}
     * — never computed by this class itself; see this class's own top-level documentation note for
     * details, including the "Balance unavailable" fallback for a transfer-affected account.
     */
    private VBox buildFinancialDataSection() {
        Label sectionLabel = new Label("Financial Data");
        sectionLabel.getStyleClass().add("section-heading");

        // ---------- Accounts ----------

        Label accountsHeading = new Label("Accounts");
        accountsHeading.getStyleClass().add("field-label");

        FlowPane accountsRow = new FlowPane(16, 16);

        // ---------- Transactions list ----------

        Label transactionsHeading = new Label("Transactions");
        transactionsHeading.getStyleClass().add("field-label");

        TableView<TransactionRowView> transactionsTable = new TableView<>();
        TableColumn<TransactionRowView, String> dateColumn = new TableColumn<>("Date");
        dateColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDate()));
        TableColumn<TransactionRowView, String> typeColumn = new TableColumn<>("Type");
        typeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getType()));
        TableColumn<TransactionRowView, String> categoryColumn = new TableColumn<>("Category");
        categoryColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCategory()));
        TableColumn<TransactionRowView, String> amountColumn = new TableColumn<>("Amount");
        amountColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getAmount()));
        TableColumn<TransactionRowView, String> accountColumn = new TableColumn<>("Account");
        accountColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getAccount()));
        transactionsTable.getColumns().setAll(List.of(
                dateColumn, typeColumn, categoryColumn, amountColumn, accountColumn));
        transactionsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        transactionsTable.setPlaceholder(new Label("No transactions yet. Add one below."));
        transactionsTable.setPrefHeight(200);

        // ---------- Add Transaction form ----------

        Label addTransactionHeading = new Label("Add Transaction");
        addTransactionHeading.getStyleClass().add("field-label");

        Label dateFieldLabel = new Label("Date");
        dateFieldLabel.getStyleClass().add("field-label");
        TextField transactionDateField = new TextField(LocalDate.now().toString());
        transactionDateField.getStyleClass().add("finsight-text-field");
        transactionDateField.setPrefColumnCount(10);
        VBox dateFieldBox = new VBox(4, dateFieldLabel, transactionDateField);

        Label amountFieldLabel = new Label("Amount (₹)");
        amountFieldLabel.getStyleClass().add("field-label");
        TextField transactionAmountField = new TextField();
        transactionAmountField.getStyleClass().add("finsight-text-field");
        transactionAmountField.setPrefColumnCount(10);
        VBox amountFieldBox = new VBox(4, amountFieldLabel, transactionAmountField);

        Label typeFieldLabel = new Label("Type");
        typeFieldLabel.getStyleClass().add("field-label");
        ComboBox<TransactionType> transactionTypeComboBox =
                new ComboBox<>(FXCollections.observableArrayList(TransactionType.INCOME, TransactionType.EXPENSE));
        transactionTypeComboBox.getStyleClass().add("finsight-text-field");
        VBox typeFieldBox = new VBox(4, typeFieldLabel, transactionTypeComboBox);

        Label categoryFieldLabel = new Label("Category");
        categoryFieldLabel.getStyleClass().add("field-label");
        ComboBox<Category> transactionCategoryComboBox = new ComboBox<>();
        transactionCategoryComboBox.getStyleClass().add("finsight-text-field");
        transactionCategoryComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Category category) {
                return category == null ? "" : category.getName();
            }

            @Override
            public Category fromString(String string) {
                return null; // this ComboBox is not editable; a reverse conversion is never used
            }
        });
        VBox categoryFieldBox = new VBox(4, categoryFieldLabel, transactionCategoryComboBox);

        Label accountFieldLabel = new Label("Account");
        accountFieldLabel.getStyleClass().add("field-label");
        ComboBox<Account> transactionAccountComboBox = new ComboBox<>();
        transactionAccountComboBox.getStyleClass().add("finsight-text-field");
        transactionAccountComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Account account) {
                return account == null ? "" : account.getName();
            }

            @Override
            public Account fromString(String string) {
                return null; // this ComboBox is not editable; a reverse conversion is never used
            }
        });
        VBox accountFieldBox = new VBox(4, accountFieldLabel, transactionAccountComboBox);

        Button addTransactionButton = new Button("Add Transaction");
        addTransactionButton.getStyleClass().add("primary-button");

        HBox transactionFormRowTop = new HBox(16, dateFieldBox, amountFieldBox, typeFieldBox);
        HBox transactionFormRowBottom = new HBox(16, categoryFieldBox, accountFieldBox, addTransactionButton);
        transactionFormRowBottom.setAlignment(Pos.BOTTOM_LEFT);
        VBox transactionFormRows = new VBox(12, transactionFormRowTop, transactionFormRowBottom);

        Label financialDataStatusLabel = new Label();
        financialDataStatusLabel.getStyleClass().add("status-message");
        financialDataStatusLabel.setWrapText(true);

        // Every category currently persisted (Milestone 5J's demo seed data provides one INCOME
        // and one EXPENSE category — see DemoDataSeeder), kept here so the category ComboBox can
        // be re-filtered by the selected TransactionType without re-fetching on every keystroke.
        ObservableList<Category> allCategories = FXCollections.observableArrayList();

        // Re-filters the category ComboBox's options to only those categories whose type matches
        // the selected TransactionType — a presentation convenience only (the authoritative
        // category-type check still happens inside TransactionApplicationService.recordIncome/
        // recordExpense; this filtering just makes the easy path easier, it does not replace that
        // check). Selecting a category that is no longer offered (e.g. after switching type)
        // simply clears the ComboBox's selection.
        Consumer<TransactionType> applyCategoryFilter = selectedType -> {
            if (selectedType == null) {
                transactionCategoryComboBox.getItems().clear();
                return;
            }
            CategoryType requiredCategoryType =
                    selectedType == TransactionType.INCOME ? CategoryType.INCOME : CategoryType.EXPENSE;
            List<Category> matching = allCategories.stream()
                    .filter(category -> category.getType() == requiredCategoryType)
                    .toList();
            transactionCategoryComboBox.getItems().setAll(matching);
        };
        transactionTypeComboBox.valueProperty().addListener(
                (observable, oldValue, newValue) -> applyCategoryFilter.accept(newValue));

        // Re-reads every account/category/transaction through FinancialDataApplicationService and
        // repaints the accounts row, the category/account ComboBox options, and the transactions
        // table from that fresh read — never by mutating any of those lists in place from a single
        // add-transaction call, so what is shown always matches what is actually persisted. This is
        // also what makes the account balance cards refresh immediately after a successful "Add
        // Transaction" (Milestone 5K): getAccountBalances() below re-reads every account's
        // transactions and recomputes its balance fresh, every time this runs.
        Runnable refreshFinancialData = () -> {
            List<AccountBalance> accountBalances = compositionRoot.getFinancialDataApplicationService().getAccountBalances();
            List<Account> accounts = accountBalances.stream().map(AccountBalance::getAccount).toList();
            List<Category> categories = compositionRoot.getFinancialDataApplicationService().getCategories();
            List<Transaction> transactions = compositionRoot.getFinancialDataApplicationService().getTransactions();

            accountsRow.getChildren().setAll(accountBalances.stream().map(this::accountCard).toList());

            allCategories.setAll(categories);
            applyCategoryFilter.accept(transactionTypeComboBox.getValue());

            transactionAccountComboBox.getItems().setAll(accounts);

            transactionsTable.getItems().setAll(
                    FinancialDataFormatter.toTransactionRows(transactions, categories, accounts));
        };

        addTransactionButton.setOnAction(event -> {
            TransactionType selectedType = transactionTypeComboBox.getValue();
            Category selectedCategory = transactionCategoryComboBox.getValue();
            Account selectedAccount = transactionAccountComboBox.getValue();
            if (selectedType == null || selectedCategory == null || selectedAccount == null) {
                showStatus(financialDataStatusLabel,
                        "Please select a transaction type, category, and account.", false);
                return;
            }
            try {
                LocalDate date = LocalDate.parse(transactionDateField.getText().trim());
                BigDecimal amount = new BigDecimal(transactionAmountField.getText().trim());

                if (selectedType == TransactionType.INCOME) {
                    compositionRoot.getTransactionApplicationService().recordIncome(
                            selectedAccount.getId(), amount, selectedCategory.getId(), date, null);
                } else {
                    compositionRoot.getTransactionApplicationService().recordExpense(
                            selectedAccount.getId(), amount, selectedCategory.getId(), date, null);
                }

                transactionAmountField.clear();
                showStatus(financialDataStatusLabel,
                        "Transaction saved successfully. Click \"Run Simulation\" to see the updated projection.",
                        true);
                refreshFinancialData.run();
            } catch (RuntimeException e) {
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                showStatus(financialDataStatusLabel, "Could not add the transaction: " + reason, false);
            }
        });

        // Initial population, so accounts/transactions are visible immediately on launch rather
        // than only after some button click — both are read-only displays of already-persisted
        // state, unlike the Projection/Scenario sections' results, which only exist after a run.
        refreshFinancialData.run();

        VBox financialDataSection = new VBox(16,
                sectionLabel,
                accountsHeading, accountsRow,
                transactionsHeading, transactionsTable,
                addTransactionHeading, transactionFormRows, financialDataStatusLabel);
        financialDataSection.getStyleClass().add("section-card");
        return financialDataSection;
    }

    /**
     * A compact account display card: reuses {@link #metricCard} exactly as the Projection and
     * Scenario sections already do, with the account's name/type/currency as the muted label line
     * and its current balance (Milestone 5K; opening balance combined with transactions, already
     * computed by {@code AccountBalanceCalculator} and carried by {@code accountBalance}) as the
     * prominent value — introducing no new card layout or CSS class for the Accounts display. When
     * {@link AccountBalance#getCurrentBalance()} is absent (a transfer-affected account —
     * see that class's own documentation), the "Balance unavailable" placeholder
     * {@link FinancialDataFormatter#formatAccountBalance(AccountBalance)} returns is shown as-is,
     * without the "₹" prefix that only makes sense for an actual number.
     */
    private VBox accountCard(AccountBalance accountBalance) {
        Account account = accountBalance.getAccount();
        String balanceText = FinancialDataFormatter.formatAccountBalance(accountBalance);
        String displayedValue = accountBalance.getCurrentBalance().isPresent() ? "₹" + balanceText : balanceText;
        Label balanceLabel = new Label(displayedValue);
        String subtitle = account.getName() + " · " + FinancialDataFormatter.formatAccountSubtitle(account);
        return metricCard(subtitle, balanceLabel);
    }

    /**
     * A small, self-contained "metric card": a muted label above a prominent value, both wrapped
     * in a bordered {@link VBox}. Purely a layout/styling helper — {@code valueLabel} is the same
     * {@link Label} instance the caller already updates via {@code setText}, so no data flows
     * through this method beyond arranging the two nodes.
     */
    private static VBox metricCard(String label, Label valueLabel) {
        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("metric-label");
        labelNode.setWrapText(true);
        valueLabel.getStyleClass().add("metric-value");

        VBox card = new VBox(6, labelNode, valueLabel);
        card.getStyleClass().add("metric-card");
        HBox.setHgrow(card, Priority.NEVER);
        return card;
    }

    /**
     * Sets {@code label}'s text and swaps in the {@code status-success}/{@code status-error} CSS
     * class for {@code success}, replacing whichever of the two was there before. Purely visual —
     * every message text passed to this method is unchanged from what earlier milestones already
     * displayed.
     */
    private static void showStatus(Label label, String message, boolean success) {
        label.setText(message);
        label.getStyleClass().removeAll("status-success", "status-error");
        label.getStyleClass().add(success ? "status-success" : "status-error");
    }

    /**
     * Applies {@code metric-value-positive}/{@code metric-value-negative} to {@code valueLabel}
     * based on the leading character of {@code formattedValue} (already produced by
     * {@link SimulationResultFormatter}'s signed-money formatting: a leading {@code '+'} for a
     * positive amount, a leading {@code '-'} for a negative one, produced by {@link BigDecimal}
     * itself). This reads a formatting convention that already exists; it does not parse the
     * amount or perform any arithmetic. Color is a restrained accent only — the sign character
     * itself, not color, is what actually communicates positive vs. negative.
     */
    private static void applySemanticValueStyle(Label valueLabel, String formattedValue) {
        valueLabel.getStyleClass().removeAll("metric-value-positive", "metric-value-negative");
        if (formattedValue.startsWith("-")) {
            valueLabel.getStyleClass().add("metric-value-negative");
        } else if (formattedValue.startsWith("+")) {
            valueLabel.getStyleClass().add("metric-value-positive");
        }
    }

    private static void setVisibleAndManaged(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    /**
     * Runs the fixed 12-month, zero-growth, no-events baseline simulation against
     * {@link CompositionRoot}'s already-composed {@link SimulationApplicationService}, and returns
     * the full {@link SimulationResult} so the caller can both format it for display and hand its
     * {@code Projection} to {@link ProjectionChartView} without a second service call. Any failure
     * (most commonly {@code ApplicationValidationException} if the persisted data is somehow
     * incomplete, or {@code RepositoryException} on a storage failure) propagates unchanged to the
     * caller, which is responsible for turning it into a user-facing message rather than letting
     * it crash the JavaFX Application Thread.
     */
    private SimulationResult runBaselineSimulation() {
        SimulationApplicationService simulationApplicationService = compositionRoot.getSimulationApplicationService();

        LocalDate asOfDate = LocalDate.now();
        SimulationParameters parameters =
                new SimulationParameters(PROJECTION_MONTHS, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE);
        Scenario baselineOnlyScenario = new Scenario(
                0L, "Baseline (no changes)", "No scenario events — shows the baseline projection only.",
                asOfDate, List.of());

        return simulationApplicationService.runScenario(asOfDate, parameters, baselineOnlyScenario);
    }

    /**
     * Runs the same fixed 12-month, zero-growth parameters as {@link #runBaselineSimulation()},
     * but with a real, one-event {@link Scenario}: a single {@link ExpenseChangeEvent}, effective
     * immediately, replacing monthly expenses with {@code newMonthlyExpenses} for the whole
     * projection. {@link SimulationApplicationService#runScenario} itself computes both the
     * baseline and scenario projections and their {@code ScenarioComparison} from this one call —
     * this method performs no comparison or chart arithmetic itself, only returns the result for
     * the caller to format and plot.
     */
    private SimulationResult runExpenseIncreaseScenario(BigDecimal newMonthlyExpenses) {
        SimulationApplicationService simulationApplicationService = compositionRoot.getSimulationApplicationService();

        LocalDate asOfDate = LocalDate.now();
        SimulationParameters parameters =
                new SimulationParameters(PROJECTION_MONTHS, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE);
        Scenario expenseIncreaseScenario = new Scenario(
                0L, "Increased monthly expenses",
                "Monthly expenses change to a new fixed amount, effective immediately.",
                asOfDate, List.of(new ExpenseChangeEvent(asOfDate, newMonthlyExpenses)));

        return simulationApplicationService.runScenario(asOfDate, parameters, expenseIncreaseScenario);
    }

    @Override
    public void stop() {
        compositionRoot.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
