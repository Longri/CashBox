package com.cashbox.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Admin-Zugangsdaten aus der .env (ADMIN_USER / ADMIN_PASSWORD).
 */
@ConfigurationProperties(prefix = "cashbox.admin")
public record AdminProperties(String username, String password) {

    public boolean isConfigured() {
        return username != null && !username.isBlank() && password != null && !password.isBlank();
    }
}
