package com.cashbox.ui;

import com.vaadin.flow.component.html.Image;

/**
 * Logo von CashBox: auf der Anmeldeseite und in der Kopfleiste jeder Seite. Die SVG hat einen
 * durchsichtigen Hintergrund und passt so zu hellem und dunklem Farbschema. Sie steht immer neben
 * dem Namen „CashBox“, deshalb ist der Alternativtext leer (Screenreader lesen den Namen nicht doppelt).
 */
final class AppLogo {

    /** In {@code META-INF/resources}; ohne Anmeldung erreichbar (SecurityConfig). */
    static final String SRC = "cashboxIcon.svg";

    private AppLogo() {
    }

    static Image create(String className) {
        Image logo = new Image(SRC, "");
        logo.addClassName(className);
        return logo;
    }
}
