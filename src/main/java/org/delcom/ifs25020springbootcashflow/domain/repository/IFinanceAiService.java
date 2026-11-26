package org.delcom.ifs25020springbootcashflow.domain.repository;

import org.delcom.ifs25020springbootcashflow.domain.entity.AiGeneratedTransaction;
import org.delcom.ifs25020springbootcashflow.domain.exception.DomainException;

import java.util.List;

/** Port AI: mengubah catatan keuangan teks bebas menjadi daftar transaksi. */
public interface IFinanceAiService {
    List<AiGeneratedTransaction> generateTransactions(String prompt) throws DomainException;
}