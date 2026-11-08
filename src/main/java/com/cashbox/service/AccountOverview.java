package com.cashbox.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.cashbox.model.GoalIcon;
import com.cashbox.model.GoalStatus;

/**
 * Momentaufnahme des Sparkontos für die Oberfläche (keine JPA-Entitäten).
 */
public record AccountOverview(String accountName, BigDecimal balance, BigDecimal buffer, List<GoalInfo> goals) {

    /**
     * @param icon     gewähltes Standardsymbol oder {@code null} für {@link GoalIcon#DEFAULT}
     * @param hasImage {@code true}, wenn ein eigenes Bild hinterlegt ist
     */
    public record GoalInfo(Long id, String name, BigDecimal assigned, BigDecimal target, GoalStatus status,
                           GoalIcon icon, boolean hasImage) {
    }

    /** Puffer zuerst, danach die Sparziele in Anzeigereihenfolge. */
    public List<Pot> pots() {
        List<Pot> pots = new ArrayList<>(goals.size() + 1);
        pots.add(Pot.buffer(buffer));
        goals.forEach(g -> pots.add(new Pot(g.id(), g.name(), g.assigned(), g.status())));
        return pots;
    }
}
