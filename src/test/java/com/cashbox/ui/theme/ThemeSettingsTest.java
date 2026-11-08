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
        assertEquals(new ThemeSettings(ThemeFamily.LUMO, ColorMode.SYSTEM), ThemeSettings.DEFAULT);
    }

    @Test
    void readsBothCookies() {
        ThemeSettings settings = ThemeSettings.of(request(
                new Cookie("other", "x"),
                new Cookie("cashbox-theme", "aura"),
                new Cookie("cashbox-color-mode", "system")));

        assertEquals(new ThemeSettings(ThemeFamily.AURA, ColorMode.SYSTEM), settings);
    }

    @Test
    void unknownValuesFallBackToDefault() {
        assertEquals(new ThemeSettings(ThemeFamily.LUMO, ColorMode.DARK), ThemeSettings.of(request(
                new Cookie("cashbox-theme", "material"),
                new Cookie("cashbox-color-mode", "dark"))));
        assertEquals(new ThemeSettings(ThemeFamily.AURA, ColorMode.SYSTEM), ThemeSettings.of(request(
                new Cookie("cashbox-theme", "aura"),
                new Cookie("cashbox-color-mode", "sepia"))));
    }

    @Test
    void bootstrapPageGetsThemeBeforeAppStylesAndAfterBase() {
        Document document = Jsoup.parse("""
                <html><head>
                  <base href="./">
                  <link rel="stylesheet" href="./styles.css">
                </head><body></body></html>""");

        new ThemeSettings(ThemeFamily.AURA, ColorMode.DARK).applyTo(document);

        List<String> order = document.head().children().stream()
                .map(e -> e.is("base") ? "base" : e.attr("href"))
                .toList();
        assertEquals(List.of("base", "aura/aura.css", "aura-compat.css", "./styles.css"), order);
        Element html = document.head().parent();
        assertEquals("dark", html.attr("theme"));
        assertEquals("color-scheme: dark;", html.attr("style"));
    }

    @Test
    void automaticColorModeLetsTheBrowserDecide() {
        Document document = Jsoup.parse("<html style=\"height: 100%\"><head></head><body></body></html>");

        new ThemeSettings(ThemeFamily.LUMO, ColorMode.SYSTEM).applyTo(document);

        Element html = document.head().parent();
        assertEquals("light-dark", html.attr("theme"));
        assertEquals("height: 100%; color-scheme: light dark;", html.attr("style"));
        assertEquals("lumo/lumo.css", document.head().selectFirst("link[rel=stylesheet]").attr("href"));
    }

    private static VaadinRequest request(Cookie... cookies) {
        VaadinRequest request = mock(VaadinRequest.class);
        when(request.getCookies()).thenReturn(cookies.length == 0 ? null : cookies);
        return request;
    }
}
