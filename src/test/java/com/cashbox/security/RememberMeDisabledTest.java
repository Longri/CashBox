package com.cashbox.security;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationFilter;

/** REMEMBER_ME_DAYS=0 schaltet "Angemeldet bleiben" ab. */
@SpringBootTest(properties = "REMEMBER_ME_DAYS=0")
class RememberMeDisabledTest {

    @Autowired SecurityFilterChain securityFilterChain;

    @Test
    void noAutomaticLoginWhenDisabled() {
        assertFalse(securityFilterChain.getFilters().stream().anyMatch(RememberMeAuthenticationFilter.class::isInstance));
    }
}
