package com.cashbox.ui;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;

import com.cashbox.service.AccountOverview;
import com.cashbox.service.BookingService;
import com.cashbox.service.Pot;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.tabs.TabsVariant;
import com.vaadin.flow.component.textfield.Autocomplete;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.Result;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.data.binder.ValueContext;
import com.vaadin.flow.data.converter.Converter;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;

/**
 * Dialog für Ein- und Umbuchungen.
 * <ul>
 * <li><b>Einbuchen:</b> neues Geld auf das Sparkonto – landet im Puffer oder direkt in einem Sparziel.</li>
 * <li><b>Umbuchen:</b> Geld zwischen Puffer und Sparzielen verschieben; der Kontostand bleibt gleich.</li>
 * </ul>
 * Auf schmalen Bildschirmen (Handy) wird der Dialog per CSS als Vollbild angezeigt
 * (siehe {@code styles.css}, Klasse {@code booking-dialog}).
 */
public class BookingDialog extends Dialog {

    public enum Mode {
        DEPOSIT, TRANSFER
    }

    private static final Logger log = LoggerFactory.getLogger(BookingDialog.class);

    private final BookingService service;
    private final Runnable onBooked;
    private final BigDecimal balance;
    private final List<Pot> pots;
    private final boolean transferPossible;

    private final Tab depositTab = new Tab(VaadinIcon.PLUS.create(), new Span("Einbuchen"));
    private final Tab transferTab = new Tab(VaadinIcon.EXCHANGE.create(), new Span("Umbuchen"));
    private final Tabs modeTabs = new Tabs(depositTab, transferTab);
    private final Paragraph modeHint = new Paragraph();
    private final Div errorBanner = new Div();

    private final TextField amount = new TextField("Betrag");
    private final Button maxButton = new Button("Alles");
    private final Select<Pot> source = new Select<>();
    private final Button swapButton = new Button(VaadinIcon.ARROWS_LONG_V.create());
    private final Div swapRow = new Div(swapButton);
    private final Select<Pot> target = new Select<>();
    private final TextField note = new TextField("Notiz (optional)");
    private final Div preview = new Div();
    private final Button saveButton = new Button();

    private final Binder<Draft> binder = new Binder<>();
    private final Binder.Binding<Draft, BigDecimal> amountBinding;
    private Mode mode;

