package com.cashbox.security;

import java.sql.Timestamp;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Löscht abgelaufene "Angemeldet bleiben"-Tokens. Spring Security entfernt sie nur beim
 * Abmelden; Browser, die nie wiederkommen, blieben sonst für immer in persistent_logins.
 */
@Component
public class RememberMeTokenCleanup {

    private static final Logger log = LoggerFactory.getLogger(RememberMeTokenCleanup.class);

    private final JdbcTemplate jdbc;
    private final RememberMeProperties rememberMe;

    public RememberMeTokenCleanup(JdbcTemplate jdbc, RememberMeProperties rememberMe) {
        this.jdbc = jdbc;
        this.rememberMe = rememberMe;
    }

    /** Täglich um 3:30 Uhr. Ist die Funktion abgeschaltet (0 Tage), wird alles gelöscht. */
    @Scheduled(cron = "0 30 3 * * *")
    public int deleteExpiredTokens() {
        Instant cutoff = Instant.now().minus(rememberMe.validity());
        int deleted = jdbc.update("DELETE FROM persistent_logins WHERE last_used < ?", Timestamp.from(cutoff));
        if (deleted > 0) {
            log.info("{} abgelaufene Anmelde-Token(s) gelöscht", deleted);
        }
        return deleted;
    }
}
