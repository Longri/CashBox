package com.cashbox.ui.theme;

import org.springframework.stereotype.Component;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;

/**
 * Setzt beim Ausliefern jeder Startseite das im Browser gewählte Theme (Lumo/Aura) und Farbschema.
 * Deshalb trägt {@link com.cashbox.Application} kein festes {@code @StyleSheet} für ein Theme.
 */
@Component
class ThemeBootstrapListener implements VaadinServiceInitListener {

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.addIndexHtmlRequestListener(response ->
                ThemeSettings.of(response.getVaadinRequest()).applyTo(response.getDocument()));
    }
}
