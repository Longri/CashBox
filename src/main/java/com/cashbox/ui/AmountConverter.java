package com.cashbox.ui;

import java.math.BigDecimal;

import com.cashbox.service.BookingService;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.Autocomplete;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.Result;
import com.vaadin.flow.data.binder.ValueContext;
import com.vaadin.flow.data.converter.Converter;

/**
 * Text ↔ Betrag; akzeptiert Komma und Punkt als Dezimaltrenner. Dazu die gemeinsame
 * Einrichtung und Prüfung von Betragsfeldern (Buchungs- und Sparziel-Dialog).
 */
final class AmountConverter implements Converter<String, BigDecimal> {

    /** Richtet ein Textfeld als Betragsfeld ein: Ziffernblock auf dem Handy, €-Suffix, große Schrift. */
    static void configureField(TextField field) {
        field.addClassName("amount-field");
        field.setPlaceholder("0,00");
        field.setSuffixComponent(new Span("€"));
        field.setAllowedCharPattern("[\\d.,]");
        field.setAutocomplete(Autocomplete.OFF);
        // Ziffernblock mit Dezimaltrenner auf Smartphones (wird an das <input> weitergereicht)
        field.getElement().setProperty("inputMode", "decimal");
    }

    /**
     * Bindet ein Betragsfeld mit den Standardprüfungen (Pflicht, größer 0,
     * höchstens zwei Nachkommastellen, Obergrenze). Weitere Prüfungen können angehängt werden.
     */
    static <B> Binder.BindingBuilder<B, BigDecimal> bind(Binder<B> binder, TextField field, String requiredMessage) {
        return binder.forField(field)
                .asRequired(requiredMessage)
                .withConverter(new AmountConverter())
                .withValidator(v -> v.signum() > 0, "Der Betrag muss größer als 0 sein.")
                .withValidator(v -> v.scale() <= 2, "Höchstens zwei Nachkommastellen – bitte ohne Tausenderpunkt eingeben.")
                .withValidator(v -> v.compareTo(BookingService.MAX_AMOUNT) < 0, "Der Betrag ist zu groß.");
    }

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
