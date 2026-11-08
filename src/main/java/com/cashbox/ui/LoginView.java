package com.cashbox.ui;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Anmeldedialog. Das Formular wird per POST an /login (Spring Security) gesendet.
 * {@code autoLayout = false}: ohne {@link MainLayout} (keine Navigation/Abmelden vor dem Login).
 */
@Route(value = "login", autoLayout = false)
@PageTitle("Anmelden | CashBox")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final LoginForm login = new LoginForm();

    public LoginView() {
        setSizeFull();
        setAlignItems(FlexComponent.Alignment.CENTER);
        setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        LoginI18n i18n = LoginI18n.createDefault();
        i18n.getForm().setTitle("Anmelden");
        i18n.getForm().setUsername("Benutzername");
        i18n.getForm().setPassword("Passwort");
        i18n.getForm().setSubmit("Anmelden");
        i18n.getForm().setForgotPassword(null);
        i18n.getErrorMessage().setTitle("Anmeldung fehlgeschlagen");
        i18n.getErrorMessage().setMessage("Benutzername oder Passwort ist falsch.");
        login.setI18n(i18n);
        login.setAction("login");
        login.setForgotPasswordButtonVisible(false);

        H1 title = new H1("CashBox");
        title.addClassName("login-title");
        add(AppLogo.create("login-logo"), title, login);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        login.setError(event.getLocation().getQueryParameters().getParameters().containsKey("error"));
    }
}
