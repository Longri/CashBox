package com.cashbox.ui;

import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;

/**
 * Statusleiste der Web-App auf dem iPhone-Home-Bildschirm. Für {@code @PWA} setzt Vaadin
 * {@code black-translucent}: weiße Schrift, die App reicht unter die Statusleiste. Auf der hellen
 * Kopfleiste wären Uhrzeit und Akku dann unsichtbar. {@code default} zeigt die Statusleiste
 * lesbar über der App.
 */
@Component
class StatusBarBootstrapListener implements VaadinServiceInitListener {

    static final String STATUS_BAR_STYLE = "apple-mobile-web-app-status-bar-style";
    static final String READABLE = "default";

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.addIndexHtmlRequestListener(response -> useReadableStatusBar(response.getDocument()));
    }

    static void useReadableStatusBar(Document document) {
        document.head().select("meta[name=" + STATUS_BAR_STYLE + "]").attr("content", READABLE);
    }
}
