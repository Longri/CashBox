package com.cashbox.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cashbox.model.AppUser;
import com.cashbox.repository.AppUserRepository;
import com.cashbox.security.AppUserDetailsService;

/**
 * Verwaltung der DB-Benutzer (der Admin aus der .env ist hier nicht enthalten).
 */
@Service
public class UserService {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AppUserDetailsService details;

    public UserService(AppUserRepository users, PasswordEncoder encoder, AppUserDetailsService details) {
        this.users = users;
        this.encoder = encoder;
        this.details = details;
    }

    @Transactional(readOnly = true)
    public List<AppUser> findAll() {
        return users.findAll();
    }

    @Transactional
    public AppUser create(String username, String rawPassword) {
        String name = username == null ? "" : username.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Benutzername fehlt.");
        }
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new IllegalArgumentException("Das Passwort muss mindestens 8 Zeichen haben.");
        }
        if (details.isAdminName(name) || users.existsByUsernameIgnoreCase(name)) {
            throw new IllegalArgumentException("Benutzername ist bereits vergeben.");
        }
        return users.save(new AppUser(name, encoder.encode(rawPassword)));
    }

    @Transactional
    public void delete(AppUser user) {
        users.deleteById(user.getId());
    }
}
