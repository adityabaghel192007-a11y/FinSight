package com.finsight.service;

import com.finsight.domain.account.Account;
import com.finsight.domain.account.AccountType;
import com.finsight.domain.account.Currency;
import com.finsight.domain.category.Category;
import com.finsight.domain.category.CategoryType;
import com.finsight.domain.transaction.Transaction;
import com.finsight.domain.transaction.TransactionType;
import com.finsight.repository.AccountRepository;
import com.finsight.repository.CategoryRepository;
import com.finsight.repository.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link FinancialDataApplicationService} (Milestone 5J), using the same small
 * in-memory fakes of {@link AccountRepository}, {@link CategoryRepository}, and
 * {@link TransactionRepository} that {@code TransactionApplicationServiceTest} already
 * establishes for this codebase — this class is deliberately a thin, direct pass-through to those
 * three repositories' own {@code findAll()}, so these tests only need to prove exactly that: an
 * empty repository yields an empty list, and a populated one yields exactly what was saved.
 *
 * <p><b>Milestone 5K</b> added {@link FinancialDataApplicationService#getAccountBalances()}, which
 * delegates to {@code AccountBalanceCalculator} (already exhaustively unit- and integration-tested
 * in its own right) rather than performing any arithmetic itself. The tests added below for it
 * therefore focus on proving the delegation and per-account wiring are correct — that the right
 * account's own transactions are fed to the calculator and the result comes back attached to the
 * right {@link Account} — not on re-proving the calculator's own arithmetic.
 */
class FinancialDataApplicationServiceTest {

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

    private static final class InMemoryCategoryRepository implements CategoryRepository {
        private final Map<Long, Category> categories = new HashMap<>();
        private long nextId = 1;

        @Override
        public Category save(Category category) {
            long id = nextId++;
            Category saved = category.withId(id);
            categories.put(id, saved);
            return saved;
        }

        @Override
        public Optional<Category> findById(long id) {
            return Optional.ofNullable(categories.get(id));
        }

        @Override
        public List<Category> findAll() {
            return List.copyOf(categories.values());
        }

        @Override
        public void delete(long id) {
            categories.remove(id);
        }
    }

    private static final class InMemoryTransactionRepository implements TransactionRepository {
        private final Map<Long, Transaction> transactions = new HashMap<>();
        private long nextId = 1;

        @Override
        public Transaction save(Transaction transaction) {
            long id = nextId++;
            Transaction saved = transaction.withId(id);
            transactions.put(id, saved);
            return saved;
        }

        @Override
        public Optional<Transaction> findById(long id) {
            return Optional.ofNullable(transactions.get(id));
        }

        @Override
        public List<Transaction> findAll() {
            return List.copyOf(transactions.values());
        }

        @Override
        public List<Transaction> findByAccountId(long accountId) {
            return transactions.values().stream().filter(t -> t.getAccountId() == accountId).toList();
        }

        @Override
        public void assignTransferGroupId(long transactionId, long transferGroupId) {
            throw new UnsupportedOperationException("not needed by these tests");
        }

        @Override
        public void delete(long id) {
            transactions.remove(id);
        }
    }

    @Test
    void everyGetterReturnsEmptyWhenNothingIsPersisted() {
        FinancialDataApplicationService service = new FinancialDataApplicationService(
                new InMemoryAccountRepository(), new InMemoryCategoryRepository(), new InMemoryTransactionRepository());

        assertTrue(service.getAccounts().isEmpty());
        assertTrue(service.getCategories().isEmpty());
        assertTrue(service.getTransactions().isEmpty());
    }

    @Test
    void getAccountsReturnsExactlyWhatWasSaved() {
        InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
        Account saved = accountRepository.save(
                new Account(0L, "Demo Checking Account", AccountType.BANK, Currency.INR, new BigDecimal("150000.00")));
        FinancialDataApplicationService service = new FinancialDataApplicationService(
                accountRepository, new InMemoryCategoryRepository(), new InMemoryTransactionRepository());

        List<Account> accounts = service.getAccounts();

        assertEquals(1, accounts.size());
        assertEquals(saved, accounts.get(0));
        assertEquals("Demo Checking Account", accounts.get(0).getName());
    }

    @Test
    void getCategoriesReturnsExactlyWhatWasSaved() {
        InMemoryCategoryRepository categoryRepository = new InMemoryCategoryRepository();
        Category income = categoryRepository.save(new Category(0L, "Salary", CategoryType.INCOME));
        Category expense = categoryRepository.save(new Category(0L, "Groceries", CategoryType.EXPENSE));
        FinancialDataApplicationService service = new FinancialDataApplicationService(
                new InMemoryAccountRepository(), categoryRepository, new InMemoryTransactionRepository());

        List<Category> categories = service.getCategories();

        assertEquals(2, categories.size());
        assertTrue(categories.contains(income));
        assertTrue(categories.contains(expense));
    }

    @Test
    void getTransactionsReturnsExactlyWhatWasSaved() {
        InMemoryTransactionRepository transactionRepository = new InMemoryTransactionRepository();
        Transaction saved = transactionRepository.save(new Transaction(
                0L, 1L, new BigDecimal("5000.00"), TransactionType.EXPENSE, 2L,
                LocalDate.of(2026, 4, 1), "Groceries run", null));
        FinancialDataApplicationService service = new FinancialDataApplicationService(
                new InMemoryAccountRepository(), new InMemoryCategoryRepository(), transactionRepository);

        List<Transaction> transactions = service.getTransactions();

        assertEquals(1, transactions.size());
        assertEquals(saved, transactions.get(0));
        assertEquals(0, new BigDecimal("5000.00").compareTo(transactions.get(0).getAmount()));
        assertEquals(TransactionType.EXPENSE, transactions.get(0).getType());
    }

    @Test
    void getAccountBalancesReturnsOpeningBalanceUnchangedWhenAccountHasNoTransactions() {
        InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
        Account account = accountRepository.save(
                new Account(0L, "Demo Checking Account", AccountType.BANK, Currency.INR, new BigDecimal("150000.00")));
        FinancialDataApplicationService service = new FinancialDataApplicationService(
                accountRepository, new InMemoryCategoryRepository(), new InMemoryTransactionRepository());

        List<AccountBalance> balances = service.getAccountBalances();

        assertEquals(1, balances.size());
        assertEquals(account, balances.get(0).getAccount());
        assertEquals(0, new BigDecimal("150000.00").compareTo(balances.get(0).getCurrentBalance().orElseThrow()));
    }

    @Test
    void getAccountBalancesAddsIncomeToTheOpeningBalance() {
        InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
        Account account = accountRepository.save(
                new Account(0L, "Demo Checking Account", AccountType.BANK, Currency.INR, new BigDecimal("150000.00")));
        InMemoryTransactionRepository transactionRepository = new InMemoryTransactionRepository();
        transactionRepository.save(new Transaction(
                0L, account.getId(), new BigDecimal("15000.00"), TransactionType.INCOME, 1L,
                LocalDate.of(2026, 4, 1), "Salary", null));
        FinancialDataApplicationService service = new FinancialDataApplicationService(
                accountRepository, new InMemoryCategoryRepository(), transactionRepository);

        BigDecimal balance = service.getAccountBalances().get(0).getCurrentBalance().orElseThrow();

        assertEquals(0, new BigDecimal("165000.00").compareTo(balance));
    }

    @Test
    void getAccountBalancesSubtractsExpenseFromTheOpeningBalance() {
        InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
        Account account = accountRepository.save(
                new Account(0L, "Demo Checking Account", AccountType.BANK, Currency.INR, new BigDecimal("150000.00")));
        InMemoryTransactionRepository transactionRepository = new InMemoryTransactionRepository();
        transactionRepository.save(new Transaction(
                0L, account.getId(), new BigDecimal("5000.00"), TransactionType.EXPENSE, 2L,
                LocalDate.of(2026, 4, 2), "Groceries", null));
        FinancialDataApplicationService service = new FinancialDataApplicationService(
                accountRepository, new InMemoryCategoryRepository(), transactionRepository);

        BigDecimal balance = service.getAccountBalances().get(0).getCurrentBalance().orElseThrow();

        assertEquals(0, new BigDecimal("145000.00").compareTo(balance));
    }

    @Test
    void getAccountBalancesCombinesMultipleIncomeAndExpenseTransactions() {
        InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
        Account account = accountRepository.save(
                new Account(0L, "Demo Checking Account", AccountType.BANK, Currency.INR, new BigDecimal("150000.00")));
        InMemoryTransactionRepository transactionRepository = new InMemoryTransactionRepository();
        transactionRepository.save(new Transaction(
                0L, account.getId(), new BigDecimal("15000.00"), TransactionType.INCOME, 1L,
                LocalDate.of(2026, 4, 1), null, null));
        transactionRepository.save(new Transaction(
                0L, account.getId(), new BigDecimal("5000.00"), TransactionType.EXPENSE, 2L,
                LocalDate.of(2026, 4, 2), null, null));
        transactionRepository.save(new Transaction(
                0L, account.getId(), new BigDecimal("2000.00"), TransactionType.EXPENSE, 2L,
                LocalDate.of(2026, 4, 3), null, null));
        FinancialDataApplicationService service = new FinancialDataApplicationService(
                accountRepository, new InMemoryCategoryRepository(), transactionRepository);

        BigDecimal balance = service.getAccountBalances().get(0).getCurrentBalance().orElseThrow();

        assertEquals(0, new BigDecimal("158000.00").compareTo(balance));
    }

    @Test
    void getAccountBalancesReturnsAbsentForATransferAffectedAccountWithoutBreakingOtherAccounts() {
        InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
        Account transferAccount = accountRepository.save(
                new Account(0L, "Savings Account", AccountType.BANK, Currency.INR, new BigDecimal("50000.00")));
        Account plainAccount = accountRepository.save(
                new Account(0L, "Demo Checking Account", AccountType.BANK, Currency.INR, new BigDecimal("150000.00")));
        InMemoryTransactionRepository transactionRepository = new InMemoryTransactionRepository();
        transactionRepository.save(new Transaction(
                0L, transferAccount.getId(), new BigDecimal("10000.00"), TransactionType.TRANSFER, null,
                LocalDate.of(2026, 4, 1), null, 1L));
        transactionRepository.save(new Transaction(
                0L, plainAccount.getId(), new BigDecimal("15000.00"), TransactionType.INCOME, 1L,
                LocalDate.of(2026, 4, 1), null, null));
        FinancialDataApplicationService service = new FinancialDataApplicationService(
                accountRepository, new InMemoryCategoryRepository(), transactionRepository);

        List<AccountBalance> balances = service.getAccountBalances();

        AccountBalance transferResult = balances.stream()
                .filter(b -> b.getAccount().getId() == transferAccount.getId())
                .findFirst().orElseThrow();
        AccountBalance plainResult = balances.stream()
                .filter(b -> b.getAccount().getId() == plainAccount.getId())
                .findFirst().orElseThrow();

        assertTrue(transferResult.getCurrentBalance().isEmpty(),
                "a transfer-affected account's balance must be reported absent, not guessed");
        assertEquals(0, new BigDecimal("165000.00").compareTo(plainResult.getCurrentBalance().orElseThrow()),
                "a transfer against one account must not prevent another account's balance from computing");
    }

    @Test
    void constructorRejectsNullRepositories() {
        AccountRepository accountRepository = new InMemoryAccountRepository();
        CategoryRepository categoryRepository = new InMemoryCategoryRepository();
        TransactionRepository transactionRepository = new InMemoryTransactionRepository();

        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> new FinancialDataApplicationService(null, categoryRepository, transactionRepository));
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> new FinancialDataApplicationService(accountRepository, null, transactionRepository));
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> new FinancialDataApplicationService(accountRepository, categoryRepository, null));
    }
}
