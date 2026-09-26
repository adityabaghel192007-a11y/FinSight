package com.finsight.ui;

import com.finsight.domain.investment.Investment;
import com.finsight.service.SimulationApplicationService;
import com.finsight.service.SimulationResult;
import com.finsight.simulation.model.SimulationParameters;
import com.finsight.simulation.scenario.ExpenseChangeEvent;
import com.finsight.simulation.scenario.IncomeChangeEvent;
import com.finsight.simulation.scenario.InvestmentContributionChangeEvent;
import com.finsight.simulation.scenario.InvestmentReturnChangeEvent;
import com.finsight.simulation.scenario.OneTimeExpenseEvent;
import com.finsight.simulation.scenario.Scenario;
import com.finsight.simulation.scenario.ScenarioEvent;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The Decision Lab (Milestone 6B): one unified, multi-event scenario builder, replacing the two
 * separate single-event scenario sections ("Scenario Simulator" and "Investment Scenario") that
 * previously lived inline in {@code FinSightApplication}. A user builds up any number of
 * {@link ScenarioEvent}s of any of the five existing kinds, then runs them all together as one
 * {@link Scenario} — something {@code SimulationEngine} and {@link SimulationApplicationService}
 * already fully supported (see their own documentation and
 * {@code ScenarioApplicationTest#netWorthInvariantHoldsWithAllFiveEventTypesCombined}); this class
 * introduces no new simulation capability, only a UI that can finally build a scenario with more
 * than one event in it.
 *
 * <p><b>Extracted into its own class</b> (rather than another {@code buildXxxSection} method on
 * {@code FinSightApplication}, which was already approximately 1,300 lines) because the "show only
 * the fields relevant to the selected event type" requirement genuinely needed more branching than
 * any existing section — this is a plain, self-contained JavaFX component, exactly the same shape
 * as {@link ProjectionChartView} (a {@code Node} subclass built once and updated through a small
 * public surface), not a controller, not part of an MVC framework, and not a base for future
 * subclassing.
 *
 * <p><b>Pending events are UI-owned, transient state</b> — an {@link ObservableList} field on this
 * class, never written to any repository or the database (there is no {@code ScenarioRepository},
 * and this milestone does not add one). They exist only for as long as this component exists, and
 * running a scenario does not clear them, so the user can tweak one event and rerun without
 * rebuilding the whole list — see {@link #runScenario} below.
 *
 * <p><b>No new calculation anywhere in this class.</b> Building a {@link ScenarioEvent} from the
 * form is exactly the same "read the typed text into strongly-typed fields, let the constructor
 * validate" pattern the two sections this replaces already used; running a scenario delegates
 * entirely to the already-composed {@link SimulationApplicationService} this class is handed at
 * construction; and displaying the result reuses {@link SimulationResultFormatter#formatComparison}
 * and {@link ScenarioComparisonView} exactly as Milestone 6A already built them — the same 15-card
 * (5 metrics × baseline/scenario/impact) layout the two old sections each had one copy of, now
 * appearing exactly once here. If the engine rejects the combined events (e.g. two same-property,
 * same-date changes — see {@code SimulationEngine}'s own "Conflict validation" documentation), its
 * {@link IllegalArgumentException} message is caught and shown through this class's own status
 * label, unchanged and unduplicated — this class never re-implements that check.
 *
 * <p><b>Investment gating</b> mirrors the same idea the old "Investment Scenario" section already
 * used (disable what cannot yet be used, explain why), generalized from "the whole section" to
 * just the two investment-targeting entries in the event-type picker: since
 * {@code InvestmentRepository} deliberately has no {@code update}/{@code delete} (see its own
 * documentation), the investments list handed to this class can only ever grow, never shrink, so
 * "no investments yet" is a state this component can only be in before the very first investment
 * is ever added — never something it needs to recover from mid-use.
 */
public final class DecisionLabView extends VBox {

    private static final int PROJECTION_MONTHS = 12;

    private static final String INCOME_CHANGE = "Income change";
    private static final String EXPENSE_CHANGE = "Expense change";
    private static final String ONE_TIME_EXPENSE = "One-time expense";
    private static final String INVESTMENT_CONTRIBUTION_CHANGE = "Investment contribution change";
    private static final String INVESTMENT_RETURN_CHANGE = "Investment return change";

    private final SimulationApplicationService simulationApplicationService;
    private final ObservableList<ScenarioEvent> pendingEvents = FXCollections.observableArrayList();

    /**
     * @param simulationApplicationService the already-composed service this view runs every
     *     scenario through — the same one {@code FinSightApplication}'s other sections already use
     *     via {@code CompositionRoot#getSimulationApplicationService()}; never a repository
     * @param investmentsList the same {@link ObservableList} of persisted investments
     *     {@code FinSightApplication}'s Investments section already builds and refreshes — shared,
     *     not copied, so an investment added there is immediately reflected here with no further
     *     wiring, exactly like the section this replaces
     * @param onScenarioRun invoked with the full {@link SimulationResult} after a scenario runs
     *     successfully, so the caller can refresh whatever else displays it (the shared projection
     *     chart, in {@code FinSightApplication}'s case) — this view owns only its own pending
     *     events and comparison display, not nodes that belong to another section
     */
    public DecisionLabView(
            SimulationApplicationService simulationApplicationService,
            ObservableList<Investment> investmentsList,
            Consumer<SimulationResult> onScenarioRun) {
        super(16);
        this.simulationApplicationService =
                Objects.requireNonNull(simulationApplicationService, "simulationApplicationService must not be null");
        Objects.requireNonNull(investmentsList, "investmentsList must not be null");
        Objects.requireNonNull(onScenarioRun, "onScenarioRun must not be null");

        getStyleClass().add("section-card");
        buildUi(investmentsList, onScenarioRun);
    }

    private void buildUi(ObservableList<Investment> investmentsList, Consumer<SimulationResult> onScenarioRun) {
        Label sectionLabel = new Label("Decision Lab");
        sectionLabel.getStyleClass().add("section-heading");

        Label description = new Label("Build a financial \"what-if\" scenario by combining multiple changes.");
        description.getStyleClass().add("section-description");
        description.setWrapText(true);

        // ---------- Add Event ----------

        Label addEventHeading = new Label("Add Event");
        addEventHeading.getStyleClass().add("field-label");

        Label eventTypeFieldLabel = new Label("Event type");
        eventTypeFieldLabel.getStyleClass().add("field-label");
        ComboBox<String> eventTypeComboBox = new ComboBox<>(FXCollections.observableArrayList(
                INCOME_CHANGE, EXPENSE_CHANGE, ONE_TIME_EXPENSE, INVESTMENT_CONTRIBUTION_CHANGE, INVESTMENT_RETURN_CHANGE));
        eventTypeComboBox.getStyleClass().add("finsight-text-field");
        eventTypeComboBox.getSelectionModel().selectFirst();
        VBox eventTypeFieldBox = new VBox(4, eventTypeFieldLabel, eventTypeComboBox);

        Label investmentFieldLabel = new Label("Investment");
        investmentFieldLabel.getStyleClass().add("field-label");
        ComboBox<Investment> investmentComboBox = new ComboBox<>(investmentsList);
        investmentComboBox.getStyleClass().add("finsight-text-field");
        investmentComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Investment investment) {
                // "Name (Asset Type)" — never the database id (see InvestmentFormatter's own
                // documentation on this method).
                return investment == null ? "" : InvestmentFormatter.formatInvestmentOptionLabel(investment);
            }

            @Override
            public Investment fromString(String string) {
                return null; // this ComboBox is not editable; a reverse conversion is never used
            }
        });
        VBox investmentFieldBox = new VBox(4, investmentFieldLabel, investmentComboBox);

        Label gatingMessageLabel = new Label("Add an investment above to enable this event type.");
        gatingMessageLabel.getStyleClass().add("section-description");
        gatingMessageLabel.setWrapText(true);

        Label valueFieldLabel = new Label();
        valueFieldLabel.getStyleClass().add("field-label");
        TextField valueField = new TextField();
        valueField.getStyleClass().add("finsight-text-field");
        valueField.setPrefColumnCount(12);
        VBox valueFieldBox = new VBox(4, valueFieldLabel, valueField);

        Label dateFieldLabel = new Label("Effective date");
        dateFieldLabel.getStyleClass().add("field-label");
        TextField dateField = new TextField(LocalDate.now().toString());
        dateField.getStyleClass().add("finsight-text-field");
        dateField.setPrefColumnCount(10);
        VBox dateFieldBox = new VBox(4, dateFieldLabel, dateField);

        Button addEventButton = new Button("Add Event");
        addEventButton.getStyleClass().add("primary-button");

        HBox addEventFieldsRow = new HBox(16, investmentFieldBox, valueFieldBox, dateFieldBox, addEventButton);
        addEventFieldsRow.setAlignment(Pos.BOTTOM_LEFT);

        Label addEventStatusLabel = new Label(
                "Choose an event type, fill in its fields, and click \"Add Event\" to add it to the scenario below.");
        addEventStatusLabel.getStyleClass().add("section-description");
        addEventStatusLabel.setWrapText(true);

        VBox addEventBox = new VBox(12, addEventHeading, eventTypeFieldBox, gatingMessageLabel,
                addEventFieldsRow, addEventStatusLabel);

        // Re-evaluated whenever the selected event type changes or investmentsList changes (an
        // investment can only ever be added, never removed — see this class's own documentation —
        // so this state can only ever go from "gated" to "not gated," never back).
        Runnable updateFormForSelectedType = () -> {
            String selectedType = eventTypeComboBox.getValue();
            valueFieldLabel.setText(valueFieldLabelFor(selectedType));
            boolean investmentTargeting = isInvestmentTargeting(selectedType);
            boolean gated = investmentTargeting && investmentsList.isEmpty();

            setVisibleAndManaged(investmentFieldBox, investmentTargeting && !gated);
            setVisibleAndManaged(gatingMessageLabel, gated);
            setVisibleAndManaged(valueFieldBox, !gated);
            setVisibleAndManaged(dateFieldBox, !gated);
            addEventButton.setDisable(gated);
        };
        eventTypeComboBox.valueProperty().addListener((obs, oldValue, newValue) -> updateFormForSelectedType.run());
        investmentsList.addListener(
                (javafx.collections.ListChangeListener<Investment>) change -> updateFormForSelectedType.run());
        updateFormForSelectedType.run();

        addEventButton.setOnAction(event -> {
            String selectedType = eventTypeComboBox.getValue();
            if (selectedType == null) {
                showStatus(addEventStatusLabel, "Please select an event type.", false);
                return;
            }
            boolean investmentTargeting = isInvestmentTargeting(selectedType);
            Investment selectedInvestment = investmentComboBox.getValue();
            if (investmentTargeting && selectedInvestment == null) {
                showStatus(addEventStatusLabel, "Please select an investment.", false);
                return;
            }
            try {
                BigDecimal value = new BigDecimal(valueField.getText().trim());
                if (value.signum() < 0) {
                    throw new IllegalArgumentException("The value must not be negative.");
                }
                LocalDate effectiveDate = LocalDate.parse(dateField.getText().trim());

                // The investment id is read off the selected Investment object itself — never
                // typed or displayed as raw text (same rule the section this replaces already
                // followed). Domain-constructor validation (blank/negative/etc.) is not duplicated
                // here beyond the sign check above; each ScenarioEvent subtype validates itself.
                ScenarioEvent newEvent = switch (selectedType) {
                    case INCOME_CHANGE -> new IncomeChangeEvent(effectiveDate, value);
                    case EXPENSE_CHANGE -> new ExpenseChangeEvent(effectiveDate, value);
                    case ONE_TIME_EXPENSE -> new OneTimeExpenseEvent(effectiveDate, value);
                    case INVESTMENT_CONTRIBUTION_CHANGE ->
                            new InvestmentContributionChangeEvent(effectiveDate, selectedInvestment.getId(), value);
                    case INVESTMENT_RETURN_CHANGE ->
                            new InvestmentReturnChangeEvent(effectiveDate, selectedInvestment.getId(), value);
                    default -> throw new IllegalStateException("Unknown event type: " + selectedType);
                };

                pendingEvents.add(newEvent);
                valueField.clear();
                dateField.setText(LocalDate.now().toString());
                showStatus(addEventStatusLabel, "Event added below.", true);
            } catch (RuntimeException e) {
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                showStatus(addEventStatusLabel, "Could not add the event: " + reason, false);
            }
        });

        // ---------- Pending Events ----------

        Label pendingEventsHeading = new Label("Pending Events");
        pendingEventsHeading.getStyleClass().add("field-label");

        Label emptyStateLabel = new Label("No events yet. Add one above to start building your scenario.");
        emptyStateLabel.getStyleClass().add("section-description");
        emptyStateLabel.setWrapText(true);

        VBox pendingEventsContainer = new VBox(8);

        Button clearAllButton = new Button("Clear All");
        clearAllButton.getStyleClass().add("primary-button");

        Button runScenarioButton = new Button("Run Scenario");
        runScenarioButton.getStyleClass().add("primary-button");

        HBox pendingEventsActionsRow = new HBox(16, clearAllButton, runScenarioButton);

        Label runScenarioStatusLabel = new Label(
                "Add at least one event above, then click \"Run Scenario\" to compare it against the baseline.");
        runScenarioStatusLabel.getStyleClass().add("section-description");
        runScenarioStatusLabel.setWrapText(true);

        // ---------- Comparison (Milestone 6A's full five-metric presentation) ----------

        Label baselineNetWorthValue = new Label();
        Label scenarioNetWorthValue = new Label();
        Label netWorthImpactValue = new Label();
        Label baselineInvestmentValueValue = new Label();
        Label scenarioInvestmentValueValue = new Label();
        Label investmentValueImpactValue = new Label();
        Label baselineLoanBalanceValue = new Label();
        Label scenarioLoanBalanceValue = new Label();
        Label loanBalanceImpactValue = new Label();
        Label baselineCashBalanceValue = new Label();
        Label scenarioCashBalanceValue = new Label();
        Label cashBalanceImpactValue = new Label();
        Label baselineInvestmentContributionValue = new Label();
        Label scenarioInvestmentContributionValue = new Label();
        Label investmentContributionImpactValue = new Label();

        FlowPane comparisonRow = new FlowPane(16, 16,
                metricCard("Baseline — net worth after month " + PROJECTION_MONTHS, baselineNetWorthValue),
                metricCard("Scenario — net worth after month " + PROJECTION_MONTHS, scenarioNetWorthValue),
                metricCard("Net worth impact", netWorthImpactValue),
                metricCard("Baseline — investment value after month " + PROJECTION_MONTHS, baselineInvestmentValueValue),
                metricCard("Scenario — investment value after month " + PROJECTION_MONTHS, scenarioInvestmentValueValue),
                metricCard("Investment value impact", investmentValueImpactValue),
                metricCard("Baseline — loan balance after month " + PROJECTION_MONTHS, baselineLoanBalanceValue),
                metricCard("Scenario — loan balance after month " + PROJECTION_MONTHS, scenarioLoanBalanceValue),
                metricCard("Loan balance impact", loanBalanceImpactValue),
                metricCard("Baseline — cash balance after month " + PROJECTION_MONTHS, baselineCashBalanceValue),
                metricCard("Scenario — cash balance after month " + PROJECTION_MONTHS, scenarioCashBalanceValue),
                metricCard("Cash balance impact", cashBalanceImpactValue),
                metricCard("Baseline — total investment contributions", baselineInvestmentContributionValue),
                metricCard("Scenario — total investment contributions", scenarioInvestmentContributionValue),
                metricCard("Investment contribution impact", investmentContributionImpactValue));
        comparisonRow.setVisible(false);
        comparisonRow.setManaged(false);

        // Re-renders pendingEventsContainer from scratch whenever pendingEvents changes — the same
        // "clear and repopulate" approach ProjectionChartView uses for its own series, rather than
        // hand-patching individual rows in and out.
        Runnable refreshPendingEventsDisplay = () -> {
            pendingEventsContainer.getChildren().clear();
            boolean hasEvents = !pendingEvents.isEmpty();
            setVisibleAndManaged(emptyStateLabel, !hasEvents);
            setVisibleAndManaged(pendingEventsContainer, hasEvents);

            Map<Long, String> investmentNamesById = new HashMap<>();
            for (Investment investment : investmentsList) {
                investmentNamesById.put(investment.getId(), investment.getName());
            }
            for (ScenarioEvent pendingEvent : pendingEvents) {
                Label eventLabel = new Label(ScenarioEventFormatter.formatOneLine(pendingEvent, investmentNamesById));
                eventLabel.getStyleClass().add("field-label");
                eventLabel.setWrapText(true);
                HBox.setHgrow(eventLabel, Priority.ALWAYS);

                Button removeButton = new Button("Remove");
                removeButton.getStyleClass().add("primary-button");
                removeButton.setOnAction(e -> pendingEvents.remove(pendingEvent));

                HBox row = new HBox(12, eventLabel, removeButton);
                row.setAlignment(Pos.CENTER_LEFT);
                pendingEventsContainer.getChildren().add(row);
            }
        };
        pendingEvents.addListener((javafx.collections.ListChangeListener<ScenarioEvent>) change -> refreshPendingEventsDisplay.run());
        refreshPendingEventsDisplay.run();

        clearAllButton.setOnAction(event -> {
            pendingEvents.clear();
            showStatus(runScenarioStatusLabel,
                    "All pending events cleared. Add at least one event above, then click \"Run Scenario\".", true);
        });

        runScenarioButton.setOnAction(event -> {
            if (pendingEvents.isEmpty()) {
                showStatus(runScenarioStatusLabel, "Add at least one event above before running the scenario.", false);
                return;
            }
            try {
                LocalDate asOfDate = LocalDate.now();
                SimulationParameters parameters =
                        new SimulationParameters(PROJECTION_MONTHS, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE);
                Scenario scenario = new Scenario(
                        0L, "Decision Lab Scenario", null, asOfDate, List.copyOf(pendingEvents));

                SimulationResult result = simulationApplicationService.runScenario(asOfDate, parameters, scenario);
                ScenarioComparisonView view = SimulationResultFormatter.formatComparison(result);

                baselineNetWorthValue.setText(view.getBaselineNetWorthAfterFinalMonth());
                scenarioNetWorthValue.setText(view.getScenarioNetWorthAfterFinalMonth());
                netWorthImpactValue.setText(impactWithPercentage(view.getNetWorthImpact(), view.getNetWorthPercentageChange()));
                applySemanticValueStyle(netWorthImpactValue, view.getNetWorthImpact());

                baselineInvestmentValueValue.setText(view.getBaselineInvestmentValueAfterFinalMonth());
                scenarioInvestmentValueValue.setText(view.getScenarioInvestmentValueAfterFinalMonth());
                investmentValueImpactValue.setText(
                        impactWithPercentage(view.getInvestmentValueImpact(), view.getInvestmentValuePercentageChange()));
                applySemanticValueStyle(investmentValueImpactValue, view.getInvestmentValueImpact());

                baselineLoanBalanceValue.setText(view.getBaselineLoanBalanceAfterFinalMonth());
                scenarioLoanBalanceValue.setText(view.getScenarioLoanBalanceAfterFinalMonth());
                loanBalanceImpactValue.setText(
                        impactWithPercentage(view.getLoanBalanceImpact(), view.getLoanBalancePercentageChange()));
                applySemanticValueStyle(loanBalanceImpactValue, view.getLoanBalanceImpact());

                baselineCashBalanceValue.setText(view.getBaselineCashBalanceAfterFinalMonth());
                scenarioCashBalanceValue.setText(view.getScenarioCashBalanceAfterFinalMonth());
                cashBalanceImpactValue.setText(
                        impactWithPercentage(view.getCashBalanceImpact(), view.getCashBalancePercentageChange()));
                applySemanticValueStyle(cashBalanceImpactValue, view.getCashBalanceImpact());

                baselineInvestmentContributionValue.setText(view.getBaselineTotalInvestmentContributions());
                scenarioInvestmentContributionValue.setText(view.getScenarioTotalInvestmentContributions());
                investmentContributionImpactValue.setText(impactWithPercentage(
                        view.getInvestmentContributionImpact(), view.getInvestmentContributionPercentageChange()));
                applySemanticValueStyle(investmentContributionImpactValue, view.getInvestmentContributionImpact());

                int count = pendingEvents.size();
                showStatus(runScenarioStatusLabel, "Comparing the baseline against this Decision Lab scenario ("
                        + count + (count == 1 ? " event)." : " events)."), true);
                setVisibleAndManaged(comparisonRow, true);

                // pendingEvents is deliberately left untouched — rerunning after tweaking the list
                // replaces this same displayed comparison rather than clearing anything (see this
                // class's own documentation).
                onScenarioRun.accept(result);
            } catch (RuntimeException e) {
                // Deliberately does not touch comparisonRow's visibility here: whatever comparison
                // was already displayed from an earlier successful run remains exactly as it was,
                // matching the same "don't clear on failure" behavior the two sections this
                // replaces already had.
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                showStatus(runScenarioStatusLabel, "Could not run the scenario: " + reason, false);
            }
        });

        VBox pendingEventsBox = new VBox(12, pendingEventsHeading, emptyStateLabel, pendingEventsContainer,
                pendingEventsActionsRow, runScenarioStatusLabel, comparisonRow);

        getChildren().addAll(sectionLabel, description, addEventBox, pendingEventsBox);
    }

    private static boolean isInvestmentTargeting(String eventType) {
        return INVESTMENT_CONTRIBUTION_CHANGE.equals(eventType) || INVESTMENT_RETURN_CHANGE.equals(eventType);
    }

    private static String valueFieldLabelFor(String eventType) {
        if (eventType == null) {
            return "New value";
        }
        return switch (eventType) {
            case INCOME_CHANGE -> "New monthly income (₹)";
            case EXPENSE_CHANGE -> "New monthly expenses (₹)";
            case ONE_TIME_EXPENSE -> "Amount (₹)";
            case INVESTMENT_CONTRIBUTION_CHANGE -> "New monthly contribution";
            case INVESTMENT_RETURN_CHANGE -> "New expected annual return (decimal, e.g. 0.09 for 9%)";
            default -> "New value";
        };
    }

    /**
     * Combines an already-formatted signed-money impact figure with its already-formatted
     * percentage-change text into one display string — the same helper
     * {@code FinSightApplication} introduced in Milestone 6A, duplicated here (a two-line, purely
     * presentational method) rather than shared through a new cross-class utility type, matching
     * this milestone's own "avoid speculative architecture" instruction for something this small.
     */
    private static String impactWithPercentage(String impact, String percentageChange) {
        return impact + " (" + percentageChange + ")";
    }

    /** Mirrors {@code FinSightApplication.metricCard} exactly — see that method's own documentation. */
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

    /** Mirrors {@code FinSightApplication.showStatus} exactly — see that method's own documentation. */
    private static void showStatus(Label label, String message, boolean success) {
        label.setText(message);
        label.getStyleClass().removeAll("status-success", "status-error");
        label.getStyleClass().add(success ? "status-success" : "status-error");
    }

    /** Mirrors {@code FinSightApplication.applySemanticValueStyle} exactly — see its own documentation. */
    private static void applySemanticValueStyle(Label valueLabel, String formattedValue) {
        valueLabel.getStyleClass().removeAll("metric-value-positive", "metric-value-negative");
        if (formattedValue.startsWith("-")) {
            valueLabel.getStyleClass().add("metric-value-negative");
        } else if (formattedValue.startsWith("+")) {
            valueLabel.getStyleClass().add("metric-value-positive");
        }
    }

    /** Mirrors {@code FinSightApplication.setVisibleAndManaged} exactly — see its own documentation. */
    private static void setVisibleAndManaged(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
