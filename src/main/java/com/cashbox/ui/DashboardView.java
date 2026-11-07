package com.cashbox.ui;

import com.cashbox.service.GreetingService;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.PermitAll;

/**
 * Startseite. Jede Browser-Instanz erhält eine eigene View-Instanz; Spring-Beans
 * werden per Konstruktor injiziert.
 */
@Route("")
@PageTitle("Übersicht | CashBox")
@PermitAll
public class DashboardView extends VerticalLayout {

    public DashboardView(GreetingService greetingService) {
        addClassName("page-content");

        TextField name = new TextField("Ihr Name");
        name.setId("name");

        Button greet = new Button("Begrüßen", event ->
                add(new Paragraph(greetingService.greet(name.getValue()))));
        greet.setId("greet");
        greet.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        greet.addClickShortcut(Key.ENTER);

        add(name, greet);
    }
}
