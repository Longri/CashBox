package com.cashbox.ui;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;

import com.cashbox.model.GoalIcon;
import com.cashbox.model.GoalStatus;
import com.cashbox.service.GoalDetails;
import com.cashbox.service.GoalIconChoice;
import com.cashbox.service.GoalImage;
import com.cashbox.service.GoalInput;
import com.cashbox.service.GoalPhoto;
import com.cashbox.service.ImageCrop;
import com.cashbox.service.SavingsGoalService;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;

/**
 * Dialog zum Anlegen und Bearbeiten eines Sparziels.
 * <ul>
 * <li><b>Neu:</b> Name, Zielbetrag und optionale Angaben; das Ziel startet aktiv und leer.</li>
 * <li><b>Symbol:</b> ein Tipp auf das runde Symbol links neben dem Namen öffnet den {@link GoalIconDialog}
 *     (Standardsymbol oder eigenes Bild). Nach dem Hochladen und über „Ausschnitt ändern“ folgt der
 *     {@link GoalImageCropDialog}. Gespeichert wird erst mit dem Ziel.</li>
 * <li><b>Bearbeiten:</b> zusätzlich Status und – nur für leere Ziele ohne Buchungen – Löschen.</li>
 * </ul>
 * Geld wird hier nie bewegt; dafür gibt es Ein-, Um- und Ausbuchen in der Übersicht.
 * Auf schmalen Bildschirmen per CSS als Vollbild (Klasse {@code form-dialog}).
 */
public class GoalDialog extends Dialog {

    private static final Logger log = LoggerFactory.getLogger(GoalDialog.class);

    private final SavingsGoalService service;
    /** {@code null} beim Anlegen. */
    private final GoalDetails existing;
    private final Runnable onSaved;

    private final Div errorBanner = new Div();
    private final TextField name = new TextField("Name");
    private final TextField target = new TextField("Zielbetrag");
    private final DatePicker targetDate = new DatePicker("Zieldatum (optional)");
    private final IntegerField priority = new IntegerField("Priorität (optional)");
    private final TextArea description = new TextArea("Beschreibung (optional)");
    private final RadioButtonGroup<GoalStatus> status = new RadioButtonGroup<>("Status");
    private final Button saveButton = new Button();
    private final GoalAvatar iconPreview = new GoalAvatar(GoalAvatar.Size.LARGE);
    private final NativeButton iconButton = new NativeButton();
    /** Was beim Speichern mit Symbol und Bild passiert; bis zur ersten Auswahl bleibt alles, wie es ist. */
    private GoalIconChoice iconChoice = GoalIconChoice.UNCHANGED;
    /** Das gerade gezeigte Symbol, {@code null} bei eigenem Bild – für die Markierung im Auswahldialog. */
    private GoalIcon shownIcon;
    /** Vorschau eines neu gewählten Bilds bzw. Ausschnitts (noch nicht gespeichert), sonst {@code null}. */
    private GoalImage pendingImage;

    private final Binder<Draft> binder = new Binder<>();

