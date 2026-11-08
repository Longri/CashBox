package com.cashbox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;

/**
 * Einstiegspunkt der Spring-Boot-Anwendung. Als {@link AppShellConfigurator}
 * konfiguriert die Klasse zugleich die Vaadin-Seitenhülle (Stylesheets).
 * Das Theme (Lumo oder Aura) ist pro Browser wählbar und wird deshalb nicht hier,
 * sondern von {@code ThemeBootstrapListener} eingebunden.
 * {@link EnableScheduling}: für nächtliche Aufräumjobs (z. B. abgelaufene Anmelde-Tokens).
 */
@SpringBootApplication
@EnableScheduling
@StyleSheet("styles.css")
public class Application implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
