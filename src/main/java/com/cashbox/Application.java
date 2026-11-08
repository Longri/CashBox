package com.cashbox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.server.PWA;

/**
 * Einstiegspunkt der Spring-Boot-Anwendung. Als {@link AppShellConfigurator}
 * konfiguriert die Klasse zugleich die Vaadin-Seitenhülle (Stylesheets, Web-App).
 * Das Theme (Lumo oder Aura) ist pro Browser wählbar und wird deshalb nicht hier,
 * sondern von {@code ThemeBootstrapListener} eingebunden.
 * {@link PWA}: Manifest, Home-Bildschirm-Icons (auch {@code apple-touch-icon}) und Startbilder
 * erzeugt Vaadin aus {@code META-INF/resources/icons/icon.png}: das Logo
 * {@code META-INF/resources/cashboxIcon.svg} als PNG, 512×512 mit durchsichtigem Hintergrund.
 * Gewollt, obwohl iOS durchsichtige Stellen auf dem Home-Bildschirm schwarz füllt. Das Pixel
 * oben links ist zugleich die Hintergrundfarbe der Startbilder, hier also durchsichtig.
 * {@link EnableScheduling}: für nächtliche Aufräumjobs (z. B. abgelaufene Anmelde-Tokens).
 */
@SpringBootApplication
@EnableScheduling
@StyleSheet("styles.css")
@PWA(name = "CashBox", shortName = "CashBox",
        description = "Guthaben eines Sparkontos auf virtuelle Spartöpfe verteilen",
        themeColor = "#ffffff", backgroundColor = "#ffffff")
public class Application implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
