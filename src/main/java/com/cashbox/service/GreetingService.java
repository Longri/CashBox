package com.cashbox.service;

import org.springframework.stereotype.Service;

/**
 * Platzhalter-Service, zeigt die Injektion von Spring-Beans in Vaadin-Views.
 */
@Service
public class GreetingService {

    public String greet(String name) {
        if (name == null || name.isBlank()) {
            return "Willkommen bei CashBox";
        }
        return "Willkommen bei CashBox, " + name.trim();
    }
}
