package com.cashbox.ui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import com.vaadin.flow.server.VaadinRequest;

import jakarta.servlet.http.Cookie;

class ThemeSettingsTest {

    @Test
    void withoutCookiesUsesDefault() {
        assertEquals(ThemeSettings.DEFAULT, ThemeSettings.of(request()));
        assertEquals(ThemeSettings.DEFAULT, ThemeSettings.of(null));
        assertEquals(new ThemeSettings(ThemeFamily.LUMO, ColorMode.SYSTEM, AccentColor.BLUE, false),
                ThemeSettings.DEFAULT);
    }

    @Test
    void readsAllCookies() {
        ThemeSettings settings = ThemeSettings.of(request(
                new Cookie("other", "x"),
                new Cookie("cashbox-theme", "aura"),
                new Cookie("cashbox-color-mode", "dark"),
                new Cookie("cashbox-accent", "green"),
                new Cookie("cashbox-density", "compact")));

        assertEquals(new ThemeSettings(ThemeFamily.AURA, ColorMode.DARK, AccentColor.GREEN, true), settings);
    }

    @Test
    void unknownValuesFallBackToDefault() {
        assertEquals(ThemeSettings.DEFAULT, ThemeSettings.of(request(
                new Cookie("cashbox-theme", "material"),
                new Cookie("cashbox-color-mode", "sepia"),
                new Cookie("cashbox-accent", "<script>"),
                new Cookie("cashbox-density", "tiny"))));
        assertEquals(new ThemeSettings(ThemeFamily.LUMO, ColorMode.DARK, AccentColor.BLUE, false),
                ThemeSettings.of(request(
                        new Cookie("cashbox-theme", "material"),
                        new Cookie("cashbox-color-mode", "dark"),
                        new Cookie("cashbox-density", "normal"))));
    }

    @Test
    void bootstrapPageGetsThemeBeforeAppStylesAndAfterBase() {
        Document document = Jsoup.parse("""
                <html><head>
                  <base href="./">
                  <link rel="stylesheet" href="./styles.css">
                </head><body></body></html>""");

        new ThemeSettings(ThemeFamily.AURA, ColorMode.DARK, AccentColor.PURPLE, false).applyTo(document);

        assertEquals(List.of("base", "aura/aura.css", "theme-aura.css", "./styles.css"), headOrder(document));
        Element html = document.head().parent();
        assertEquals("dark", html.attr("theme"));
        assertEquals("color-scheme: dark;", html.attr("style"));
        assertEquals("purple", html.attr("data-accent"));
    }

    @Test
    void compactLoadsThePresetOfTheTheme() {
        Document lumo = Jsoup.parse("<html><head><link rel=\"stylesheet\" href=\"./styles.css\"></head></html>");
        new ThemeSettings(ThemeFamily.LUMO, ColorMode.LIGHT, AccentColor.BLUE, true).applyTo(lumo);
        assertEquals(List.of("lumo/lumo.css", "theme-lumo.css", "lumo/presets/compact.css", "./styles.css"),
                headOrder(lumo));
        // Die Einstellungsseite findet den Link über seine ID, um ihn live zu entfernen
        assertEquals("lumo/presets/compact.css", lumo.getElementById("cashbox-compact").attr("href"));

        Document aura = Jsoup.parse("<html><head></head></html>");
        new ThemeSettings(ThemeFamily.AURA, ColorMode.LIGHT, AccentColor.BLUE, true).applyTo(aura);
        assertEquals(List.of("aura/aura.css", "theme-aura.css", "theme-aura-compact.css"), headOrder(aura));
    }

    @Test
    void automaticColorModeLetsTheBrowserDecide() {
        Document document = Jsoup.parse("<html style=\"height: 100%\"><head></head><body></body></html>");

        new ThemeSettings(ThemeFamily.LUMO, ColorMode.SYSTEM, AccentColor.BLUE, false).applyTo(document);

        Element html = document.head().parent();
        assertEquals("light-dark", html.attr("theme"));
        assertEquals("height: 100%; color-scheme: light dark;", html.attr("style"));
        assertEquals(List.of("lumo/lumo.css", "theme-lumo.css"), headOrder(document));
    }

    private static List<String> headOrder(Document document) {
        return document.head().children().stream()
                .map(e -> e.is("base") ? "base" : e.attr("href"))
                .toList();
    }

    private static VaadinRequest request(Cookie... cookies) {
        VaadinRequest request = mock(VaadinRequest.class);
        when(request.getCookies()).thenReturn(cookies.length == 0 ? null : cookies);
        return request;
    }
}
