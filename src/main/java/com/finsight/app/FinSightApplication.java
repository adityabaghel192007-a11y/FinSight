package com.finsight.app;

import com.finsight.domain.account.Account;
import com.finsight.domain.account.Currency;
import com.finsight.domain.category.Category;
import com.finsight.domain.category.CategoryType;
import com.finsight.domain.financialprofile.FinancialProfile;
import com.finsight.domain.investment.AssetType;
import com.finsight.domain.investment.Investment;
import com.finsight.domain.transaction.Transaction;
import com.finsight.domain.transaction.TransactionType;
import com.finsight.service.AccountBalance;
import com.finsight.service.SimulationApplicationService;
import com.finsight.service.SimulationResult;
import com.finsight.simulation.model.SimulationParameters;
import com.finsight.simulation.scenario.Scenario;
import com.finsight.ui.DecisionLabView;
import com.finsight.ui.FinancialDataFormatter;
import com.finsight.ui.FinancialOverviewView;
import com.finsight.ui.InvestmentFormatter;
import com.finsight.ui.InvestmentRowView;
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
 * ({@link #runBaselineSimulation()}, and — before Milestone 6B replaced it with
 * {@link DecisionLabView} — the scenario-running methods that stood here at the time) is
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
 * <p><b>Building and running a scenario</b> is the Decision Lab's job (Milestone 6B) — see
 * {@link DecisionLabView}'s own documentation for the full design. In short: it replaced two
 * earlier, single-event-only sections ("Scenario Simulator" from Milestone 5F, and an
 * "Investment Scenario" section from the Investments milestone) with one unified section that
 * lets the user build up any number of heterogeneous {@code ScenarioEvent}s and run them all
 * together as one {@link Scenario} — {@code SimulationEngine} and
 * {@code SimulationApplicationService} already fully supported this; only the UI could not build
 * such a scenario before. On a successful run, {@link DecisionLabView} reports the
 * {@code SimulationResult} back to this class via a callback, which refreshes
 * {@link ProjectionChartView#showBaseline}/{@link ProjectionChartView#showScenario} exactly as the
 * two sections it replaced already did — refreshing the baseline series too (with data that is, by
 * construction of {@code SimulationApplicationService.runScenario}, built from the very same
 * snapshot as the scenario) keeps both chart series drawn from one shared timeline, exactly as
 * Milestone 5G originally required.
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
 *
 * <p><b>The Investments section (Investments milestone)</b> displays persisted investments and
 * lets the user add a new one, entirely through
 * {@link CompositionRoot#getInvestmentApplicationService()} — never a repository directly.
 * Invested value, current value, and gain/loss are computed by the existing, already-tested
 * {@code InvestmentCalculator} (via {@link InvestmentFormatter}), never by this class; every figure
 * is shown in the investment's own currency, with no conversion to INR (see
 * {@link InvestmentFormatter}'s own documentation for why). ADD and VIEW are the only supported
 * workflows — there is deliberately no edit or delete, matching {@code InvestmentRepository}'s own
 * by-design omission of {@code update}. Because {@code SnapshotBuilder} already consumes every
 * persisted investment unconditionally, an investment added here is automatically reflected the
 * very next time "Run Simulation" or the Decision Lab's "Run Scenario" is run — the same "no
 * further wiring needed"
 * property every other section of this application already relies on.
 *
 * <p><b>Milestone 6A ("Expose the full Scenario Comparison")</b> widened the comparison display
 * from showing only net worth to showing all five metric categories {@code ScenarioComparison}
 * already computed (net worth, investment value, loan balance, cash balance, total investment
 * contributions), each as its own baseline/scenario pair plus one "impact" card combining the
 * absolute difference and the percentage change. That widened {@link ScenarioComparisonView}/
 * {@link SimulationResultFormatter#formatComparison} is exactly what {@link DecisionLabView} now
 * reads to build its own single copy of that same 15-card layout (Milestone 6A's two duplicate
 * copies — one per old scenario section — collapsed into one, in {@link DecisionLabView} itself,
 * when Milestone 6B replaced those two sections).
 *
 * <p><b>Financial Overview (Milestone 7A):</b> a compact, read-only {@link FinancialOverviewView}
 * is placed at the very top of {@code content}, immediately below the header and before the
 * Financial Profile section, so a user sees their current financial position — monthly income,
 * expenses, and savings, plus consolidated cash, investments, loans, and net worth — as soon as
 * FinSight opens. It is built once here from
 * {@link CompositionRoot#getFinancialOverviewApplicationService()} and the same fixed
 * {@code usdToInrRate} used elsewhere in this class; see that view's own documentation for why it
 * computes its figures once, at construction, rather than exposing a refresh method.
 */
public class FinSightApplication extends Application {

    private static final int PROJECTION_MONTHS = 12;

    private CompositionRoot compositionRoot;

    @Override
    public void init() {
        compositionRoot = new CompositionRoot(DatabaseLocation.jdbcUrl());
    }

    @Override
    public void start(Stage primaryStage) {
        VBox header = buildHeader();

        // The same fixed, no-live-lookup USD/INR rate every other section of this application
        // already uses (see runBaselineSimulation()'s identical SimulationParameters construction
        // below) — Milestone 7A introduces no new FX source.
        FinancialOverviewView financialOverviewView = new FinancialOverviewView(
                compositionRoot.getFinancialOverviewApplicationService(), BigDecimal.ONE);

        VBox profileSection = buildProfileSection();
        VBox financialDataSection = buildFinancialDataSection();

        // Shared between buildInvestmentsSection() below and the Decision Lab's investment
        // scenario controls further down this method: every refresh inside
        // buildInvestmentsSection() (initial population and after a successful add) updates this
        // same ObservableList, so the scenario controls' ComboBox and enabled/disabled state stay
        // in sync with what is actually persisted, with no separate notification mechanism needed.
        ObservableList<Investment> investmentsList = FXCollections.observableArrayList();
        VBox investmentsSection = buildInvestmentsSection(investmentsList);

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

        // ---------- Decision Lab section (Milestone 6B: unified multi-event scenario builder) ----------
        //
        // Replaces the old separate "Scenario Simulator" (expense-only) and "Investment Scenario"
        // sections above with one DecisionLabView — see that class's own documentation for why it
        // was extracted rather than becoming another buildXxxSection method here, and for exactly
        // how it builds and runs a multi-event Scenario through the same, unmodified
        // SimulationApplicationService every other section already uses. onDecisionLabScenarioRun
        // below is exactly the chart/caption/metrics-row refresh the two old sections each used to
        // perform inline after a successful run — those nodes belong to the Financial Projection
        // section above, not to the Decision Lab, so DecisionLabView reports its result back
        // through this callback rather than reaching into another section's nodes itself.

        Consumer<SimulationResult> onDecisionLabScenarioRun = result -> {
            projectionChartView.showBaseline(result.getBaselineProjection());
            projectionChartView.showScenario(result.getScenarioProjection());
            setVisibleAndManaged(projectionPeriodCaption, true);
            setVisibleAndManaged(metricsRow, true);
            setVisibleAndManaged(projectionChartView, true);
        };
        DecisionLabView decisionLabView = new DecisionLabView(
                compositionRoot.getSimulationApplicationService(), investmentsList, onDecisionLabScenarioRun);

        // ---------- Assemble the page ----------

        VBox content = new VBox(24, financialOverviewView, profileSection, financialDataSection,
                investmentsSection, projectionSection, decisionLabView);
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
     * Builds the "Investments" card (the Investments milestone): a table of persisted investments
     * and an "Add Investment" form — reusing the existing 5I/5J visual language
     * ({@code section-card}, {@code field-label}, {@code finsight-text-field},
     * {@code primary-button}, {@code status-message}) rather than introducing any new CSS class.
     *
     * <p>Every piece of data shown here is read through
     * {@link CompositionRoot#getInvestmentApplicationService()} (never a repository directly), and
     * "Add Investment" delegates entirely to {@code InvestmentApplicationService.addInvestment} —
     * this method parses only the numeric text fields (matching the exact pattern the Financial
     * Data section's "Add Transaction" form already established) and performs the "an account,
     * asset type, and currency must actually be selected" empty-field check the milestone spec
     * explicitly allows the UI to do; every other validation rule (account exists, name non-blank,
     * quantity positive, ...) remains {@code InvestmentApplicationService}'s and
     * {@link Investment}'s own responsibility, invoked and never duplicated here.
     *
     * <p>Every monetary figure is displayed via {@link InvestmentFormatter}, in the investment's
     * own currency — never converted to INR here (see that class's own documentation for why).
     *
     * @param investmentsList the single {@link ObservableList} shared with the Decision Lab's
     *     investment scenario controls built later in {@link #start(Stage)}; every refresh here
     *     (initial population and after a successful add) updates this same list, so those
     *     controls' {@code ComboBox} and enabled state stay in sync with what is actually
     *     persisted, with no separate notification mechanism needed
     */
    private VBox buildInvestmentsSection(ObservableList<Investment> investmentsList) {
        Label sectionLabel = new Label("Investments");
        sectionLabel.getStyleClass().add("section-heading");

        // ---------- Holdings table ----------

        Label holdingsHeading = new Label("Holdings");
        holdingsHeading.getStyleClass().add("field-label");

        TableView<InvestmentRowView> investmentsTable = new TableView<>();
        TableColumn<InvestmentRowView, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        TableColumn<InvestmentRowView, String> tickerColumn = new TableColumn<>("Ticker");
        tickerColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTicker()));
        TableColumn<InvestmentRowView, String> assetTypeColumn = new TableColumn<>("Asset Type");
        assetTypeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getAssetType()));
        TableColumn<InvestmentRowView, String> currencyColumn = new TableColumn<>("Currency");
        currencyColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCurrency()));
        TableColumn<InvestmentRowView, String> quantityColumn = new TableColumn<>("Quantity");
        quantityColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getQuantity()));
        TableColumn<InvestmentRowView, String> avgPriceColumn = new TableColumn<>("Avg. Purchase Price");
        avgPriceColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getAveragePurchasePrice()));
        TableColumn<InvestmentRowView, String> currentPriceColumn = new TableColumn<>("Current Price");
        currentPriceColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCurrentPrice()));
        TableColumn<InvestmentRowView, String> investedValueColumn = new TableColumn<>("Invested Value");
        investedValueColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getInvestedValue()));
        TableColumn<InvestmentRowView, String> currentValueColumn = new TableColumn<>("Current Value");
        currentValueColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCurrentValue()));
        TableColumn<InvestmentRowView, String> gainLossColumn = new TableColumn<>("Gain/Loss");
        gainLossColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getGainLoss()));
        TableColumn<InvestmentRowView, String> accountColumn = new TableColumn<>("Account");
        accountColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getAccountName()));
        investmentsTable.getColumns().setAll(List.of(
                nameColumn, tickerColumn, assetTypeColumn, currencyColumn, quantityColumn,
                avgPriceColumn, currentPriceColumn, investedValueColumn, currentValueColumn,
                gainLossColumn, accountColumn));
        investmentsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        investmentsTable.setPlaceholder(new Label("No investments yet. Add one below."));
        investmentsTable.setPrefHeight(200);

        // ---------- Add Investment form ----------

        Label addInvestmentHeading = new Label("Add Investment");
        addInvestmentHeading.getStyleClass().add("field-label");

        Label accountFieldLabel = new Label("Account");
        accountFieldLabel.getStyleClass().add("field-label");
        ComboBox<Account> investmentAccountComboBox = new ComboBox<>();
        investmentAccountComboBox.getStyleClass().add("finsight-text-field");
        investmentAccountComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Account account) {
                return account == null ? "" : account.getName();
            }

            @Override
            public Account fromString(String string) {
                return null; // this ComboBox is not editable; a reverse conversion is never used
            }
        });
        VBox accountFieldBox = new VBox(4, accountFieldLabel, investmentAccountComboBox);

        Label nameFieldLabel = new Label("Name");
        nameFieldLabel.getStyleClass().add("field-label");
        TextField investmentNameField = new TextField();
        investmentNameField.getStyleClass().add("finsight-text-field");
        investmentNameField.setPrefColumnCount(14);
        VBox nameFieldBox = new VBox(4, nameFieldLabel, investmentNameField);

        Label tickerFieldLabel = new Label("Ticker (optional)");
        tickerFieldLabel.getStyleClass().add("field-label");
        TextField investmentTickerField = new TextField();
        investmentTickerField.getStyleClass().add("finsight-text-field");
        investmentTickerField.setPrefColumnCount(10);
        VBox tickerFieldBox = new VBox(4, tickerFieldLabel, investmentTickerField);

        Label assetTypeFieldLabel = new Label("Asset Type");
        assetTypeFieldLabel.getStyleClass().add("field-label");
        ComboBox<AssetType> investmentAssetTypeComboBox =
                new ComboBox<>(FXCollections.observableArrayList(AssetType.values()));
        investmentAssetTypeComboBox.getStyleClass().add("finsight-text-field");
        VBox assetTypeFieldBox = new VBox(4, assetTypeFieldLabel, investmentAssetTypeComboBox);

        Label currencyFieldLabel = new Label("Currency");
        currencyFieldLabel.getStyleClass().add("field-label");
        ComboBox<Currency> investmentCurrencyComboBox =
                new ComboBox<>(FXCollections.observableArrayList(Currency.values()));
        investmentCurrencyComboBox.getStyleClass().add("finsight-text-field");
        VBox currencyFieldBox = new VBox(4, currencyFieldLabel, investmentCurrencyComboBox);

        Label quantityFieldLabel = new Label("Quantity");
        quantityFieldLabel.getStyleClass().add("field-label");
        TextField investmentQuantityField = new TextField();
        investmentQuantityField.getStyleClass().add("finsight-text-field");
        investmentQuantityField.setPrefColumnCount(8);
        VBox quantityFieldBox = new VBox(4, quantityFieldLabel, investmentQuantityField);

        Label avgPriceFieldLabel = new Label("Average Purchase Price");
        avgPriceFieldLabel.getStyleClass().add("field-label");
        TextField investmentAvgPriceField = new TextField();
        investmentAvgPriceField.getStyleClass().add("finsight-text-field");
        investmentAvgPriceField.setPrefColumnCount(10);
        VBox avgPriceFieldBox = new VBox(4, avgPriceFieldLabel, investmentAvgPriceField);

        Label currentPriceFieldLabel = new Label("Current Price");
        currentPriceFieldLabel.getStyleClass().add("field-label");
        TextField investmentCurrentPriceField = new TextField();
        investmentCurrentPriceField.getStyleClass().add("finsight-text-field");
        investmentCurrentPriceField.setPrefColumnCount(10);
        VBox currentPriceFieldBox = new VBox(4, currentPriceFieldLabel, investmentCurrentPriceField);

        Label expectedReturnFieldLabel = new Label("Expected Annual Return");
        expectedReturnFieldLabel.getStyleClass().add("field-label");
        TextField investmentExpectedReturnField = new TextField("0.00");
        investmentExpectedReturnField.getStyleClass().add("finsight-text-field");
        investmentExpectedReturnField.setPrefColumnCount(8);
        VBox expectedReturnFieldBox = new VBox(4, expectedReturnFieldLabel, investmentExpectedReturnField);

        Label monthlyContributionFieldLabel = new Label("Monthly Contribution");
        monthlyContributionFieldLabel.getStyleClass().add("field-label");
        TextField investmentMonthlyContributionField = new TextField("0.00");
        investmentMonthlyContributionField.getStyleClass().add("finsight-text-field");
        investmentMonthlyContributionField.setPrefColumnCount(10);
        VBox monthlyContributionFieldBox = new VBox(4, monthlyContributionFieldLabel, investmentMonthlyContributionField);

        Button addInvestmentButton = new Button("Add Investment");
        addInvestmentButton.getStyleClass().add("primary-button");

        HBox investmentFormRowOne = new HBox(16, accountFieldBox, nameFieldBox, tickerFieldBox);
        HBox investmentFormRowTwo = new HBox(16, assetTypeFieldBox, currencyFieldBox, quantityFieldBox);
        HBox investmentFormRowThree = new HBox(16, avgPriceFieldBox, currentPriceFieldBox, expectedReturnFieldBox);
        HBox investmentFormRowFour = new HBox(16, monthlyContributionFieldBox, addInvestmentButton);
        investmentFormRowFour.setAlignment(Pos.BOTTOM_LEFT);
        VBox investmentFormRows = new VBox(
                12, investmentFormRowOne, investmentFormRowTwo, investmentFormRowThree, investmentFormRowFour);

        Label investmentsStatusLabel = new Label();
        investmentsStatusLabel.getStyleClass().add("status-message");
        investmentsStatusLabel.setWrapText(true);

        // Re-reads every account/investment through FinancialDataApplicationService/
        // InvestmentApplicationService and repaints the account ComboBox and the investments table
        // from that fresh read — never by mutating either in place from a single add-investment
        // call, matching buildFinancialDataSection()'s identical refresh discipline. Also updates
        // investmentsList, the ObservableList shared with the Decision Lab's investment scenario
        // controls, so their ComboBox and enabled state stay in sync immediately.
        Runnable refreshInvestments = () -> {
            List<Account> accounts = compositionRoot.getFinancialDataApplicationService().getAccounts();
            List<Investment> investments = compositionRoot.getInvestmentApplicationService().getInvestments();

            investmentAccountComboBox.getItems().setAll(accounts);
            investmentsTable.getItems().setAll(InvestmentFormatter.toInvestmentRows(investments, accounts));
            investmentsList.setAll(investments);
        };

        addInvestmentButton.setOnAction(event -> {
            Account selectedAccount = investmentAccountComboBox.getValue();
            AssetType selectedAssetType = investmentAssetTypeComboBox.getValue();
            Currency selectedCurrency = investmentCurrencyComboBox.getValue();
            if (selectedAccount == null || selectedAssetType == null || selectedCurrency == null) {
                showStatus(investmentsStatusLabel, "Please select an account, asset type, and currency.", false);
                return;
            }
            try {
                String name = investmentNameField.getText().trim();
                String ticker = investmentTickerField.getText().trim();
                BigDecimal quantity = new BigDecimal(investmentQuantityField.getText().trim());
                BigDecimal averagePurchasePrice = new BigDecimal(investmentAvgPriceField.getText().trim());
                BigDecimal currentPrice = new BigDecimal(investmentCurrentPriceField.getText().trim());
                BigDecimal expectedAnnualReturn = new BigDecimal(investmentExpectedReturnField.getText().trim());
                BigDecimal monthlyContribution = new BigDecimal(investmentMonthlyContributionField.getText().trim());

                compositionRoot.getInvestmentApplicationService().addInvestment(
                        selectedAccount.getId(), name, ticker.isEmpty() ? null : ticker, selectedAssetType,
                        selectedCurrency, quantity, averagePurchasePrice, currentPrice,
                        expectedAnnualReturn, monthlyContribution);

                investmentNameField.clear();
                investmentTickerField.clear();
                investmentQuantityField.clear();
                investmentAvgPriceField.clear();
                investmentCurrentPriceField.clear();
                investmentExpectedReturnField.setText("0.00");
                investmentMonthlyContributionField.setText("0.00");

                showStatus(investmentsStatusLabel,
                        "Investment saved. Click \"Run Simulation\" to see it reflected in your projection.", true);
                refreshInvestments.run();
            } catch (RuntimeException e) {
                // Every failure this can realistically be (ApplicationValidationException for a
                // stale/missing account, IllegalArgumentException/NullPointerException from
                // Investment's own constructor, NumberFormatException from a malformed numeric
                // field, or RepositoryException from a storage failure) already carries a plain,
                // user-facing message — never a raw SQL stack trace — matching every other
                // try/catch in this class.
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                showStatus(investmentsStatusLabel, "Could not add the investment: " + reason, false);
            }
        });

        // Initial population, so investments are visible immediately on launch rather than only
        // after some button click — matching buildFinancialDataSection()'s identical rationale.
        refreshInvestments.run();

        VBox investmentsSection = new VBox(16,
                sectionLabel,
                holdingsHeading, investmentsTable,
                addInvestmentHeading, investmentFormRows, investmentsStatusLabel);
        investmentsSection.getStyleClass().add("section-card");
        return investmentsSection;
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

    @Override
    public void stop() {
        compositionRoot.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
