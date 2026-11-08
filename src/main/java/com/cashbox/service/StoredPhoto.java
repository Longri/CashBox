package com.cashbox.service;

/**
 * Gespeichertes Quellbild eines Sparziels samt aktuellem Ausschnitt – Grundlage, um den
 * Ausschnitt nachträglich zu ändern.
 */
public record StoredPhoto(GoalPhoto photo, ImageCrop crop) {
}
