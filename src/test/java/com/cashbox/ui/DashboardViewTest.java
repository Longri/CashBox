package com.cashbox.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.browserless.SpringSecurityContextHandler;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.TextField;

@SpringBootTest
class DashboardViewTest extends SpringBrowserlessTest {

    @BeforeEach
    void open() {
        // Angemeldeter Benutzer, sonst verweigert die Zugriffsprüfung den Zugriff auf die View
        SecurityContextHolder.getContext().setAuthentication(
                new SpringSecurityContextHandler().createCredentials("tester", "USER"));
        navigate(DashboardView.class);
    }

    @Test
    void noGreetingBeforeClick() {
        assertTrue($(Paragraph.class).all().isEmpty());
    }

    @Test
    void greetsAnonymousWhenNameIsEmpty() {
        test($(Button.class).withId("greet").single()).click();
        assertEquals("Willkommen bei CashBox", $(Paragraph.class).single().getText());
    }

    @Test
    void greetsByName() {
        test($(TextField.class).single()).setValue("Andre");
        test($(Button.class).withId("greet").single()).click();
        assertEquals("Willkommen bei CashBox, Andre", $(Paragraph.class).single().getText());
    }
}
