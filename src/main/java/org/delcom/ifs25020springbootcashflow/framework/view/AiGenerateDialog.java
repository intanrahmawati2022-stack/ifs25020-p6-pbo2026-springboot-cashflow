package org.delcom.ifs25020springbootcashflow.framework.view;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextArea;
import org.delcom.ifs25020springbootcashflow.adapter.presenter.FinancePresenter;
import org.delcom.ifs25020springbootcashflow.domain.entity.AiGeneratedTransaction;
import org.delcom.ifs25020springbootcashflow.domain.entity.Transaction;
import org.delcom.ifs25020springbootcashflow.domain.exception.DomainException;
import org.delcom.ifs25020springbootcashflow.usecase.FinanceAiUseCase;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Dialog Generate AI: prompt -> Proses AI (ProgressBar) -> preview -> tambahkan ke catatan. */
public class AiGenerateDialog extends Dialog {

    /** Hasil proses AI di background: daftar transaksi atau pesan error. */
    private record Outcome(List<AiGeneratedTransaction> items, String error) {
    }

    private final TextArea prompt = new TextArea("Catatan keuangan");
    private final ProgressBar progress = new ProgressBar();
    private final Grid<AiGeneratedTransaction> preview = new Grid<>();
    private final Button process = new Button("Proses AI");
    private final Button add = new Button("Tambahkan ke catatan");
    private List<AiGeneratedTransaction> generated = List.of();

    public AiGenerateDialog(int userId, FinanceAiUseCase aiUseCase, FinancePresenter presenter, Runnable onSaved) {
        addClassName("cf-dialog");
        setModal(true);
        setCloseOnOutsideClick(false);
        setHeaderTitle("Generate AI");
        setWidth("min(94vw, 640px)");

        prompt.setWidthFull();
        prompt.setMinHeight("110px");
        prompt.setPlaceholder("contoh: Gaji masuk 5 juta, beli kopi 25 ribu, bayar listrik 350 ribu");

        progress.setIndeterminate(true);
        progress.setVisible(false);

        preview.addColumn(AiGeneratedTransaction::description).setHeader("Keterangan").setFlexGrow(2);
        preview.addColumn(i -> presenter.formatMoney(i.amount())).setHeader("Jumlah");
        preview.addColumn(i -> presenter.typeLabel(i.type())).setHeader("Tipe");
        preview.setHeight("220px");
        preview.setVisible(false);

        add(new VerticalLayout(prompt, progress, preview));

        process.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        add.setEnabled(false);

        Button cancel = new Button("Batal", e -> close());
        process.addClickListener(e -> startProcessing(aiUseCase, presenter));
        add.addClickListener(e -> saveResult(userId, aiUseCase, presenter, onSaved));
        getFooter().add(cancel, process, add);
    }

    private void startProcessing(FinanceAiUseCase aiUseCase, FinancePresenter presenter) {
        UI ui = UI.getCurrent();
        String text = prompt.getValue();
        setBusy(true);
        ui.setPollInterval(300);
        CompletableFuture
                .supplyAsync(() -> generate(aiUseCase, text))
                .exceptionally(ex -> new Outcome(List.of(), "terjadi kesalahan tak terduga"))
                .thenAccept(outcome -> ui.access(() -> finish(ui, outcome, presenter)));
    }

    private static Outcome generate(FinanceAiUseCase aiUseCase, String text) {
        try {
            return new Outcome(aiUseCase.generateTransactions(text), null);
        } catch (DomainException ex) {
            return new Outcome(List.of(), ex.getMessage());
        }
    }

    private void finish(UI ui, Outcome outcome, FinancePresenter presenter) {
        ui.setPollInterval(-1);
        generated = outcome.items();
        preview.setItems(generated);
        preview.setVisible(!generated.isEmpty());
        setBusy(false);
        if (outcome.error() != null) {
            Dialogs.error(presenter.failure(outcome.error()));
        }
    }

    private void setBusy(boolean busy) {
        progress.setVisible(busy);
        prompt.setEnabled(!busy);
        process.setEnabled(!busy);
        add.setEnabled(!busy && !generated.isEmpty());
    }

    private void saveResult(int userId, FinanceAiUseCase aiUseCase, FinancePresenter presenter, Runnable onSaved) {
        try {
            List<Transaction> saved = aiUseCase.saveGeneratedTransactions(userId, generated);
            Dialogs.success(presenter.successAiAdded(saved.size()));
            close();
            onSaved.run();
        } catch (DomainException ex) {
            Dialogs.error(presenter.failure(ex.getMessage()));
        }
    }
}