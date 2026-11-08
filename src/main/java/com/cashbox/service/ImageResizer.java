package com.cashbox.service;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * Bildverarbeitung für eigene Sparziel-Bilder.
 * <ul>
 * <li>{@link #readUpload}: Upload-Datei lesen, schon beim Dekodieren verkleinern (Subsampling – so passen
 *     auch große Handyfotos in wenig Speicher), EXIF-Ausrichtung anwenden, auf Höchstmaß verkleinern.</li>
 * <li>{@link #cropSquare}: quadratischen Ausschnitt schneiden und schrittweise verkleinern
 *     (sauberer als in einem Schritt). Kleinere Ausschnitte werden nicht vergrößert; beim Herauszoomen
 *     bleibt der Rand um das Bild durchsichtig.</li>
 * <li>{@link #encode}: PNG, wenn das Bild durchsichtige Stellen hat, sonst JPEG.</li>
 * </ul>
 * Fehler beim Lesen kommen als {@link IllegalArgumentException} mit einer Meldung für den Benutzer.
 */
final class ImageResizer {

    /** Schutz vor „Dekompressionsbomben“: kleine Datei, riesige Abmessungen. */
    static final long MAX_PIXELS = 100_000_000L;

    private static final float JPEG_QUALITY = 0.88f;

    record Encoded(byte[] data, String contentType) {
    }

    private ImageResizer() {
    }

    /**
     * Liest eine hochgeladene Datei: richtig gedreht und so verkleinert, dass die längere Seite
     * höchstens {@code maxSide} Pixel hat.
     */
    static BufferedImage readUpload(byte[] raw, int maxSide) {
        BufferedImage decoded = decode(raw, maxSide);
        return fit(orient(toArgb(decoded), exifOrientation(raw)), maxSide);
    }

    /** Liest ein bereits aufbereitetes Bild (z. B. das gespeicherte Quellbild). */
    static BufferedImage read(byte[] data) {
        return toArgb(decode(data, Integer.MAX_VALUE));
    }

    /**
     * Schneidet den Ausschnitt aus und verkleinert ihn auf höchstens {@code size} Pixel.
     * <p>
     * Pro Achse gilt: Ist der Ausschnitt kleiner als das Bild, bleibt er ganz im Bild; ist er größer
     * (herausgezoomt), bleibt das Bild ganz im Ausschnitt und der Rest wird durchsichtig. Der Mittelpunkt
     * liegt immer im Bild.
     *
     * @param minSide kleinste Kantenlänge des Ausschnitts in Bildpixeln (begrenzt das Hineinzoomen)
     * @param zoomOut wie oft die längere Bildseite höchstens in den Ausschnitt passen darf (begrenzt das Herauszoomen)
     */
    static BufferedImage cropSquare(BufferedImage image, ImageCrop crop, int size, int minSide, double zoomOut) {
        int width = image.getWidth();
        int height = image.getHeight();
        int shortSide = Math.min(width, height);
        double maxSide = Math.max(shortSide,
                Math.min(ImageCrop.MAX_SIZE * shortSide, zoomOut * Math.max(width, height)));
        double side = Math.clamp(crop.size() * shortSide, Math.min(minSide, shortSide), maxSide);
        double left = cropStart(crop.centerX(), width, side);
        double top = cropStart(crop.centerY(), height, side);
        int target = (int) Math.max(1, Math.round(Math.min(size, side)));

        int whole = (int) Math.round(side);
        if (left >= -0.5 && top >= -0.5 && left + whole <= width + 0.5 && top + whole <= height + 0.5) {
            // Ausschnitt liegt im Bild: ausschneiden und verkleinern.
            int x = Math.clamp(Math.round(left), 0, width - whole);
            int y = Math.clamp(Math.round(top), 0, height - whole);
            return scaleDown(image.getSubimage(x, y, whole, whole), target, target);
        }
        // Herausgezoomt: Bild verkleinern und auf eine durchsichtige Fläche setzen.
        double factor = target / side;
        BufferedImage scaled = scaleDown(image, (int) Math.max(1, Math.round(width * factor)),
                (int) Math.max(1, Math.round(height * factor)));
        BufferedImage canvas = new BufferedImage(target, target, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.drawImage(scaled, (int) Math.round(-left * factor), (int) Math.round(-top * factor), null);
        g.dispose();
        return canvas;
    }

    /**
     * Linke bzw. obere Kante des Ausschnitts in Bildpixeln für eine Achse der Länge {@code extent}:
     * ein kleinerer Ausschnitt bleibt im Bild, ein größerer umschließt es; der Mittelpunkt bleibt im Bild.
     */
    private static double cropStart(double center, int extent, double side) {
        double a = side / 2;
        double b = extent - side / 2;
        double mid = Math.clamp(center * extent, Math.max(0, Math.min(a, b)), Math.min(extent, Math.max(a, b)));
        return mid - side / 2;
    }

    // ------------------------------------------------------------------ Lesen

    /**
     * Dekodiert das erste Bild der Datei. Ist es deutlich größer als {@code maxSide}, wird schon beim
     * Lesen nur jedes n-te Pixel genommen; das Ergebnis bleibt mindestens {@code maxSide} groß.
     */
    private static BufferedImage decode(byte[] raw, int maxSide) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(raw))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw new RejectedImage(
                        "Dieses Dateiformat wird nicht unterstützt. Bitte ein JPG-, PNG- oder GIF-Bild wählen.", null);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    throw unreadable(null);
                }
                if ((long) width * height > MAX_PIXELS) {
                    throw new RejectedImage("Das Bild ist zu groß (höchstens "
                            + MAX_PIXELS / 1_000_000 + " Megapixel).", null);
                }
                int step = Math.max(1, Math.max(width, height) / maxSide);
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(step, step, 0, 0);
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        } catch (RejectedImage ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            // Kaputte oder exotische Dateien (z. B. CMYK-JPEG): ImageIO meldet das auf Englisch.
            throw unreadable(ex);
        }
    }

    private static RejectedImage unreadable(Exception cause) {
        return new RejectedImage(
                "Das Bild konnte nicht gelesen werden. Bitte ein anderes JPG-, PNG- oder GIF-Bild wählen.", cause);
    }

    /** Eigene Ablehnung mit Meldung für den Benutzer – wird nicht noch einmal umverpackt. */
    private static final class RejectedImage extends IllegalArgumentException {
        RejectedImage(String message, Exception cause) {
            super(message, cause);
        }
    }

    // ------------------------------------------------------------------ Ausrichtung (EXIF)

    /**
     * Liest den EXIF-Tag „Orientation“ (0x0112) aus einem JPEG; 1 (= unverändert), wenn es keinen gibt
     * oder die Datei kein JPEG ist.
     */
    static int exifOrientation(byte[] data) {
        if (data.length < 4 || u8(data, 0) != 0xFF || u8(data, 1) != 0xD8) {
            return 1;
        }
        int pos = 2;
        while (pos + 4 <= data.length && u8(data, pos) == 0xFF) {
            int marker = u8(data, pos + 1);
            if (marker == 0xDA || marker == 0xD9) {
                return 1; // Bilddaten bzw. Dateiende – kein EXIF davor
            }
            int length = u16(data, pos + 2, false);
            int start = pos + 4;
            int end = pos + 2 + length;
            if (length < 2 || end > data.length) {
                return 1;
            }
            if (marker == 0xE1 && end - start > 14 && data[start] == 'E' && data[start + 1] == 'x'
                    && data[start + 2] == 'i' && data[start + 3] == 'f' && data[start + 4] == 0
                    && data[start + 5] == 0) {
                return tiffOrientation(data, start + 6, end);
            }
            pos = end;
        }
        return 1;
    }

    private static int tiffOrientation(byte[] data, int tiff, int end) {
        boolean littleEndian;
        if (data[tiff] == 'I' && data[tiff + 1] == 'I') {
            littleEndian = true;
        } else if (data[tiff] == 'M' && data[tiff + 1] == 'M') {
            littleEndian = false;
        } else {
            return 1;
        }
        long offset = u32(data, tiff + 4, littleEndian);
        if (offset < 8 || tiff + offset + 2 > end) {
            return 1;
        }
        int ifd = tiff + (int) offset;
        int entries = u16(data, ifd, littleEndian);
        for (int i = 0; i < entries; i++) {
            int entry = ifd + 2 + i * 12;
            if (entry + 12 > end) {
                return 1;
            }
            if (u16(data, entry, littleEndian) == 0x0112) {
                int value = u16(data, entry + 8, littleEndian);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    /** Dreht bzw. spiegelt ein Bild gemäß EXIF-Ausrichtung 1–8; bei 5–8 tauschen Breite und Höhe. */
    static BufferedImage orient(BufferedImage image, int orientation) {
        int w = image.getWidth();
        int h = image.getHeight();
        // Matrix (m00, m10, m01, m11, m02, m12): x' = m00·x + m01·y + m02, y' = m10·x + m11·y + m12
        AffineTransform transform = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);   // horizontal gespiegelt
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);  // 180°
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);   // vertikal gespiegelt
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);    // an der Hauptdiagonale gespiegelt
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);   // 90° im Uhrzeigersinn
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);  // an der Nebendiagonale gespiegelt
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, w);   // 90° gegen den Uhrzeigersinn
            default -> null;
        };
        if (transform == null) {
            return image;
        }
        boolean swap = orientation >= 5;
        BufferedImage result = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.drawImage(image, transform, null);
        g.dispose();
        return result;
    }

    // ------------------------------------------------------------------ Verkleinern

    private static BufferedImage toArgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
            return image;
        }
        BufferedImage argb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = argb.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return argb;
    }

    /** Verkleinert so, dass die längere Seite höchstens {@code maxSide} Pixel hat (Seitenverhältnis bleibt). */
    private static BufferedImage fit(BufferedImage image, int maxSide) {
        int longSide = Math.max(image.getWidth(), image.getHeight());
        if (longSide <= maxSide) {
            return image;
        }
        double factor = (double) maxSide / longSide;
        return scaleDown(image, Math.max(1, (int) Math.round(image.getWidth() * factor)),
                Math.max(1, (int) Math.round(image.getHeight() * factor)));
    }

    /** Halbiert, solange das noch über der Zielgröße bleibt, und skaliert dann genau auf die Zielgröße. */
    private static BufferedImage scaleDown(BufferedImage image, int targetWidth, int targetHeight) {
        BufferedImage current = image;
        while (current.getWidth() / 2 >= targetWidth && current.getHeight() / 2 >= targetHeight) {
            current = resize(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        return current.getWidth() == targetWidth && current.getHeight() == targetHeight
                ? current : resize(current, targetWidth, targetHeight);
    }

    private static BufferedImage resize(BufferedImage image, int width, int height) {
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setComposite(AlphaComposite.Src);
        g.drawImage(image, 0, 0, width, height, null);
        g.dispose();
        return result;
    }

    // ------------------------------------------------------------------ Schreiben

    static Encoded encode(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (hasTransparency(image)) {
                ImageIO.write(image, "png", out);
                return new Encoded(out.toByteArray(), "image/png");
            }
            writeJpeg(withoutAlpha(image), out);
            return new Encoded(out.toByteArray(), "image/jpeg");
        } catch (IOException ex) {
            throw new IllegalStateException("Das Bild konnte nicht gespeichert werden.", ex);
        }
    }

    private static boolean hasTransparency(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) {
            return false;
        }
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) >>> 24 != 0xFF) {
                    return true;
                }
            }
        }
        return false;
    }

    private static BufferedImage withoutAlpha(BufferedImage image) {
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private static void writeJpeg(BufferedImage image, ByteArrayOutputStream out) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    // ------------------------------------------------------------------ Bytes

    private static int u8(byte[] data, int pos) {
        return data[pos] & 0xFF;
    }

    private static int u16(byte[] data, int pos, boolean littleEndian) {
        return littleEndian
                ? u8(data, pos) | u8(data, pos + 1) << 8
                : u8(data, pos) << 8 | u8(data, pos + 1);
    }

    private static long u32(byte[] data, int pos, boolean littleEndian) {
        return littleEndian
                ? (long) u16(data, pos, true) | (long) u16(data, pos + 2, true) << 16
                : (long) u16(data, pos, false) << 16 | u16(data, pos + 2, false);
    }
}
