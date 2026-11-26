package org.delcom.ifs25020springbootcashflow.domain.entity;

/** Filter tipe transaksi pada daftar. */
public enum TransactionTypeFilter {
    ALL("Semua"),
    INCOME("Pemasukan"),
    EXPENSE("Pengeluaran");

    private final String label;

    TransactionTypeFilter(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean matches(Transaction t) {
        return this == ALL || t.getType().name().equals(name());
    }
}