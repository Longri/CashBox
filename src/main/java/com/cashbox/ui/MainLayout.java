package com.cashbox.ui;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.Layout;

import jakarta.annotation.security.PermitAll;
import com.vaadin.flow.component.applayout.DrawerToggle;

/**
 * Hauptlayout mit Kopfzeile und Navigation. Gilt für alle Views ohne eigenes Layout.
 */
@Layout
@PermitAll
public class MainLayout extends AppLayout {

    public MainLayout(AuthenticationContext auth) {
        H1 title = new H1("CashBox");
        title.getStyle().set("font-size", "var(--lumo-font-size-l)").set("margin", "0");
        Button logout = new Button("Abmelden (" + auth.getPrincipalName().orElse("?") + ")", e -> auth.logout());
        logout.getStyle().set("margin-left", "auto").set("margin-right", "var(--lumo-space-m)");
        addToNavbar(new DrawerToggle(), title, logout);

        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem("Übersicht", DashboardView.class, VaadinIcon.DASHBOARD.create()));
        if (auth.hasRole("ADMIN")) {
            nav.addItem(new SideNavItem("Benutzer", UsersView.class, VaadinIcon.USERS.create()));
        }
        addToDrawer(nav);
    }
}
