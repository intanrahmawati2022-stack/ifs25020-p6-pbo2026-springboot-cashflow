package org.delcom.ifs25020springbootcashflow.usecase;

import org.delcom.ifs25020springbootcashflow.adapter.ai.InMemoryFinanceAiService;
import org.delcom.ifs25020springbootcashflow.adapter.repository.TransactionRepository;
import org.delcom.ifs25020springbootcashflow.domain.entity.AiGeneratedTransaction;
import org.delcom.ifs25020springbootcashflow.domain.entity.Transaction;
import org.delcom.ifs25020springbootcashflow.domain.entity.TransactionType;
import org.delcom.ifs25020springbootcashflow.domain.exception.ValidationException;
import org.delcom.ifs25020springbootcashflow.domain.repository.IFinanceAiService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FinanceAiUseCaseTest {

    private static AiGeneratedTransaction item(String d, long a, TransactionType t) {
        return new AiGeneratedTransaction(d, a, t);
    }

    @Test
    void generateRejectsBlankPrompt() {
        FinanceAiUseCase uc = new FinanceAiUseCase(new InMemoryFinanceAiService(), new TransactionRepository());
        assertThrows(ValidationException.class, () -> uc.generateTransactions(null));
        assertThrows(ValidationException.class, () -> uc.generateTransactions("   "));
    }

    @Test
    void generateTrimsPromptAndReturnsValidItems() throws Exception {
        InMemoryFinanceAiService ai = new InMemoryFinanceAiService();
        FinanceAiUseCase uc = new FinanceAiUseCase(ai, new TransactionRepository());
        assertEquals(2, uc.generateTransactions("  gaji dan makan  ").size());
        assertEquals("gaji dan makan", ai.getLastPrompt());
    }

    @Test
    void generateFiltersInvalidItems() throws Exception {
        IFinanceAiService ai = prompt -> Arrays.asList(
                null,
                item(null, 10, TransactionType.INCOME),
                item(" ", 10, TransactionType.INCOME),
                item("nol", 0, TransactionType.INCOME),
                item("tanpa tipe", 10, null),
                item("valid", 10, TransactionType.EXPENSE));
        FinanceAiUseCase uc = new FinanceAiUseCase(ai, new TransactionRepository());
        List<AiGeneratedTransaction> result = uc.generateTransactions("x");
        assertEquals(1, result.size());
        assertEquals("valid", result.get(0).description());
    }

    @Test
    void generateFailsWhenNothingValid() {
        FinanceAiUseCase nullResult = new FinanceAiUseCase(p -> null, new TransactionRepository());
        assertThrows(ValidationException.class, () -> nullResult.generateTransactions("x"));
        FinanceAiUseCase empty = new FinanceAiUseCase(p -> List.of(), new TransactionRepository());
        assertThrows(ValidationException.class, () -> empty.generateTransactions("x"));
        FinanceAiUseCase invalid = new FinanceAiUseCase(
                p -> List.of(item("x", -1, TransactionType.INCOME)), new TransactionRepository());
        assertThrows(ValidationException.class, () -> invalid.generateTransactions("x"));
    }

    @Test
    void saveStoresItemsForUser() throws Exception {
        TransactionRepository repo = new TransactionRepository();
        FinanceAiUseCase uc = new FinanceAiUseCase(new InMemoryFinanceAiService(), repo);
        List<Transaction> saved = uc.saveGeneratedTransactions(7, List.of(
                item("  Gaji  ", 100, TransactionType.INCOME),
                item("Makan", 50, TransactionType.EXPENSE)));
        assertEquals(2, saved.size());
        assertEquals("Gaji", saved.get(0).getDescription());
        assertEquals(2, repo.findByUserId(7).size());
    }

    @Test
    void saveRejectsEmptyOrInvalidInput() {
        TransactionRepository repo = new TransactionRepository();
        FinanceAiUseCase uc = new FinanceAiUseCase(new InMemoryFinanceAiService(), repo);
        assertThrows(ValidationException.class, () -> uc.saveGeneratedTransactions(1, null));
        assertThrows(ValidationException.class, () -> uc.saveGeneratedTransactions(1, List.of()));
        assertThrows(ValidationException.class, () -> uc.saveGeneratedTransactions(1,
                List.of(item("ok", 10, TransactionType.INCOME), item("", 10, TransactionType.INCOME))));
        assertTrue(repo.findAll().isEmpty());
    }
}