package com.cashbox.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Angemeldet bleiben: Laufzeit des Remember-Me-Cookies aus der .env (REMEMBER_ME_DAYS).
 * {@code 0} schaltet die Funktion ab, dann muss man sich bei jeder neuen Sitzung anmelden.
 */
@ConfigurationProperties(prefix = "cashbox.remember-me")
public record RememberMeProperties(int days) {

    public RememberMeProperties {
        if (days < 0) {
            throw new IllegalArgumentException("REMEMBER_ME_DAYS darf nicht negativ sein: " + days);
        }
    }

    public boolean isEnabled() {
        return days > 0;
    }

    public Duration validity() {
        return Duration.ofDays(days);
    }

    /** Für Spring Security, das die Laufzeit als int in Sekunden erwartet. */
    public int validitySeconds() {
        return Math.toIntExact(validity().toSeconds());
    }
}
