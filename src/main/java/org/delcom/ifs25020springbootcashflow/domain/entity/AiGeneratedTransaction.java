package org.delcom.ifs25020springbootcashflow.domain.entity;

/** Hasil generasi AI sebelum disimpan ke database (belum punya id/userId). */
public record AiGeneratedTransaction(String description, long amount, TransactionType type) {
}