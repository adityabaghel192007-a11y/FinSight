package com.finsight.service;

import com.finsight.domain.account.Account;
import com.finsight.domain.account.AccountType;
import com.finsight.domain.account.Currency;
import com.finsight.domain.investment.AssetType;
import com.finsight.domain.investment.Investment;
import com.finsight.repository.AccountRepository;
import com.finsight.repository.InvestmentRepository;
import com.finsight.repository.RepositoryException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link InvestmentApplicationService}, using small in-memory fakes of
 * {@link AccountRepository} and {@link InvestmentRepository} rather than a real database or a
 * mocking framework — exactly the same style {@code TransactionApplicationServiceTest} already
 * established for this project. Real-database behavior (foreign-key enforcement, round-trip
 * fidelity) is separately and more authoritatively proven in
 * {@link InvestmentApplicationServiceIntegrationTest}.
 */
class InvestmentApplicationServiceTest {

    // ===================== in-memory fakes =====================

    private static final class InMemoryAccountRepository implements AccountRepository {
        private final Map<Long, Account> accounts = new HashMap<>();
        private long nextId = 1;

        @Override
        public Account save(Account account) {
            long id = nextId++;
            Account saved = account.withId(id);
            accounts.put(id, saved);
            return saved;
        }

        @Override
        public Optional<Account> findById(long id) {
            return Optional.ofNullable(accounts.get(id));
        }

        @Override
        public List<Account> findAll() {
            return List.copyOf(accounts.values());
        }

        @Override
        public void delete(long id) {
            accounts.remove(id);
        }
    }

    private static final class InMemoryInvestmentRepository implements InvestmentRepository {
        private final Map<Long, Investment> investments = new HashMap<>();
        private long nextId = 1;

        @Override
        public Investment save(Investment investment) {
            long id = nextId++;
            Investment saved = investment.withId(id);
            investments.put(id, saved);
            return saved;
        }

        @Override
        public Optional<Investment> findById(long id) {
            return Optional.ofNullable(investments.get(id));
        }

        @Override
        public List<Investment> findAll() {
            return List.copyOf(investments.values());
        }

        @Override
        public void delete(long id) {
            investments.remove(id);
        }
    }

    /** A fake whose {@link #save} always fails, for proving a persistence failure propagates. */
    private static final class FailingInvestmentRepository implements InvestmentRepository {
        @Override
        public Investment save(Investment investment) {
            throw new RepositoryException("simulated persistence failure");
        }

        @Override
        public Optional<Investment> findById(long id) {
            return Optional.empty();
        }

        @Override
        public List<Investment> findAll() {
            return List.of();
        }

        @Override
        public void delete(long id) {
            // no-op
        }
    }

    // ===================== fixture =====================

    private static final class Fixture {
        private final InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
        private final InMemoryInvestmentRepository investmentRepository = new InMemoryInvestmentRepository();
        private final InvestmentApplicationService service =
                new InvestmentApplicationService(investmentRepository, accountRepository);

        private long newAccountId() {
            return accountRepository.save(
                    new Account(0L, "Brokerage", AccountType.INVESTMENT, Currency.INR, new BigDecimal("0.00"))).getId();
        }
    }

    private static Investment sampleAdd(InvestmentApplicationService service, long accountId) {
        return service.addInvestment(
                accountId, "Nifty Index Fund", "NIFTYBEES", AssetType.ETF, Currency.INR,
                new BigDecimal("100"), new BigDecimal("200.00"), new BigDecimal("250.00"),
                new BigDecimal("0.10"), new BigDecimal("5000.00"));
    }

    // ===================== A: successful add =====================

    @Test
    void addInvestmentPersistsAndReturnsItWithAGeneratedId() {
        Fixture fx = new Fixture();
        long accountId = fx.newAccountId();

        Investment result = sampleAdd(fx.service, accountId);

        assertTrue(result.getId() > 0, "the returned investment should carry a generated id");
        assertEquals(accountId, result.getAccountId());
        assertEquals("Nifty Index Fund", result.getName());
        assertEquals(Optional.of("NIFTYBEES"), result.getTicker());
        assertEquals(AssetType.ETF, result.getAssetType());
        assertEquals(Currency.INR, result.getCurrency());
        assertEquals(0, new BigDecimal("100").compareTo(result.getQuantity()));
        assertEquals(0, new BigDecimal("200.00").compareTo(result.getAveragePurchasePrice()));
        assertEquals(0, new BigDecimal("250.00").compareTo(result.getCurrentPrice()));
        assertEquals(0, new BigDecimal("0.10").compareTo(result.getExpectedAnnualReturn()));
        assertEquals(0, new BigDecimal("5000.00").compareTo(result.getMonthlyContribution()));
    }

