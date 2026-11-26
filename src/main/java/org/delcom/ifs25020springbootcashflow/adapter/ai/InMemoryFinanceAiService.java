package org.delcom.ifs25020springbootcashflow.adapter.ai;

import org.delcom.ifs25020springbootcashflow.domain.entity.AiGeneratedTransaction;
import org.delcom.ifs25020springbootcashflow.domain.entity.TransactionType;
import org.delcom.ifs25020springbootcashflow.domain.repository.IFinanceAiService;

import java.util.List;

/** Stub AI deterministik untuk unit test: tanpa jaringan, selalu mengembalikan data tetap. */
public class InMemoryFinanceAiService implements IFinanceAiService {

    private final List<AiGeneratedTransaction> fixed;
    private String lastPrompt;

    public InMemoryFinanceAiService() {
        this(List.of(
                new AiGeneratedTransaction("Gaji bulanan", 5_000_000L, TransactionType.INCOME),
                new AiGeneratedTransaction("Makan siang", 25_000L, TransactionType.EXPENSE)));
    }

    public InMemoryFinanceAiService(List<AiGeneratedTransaction> fixed) {
        this.fixed = List.copyOf(fixed);
    }

    @Override
    public List<AiGeneratedTransaction> generateTransactions(String prompt) {
        this.lastPrompt = prompt;
        return fixed;
    }

    public String getLastPrompt() {
        return lastPrompt;
    }
}