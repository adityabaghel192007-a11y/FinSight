package com.finsight.ui;

import com.finsight.service.FinancialOverviewApplicationService;
import com.finsight.service.FinancialOverviewData;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The Financial Overview (Milestone 7A): a compact, read-only summary of the user's current
 * financial position — monthly income, expenses, and savings, plus cash, investments, loans, and
 * net worth — placed at the very top of the application so a user immediately understands where
 * they stand as soon as FinSight opens.
 *
 * <p>A plain, self-contained JavaFX component, exactly the same shape as
 * {@link ProjectionChartView} and {@link DecisionLabView}: a {@code Node} subclass built once in
 * its constructor, not a controller, not part of an MVC framework, and not a base for future
 * subclassing. Unlike those two, this view has no interactive element at all — no button, no
 * editable field, no scenario or gating logic — matching this milestone's explicit scope: a
 * read-only snapshot, nothing more.
 *
 * <p><b>No formatter class.</b> Per this milestone's architecture decision, a separate
 * {@code FinancialOverviewFormatter} was deliberately not introduced: this view reads the seven
 * already-computed {@link FinancialOverviewData} figures and formats each with one small private
 * {@link #formatMoney(BigDecimal)} helper — the same tiny, class-local formatting helper every
 * other UI class in this package already duplicates for itself ({@code InvestmentFormatter},
 * {@code FinancialDataFormatter}, {@code SimulationResultFormatter}) rather than sharing through a
 * new utility type. Every figure here is already consolidated into INR by
 * {@link FinancialOverviewApplicationService}, so — matching that service's own formatting
 * precedent in {@code SimulationResultFormatter} for absolute (not delta) money figures — this
 * view performs no calculation, only string formatting.
 *
 * <p><b>Computed once, at construction.</b> This view fetches
 * {@link FinancialOverviewApplicationService#getOverview(BigDecimal)} exactly once, when built,
 * and does not expose a public refresh method: the milestone's own scope excludes buttons, editing
 * controls, and scenario functionality, and no other section of this application currently pushes
 * a live-refresh signal into an unrelated section either (adding an investment does not, today,
 * automatically re-run the Financial Projection). A snapshot that is accurate as of application
 * launch — matching the product goal of "when a user opens FinSight, they immediately understand
 * their current financial position" — was judged the right, minimal scope for this milestone;
 * wiring a live refresh into the Profile/Financial Data/Investments sections' own success handlers
 * would be a natural, small follow-up if live reactivity is wanted later.
 *
 * <p><b>Failure handling.</b> If {@link FinancialOverviewApplicationService#getOverview} throws
 * (no {@link com.finsight.domain.financialprofile.FinancialProfile} recorded yet, an invalid FX
 * rate, or an unresolved {@code TRANSFER} balance — see that class's own documentation), this view
 * does not propagate the exception and abort the whole application's launch: it shows a single
 * status message instead, matching the "catch, report, keep going" pattern every other section of
 * {@code FinSightApplication} already uses for its own button handlers.
 *
 * <p><b>Primary-section styling (Milestone 7B).</b> This is the first thing a user sees on launch,
 * so it carries the {@code section-card-primary}/{@code section-heading-primary} CSS modifiers in
 * addition to (not instead of) the ordinary {@code section-card}/{@code section-heading} every
 * other section already uses — a slightly larger heading and an accent top border in the same
 * blue already used for {@code .primary-button}, nothing else. No new color, radius, padding, or
 * shadow is introduced; see {@code finsight.css}'s own comment on these two modifier classes. The
 * one-line description beneath the heading mirrors the heading+description pattern
 * {@code DecisionLabView} already established, rather than inventing a new convention.
 */
public final class FinancialOverviewView extends VBox {

    public FinancialOverviewView(
            FinancialOverviewApplicationService financialOverviewApplicationService, BigDecimal usdToInrRate) {
        super(16);
        Objects.requireNonNull(financialOverviewApplicationService, "financialOverviewApplicationService must not be null");
        Objects.requireNonNull(usdToInrRate, "usdToInrRate must not be null");

        getStyleClass().addAll("section-card", "section-card-primary");
        buildUi(financialOverviewApplicationService, usdToInrRate);
    }

    private void buildUi(FinancialOverviewApplicationService financialOverviewApplicationService, BigDecimal usdToInrRate) {
        Label heading = new Label("Financial Overview");
        heading.getStyleClass().addAll("section-heading", "section-heading-primary");

        Label description = new Label("Your current financial position, at a glance.");
        description.getStyleClass().add("section-description");

        getChildren().addAll(heading, description);

        try {
            FinancialOverviewData overview = financialOverviewApplicationService.getOverview(usdToInrRate);

            HBox monthlyRow = new HBox(16,
                    metricCard("Monthly Income", formatMoney(overview.getMonthlyIncome())),
                    metricCard("Monthly Expenses", formatMoney(overview.getMonthlyExpenses())),
                    metricCard("Monthly Savings", formatMoney(overview.getMonthlySavings())));

            HBox positionRow = new HBox(16,
                    metricCard("Cash", formatMoney(overview.getCash())),
                    metricCard("Investments", formatMoney(overview.getInvestments())),
                    metricCard("Loans", formatMoney(overview.getLoans())));

            // Net worth is the headline current-position metric, so it gets its own row and a
            // larger value font — the same metric-card/metric-value styling as every other card
            // here, just emphasized by size, not by a new CSS class or color.
            VBox netWorthCard = metricCard("Net Worth", formatMoney(overview.getNetWorth()));
            netWorthCard.setStyle("-fx-font-size: 22px;");
            HBox netWorthRow = new HBox(netWorthCard);

            getChildren().addAll(monthlyRow, positionRow, netWorthRow);
        } catch (RuntimeException e) {
            // Matches the "catch RuntimeException, show a status label, keep the rest of the
            // application usable" pattern every other section's button handler already follows
            // (see e.g. FinSightApplication.saveProfileButton's own handler) — the two failures
            // getOverview can actually throw (ApplicationValidationException, IllegalArgumentException
            // — see its own documentation) are both unchecked, so this is caught the same way.
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            Label statusLabel = new Label("Financial overview unavailable: " + reason);
            statusLabel.getStyleClass().addAll("status-message", "status-error");
            statusLabel.setWrapText(true);
            getChildren().add(statusLabel);
        }
    }

    /**
     * The same "muted label above a prominent value, both in a bordered card" layout as
     * {@code FinSightApplication.metricCard}/{@code DecisionLabView.metricCard} — this overload
     * takes the value as a plain, already-formatted {@code String} rather than a reusable
     * {@link Label} reference, since this view never updates a card's value after construction
     * (see this class's own "Computed once, at construction" documentation).
     */
    private static VBox metricCard(String label, String value) {
        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("metric-label");
        labelNode.setWrapText(true);

        Label valueNode = new Label(value);
        valueNode.getStyleClass().add("metric-value");

        VBox card = new VBox(6, labelNode, valueNode);
        card.getStyleClass().add("metric-card");
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setAlignment(Pos.CENTER_LEFT);
        return card;
    }

    /**
     * Every figure {@link FinancialOverviewData} carries is already consolidated into INR, so this
     * always shows a {@code ₹} prefix — never {@code $} — with the sign placed before the
     * currency symbol for a negative figure (e.g. {@code "-₹5,000.00"}), matching
     * {@code InvestmentFormatter.formatSignedMoney}'s identical sign-before-prefix convention. A
     * non-negative figure gets no leading sign, matching {@code SimulationResultFormatter.formatMoney}'s
     * plain (non-delta) convention for an absolute value.
     */
    private static String formatMoney(BigDecimal amount) {
        if (amount.signum() < 0) {
            return "-₹" + String.format("%,.2f", amount.abs());
        }
        return "₹" + String.format("%,.2f", amount);
    }
}
