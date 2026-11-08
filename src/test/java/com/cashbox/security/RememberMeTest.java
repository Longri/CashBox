package com.cashbox.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.rememberme.CookieTheftException;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationFilter;

import jakarta.servlet.http.Cookie;

/**
 * "Angemeldet bleiben" gegen die lokale MariaDB (Tabelle persistent_logins).
 * REMEMBER_ME_DAYS wird hier wie aus der .env gesetzt, um den Weg bis zum Cookie zu prüfen.
 */
@SpringBootTest(properties = {
        "REMEMBER_ME_DAYS=7",
        "cashbox.admin.username=" + RememberMeTest.USER,
        "cashbox.admin.password=nur-fuer-tests" })
class RememberMeTest {

    static final String USER = "rememberme-testadmin";

    @Autowired SecurityFilterChain securityFilterChain;
    @Autowired RememberMeTokenCleanup cleanup;
    @Autowired JdbcTemplate jdbc;

    /** Die Services, die der Login-Filter tatsächlich benutzt. */
    PersistentTokenBasedRememberMeServices services;

    @BeforeEach
    void findRememberMeServices() {
        services = assertInstanceOf(PersistentTokenBasedRememberMeServices.class,
                loginFilter().getRememberMeServices(), "Login muss das Cookie setzen");
    }

    @AfterEach
    void removeTestTokens() {
        jdbc.update("DELETE FROM persistent_logins WHERE username = ?", USER);
    }

    @Test
    void filterChainLogsInAutomatically() {
        assertTrue(securityFilterChain.getFilters().stream().anyMatch(RememberMeAuthenticationFilter.class::isInstance),
                "Ohne RememberMeAuthenticationFilter keine automatische Anmeldung");
    }

    @Test
    void loginSetsCookieFromEnvAndNextVisitIsLoggedInAutomatically() {
        Cookie cookie = login();

        assertEquals(Duration.ofDays(7).toSeconds(), cookie.getMaxAge());
        assertTrue(cookie.isHttpOnly());
        assertEquals(1, tokenCount());

        Authentication remembered = services.autoLogin(visitWith(cookie), new MockHttpServletResponse());
        assertNotNull(remembered, "Mit gültigem Cookie muss die Anmeldung automatisch erfolgen");
        assertInstanceOf(RememberMeAuthenticationToken.class, remembered);
        assertEquals(USER, remembered.getName());
    }

    @Test
    void reusedOldCookieCountsAsTheftAndEndsAllLogins() {
        Cookie cookie = login();
        services.autoLogin(visitWith(cookie), new MockHttpServletResponse()); // Token wechselt

        assertThrows(CookieTheftException.class,
                () -> services.autoLogin(visitWith(cookie), new MockHttpServletResponse()));
        assertEquals(0, tokenCount());
    }

    @Test
    void logoutDeletesTokenAndCookie() {
        Cookie cookie = login();

        MockHttpServletResponse response = new MockHttpServletResponse();
        services.logout(visitWith(cookie), response, authentication());

        assertEquals(0, tokenCount());
        assertEquals(0, response.getCookie(SecurityConfig.REMEMBER_ME_COOKIE).getMaxAge());
    }

    @Test
    void cleanupDeletesOnlyExpiredTokens() {
        String expired = insertToken(Instant.now().minus(Duration.ofDays(8)));
        String fresh = insertToken(Instant.now().minus(Duration.ofDays(1)));

        cleanup.deleteExpiredTokens();

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM persistent_logins WHERE series = ?", Integer.class, expired));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM persistent_logins WHERE series = ?", Integer.class, fresh));
    }

    private UsernamePasswordAuthenticationFilter loginFilter() {
        return securityFilterChain.getFilters().stream()
                .filter(UsernamePasswordAuthenticationFilter.class::isInstance)
                .map(UsernamePasswordAuthenticationFilter.class::cast)
                .findFirst().orElseThrow();
    }

    private Cookie login() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        services.loginSuccess(new MockHttpServletRequest("POST", "/login"), response, authentication());
        Cookie cookie = response.getCookie(SecurityConfig.REMEMBER_ME_COOKIE);
        assertNotNull(cookie, "Login muss das Remember-Me-Cookie setzen");
        return cookie;
    }

    private static Authentication authentication() {
        return UsernamePasswordAuthenticationToken.authenticated(USER, null, List.of());
    }

    private static MockHttpServletRequest visitWith(Cookie cookie) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setCookies(cookie);
        return request;
    }

    private int tokenCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM persistent_logins WHERE username = ?", Integer.class, USER);
    }

    private String insertToken(Instant lastUsed) {
        String series = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO persistent_logins (username, series, token, last_used) VALUES (?, ?, ?, ?)",
                USER, series, "t", Timestamp.from(lastUsed));
        return series;
    }
}
