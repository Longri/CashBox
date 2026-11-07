package com.cashbox.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.login.LoginForm;

@SpringBootTest
class LoginViewTest extends SpringBrowserlessTest {

    @Test
    void anonymousSeesLoginForm() {
        navigate(LoginView.class);
        assertEquals("login", $(LoginForm.class).single().getAction());
    }

    @Test
    void anonymousCannotOpenDashboard() {
        assertThrows(Exception.class, () -> navigate(DashboardView.class));
    }
}
