package com.cashbox.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.cashbox.repository.AppUserRepository;

/**
 * Lädt Benutzer für den Login: zuerst der Admin aus der .env, danach die
 * Benutzer aus der Datenbank.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_USER = "USER";

    private static final Logger log = LoggerFactory.getLogger(AppUserDetailsService.class);

    private final AppUserRepository users;
    private final AdminProperties admin;
    private final String adminPasswordHash;

    public AppUserDetailsService(AppUserRepository users, AdminProperties admin, PasswordEncoder encoder) {
        this.users = users;
        this.admin = admin;
        // Klartext aus der .env wird nur einmal beim Start gehasht und nicht weiter vorgehalten.
        this.adminPasswordHash = admin.isConfigured() ? encoder.encode(admin.password()) : null;
        if (!admin.isConfigured()) {
            log.warn("Kein Admin konfiguriert: ADMIN_USER und ADMIN_PASSWORD in der .env setzen.");
        }
    }

    public boolean isAdminName(String username) {
        return admin.isConfigured() && admin.username().equalsIgnoreCase(username);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (isAdminName(username)) {
            return User.withUsername(admin.username())
                    .password(adminPasswordHash)
                    .roles(ROLE_ADMIN)
                    .build();
        }
        return users.findByUsernameIgnoreCase(username)
                .map(u -> User.withUsername(u.getUsername())
                        .password(u.getPasswordHash())
                        .disabled(!u.isEnabled())
                        .roles(ROLE_USER)
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Unbekannter Benutzer"));
    }
}
