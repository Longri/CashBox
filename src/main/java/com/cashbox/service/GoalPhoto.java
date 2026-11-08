package com.cashbox.service;

import java.util.List;
import java.util.Objects;

/**
 * Hochgeladenes Bild für ein Sparziel – das Quellbild, aus dem der angezeigte Ausschnitt
 * ({@link GoalImage}) berechnet wird. Richtig gedreht und auf höchstens {@value #MAX_SIDE} px
 * (längere Seite) verkleinert, als JPEG oder – bei durchsichtigen Stellen – als PNG.
 * <p>
 * Neue Bilder entstehen nur über {@link #fromUpload}, das jede Datei dekodiert und neu kodiert.
 * So landen weder riesige Fotos noch beliebige andere Dateien in der Datenbank.
 */
public final class GoalPhoto {

    /** Längere Seite des gespeicherten Quellbilds – genug Reserve zum Hineinzoomen. */
    public static final int MAX_SIDE = 1600;

    /** Größte erlaubte Upload-Datei; Handyfotos liegen meist bei 2–8 MB. */
    public static final int MAX_UPLOAD_BYTES = 15 * 1024 * 1024;

    /** Für die Upload-Komponente: Dateitypen, die {@link #fromUpload} lesen kann. */
    public static final List<String> ACCEPTED_FILE_TYPES = List.of(
            "image/jpeg", "image/png", "image/gif", "image/bmp", ".jpg", ".jpeg", ".png", ".gif", ".bmp");

    private final byte[] data;
    private final String contentType;

    GoalPhoto(byte[] data, String contentType) {
        this.data = Objects.requireNonNull(data, "data");
        this.contentType = Objects.requireNonNull(contentType, "contentType");
    }

    /**
     * Bereitet eine hochgeladene Bilddatei auf.
     *
     * @throws IllegalArgumentException mit Meldung für den Benutzer, wenn die Datei kein lesbares Bild ist
     */
    public static GoalPhoto fromUpload(byte[] upload) {
        if (upload == null || upload.length == 0) {
            throw new IllegalArgumentException("Die Datei ist leer.");
        }
        if (upload.length > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException(
                    "Die Datei ist zu groß (höchstens " + MAX_UPLOAD_BYTES / (1024 * 1024) + " MB).");
        }
        ImageResizer.Encoded encoded = ImageResizer.encode(ImageResizer.readUpload(upload, MAX_SIDE));
        return new GoalPhoto(encoded.data(), encoded.contentType());
    }

    /** Die Bilddaten (Kopie). */
    public byte[] data() {
        return data.clone();
    }

    /** Ohne Kopie – nur innerhalb des Service-Pakets, z. B. zum Speichern. */
    byte[] rawData() {
        return data;
    }

    public int size() {
        return data.length;
    }

    /** {@code image/jpeg} oder {@code image/png}. */
    public String contentType() {
        return contentType;
    }

    public String fileExtension() {
        return "image/png".equals(contentType) ? "png" : "jpg";
    }
}