    public BookingDialog(BookingService service, Mode initialMode, Runnable onBooked) {
        this.service = service;
        this.onBooked = onBooked;
        AccountOverview overview = service.getOverview();
        this.balance = overview.balance();
        this.pots = overview.pots();
        this.transferPossible = pots.size() > 1 && pots.stream().anyMatch(p -> p.available().signum() > 0);

        addClassName("booking-dialog");
        setHeaderTitle("Buchung");
        setCloseOnOutsideClick(false); // versehentliches Tippen daneben soll keine Eingaben verwerfen
        setCloseOnEsc(true);

        Button close = new Button(VaadinIcon.CLOSE.create(), e -> close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        close.setAriaLabel("Schließen");
        getHeader().add(close);

        configureModeTabs();
        configureAmount();
        configurePotSelects();
        note.setMaxLength(BookingService.MAX_NOTE_LENGTH);
        note.setWidthFull();
        note.setValueChangeMode(ValueChangeMode.LAZY);
        note.setId("booking-note");
        preview.addClassName("booking-preview");
        preview.setId("booking-preview");
        errorBanner.addClassName("booking-error");
        errorBanner.getElement().setAttribute("role", "alert");
        errorBanner.setVisible(false);
        modeHint.addClassName("booking-hint");

        amountBinding = bindFields();

        HorizontalLayout amountRow = new HorizontalLayout(amount, maxButton);
        amountRow.setWidthFull();
        amountRow.setAlignItems(FlexComponent.Alignment.BASELINE);
        amountRow.setFlexGrow(1, amount);

        swapRow.addClassName("booking-swap");

        VerticalLayout content = new VerticalLayout(modeTabs, modeHint, errorBanner, amountRow,
                source, swapRow, target, note, preview);
        content.setPadding(false);
        content.setSpacing(false);
        content.addClassName("booking-form");
        add(content);

        Button cancel = new Button("Abbrechen", e -> close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        cancel.addClassName("booking-footer-button");
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClassName("booking-footer-button");
        saveButton.setId("booking-save");
        saveButton.addClickListener(e -> save());
        // Enter bzw. „Los" auf der Handytastatur bucht direkt aus den Textfeldern heraus.
        saveButton.addClickShortcut(Key.ENTER).listenOn(amount, note);
        getFooter().add(cancel, saveButton);

        addOpenedChangeListener(e -> {
            if (e.isOpened()) {
                amount.focus();
            }
        });

        modeTabs.setSelectedTab(initialMode == Mode.TRANSFER ? transferTab : depositTab);
        setMode(initialMode);
    }

    // ------------------------------------------------------------------ Aufbau

    private void configureModeTabs() {
        modeTabs.addThemeVariants(TabsVariant.LUMO_EQUAL_WIDTH_TABS);
        modeTabs.setWidthFull();
        depositTab.setId("tab-deposit");
        transferTab.setId("tab-transfer");
        modeTabs.addSelectedChangeListener(e -> setMode(e.getSelectedTab() == transferTab ? Mode.TRANSFER : Mode.DEPOSIT));
    }

    private void configureAmount() {
        amount.setId("booking-amount");
        amount.addClassName("amount-field");
        amount.setPlaceholder("0,00");
        amount.setSuffixComponent(new Span("€"));
        amount.setAllowedCharPattern("[\\d.,]");
        amount.setAutocomplete(Autocomplete.OFF);
        amount.setValueChangeMode(ValueChangeMode.EAGER);
        // Ziffernblock mit Dezimaltrenner auf Smartphones (wird an das <input> weitergereicht)
        amount.getElement().setProperty("inputMode", "decimal");
        amount.addValueChangeListener(e -> updateSummary());

        maxButton.setId("booking-max");
        maxButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        maxButton.setAriaLabel("Gesamten verfügbaren Betrag übernehmen");
        maxButton.addClickListener(e -> {
            Pot from = source.getValue();
            if (from != null) {
                amount.setValue(Money.formatPlain(from.available()));
            }
        });
    }

    private void configurePotSelects() {
        source.setId("booking-source");
        source.setLabel("Von");
        source.setWidthFull();
        source.setItems(pots);
        source.setItemLabelGenerator(Pot::name);
        source.setRenderer(potRenderer());
        source.setItemEnabledProvider(p -> p.available().signum() > 0);
        source.addValueChangeListener(e -> {
            // Wird die bisherige Zielauswahl zur Quelle, tauschen statt doppelt wählen.
            if (e.getValue() != null && e.getValue().equals(target.getValue())) {
                target.setValue(e.getOldValue());
            }
            refreshTargetItems();
            if (!amount.isEmpty()) {
                amountBinding.validate();
            }
            updateSummary();
        });

        target.setId("booking-target");
        target.setWidthFull();
        target.setItems(pots);
        target.setItemLabelGenerator(Pot::name);
        target.setRenderer(potRenderer());
        refreshTargetItems();
        target.addValueChangeListener(e -> updateSummary());

        swapButton.setId("booking-swap");
        swapButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        swapButton.setAriaLabel("Von und Nach tauschen");
        swapButton.addClickListener(e -> {
            Pot from = source.getValue();
            Pot to = target.getValue();
            source.setValue(to);
            target.setValue(from);
        });
    }

    /**
     * Sperrt in „Nach" die aktuell in „Von" gewählte Quelle. Bewusst nicht über
     * {@code refreshAll()} des Data-Views: das setzt beim Select den Wert zurück.
     */
    private void refreshTargetItems() {
        target.setItemEnabledProvider(p -> mode != Mode.TRANSFER || !p.equals(source.getValue()));
    }

    private static ComponentRenderer<Component, Pot> potRenderer() {
        return new ComponentRenderer<>(pot -> {
            Span name = new Span(pot.name());
            Span available = new Span(Money.format(pot.available()));
            available.addClassName("pot-amount");
            Div row = new Div(name, available);
            row.addClassName("pot-item");
            return row;
        });
    }

    private Binder.Binding<Draft, BigDecimal> bindFields() {
        Binder.Binding<Draft, BigDecimal> amountBind = binder.forField(amount)
                .asRequired("Bitte einen Betrag eingeben.")
                .withConverter(new AmountConverter())
                .withValidator(v -> v.signum() > 0, "Der Betrag muss größer als 0 sein.")
                .withValidator(v -> v.scale() <= 2, "Höchstens zwei Nachkommastellen – bitte ohne Tausenderpunkt eingeben.")
                .withValidator(v -> v.compareTo(BookingService.MAX_AMOUNT) < 0, "Der Betrag ist zu groß.")
                .withValidator((v, ctx) -> {
                    Pot from = source.getValue();
                    if (mode == Mode.TRANSFER && from != null && v.compareTo(from.available()) > 0) {
                        // Der verfügbare Betrag steht bereits im Hilfetext darunter.
                        return ValidationResult.error("Nicht genug Geld in „" + from.name() + "“.");
                    }
                    return ValidationResult.ok();
                })
                .bind(Draft::getAmount, Draft::setAmount);

        binder.forField(source)
                .withValidator(v -> mode != Mode.TRANSFER || v != null, "Bitte wählen, woher das Geld kommt.")
                .bind(Draft::getSource, Draft::setSource);

        binder.forField(target)
                .asRequired("Bitte ein Ziel wählen.")
                .withValidator(v -> mode != Mode.TRANSFER || !v.equals(source.getValue()),
                        "Von und Nach müssen sich unterscheiden.")
                .bind(Draft::getTarget, Draft::setTarget);

        binder.forField(note).bind(Draft::getNote, Draft::setNote);
        return amountBind;
    }

    // ------------------------------------------------------------------ Verhalten

    private void setMode(Mode newMode) {
        this.mode = newMode;
        boolean transfer = newMode == Mode.TRANSFER;
        hideError();

        source.setVisible(transfer);
        source.setRequiredIndicatorVisible(transfer);
        swapRow.setVisible(transfer);
        maxButton.setVisible(transfer);
        target.setLabel(transfer ? "Nach" : "Zuordnen zu");

        if (transfer) {
            if (source.getValue() == null || source.getValue().available().signum() == 0) {
                source.setValue(pots.stream().filter(p -> p.available().signum() > 0).findFirst().orElse(null));
            }
            if (target.getValue() == null || target.getValue().equals(source.getValue())) {
                target.setValue(pots.stream().filter(p -> !p.equals(source.getValue())).findFirst().orElse(null));
            }
        } else if (target.getValue() == null) {
            target.setValue(pots.getFirst()); // Puffer
        }
        refreshTargetItems();

        modeHint.removeClassName("booking-hint--warning");
        if (!transfer) {
            modeHint.setText("Neues Geld auf dem Sparkonto. Es landet im Puffer oder direkt in einem Sparziel.");
        } else if (pots.size() < 2) {
            modeHint.setText("Es gibt noch keine Sparziele. Lege zuerst ein Sparziel an, um umbuchen zu können.");
            modeHint.addClassName("booking-hint--warning");
        } else if (!transferPossible) {
            modeHint.setText("Es ist noch kein Geld vorhanden, das umgebucht werden könnte.");
            modeHint.addClassName("booking-hint--warning");
        } else {
            modeHint.setText("Geld zwischen Puffer und Sparzielen verschieben. Der Kontostand bleibt gleich.");
        }
        boolean formEnabled = !transfer || transferPossible;
        amount.setEnabled(formEnabled);
        source.setEnabled(formEnabled);
        target.setEnabled(formEnabled);
        note.setEnabled(formEnabled);
        swapButton.setEnabled(formEnabled);
        saveButton.setEnabled(formEnabled);

        if (!amount.isEmpty()) {
            amountBinding.validate();
        }
        updateSummary();
    }

    /** Aktualisiert Hilfetext, Vorschau und Beschriftung des Buchen-Buttons. */
    private void updateSummary() {
        BigDecimal value = Money.parse(amount.getValue());
        boolean valid = value != null && value.signum() > 0 && value.scale() <= 2;
        Pot from = source.getValue();
        Pot to = target.getValue();

        if (mode == Mode.TRANSFER && from != null) {
            amount.setHelperText("Verfügbar in „" + from.name() + "“: " + Money.format(from.available()));
        } else {
            amount.setHelperText(null);
        }
        maxButton.setEnabled(mode == Mode.TRANSFER && from != null && from.available().signum() > 0);

        String verb = mode == Mode.TRANSFER ? "umbuchen" : "einbuchen";
        saveButton.setText(valid ? Money.format(value) + " " + verb : capitalize(verb));

        preview.removeAll();
        if (!valid || to == null || mode == Mode.TRANSFER && (from == null || from.equals(to))) {
            preview.setVisible(false);
            return;
        }
        Span title = new Span("Vorschau");
        title.addClassName("booking-preview__title");
        preview.add(title);
        if (mode == Mode.DEPOSIT) {
            preview.add(previewRow("Kontostand", balance, balance.add(value)));
        } else {
            preview.add(previewRow(from.name(), from.available(), from.available().subtract(value)));
        }
        preview.add(previewRow(to.name(), to.available(), to.available().add(value)));
        preview.setVisible(true);
    }

    private static Component previewRow(String label, BigDecimal before, BigDecimal after) {
        Span name = new Span(label);
        name.addClassName("booking-preview__name");
        Span change = new Span(Money.format(before) + " → " + Money.format(after));
        change.addClassName("booking-preview__change");
        if (after.signum() < 0) {
            change.addClassName("booking-preview__change--negative");
        }
        Div row = new Div(name, change);
        row.addClassName("booking-preview__row");
        return row;
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
        BigDecimal value = draft.getAmount();
        String message;
        try {
            if (mode == Mode.DEPOSIT) {
                service.deposit(value, draft.getTarget().goalId(), draft.getNote());
                message = Money.format(value) + " eingebucht"
                        + (draft.getTarget().isBuffer() ? "" : " → " + draft.getTarget().name());
            } else {
                service.transfer(draft.getSource().goalId(), draft.getTarget().goalId(), value, draft.getNote());
                message = Money.format(value) + " von „" + draft.getSource().name() + "“ nach „"
                        + draft.getTarget().name() + "“ umgebucht";
            }
        } catch (OptimisticLockingFailureException ex) {
            showError("Die Daten wurden zwischenzeitlich geändert. Bitte den Dialog schließen und erneut öffnen.");
            return;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            showError(ex.getMessage());
            return;
        } catch (RuntimeException ex) {
            // Nicht fachlich erwartet (z. B. Datenbankfehler): protokollieren und sichtbar machen,
            // statt den Dialog kommentarlos offen stehen zu lassen.
            log.error("Buchung fehlgeschlagen", ex);
            showError("Die Buchung konnte wegen eines technischen Fehlers nicht gespeichert werden.");
            return;
        }
        close();
        Notification notification = Notification.show(message, 4000, Notification.Position.BOTTOM_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        onBooked.run();
    }

    private void showError(String text) {
        errorBanner.setText(text);
        errorBanner.setVisible(true);
    }

    private void hideError() {
        errorBanner.setVisible(false);
    }

    private static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    // ------------------------------------------------------------------ Hilfsklassen

    /** Text ↔ Betrag; akzeptiert Komma und Punkt als Dezimaltrenner. */
    private static final class AmountConverter implements Converter<String, BigDecimal> {
        @Override
        public Result<BigDecimal> convertToModel(String value, ValueContext context) {
            BigDecimal parsed = Money.parse(value);
            return parsed == null
                    ? Result.error("Bitte einen gültigen Betrag eingeben, z. B. 12,50.")
                    : Result.ok(parsed);
        }

        @Override
        public String convertToPresentation(BigDecimal value, ValueContext context) {
            return value == null ? "" : Money.formatPlain(value);
        }
    }

    /** Formularzustand für den Binder. */
    private static final class Draft {
        private BigDecimal amount;
        private Pot source;
        private Pot target;
        private String note;

        BigDecimal getAmount() { return amount; }
        void setAmount(BigDecimal amount) { this.amount = amount; }
        Pot getSource() { return source; }
        void setSource(Pot source) { this.source = source; }
        Pot getTarget() { return target; }
        void setTarget(Pot target) { this.target = target; }
        String getNote() { return note; }
        void setNote(String note) { this.note = note; }
    }
}