    /**
     * @param existing das zu bearbeitende Ziel oder {@code null} für ein neues
     * @param onSaved  wird nach Speichern oder Löschen aufgerufen
     */
    public GoalDialog(SavingsGoalService service, GoalDetails existing, Runnable onSaved) {
        this.service = service;
        this.existing = existing;
        this.onSaved = onSaved;

        addClassNames("form-dialog", "goal-dialog");
        setHeaderTitle(isNew() ? "Neues Sparziel" : "Sparziel bearbeiten");
        setCloseOnOutsideClick(false); // versehentliches Tippen daneben soll keine Eingaben verwerfen
        setCloseOnEsc(true);

        Button close = new Button(VaadinIcon.CLOSE.create(), e -> close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        close.setAriaLabel("Schließen");
        getHeader().add(close);

        configureFields();
        bindFields();

        errorBanner.addClassName("form-error");
        errorBanner.getElement().setAttribute("role", "alert");
        errorBanner.setVisible(false);

        configureIcon();
        Div identity = new Div(iconButton, name);
        identity.addClassName("goal-identity");

        VerticalLayout content = new VerticalLayout(errorBanner, identity, target, targetDate, priority, description);
        content.setPadding(false);
        content.setSpacing(false);
        content.addClassName("goal-form");
        add(content);

        Button cancel = new Button("Abbrechen", e -> close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        cancel.addClassName("dialog-footer-button");
        saveButton.setText(isNew() ? "Anlegen" : "Speichern");
        saveButton.setId("goal-save");
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClassName("dialog-footer-button");
        saveButton.addClickListener(e -> save());
        // Enter speichert aus den einzeiligen Feldern heraus (nicht aus Beschreibung und Datumsauswahl).
        saveButton.addClickShortcut(Key.ENTER).listenOn(name, target, priority);

        if (isNew()) {
            binder.readBean(new Draft());
        } else {
            addEditOnlyParts(content);
            binder.readBean(Draft.of(existing));
        }
        getFooter().add(cancel, saveButton);

        addOpenedChangeListener(e -> {
            if (e.isOpened()) {
                name.focus();
            }
        });
    }

    private boolean isNew() {
        return existing == null;
    }

    // ------------------------------------------------------------------ Aufbau

    private void configureIcon() {
        if (isNew()) {
            iconPreview.showIcon(null);
            shownIcon = GoalIcon.DEFAULT;
        } else {
            iconPreview.showStored(existing.id(), existing.icon(), existing.hasImage(), service);
            shownIcon = existing.hasImage() ? null : orDefault(existing.icon());
        }
        Span badge = new Span(VaadinIcon.PENCIL.create());
        badge.addClassName("goal-icon-button__badge");
        iconButton.add(iconPreview, badge);
        iconButton.setId("goal-icon");
        iconButton.addClassName("goal-icon-button");
        iconButton.setAriaLabel("Symbol oder Bild ändern");
        iconButton.getElement().setAttribute("title", "Symbol oder Bild ändern");
        iconButton.addClickListener(e -> openIconDialog());
    }

    private void configureFields() {
        name.setId("goal-name");
        name.setWidthFull();
        name.setMaxLength(SavingsGoalService.MAX_NAME_LENGTH);
        name.setPlaceholder("z. B. Urlaub, Fahrrad, Notgroschen");

        target.setId("goal-target");
        target.setWidthFull();
        AmountConverter.configureField(target);

        targetDate.setId("goal-date");
        targetDate.setWidthFull();
        targetDate.setLocale(Locale.GERMANY);
        targetDate.setI18n(germanDatePickerI18n());
        targetDate.setClearButtonVisible(true);
        targetDate.setHelperText("Bis wann das Geld zusammen sein soll.");
        if (isNew()) {
            targetDate.setMin(LocalDate.now());
        }

        priority.setId("goal-priority");
        priority.setWidthFull();
        priority.setMin(SavingsGoalService.MIN_PRIORITY);
        priority.setMax(SavingsGoalService.MAX_PRIORITY);
        priority.setStepButtonsVisible(true);
        priority.setHelperText("1 = am wichtigsten. Bestimmt die Reihenfolge der Sparziele – "
                + "die du in der Liste auch per Ziehen am Griff ändern kannst.");

        description.setId("goal-description");
        description.setWidthFull();
        description.setMaxLength(SavingsGoalService.MAX_DESCRIPTION_LENGTH);
        description.setMinRows(2);
        description.setMaxRows(6);
        description.setPlaceholder("Details, Links, Gedanken zum Ziel");

        // Wird nur beim Bearbeiten angezeigt, ist aber immer gebunden (neue Ziele: ACTIVE).
        status.setId("goal-status");
        status.setItems(GoalStatus.values());
        status.setItemLabelGenerator(GoalStatus::getLabel);
        status.setHelperText("Nur aktive Ziele bekommen neues Geld. Aus pausierten und erreichten Zielen "
                + "kannst du weiterhin um- und ausbuchen.");
    }

    private void bindFields() {
        binder.forField(name)
                .asRequired("Bitte einen Namen eingeben.")
                .withValidator(v -> !v.isBlank(), "Bitte einen Namen eingeben.")
                .bind(Draft::getName, Draft::setName);

        AmountConverter.bind(binder, target, "Bitte einen Zielbetrag eingeben.")
                .bind(Draft::getTarget, Draft::setTarget);

        binder.forField(targetDate).bind(Draft::getTargetDate, Draft::setTargetDate);

        binder.forField(priority)
                .withValidator(v -> v == null
                                || v >= SavingsGoalService.MIN_PRIORITY && v <= SavingsGoalService.MAX_PRIORITY,
                        "Bitte eine Zahl von " + SavingsGoalService.MIN_PRIORITY + " bis "
                                + SavingsGoalService.MAX_PRIORITY + " eingeben.")
                .bind(Draft::getPriority, Draft::setPriority);

        binder.forField(description).bind(Draft::getDescription, Draft::setDescription);

        binder.forField(status)
                .asRequired("Bitte einen Status wählen.")
                .bind(Draft::getStatus, Draft::setStatus);
    }

    /** Status, Hinweise zum zugewiesenen Geld und Löschen – nur beim Bearbeiten. */
    private void addEditOnlyParts(VerticalLayout content) {
        target.setHelperText("Bereits zugewiesen: " + Money.format(existing.assignedAmount()));
        content.add(status);

        if (existing.status() == GoalStatus.ACTIVE && existing.isFunded()) {
            Paragraph funded = new Paragraph("Der Zielbetrag ist erreicht. Du kannst das Ziel als „"
                    + GoalStatus.REACHED.getLabel() + "“ markieren.");
            funded.addClassNames("form-hint", "form-hint--success");
            funded.setId("goal-funded-hint");
            content.addComponentAtIndex(1, funded);
        }

        if (service.canDelete(existing.id())) {
            Button delete = new Button("Löschen", VaadinIcon.TRASH.create(), e -> confirmDelete());
            delete.setId("goal-delete");
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            delete.addClassName("dialog-footer-delete");
            getFooter().add(delete);
        } else {
            Paragraph hint = new Paragraph("Löschen geht nur bei leeren Zielen ohne Buchungen. Ziele, die du "
                    + "nicht mehr brauchst, setzt du auf „" + GoalStatus.PAUSED.getLabel() + "“ oder „"
                    + GoalStatus.REACHED.getLabel() + "“.");
            hint.addClassName("form-hint");
            hint.setId("goal-delete-hint");
            content.add(hint);
        }
    }

    // ------------------------------------------------------------------ Verhalten

    private void openIconDialog() {
        GoalAvatar currentImage = null;
        if (shownIcon == null) {
            currentImage = new GoalAvatar(GoalAvatar.Size.MEDIUM);
            if (pendingImage != null) {
                currentImage.showImage(pendingImage);
            } else {
                currentImage.showStored(existing.id(), null, true, service);
            }
        }
        new GoalIconDialog(shownIcon, currentImage, this::symbolChosen, this::photoUploaded, this::editCrop).open();
    }

    private void symbolChosen(GoalIcon icon) {
        iconChoice = new GoalIconChoice.Symbol(icon);
        pendingImage = null;
        shownIcon = orDefault(icon);
        iconPreview.showIcon(icon);
        iconButton.focus();
    }

    /** Neues Bild: erst den Ausschnitt wählen lassen; Abbrechen verwirft den Upload. */
    private void photoUploaded(GoalPhoto photo) {
        new GoalImageCropDialog(photo, ImageCrop.FULL,
                crop -> imageChosen(new GoalIconChoice.Image(photo, crop), photo, crop)).open();
    }

    /** Ausschnitt des aktuellen Bilds ändern – des neu hochgeladenen oder des gespeicherten. */
    private void editCrop() {
        hideError();
        if (iconChoice instanceof GoalIconChoice.Image image) {
            new GoalImageCropDialog(image.photo(), image.crop(),
                    crop -> imageChosen(new GoalIconChoice.Image(image.photo(), crop), image.photo(), crop)).open();
            return;
        }
        service.findPhoto(existing.id()).ifPresentOrElse(stored -> {
            ImageCrop current = iconChoice instanceof GoalIconChoice.Crop pending ? pending.crop() : stored.crop();
            new GoalImageCropDialog(stored.photo(), current,
                    crop -> imageChosen(new GoalIconChoice.Crop(crop), stored.photo(), crop)).open();
        }, () -> showError("Das Bild ist nicht mehr vorhanden. Bitte lade es neu hoch."));
    }

    private void imageChosen(GoalIconChoice choice, GoalPhoto photo, ImageCrop crop) {
        GoalImage preview;
        try {
            preview = GoalImage.render(photo, crop);
        } catch (RuntimeException ex) {
            handleError(ex, "Bildausschnitt konnte nicht berechnet werden");
            return;
        }
        iconChoice = choice;
        pendingImage = preview;
        shownIcon = null;
        iconPreview.showImage(preview);
        iconButton.focus();
    }

    private static GoalIcon orDefault(GoalIcon icon) {
        return icon == null ? GoalIcon.DEFAULT : icon;
    }

    private void save() {
        hideError();
        if (!binder.validate().isOk()) {
            return;
        }
        Draft draft = new Draft();
        if (!binder.writeBeanIfValid(draft)) {
            return;
        }
        GoalInput input = new GoalInput(draft.getName(), draft.getTarget(), draft.getTargetDate(),
                draft.getPriority(), draft.getDescription(), iconChoice);
        String message;
        try {
            if (isNew()) {
                GoalDetails created = service.create(input);
                message = "Sparziel „" + created.name() + "“ angelegt";
            } else {
                GoalDetails updated = service.update(existing.id(), existing.version(), input, draft.getStatus());
                message = "Sparziel „" + updated.name() + "“ gespeichert";
            }
        } catch (RuntimeException ex) {
            handleError(ex, "Speichern des Sparziels fehlgeschlagen");
            return;
        }
        finish(message);
    }

    private void confirmDelete() {
        ConfirmDialog confirm = new ConfirmDialog();
        confirm.setHeader("Sparziel löschen?");
        confirm.setText("„" + existing.name() + "“ wird endgültig gelöscht.");
        confirm.setCancelable(true);
        confirm.setCancelText("Abbrechen");
        confirm.setConfirmText("Löschen");
        confirm.setConfirmButtonTheme("error primary");
        confirm.addConfirmListener(e -> delete());
        confirm.open();
    }

    private void delete() {
        hideError();
        try {
            service.delete(existing.id(), existing.version());
        } catch (RuntimeException ex) {
            handleError(ex, "Löschen des Sparziels fehlgeschlagen");
            return;
        }
        finish("Sparziel „" + existing.name() + "“ gelöscht");
    }

    private void handleError(RuntimeException ex, String logMessage) {
        if (ex instanceof OptimisticLockingFailureException) {
            showError("Das Sparziel wurde zwischenzeitlich geändert. Bitte den Dialog schließen und erneut öffnen.");
        } else if (ex instanceof IllegalArgumentException || ex instanceof IllegalStateException) {
            showError(ex.getMessage());
        } else {
            // Nicht fachlich erwartet (z. B. Datenbankfehler): protokollieren und sichtbar machen.
            log.error(logMessage, ex);
            showError("Das Sparziel konnte wegen eines technischen Fehlers nicht gespeichert werden.");
        }
    }

    private void finish(String message) {
        close();
        Notification notification = Notification.show(message, 4000, Notification.Position.BOTTOM_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        onSaved.run();
    }

    private void showError(String text) {
        errorBanner.setText(text);
        errorBanner.setVisible(true);
    }

    private void hideError() {
        errorBanner.setVisible(false);
    }

    // ------------------------------------------------------------------ Hilfsklassen

    private static DatePicker.DatePickerI18n germanDatePickerI18n() {
        DatePicker.DatePickerI18n i18n = new DatePicker.DatePickerI18n();
        i18n.setMonthNames(List.of("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August",
                "September", "Oktober", "November", "Dezember"));
        i18n.setWeekdays(List.of("Sonntag", "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag"));
        i18n.setWeekdaysShort(List.of("So", "Mo", "Di", "Mi", "Do", "Fr", "Sa"));
        i18n.setFirstDayOfWeek(1);
        i18n.setToday("Heute");
        i18n.setCancel("Abbrechen");
        i18n.setDateFormat("dd.MM.yyyy");
        i18n.setBadInputErrorMessage("Bitte ein gültiges Datum eingeben, z. B. 31.12.2027.");
        i18n.setMinErrorMessage("Das Datum darf nicht in der Vergangenheit liegen.");
        return i18n;
    }

    /** Formularzustand für den Binder. */
    private static final class Draft {
        private String name;
        private BigDecimal target;
        private LocalDate targetDate;
        private Integer priority;
        private String description;
        private GoalStatus status = GoalStatus.ACTIVE;

        static Draft of(GoalDetails goal) {
            Draft draft = new Draft();
            draft.name = goal.name();
            draft.target = goal.targetAmount();
            draft.targetDate = goal.targetDate();
            draft.priority = goal.priority();
            draft.description = goal.description();
            draft.status = goal.status();
            return draft;
        }

        String getName() { return name; }
        void setName(String name) { this.name = name; }
        BigDecimal getTarget() { return target; }
        void setTarget(BigDecimal target) { this.target = target; }
        LocalDate getTargetDate() { return targetDate; }
        void setTargetDate(LocalDate targetDate) { this.targetDate = targetDate; }
        Integer getPriority() { return priority; }
        void setPriority(Integer priority) { this.priority = priority; }
        String getDescription() { return description; }
        void setDescription(String description) { this.description = description; }
        GoalStatus getStatus() { return status; }
        void setStatus(GoalStatus status) { this.status = status; }
    }
}
