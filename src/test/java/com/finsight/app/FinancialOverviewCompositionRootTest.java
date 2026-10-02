package com.finsight.app;

import com.finsight.service.FinancialOverviewApplicationService;
import com.finsight.service.FinancialOverviewData;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Milestone 7A's required {@link CompositionRoot}-backed integration test: proves
 * {@link FinancialOverviewApplicationService} is genuinely constructed from
 * {@link CompositionRoot}'s own already-wired repositories and is reachable through its exposed
 * {@link CompositionRoot#getFinancialOverviewApplicationService()} accessor — the same
 * "constructed and wired, proven through a real successful call rather than only a null check"
 * pattern already established by {@code CompositionRootTest}'s own
 * {@code servicesAreGenuinelyWiredThroughToARealSuccessfulScenarioRun} test, kept in its own file
 * rather than added to {@code CompositionRootTest.java} so no existing test is touched.
 *
 * <p>A freshly constructed {@link CompositionRoot} against an empty database is not actually
 * empty by the time this test can observe it: {@code DemoDataSeeder} (see its own documentation)
 * seeds exactly one {@link com.finsight.domain.financialprofile.FinancialProfile} (monthly income
 * {@code 60,000.00}, monthly expenses {@code 40,000.00}) and one {@code Account} ("Demo Checking
 * Account", INR, opening balance {@code 150,000.00}, no transactions) into any database that has
 * never had a profile recorded. That seeded, deterministic data is exactly what this test relies
 * on — no separate seeding step of its own is needed — and it also happens to be enough to
 * exercise every consolidation path {@link FinancialOverviewApplicationService#getOverview}
 * performs except investments/loans (for which the demo data seeds none, so both are correctly
 * zero): the FinancialProfile read, the single-account cash consolidation (no currency conversion
 * needed here since the demo account is INR), and the {@code NetWorthCalculator} call.
 */
class FinancialOverviewCompositionRootTest {

    @Test
    void financialOverviewApplicationServiceIsWiredThroughToARealSuccessfulOverviewCall() {
        try (CompositionRoot context = new CompositionRoot("jdbc:sqlite::memory:")) {
            FinancialOverviewApplicationService financialOverviewApplicationService =
                    context.getFinancialOverviewApplicationService();
            assertNotNull(financialOverviewApplicationService);

            FinancialOverviewData overview = financialOverviewApplicationService.getOverview(BigDecimal.ONE);

            assertEquals(0, new BigDecimal("60000.00").compareTo(overview.getMonthlyIncome()));
            assertEquals(0, new BigDecimal("40000.00").compareTo(overview.getMonthlyExpenses()));
            assertEquals(0, new BigDecimal("20000.00").compareTo(overview.getMonthlySavings()));
            assertEquals(0, new BigDecimal("150000.00").compareTo(overview.getCash()),
                    "the demo account's opening balance, with no transactions, must pass straight "
                            + "through AccountBalanceCalculator into the consolidated cash figure");
            assertEquals(0, BigDecimal.ZERO.compareTo(overview.getInvestments()),
                    "the demo data seeds no investments");
            assertEquals(0, BigDecimal.ZERO.compareTo(overview.getLoans()), "the demo data seeds no loans");
            assertEquals(0, new BigDecimal("150000.00").compareTo(overview.getNetWorth()),
                    "with zero investments and zero loans, net worth must equal cash exactly");
        }
    }
}
