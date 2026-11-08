package com.cashbox.security;

import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import com.cashbox.ui.LoginView;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({ AdminProperties.class, RememberMeProperties.class })
public class SecurityConfig {

    /** Eigener Name statt "remember-me": Cookies gelten pro Host, nicht pro Port (localhost). */
    public static final String REMEMBER_ME_COOKIE = "cashbox-remember-me";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, RememberMeProperties rememberMe,
            AppUserDetailsService userDetailsService, PersistentTokenRepository tokenRepository) throws Exception {
        // Eigene statische Dateien aus META-INF/resources. styles.css, Lumo und Aura gibt Vaadin selbst frei;
        // die theme-*.css (siehe ThemeFamily) braucht schon die Anmeldeseite.
        // Muss vor der Vaadin-Konfiguration stehen, die alle übrigen Anfragen absichert.
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/goal-sort.js", "/image-cropper.js", "/theme-*.css").permitAll());
        if (rememberMe.isEnabled()) {
            PersistentTokenBasedRememberMeServices rememberMeServices =
                    rememberMeServices(rememberMe, userDetailsService, tokenRepository);
            // Setzt das Cookie nach jedem erfolgreichen Login und meldet beim Aufruf ohne
            // Sitzung automatisch wieder an. Abmelden löscht Cookie und Tokens des Benutzers.
            http.rememberMe(rm -> rm
                    .rememberMeServices(rememberMeServices)
                    .key(rememberMeServices.getKey()));
        }
        http.with(VaadinSecurityConfigurer.vaadin(), vaadin -> vaadin.loginView(LoginView.class));
        return http.build();
    }

    /** Tokens für "Angemeldet bleiben" in der Tabelle persistent_logins. */
    @Bean
    PersistentTokenRepository rememberMeTokenRepository(DataSource dataSource) {
        JdbcTokenRepositoryImpl repository = new JdbcTokenRepositoryImpl();
        repository.setDataSource(dataSource);
        return repository;
    }

    private static PersistentTokenBasedRememberMeServices rememberMeServices(RememberMeProperties rememberMe,
            AppUserDetailsService userDetailsService, PersistentTokenRepository tokenRepository) {
        // Der Schlüssel kennzeichnet nur die Authentifizierung innerhalb dieser Instanz; die
        // eigentliche Prüfung läuft über die Tokens in der DB. Darum darf er bei jedem Start neu sein.
        PersistentTokenBasedRememberMeServices services = new PersistentTokenBasedRememberMeServices(
                UUID.randomUUID().toString(), userDetailsService, tokenRepository);
        services.setCookieName(REMEMBER_ME_COOKIE);
        services.setTokenValiditySeconds(rememberMe.validitySeconds());
        // Die Vaadin-LoginForm hat keine Checkbox "Angemeldet bleiben": jeder Login wird gemerkt.
        services.setAlwaysRemember(true);
        return services;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