    @Test
    void addInvestmentAcceptsAnAbsentTicker() {
        Fixture fx = new Fixture();
        long accountId = fx.newAccountId();

        Investment result = fx.service.addInvestment(
                accountId, "Physical Gold", null, AssetType.GOLD, Currency.INR,
                new BigDecimal("10"), new BigDecimal("5000.00"), new BigDecimal("6200.00"),
                BigDecimal.ZERO, BigDecimal.ZERO);

        assertTrue(result.getTicker().isEmpty());
    }

    // ===================== B: successful list =====================

    @Test
    void getInvestmentsReturnsEveryPersistedInvestment() {
        Fixture fx = new Fixture();
        long accountId = fx.newAccountId();
        Investment first = sampleAdd(fx.service, accountId);
        Investment second = fx.service.addInvestment(
                accountId, "US Total Market ETF", "VTI", AssetType.US_STOCK, Currency.USD,
                new BigDecimal("5"), new BigDecimal("200.00"), new BigDecimal("230.00"),
                new BigDecimal("0.08"), new BigDecimal("100.00"));

        List<Investment> all = fx.service.getInvestments();

        assertEquals(2, all.size());
        assertTrue(all.contains(first));
        assertTrue(all.contains(second));
    }

    @Test
    void getInvestmentsReturnsEmptyListWhenNoneArePersisted() {
        Fixture fx = new Fixture();

        assertTrue(fx.service.getInvestments().isEmpty());
    }

    // ===================== C: nonexistent account rejection =====================

    @Test
    void addInvestmentRequiresExistingAccount() {
        Fixture fx = new Fixture();

        assertThrows(ApplicationValidationException.class,
                () -> sampleAdd(fx.service, 999_999L));
    }

    // ===================== D: no persistence after nonexistent-account rejection =====================

    @Test
    void nothingIsPersistedWhenTheAccountDoesNotExist() {
        Fixture fx = new Fixture();

        assertThrows(ApplicationValidationException.class,
                () -> sampleAdd(fx.service, 999_999L));

        assertTrue(fx.investmentRepository.findAll().isEmpty(),
                "nothing should be persisted when the account does not exist");
    }

    // ===================== E: domain validation exceptions propagate =====================

    @Test
    void blankNameIsRejectedByTheDomainConstructorNotDuplicatedHere() {
        Fixture fx = new Fixture();
        long accountId = fx.newAccountId();

        assertThrows(IllegalArgumentException.class,
                () -> fx.service.addInvestment(
                        accountId, "   ", null, AssetType.ETF, Currency.INR,
                        BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        assertTrue(fx.investmentRepository.findAll().isEmpty());
    }

    @Test
    void nonPositiveQuantityIsRejectedByTheDomainConstructor() {
        Fixture fx = new Fixture();
        long accountId = fx.newAccountId();

        assertThrows(IllegalArgumentException.class,
                () -> fx.service.addInvestment(
                        accountId, "Zero Quantity", null, AssetType.ETF, Currency.INR,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        assertTrue(fx.investmentRepository.findAll().isEmpty());
    }

    @Test
    void nullNameIsRejectedByTheDomainConstructor() {
        Fixture fx = new Fixture();
        long accountId = fx.newAccountId();

        assertThrows(NullPointerException.class,
                () -> fx.service.addInvestment(
                        accountId, null, null, AssetType.ETF, Currency.INR,
                        BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    // ===================== F: account remains unmodified =====================

    @Test
    void theAccountLookedUpIsNotMutatedByAddingAnInvestment() {
        Fixture fx = new Fixture();
        Account original = new Account(0L, "Brokerage", AccountType.INVESTMENT, Currency.INR, new BigDecimal("0.00"));
        Account saved = fx.accountRepository.save(original);

        sampleAdd(fx.service, saved.getId());

        // The caller's original, pre-save instance is exactly as it was.
        assertEquals(0L, original.getId());
        assertEquals("Brokerage", original.getName());

        // The persisted instance the repository holds is also unchanged by the service call.
        Account reloaded = fx.accountRepository.findById(saved.getId()).orElseThrow();
        assertEquals("Brokerage", reloaded.getName());
        assertEquals(AccountType.INVESTMENT, reloaded.getType());
        assertEquals(0, new BigDecimal("0.00").compareTo(reloaded.getOpeningBalance()));
    }

    // ===================== G: repository failure propagation =====================

    @Test
    void persistenceFailureIsPropagatedRatherThanSwallowed() {
        InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
        long accountId = accountRepository.save(
                new Account(0L, "Brokerage", AccountType.INVESTMENT, Currency.INR, BigDecimal.ZERO)).getId();
        InvestmentApplicationService service =
                new InvestmentApplicationService(new FailingInvestmentRepository(), accountRepository);

        assertThrows(RepositoryException.class, () -> sampleAdd(service, accountId));
    }
}
