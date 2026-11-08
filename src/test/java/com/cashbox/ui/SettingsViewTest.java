package com.cashbox.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;

import com.cashbox.ui.theme.AccentColor;
import com.cashbox.ui.theme.ColorMode;
import com.cashbox.ui.theme.ThemeFamily;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.browserless.SpringSecurityContextHandler;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.sidenav.SideNavItem;

@SpringBootTest
@SuppressWarnings("unchecked")
class SettingsViewTest extends SpringBrowserlessTest {

    @BeforeEach
    void login() {
        SecurityContextHolder.getContext().setAuthentication(
                new SpringSecurityContextHandler().createCredentials("tester", "USER"));
    }

    @Test
    void offersAllAppearanceOptions() {
        navigate(SettingsView.class);

        RadioButtonGroup<ThemeFamily> family = $(RadioButtonGroup.class).withId("settings-theme").single();
        RadioButtonGroup<ColorMode> colorMode = $(RadioButtonGroup.class).withId("settings-color-mode").single();
        RadioButtonGroup<AccentColor> accent = $(RadioButtonGroup.class).withId("settings-accent").single();
        Checkbox compact = $(Checkbox.class).withId("settings-compact").single();

        assertEquals(List.of(ThemeFamily.LUMO, ThemeFamily.AURA), family.getListDataView().getItems().toList());
        assertEquals(List.of(ColorMode.LIGHT, ColorMode.DARK, ColorMode.SYSTEM),
                colorMode.getListDataView().getItems().toList());
        assertEquals(List.of(AccentColor.values()), accent.getListDataView().getItems().toList());
        // Ohne Cookie: Lumo in Blau, normale Größe, Farbschema wie im Betriebssystem
        assertEquals(ThemeFamily.LUMO, family.getValue());
        assertEquals(ColorMode.SYSTEM, colorMode.getValue());
        assertEquals(AccentColor.BLUE, accent.getValue());
        assertFalse(compact.getValue());
    }

    @Test
    void reachableFromNavigation() {
        navigate(DashboardView.class);

        assertTrue($(SideNavItem.class).all().stream().anyMatch(item -> "settings".equals(item.getPath())),
                "Navigation sollte auf die Einstellungen verlinken");
    }
}
