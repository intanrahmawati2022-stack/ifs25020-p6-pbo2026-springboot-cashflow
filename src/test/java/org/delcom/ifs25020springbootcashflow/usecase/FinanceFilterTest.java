package org.delcom.ifs25020springbootcashflow.usecase;

import org.delcom.ifs25020springbootcashflow.adapter.presenter.FinancePresenter;
import org.delcom.ifs25020springbootcashflow.adapter.repository.TransactionRepository;
import org.delcom.ifs25020springbootcashflow.domain.entity.SortOption;
import org.delcom.ifs25020springbootcashflow.domain.entity.TransactionType;
import org.delcom.ifs25020springbootcashflow.domain.entity.TransactionTypeFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinanceFilterTest {

    @Test
    void listTransactionsAppliesTypeFilter() {
        TransactionRepository repo = new TransactionRepository();
        FinanceUseCase finance = new FinanceUseCase(repo);
        repo.save(1, "Gaji", 100, TransactionType.INCOME, null);
        repo.save(1, "Makan", 50, TransactionType.EXPENSE, null);

        assertEquals(1, finance.listTransactions(1, null, TransactionTypeFilter.INCOME, SortOption.AMOUNT_ASC).size());
        assertEquals(1, finance.listTransactions(1, "", TransactionTypeFilter.EXPENSE, null).size());
        assertEquals(2, finance.listTransactions(1, null, TransactionTypeFilter.ALL, null).size());
        assertEquals(2, finance.listTransactions(1, null, null, null).size());
        assertEquals(0, finance.listTransactions(1, "gaji", TransactionTypeFilter.EXPENSE, null).size());
    }

    @Test
    void filterLabels() {
        assertEquals("Semua", TransactionTypeFilter.ALL.label());
        assertEquals("Pemasukan", TransactionTypeFilter.INCOME.label());
        assertEquals("Pengeluaran", TransactionTypeFilter.EXPENSE.label());
    }

    @Test
    void presenterFormatsAiMessage() {
        assertTrue(new FinancePresenter().successAiAdded(3).contains("3"));
    }
}