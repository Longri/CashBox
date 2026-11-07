package com.cashbox.ui;

import com.cashbox.model.AppUser;
import com.cashbox.service.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;

/**
 * Benutzerverwaltung (nur Admin): Benutzer in der Datenbank anlegen und löschen.
 */
@Route(value = "users", layout = MainLayout.class)
@PageTitle("Benutzer | CashBox")
@RolesAllowed("ADMIN")
public class UsersView extends VerticalLayout {

    private final UserService service;
    private final Grid<AppUser> grid = new Grid<>(AppUser.class, false);
    private final TextField username = new TextField("Benutzername");
    private final PasswordField password = new PasswordField("Passwort (min. 8 Zeichen)");

    public UsersView(UserService service) {
        this.service = service;
        addClassName("page-content");

        grid.addColumn(AppUser::getUsername).setHeader("Benutzername");
        grid.addColumn(AppUser::getCreatedAt).setHeader("Angelegt am");
        grid.addComponentColumn(user -> {
            Button delete = new Button("Löschen", e -> {
                service.delete(user);
                refresh();
            });
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            return delete;
        });

        Button add = new Button("Anlegen", e -> create());
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        HorizontalLayout form = new HorizontalLayout(username, password, add);
        form.setDefaultVerticalComponentAlignment(Alignment.BASELINE);

        add(new H2("Benutzer"), form, grid);
        refresh();
    }

    private void create() {
        try {
            service.create(username.getValue(), password.getValue());
            username.clear();
            password.clear();
            refresh();
        } catch (IllegalArgumentException ex) {
            Notification.show(ex.getMessage());
        }
    }

    private void refresh() {
        grid.setItems(service.findAll());
    }
}
