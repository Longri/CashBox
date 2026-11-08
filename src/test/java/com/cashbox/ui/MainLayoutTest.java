package com.cashbox.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.browserless.SpringSecurityContextHandler;
import com.vaadin.flow.component.html.Image;

@SpringBootTest
class MainLayoutTest extends SpringBrowserlessTest {

    @BeforeEach
    void login() {
        SecurityContextHolder.getContext().setAuthentication(
                new SpringSecurityContextHandler().createCredentials("tester", "USER"));
    }

    @Test
    void navbarShowsLogoOnEveryPage() {
        navigate(SettingsView.class);

        Image logo = $(Image.class, $(MainLayout.class).single()).single();
        assertEquals(AppLogo.SRC, logo.getSrc());
        assertEquals("", logo.getAlt().orElse(null), "Logo steht neben dem Namen, kein doppelter Alternativtext");
    }
}
