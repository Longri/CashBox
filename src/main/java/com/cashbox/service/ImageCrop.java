package com.cashbox.service;

/**
 * Quadratischer Bildausschnitt, relativ zum Quellbild angegeben – so bleibt er gültig, egal in welcher
 * Auflösung das Bild vorliegt.
 *
 * @param centerX Mittelpunkt, 0 = linker und 1 = rechter Bildrand
 * @param centerY Mittelpunkt, 0 = oberer und 1 = unterer Bildrand
 * @param size    Kantenlänge als Anteil der kürzeren Bildseite: 1 = das Bild füllt den Ausschnitt gerade
 *                (Ausgangslage), kleiner = hineingezoomt, größer = herausgezoomt – dann ist das Bild kleiner
 *                als der Ausschnitt und der Rest bleibt durchsichtig
 */
public record ImageCrop(double centerX, double centerY, double size) {

    /** Das Bild füllt den Ausschnitt, mittig – Ausgangspunkt nach dem Hochladen. */
    public static final ImageCrop FULL = new ImageCrop(0.5, 0.5, 1);

    /** Obergrenze für {@link #size()}; die tatsächliche Grenze hängt vom Seitenverhältnis ab (siehe GoalImage). */
    public static final double MAX_SIZE = 16;

    /**
     * Werte außerhalb der Grenzen werden begrenzt. Wo das Quadrat genau liegen darf, stellt erst das
     * Zuschneiden sicher, weil es dafür das Seitenverhältnis braucht.
     *
     * @throws IllegalArgumentException bei {@code NaN}, unendlichen Werten oder {@code size <= 0}
     */
    public ImageCrop {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerY) || !Double.isFinite(size) || size <= 0) {
            throw new IllegalArgumentException("Der Bildausschnitt ist ungültig.");
        }
        centerX = Math.clamp(centerX, 0.0, 1.0);
        centerY = Math.clamp(centerY, 0.0, 1.0);
        size = Math.min(size, MAX_SIZE);
    }
}
