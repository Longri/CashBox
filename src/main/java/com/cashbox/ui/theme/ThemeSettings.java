package com.cashbox.ui.theme;

import java.util.Objects;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.vaadin.flow.component.page.Page;
import com.vaadin.flow.server.VaadinRequest;

import jakarta.servlet.http.Cookie;

/**
 * Gewähltes Aussehen: Theme, Farbschema, Akzentfarbe und kompakte Ansicht. Gespeichert als Cookies
 * im Browser, nicht beim Benutzer: Der Admin-Account steht nicht in der DB, und so passt schon die Anmeldeseite.
 */
public record ThemeSettings(ThemeFamily family, ColorMode colorMode, AccentColor accent, boolean compact) {

    /** Ohne gespeicherte Auswahl: Lumo in Blau, hell oder dunkel wie im Betriebssystem eingestellt. */
    public static final ThemeSettings DEFAULT =
            new ThemeSettings(ThemeFamily.LUMO, ColorMode.SYSTEM, AccentColor.BLUE, false);

    static final String FAMILY_COOKIE = "cashbox-theme";
    static final String COLOR_MODE_COOKIE = "cashbox-color-mode";
    static final String ACCENT_COOKIE = "cashbox-accent";
    static final String DENSITY_COOKIE = "cashbox-density";
    private static final String COMPACT = "compact";
    private static final String NORMAL = "normal";
    private static final int COOKIE_MAX_AGE_SECONDS = 365 * 24 * 60 * 60;

    /** Kennzeichnet die Stylesheets des Themes; dahinter hängt {@link #showIn} die kompakte Ansicht. */
    private static final String LINK_MARKER = "data-cashbox-theme";
    private static final String COMPACT_LINK_ID = "cashbox-compact";

    public ThemeSettings {
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(colorMode, "colorMode");
        Objects.requireNonNull(accent, "accent");
    }

    /** Auswahl aus den Cookies der Anfrage; fehlende oder unbekannte Werte ergeben die Vorgabe. */
    public static ThemeSettings of(VaadinRequest request) {
        Cookie[] cookies = request == null ? null : request.getCookies();
        if (cookies == null) {
            return DEFAULT;
        }
        ThemeFamily family = DEFAULT.family;
        ColorMode colorMode = DEFAULT.colorMode;
        AccentColor accent = DEFAULT.accent;
        boolean compact = DEFAULT.compact;
        for (Cookie cookie : cookies) {
            String value = cookie.getValue();
            switch (cookie.getName()) {
                case FAMILY_COOKIE -> family = ThemeFamily.fromKey(value, family);
                case COLOR_MODE_COOKIE -> colorMode = ColorMode.fromKey(value, colorMode);
                case ACCENT_COOKIE -> accent = AccentColor.fromKey(value, accent);
                case DENSITY_COOKIE -> {
                    if (COMPACT.equals(value)) {
                        compact = true;
                    } else if (NORMAL.equals(value)) {
                        compact = false;
                    }
                }
                default -> { }
            }
        }
        return new ThemeSettings(family, colorMode, accent, compact);
    }

    /** Auswahl des Browsers, der die laufende Anfrage stellt. */
    public static ThemeSettings current() {
        return of(VaadinRequest.getCurrent());
    }

    public ThemeSettings withFamily(ThemeFamily family) {
        return new ThemeSettings(family, colorMode, accent, compact);
    }

    public ThemeSettings withColorMode(ColorMode colorMode) {
        return new ThemeSettings(family, colorMode, accent, compact);
    }

    public ThemeSettings withAccent(AccentColor accent) {
        return new ThemeSettings(family, colorMode, accent, compact);
    }

    public ThemeSettings withCompact(boolean compact) {
        return new ThemeSettings(family, colorMode, accent, compact);
    }

    /**
     * Schreibt die Auswahl in die Startseite, bevor der Browser sie sieht.
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
        html.attr("data-accent", accent.key());

        // Vor dem ersten Stylesheet (styles.css), damit die eigenen Styles das Theme überschreiben,
        // und nach <base>, damit die relativen Pfade auch auf Unterseiten stimmen.
        Element head = document.head();
        Element firstStyleSheet = head.selectFirst("link[rel=stylesheet]");
        for (String href : family.styleSheets()) {
            insertBefore(firstStyleSheet, head, themeLink(href));
        }
        if (compact) {
            insertBefore(firstStyleSheet, head, themeLink(family.compactStyleSheet()).id(COMPACT_LINK_ID));
        }
    }

    private static Element themeLink(String href) {
        return new Element("link").attr("rel", "stylesheet").attr("href", href).attr(LINK_MARKER, "");
    }

    private static void insertBefore(Element anchor, Element head, Element element) {
        if (anchor != null) {
            anchor.before(element);
        } else {
            head.appendChild(element);
        }
    }

    /** Speichert die Auswahl im Browser, ein Jahr lang. */
    public void storeIn(Page page, String contextPath) {
        String path = contextPath == null || contextPath.isEmpty() ? "/" : contextPath;
        String attributes = "; Path=" + path + "; Max-Age=" + COOKIE_MAX_AGE_SECONDS + "; SameSite=Lax";
        String cookies = String.join("\n",
                FAMILY_COOKIE + "=" + family.key() + attributes,
                COLOR_MODE_COOKIE + "=" + colorMode.key() + attributes,
                ACCENT_COOKIE + "=" + accent.key() + attributes,
                DENSITY_COOKIE + "=" + (compact ? COMPACT : NORMAL) + attributes);
        page.executeJs("""
                const secure = location.protocol === 'https:' ? '; Secure' : '';
                for (const cookie of $0.split('\\n')) {
                    document.cookie = cookie + secure;
                }
                """, cookies);
    }

    /**
     * Zeigt Farbschema, Akzentfarbe und kompakte Ansicht sofort an. Ein anderes Theme bringt
     * dagegen eigene Komponenten-Styles mit und braucht {@link Page#reload()}.
     */
    public void showIn(Page page) {
        page.setColorScheme(colorMode.colorScheme());
        page.executeJs("""
                document.documentElement.setAttribute('data-accent', $0);
                const compactLink = document.getElementById($1);
                if (!$2) {
                    compactLink?.remove();
                } else if (!compactLink) {
                    const link = document.createElement('link');
                    link.id = $1;
                    link.rel = 'stylesheet';
                    link.href = $2;
                    link.setAttribute($3, '');
                    const themeLinks = document.head.querySelectorAll('link[' + $3 + ']');
                    if (themeLinks.length > 0) {
                        themeLinks[themeLinks.length - 1].after(link);
                    } else {
                        document.head.append(link);
                    }
                }
                """, accent.key(), COMPACT_LINK_ID, compact ? family.compactStyleSheet() : "", LINK_MARKER);
    }
}
