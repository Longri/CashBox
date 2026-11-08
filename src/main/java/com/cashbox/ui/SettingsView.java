package com.cashbox.ui;

import com.cashbox.ui.theme.ColorMode;
import com.cashbox.ui.theme.ThemeFamily;
import com.cashbox.ui.theme.ThemeSettings;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.page.Page;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.radiobutton.RadioGroupVariant;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinRequest;

import jakarta.annotation.security.PermitAll;

/**
 * Einstellungen für diesen Browser: Theme (Lumo oder Aura) und Farbschema (hell, dunkel, automatisch).
 * Das Farbschema wechselt sofort; ein anderes Theme lädt die Seite neu, weil die Stylesheets getauscht werden.
 */
@Route("settings")
@PageTitle("Einstellungen | CashBox")
@PermitAll
public class SettingsView extends VerticalLayout {

    private final RadioButtonGroup<ThemeFamily> family = new RadioButtonGroup<>("Theme");
    private final RadioButtonGroup<ColorMode> colorMode = new RadioButtonGroup<>("Farbschema");
    private ThemeSettings settings = ThemeSettings.current();

    public SettingsView() {
        addClassName("page-content");
        addClassName("settings-view");

        H2 title = new H2("Einstellungen");
        title.addClassName("page-title");

        H3 appearance = new H3("Darstellung");
        appearance.addClassName("settings-section__title");

        family.setId("settings-theme");
        family.setItems(ThemeFamily.values());
        family.setRenderer(new ComponentRenderer<>(SettingsView::familyOption));
        // Lumo ordnet Optionen standardmäßig nebeneinander an, Aura untereinander: hier überall untereinander
        family.addThemeVariants(RadioGroupVariant.LUMO_VERTICAL);
        family.setValue(settings.family());
        family.addValueChangeListener(e -> {
            if (e.isFromClient() && e.getValue() != null) {
                settings = settings.withFamily(e.getValue());
                Page page = getUI().orElseThrow().getPage();
                settings.storeIn(page, contextPath());
                page.reload();
            }
        });

        colorMode.setId("settings-color-mode");
        colorMode.setItems(ColorMode.values());
        colorMode.setRenderer(new ComponentRenderer<>(SettingsView::colorModeOption));
        colorMode.addThemeVariants(RadioGroupVariant.AURA_HORIZONTAL);
        colorMode.setHelperText("„Automatisch“ folgt der Einstellung des Geräts.");
        colorMode.setValue(settings.colorMode());
        colorMode.addValueChangeListener(e -> {
            if (e.isFromClient() && e.getValue() != null) {
                settings = settings.withColorMode(e.getValue());
                Page page = getUI().orElseThrow().getPage();
                settings.storeIn(page, contextPath());
                page.setColorScheme(settings.colorMode().colorScheme());
            }
        });

        Paragraph hint = new Paragraph("Gilt nur für diesen Browser. Beim Wechsel des Themes wird die Seite neu geladen.");
        hint.addClassName("form-hint");

        Div section = new Div(appearance, family, colorMode, hint);
        section.addClassName("settings-section");

        add(title, section);
    }

    private static Component familyOption(ThemeFamily option) {
        Span name = new Span(option.label());
        name.addClassName("settings-option__name");
        Span description = new Span(option.description());
        description.addClassName("settings-option__description");
        Div content = new Div(name, description);
        content.addClassName("settings-option");
        return content;
    }

    private static Component colorModeOption(ColorMode option) {
        Icon icon = option.icon().create();
        icon.addClassName("settings-option__icon");
        Span content = new Span(icon, new Span(option.label()));
        content.addClassName("settings-option--inline");
        return content;
    }

    private static String contextPath() {
        VaadinRequest request = VaadinRequest.getCurrent();
        return request == null ? "" : request.getContextPath();
    }
}
