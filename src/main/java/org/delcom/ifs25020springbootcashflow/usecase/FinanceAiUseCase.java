package org.delcom.ifs25020springbootcashflow.usecase;

import org.delcom.ifs25020springbootcashflow.domain.entity.AiGeneratedTransaction;
import org.delcom.ifs25020springbootcashflow.domain.entity.Transaction;
import org.delcom.ifs25020springbootcashflow.domain.exception.DomainException;
import org.delcom.ifs25020springbootcashflow.domain.exception.ValidationException;
import org.delcom.ifs25020springbootcashflow.domain.repository.IFinanceAiService;
import org.delcom.ifs25020springbootcashflow.domain.repository.ITransactionRepository;

import java.util.ArrayList;
import java.util.List;

/** Use case AI: generate transaksi dari prompt, lalu simpan hasil yang disetujui user. */
public class FinanceAiUseCase {

    private final IFinanceAiService ai;
    private final ITransactionRepository transactions;

    public FinanceAiUseCase(IFinanceAiService ai, ITransactionRepository transactions) {
        this.ai = ai;
        this.transactions = transactions;
    }

    /** Minta AI membuat daftar transaksi; hanya item valid yang dikembalikan. */
    public List<AiGeneratedTransaction> generateTransactions(String prompt) throws DomainException {
        if (prompt == null || prompt.isBlank()) {
            throw new ValidationException("Prompt tidak boleh kosong");
        }
        List<AiGeneratedTransaction> result = ai.generateTransactions(prompt.trim());
        List<AiGeneratedTransaction> valid = result == null
                ? List.of()
                : result.stream().filter(FinanceAiUseCase::isValid).toList();
        if (valid.isEmpty()) {
            throw new ValidationException("AI tidak menghasilkan transaksi yang valid");
        }
        return valid;
    }

    /** Simpan hasil AI ke database sebagai transaksi milik user. */
    public List<Transaction> saveGeneratedTransactions(int userId, List<AiGeneratedTransaction> items)
            throws DomainException {
        if (items == null || items.isEmpty()) {
            throw new ValidationException("Tidak ada transaksi untuk disimpan");
        }
        for (AiGeneratedTransaction item : items) {
            if (!isValid(item)) {
                throw new ValidationException("Ada transaksi AI yang tidak valid");
            }
        }
        List<Transaction> saved = new ArrayList<>();
        for (AiGeneratedTransaction item : items) {
            saved.add(transactions.save(userId, item.description().trim(), item.amount(), item.type(), null));
        }
        return saved;
    }

    private static boolean isValid(AiGeneratedTransaction item) {
        return item != null
                && item.description() != null && !item.description().isBlank()
                && item.amount() > 0
                && item.type() != null;
    }
}