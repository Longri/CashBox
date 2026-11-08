package com.cashbox.ui.theme;

import java.util.Objects;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.vaadin.flow.component.page.Page;
import com.vaadin.flow.server.VaadinRequest;

import jakarta.servlet.http.Cookie;

/**
 * Gewähltes Theme und Farbschema. Gespeichert als Cookies im Browser, nicht beim Benutzer:
 * Der Admin-Account steht nicht in der DB, und so passt schon die Anmeldeseite.
 */
public record ThemeSettings(ThemeFamily family, ColorMode colorMode) {

    /** Ohne gespeicherte Auswahl: Lumo, hell oder dunkel wie im Betriebssystem eingestellt. */
    public static final ThemeSettings DEFAULT = new ThemeSettings(ThemeFamily.LUMO, ColorMode.SYSTEM);

    static final String FAMILY_COOKIE = "cashbox-theme";
    static final String COLOR_MODE_COOKIE = "cashbox-color-mode";
    private static final int COOKIE_MAX_AGE_SECONDS = 365 * 24 * 60 * 60;

    public ThemeSettings {
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(colorMode, "colorMode");
    }

    /** Auswahl aus den Cookies der Anfrage; fehlende oder unbekannte Werte ergeben die Vorgabe. */
    public static ThemeSettings of(VaadinRequest request) {
        Cookie[] cookies = request == null ? null : request.getCookies();
        if (cookies == null) {
            return DEFAULT;
        }
        ThemeFamily family = DEFAULT.family;
        ColorMode colorMode = DEFAULT.colorMode;
        for (Cookie cookie : cookies) {
            switch (cookie.getName()) {
                case FAMILY_COOKIE -> family = ThemeFamily.fromKey(cookie.getValue(), family);
                case COLOR_MODE_COOKIE -> colorMode = ColorMode.fromKey(cookie.getValue(), colorMode);
                default -> { }
            }
        }
        return new ThemeSettings(family, colorMode);
    }

    /** Auswahl des Browsers, der die laufende Anfrage stellt. */
    public static ThemeSettings current() {
        return of(VaadinRequest.getCurrent());
    }

    public ThemeSettings withFamily(ThemeFamily family) {
        return new ThemeSettings(family, colorMode);
    }

    public ThemeSettings withColorMode(ColorMode colorMode) {
        return new ThemeSettings(family, colorMode);
    }

    /**
     * Schreibt Farbschema und Theme-Stylesheets in die Startseite, bevor der Browser sie sieht.
     * So erscheint die Seite gleich richtig, ohne kurz im falschen Theme aufzublitzen.
     */
    void applyTo(Document document) {
        Element html = document.head().parent();
        html.attr("theme", colorMode.colorScheme().getThemeValue());
        String style = html.attr("style").trim();
        if (!style.isEmpty()) {
            style += style.endsWith(";") ? " " : "; ";
        }
        html.attr("style", style + "color-scheme: " + colorMode.colorScheme().getValue() + ";");

        // Vor dem ersten Stylesheet (styles.css), damit die eigenen Styles das Theme überschreiben,
        // und nach <base>, damit die relativen Pfade auch auf Unterseiten stimmen.
        Element head = document.head();
        Element firstStyleSheet = head.selectFirst("link[rel=stylesheet]");
        for (String href : family.styleSheets()) {
            Element link = new Element("link").attr("rel", "stylesheet").attr("href", href);
            if (firstStyleSheet != null) {
                firstStyleSheet.before(link);
            } else {
                head.appendChild(link);
            }
        }
    }

    /**
     * Speichert die Auswahl im Browser. Das Farbschema stellt {@link Page#setColorScheme} sofort um,
     * ein anderes Theme braucht neue Stylesheets und damit {@link Page#reload()}.
     */
    public void storeIn(Page page, String contextPath) {
        String path = contextPath == null || contextPath.isEmpty() ? "/" : contextPath;
        String attributes = "; Path=" + path + "; Max-Age=" + COOKIE_MAX_AGE_SECONDS + "; SameSite=Lax";
        page.executeJs("""
                const secure = location.protocol === 'https:' ? '; Secure' : '';
                document.cookie = $0 + secure;
                document.cookie = $1 + secure;
                """,
                FAMILY_COOKIE + "=" + family.key() + attributes,
                COLOR_MODE_COOKIE + "=" + colorMode.key() + attributes);
    }
}
