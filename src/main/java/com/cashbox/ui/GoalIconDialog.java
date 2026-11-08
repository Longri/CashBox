package com.cashbox.ui;

import java.util.List;
import java.util.function.Consumer;

import com.cashbox.model.GoalIcon;
import com.cashbox.service.GoalPhoto;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.UploadI18N;
import com.vaadin.flow.server.streams.UploadHandler;

/**
 * Auswahl von Symbol oder eigenem Bild für ein Sparziel: ein Bild hochladen, den Ausschnitt des
 * vorhandenen Bilds ändern oder eines der Standardsymbole antippen. Die Auswahl geht an den
 * {@link GoalDialog} zurück und wird erst mit dem Sparziel gespeichert.
 * <p>
 * Hochgeladene Bilder werden sofort aufbereitet ({@link GoalPhoto#fromUpload}), damit Fehler
 * (kein Bild, kaputte Datei) hier und nicht erst beim Speichern auffallen.
 */
class GoalIconDialog extends Dialog {

    private final Consumer<GoalIcon> onSymbol;
    private final Consumer<GoalPhoto> onUpload;
    private final Div errorBanner = new Div();
    private final Upload upload;

    /**
     * @param selected     das aktuell gezeigte Symbol oder {@code null}, wenn ein eigenes Bild gezeigt wird
     * @param currentImage Avatar mit dem aktuellen eigenen Bild oder {@code null}, wenn es keines gibt
     * @param onSymbol     bekommt das gewählte Symbol
     * @param onUpload     bekommt das hochgeladene, aufbereitete Bild (danach folgt die Wahl des Ausschnitts)
     * @param onEditCrop   „Ausschnitt ändern“ für das aktuelle Bild
     */
    GoalIconDialog(GoalIcon selected, GoalAvatar currentImage, Consumer<GoalIcon> onSymbol,
                   Consumer<GoalPhoto> onUpload, Runnable onEditCrop) {
        this.onSymbol = onSymbol;
        this.onUpload = onUpload;

        addClassNames("form-dialog", "icon-picker");
        setHeaderTitle("Symbol wählen");
        setCloseOnEsc(true);
        setCloseOnOutsideClick(true);

        Button close = new Button(VaadinIcon.CLOSE.create(), e -> close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        close.setAriaLabel("Schließen");
        getHeader().add(close);

        errorBanner.addClassName("form-error");
        errorBanner.getElement().setAttribute("role", "alert");
        errorBanner.setVisible(false);

        VerticalLayout content = new VerticalLayout(errorBanner, sectionTitle("Eigenes Bild"));
        if (currentImage != null) {
            Button crop = new Button("Ausschnitt ändern", VaadinIcon.CROP.create(), e -> {
                close();
                onEditCrop.run();
            });
            crop.setId("goal-icon-crop");
            Div current = new Div(currentImage, crop);
            current.addClassName("icon-picker__current");
            content.add(current);
        }
        upload = createUpload(currentImage != null ? "Anderes Bild hochladen" : "Bild hochladen");
        Paragraph uploadHint = new Paragraph("JPG, PNG oder GIF bis " + GoalPhoto.MAX_UPLOAD_BYTES / (1024 * 1024)
                + " MB. Danach wählst du den Ausschnitt."
                + (currentImage != null ? " Ein Symbol entfernt das eigene Bild." : ""));
        uploadHint.addClassName("form-hint");
        content.add(upload, uploadHint, sectionTitle("Oder ein Symbol"), symbolGrid(selected));
        content.setPadding(false);
        content.setSpacing(false);
        content.addClassName("icon-picker__content");
        add(content);

        Button cancel = new Button("Abbrechen", e -> close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        cancel.addClassName("dialog-footer-button");
        getFooter().add(cancel);
    }

    private static Span sectionTitle(String text) {
        Span title = new Span(text);
        title.addClassName("icon-picker__section");
        return title;
    }

    // ------------------------------------------------------------------ Eigenes Bild

    private Upload createUpload(String buttonText) {
        UploadHandler handler = UploadHandler.inMemory((metadata, data) -> imageUploaded(data))
                .validateMetadata(event -> {
                    if (event.getFileSize() > GoalPhoto.MAX_UPLOAD_BYTES) {
                        event.reject("Die Datei ist zu groß.");
                    }
                });
        Upload result = new Upload(handler);
        result.setId("goal-icon-upload");
        result.setWidthFull();
        result.setMaxFiles(1);
        result.setMaxFileSize(GoalPhoto.MAX_UPLOAD_BYTES);
        result.setAcceptedFileTypes(GoalPhoto.ACCEPTED_FILE_TYPES.toArray(String[]::new));
        Button button = new Button(buttonText, VaadinIcon.UPLOAD.create());
        result.setUploadButton(button);
        result.setDropLabel(new Span("oder hierher ziehen"));
        result.setI18n(germanUploadI18n());
        result.addFileRejectedListener(e -> showError(e.getErrorMessage()));
        result.addFailedListener(e -> showError("Das Hochladen hat nicht geklappt. Bitte erneut versuchen."));
        return result;
    }

    /** Läuft mit Sitzungssperre (UI-Zugriff) nach vollständigem Empfang. */
    private void imageUploaded(byte[] data) {
        GoalPhoto photo;
        try {
            photo = GoalPhoto.fromUpload(data);
        } catch (IllegalArgumentException ex) {
            upload.clearFileList();
            showError(ex.getMessage());
            return;
        }
        close();
        onUpload.accept(photo);
    }

    // ------------------------------------------------------------------ Symbole

    private Div symbolGrid(GoalIcon selected) {
        Div grid = new Div();
        grid.addClassName("icon-picker__grid");
        grid.setId("goal-icon-options");
        grid.getElement().setAttribute("role", "group");
        grid.getElement().setAttribute("aria-label", "Symbole");
        for (GoalIcon icon : GoalIcon.values()) {
            NativeButton option = new NativeButton();
            option.add(VaadinIcon.valueOf(icon.name()).create());
            option.setId("goal-icon-option-" + icon.name());
            option.addClassName("icon-picker__option");
            option.setAriaLabel(icon.getLabel());
            option.getElement().setAttribute("title", icon.getLabel());
            option.getElement().setAttribute("aria-pressed", String.valueOf(icon == selected));
            option.addClickListener(e -> {
                close();
                onSymbol.accept(icon);
            });
            grid.add(option);
        }
        return grid;
    }

    // ------------------------------------------------------------------ Verhalten

    private void showError(String text) {
        errorBanner.setText(text);
        errorBanner.setVisible(true);
    }

    private static UploadI18N germanUploadI18n() {
        UploadI18N i18n = new UploadI18N();
        i18n.setAddFiles(new UploadI18N.AddFiles().setOne("Bild hochladen …").setMany("Bilder hochladen …"));
        i18n.setDropFiles(new UploadI18N.DropFiles().setOne("Bild hierher ziehen").setMany("Bilder hierher ziehen"));
        i18n.setError(new UploadI18N.Error()
                .setTooManyFiles("Bitte nur ein Bild auswählen.")
                .setFileIsTooBig("Die Datei ist zu groß (höchstens " + GoalPhoto.MAX_UPLOAD_BYTES / (1024 * 1024)
                        + " MB).")
                .setIncorrectFileType("Bitte ein JPG-, PNG- oder GIF-Bild wählen."));
        i18n.setUploading(new UploadI18N.Uploading()
                .setStatus(new UploadI18N.Uploading.Status()
                        .setConnecting("Verbinde …")
                        .setStalled("Angehalten")
                        .setProcessing("Bild wird verarbeitet …")
                        .setHeld("Wartet"))
                .setRemainingTime(new UploadI18N.Uploading.RemainingTime()
                        .setPrefix("Restzeit: ")
                        .setUnknown("Restzeit unbekannt"))
                .setError(new UploadI18N.Uploading.Error()
                        .setServerUnavailable("Server nicht erreichbar")
                        .setUnexpectedServerError("Unerwarteter Serverfehler")
                        .setForbidden("Nicht erlaubt")
                        .setFileTooLarge("Die Datei ist zu groß.")));
        i18n.setUnits(List.of("B", "kB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB"));
        return i18n;
    }
}
