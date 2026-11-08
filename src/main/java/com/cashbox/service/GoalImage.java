package com.cashbox.service;

import java.util.Objects;

/**
 * Angezeigtes Bild eines Sparziels: der gewählte quadratische Ausschnitt aus dem {@link GoalPhoto},
 * höchstens {@value #SIZE} px groß. Beim Herauszoomen ist das Bild kleiner als der Ausschnitt; der Rand
 * bleibt durchsichtig, sodass der Hintergrund des Symbols durchscheint.
 */
public final class GoalImage {

    /** Kantenlänge in Pixeln – reicht für die größte Anzeige (64 px) auch auf hochauflösenden Displays. */
    public static final int SIZE = 256;

    /**
     * Kleinster Ausschnitt in Pixeln des Quellbilds. Begrenzt das Hineinzoomen, damit das Ergebnis
     * nicht unscharf wird (bei kleineren Bildern: das ganze Bild).
     */
    public static final int MIN_CROP_PIXELS = 128;

    /**
     * Wie weit sich herauszoomen lässt: Die längere Bildseite füllt mindestens ein Drittel des Ausschnitts.
     * Der Rand um das Bild bleibt durchsichtig (das Ergebnis ist dann ein PNG).
     */
    public static final double MAX_ZOOM_OUT = 3;

    private final byte[] data;
    private final String contentType;

    GoalImage(byte[] data, String contentType) {
        this.data = Objects.requireNonNull(data, "data");
        this.contentType = Objects.requireNonNull(contentType, "contentType");
    }

    /** Schneidet den Ausschnitt aus dem Quellbild und verkleinert ihn. */
    public static GoalImage render(GoalPhoto photo, ImageCrop crop) {
        Objects.requireNonNull(photo, "photo");
        Objects.requireNonNull(crop, "crop");
        ImageResizer.Encoded encoded = ImageResizer.encode(ImageResizer.cropSquare(
                ImageResizer.read(photo.rawData()), crop, SIZE, MIN_CROP_PIXELS, MAX_ZOOM_OUT));
        return new GoalImage(encoded.data(), encoded.contentType());
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

    /** Dateiendung passend zum Inhalt, z. B. für die Bild-URL. */
    public String fileExtension() {
        return "image/png".equals(contentType) ? "png" : "jpg";
    }
}
