package com.cashbox.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/** Aufbereitung hochgeladener Bilder und Zuschnitt – ohne Spring und Datenbank. */
class GoalImageTest {

    // ------------------------------------------------------------------ Quellbild (Upload)

    @Test
    void uploadIsScaledDownKeepingAspectRatio() throws IOException {
        GoalPhoto photo = GoalPhoto.fromUpload(png(new BufferedImage(4000, 2000, BufferedImage.TYPE_INT_RGB)));

        BufferedImage result = read(photo.data());
        assertEquals(GoalPhoto.MAX_SIDE, result.getWidth());
        assertEquals(GoalPhoto.MAX_SIDE / 2, result.getHeight());
        assertEquals("image/jpeg", photo.contentType());
    }

    @Test
    void smallUploadKeepsItsSize() throws IOException {
        GoalPhoto photo = GoalPhoto.fromUpload(png(new BufferedImage(100, 60, BufferedImage.TYPE_INT_RGB)));

        BufferedImage result = read(photo.data());
        assertEquals(100, result.getWidth());
        assertEquals(60, result.getHeight());
    }

    @Test
    void appliesExifRotationOfPhoneJpegs() throws IOException {
        // Gespeichert quer: links rot, rechts blau. EXIF 6 = zum Anzeigen 90° im Uhrzeigersinn drehen,
        // danach steht das Bild hochkant mit Rot oben und Blau unten.
        BufferedImage source = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = source.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 200, 200);
        g.setColor(Color.BLUE);
        g.fillRect(200, 0, 200, 200);
        g.dispose();
        byte[] jpeg = withExifOrientation(jpeg(source), 6);
        assertEquals(6, ImageResizer.exifOrientation(jpeg));

        BufferedImage result = read(GoalPhoto.fromUpload(jpeg).data());

        assertEquals(200, result.getWidth());
        assertEquals(400, result.getHeight());
        assertDominant(Color.RED, result.getRGB(100, 50));
        assertDominant(Color.BLUE, result.getRGB(100, 350));
    }

    @Test
    void ignoresMissingExif() throws IOException {
        assertEquals(1, ImageResizer.exifOrientation(jpeg(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB))));
        assertEquals(1, ImageResizer.exifOrientation(png(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB))));
        assertEquals(1, ImageResizer.exifOrientation(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}));
    }

    @Test
    void rejectsFilesThatAreNoImages() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> GoalPhoto.fromUpload("kein Bild".getBytes()));
        assertTrue(ex.getMessage().contains("JPG"), ex.getMessage());

        byte[] broken = new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0};
        ex = assertThrows(IllegalArgumentException.class, () -> GoalPhoto.fromUpload(broken));
        assertTrue(ex.getMessage().startsWith("Das Bild konnte nicht gelesen werden"), ex.getMessage());

        assertThrows(IllegalArgumentException.class, () -> GoalPhoto.fromUpload(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> GoalPhoto.fromUpload(null));
    }

    @Test
    void rejectsOversizedFiles() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> GoalPhoto.fromUpload(new byte[GoalPhoto.MAX_UPLOAD_BYTES + 1]));
        assertTrue(ex.getMessage().contains("zu groß"), ex.getMessage());
    }

    // ------------------------------------------------------------------ Ausschnitt

    @Test
    void fullCropTakesCenterSquare() throws IOException {
        GoalImage image = GoalImage.render(stripes(), ImageCrop.FULL);

        assertEquals("image/jpeg", image.contentType());
        assertEquals("jpg", image.fileExtension());
        BufferedImage result = read(image.data());
        assertEquals(GoalImage.SIZE, result.getWidth());
        assertEquals(GoalImage.SIZE, result.getHeight());
        assertDominant(Color.GREEN, result.getRGB(3, 128));
        assertDominant(Color.GREEN, result.getRGB(252, 128));
    }

    @Test
    void zoomedCropFollowsCenter() throws IOException {
        // Halbe Höhe (300 px) mit Mittelpunkt im linken roten Streifen (x = 150 von 1200)
        GoalImage left = GoalImage.render(stripes(), new ImageCrop(0.125, 0.5, 0.5));
        BufferedImage result = read(left.data());
        assertEquals(GoalImage.SIZE, result.getWidth());
        assertDominant(Color.RED, result.getRGB(10, 10));
        assertDominant(Color.RED, result.getRGB(245, 245));

        // Mittelpunkt außerhalb: der Ausschnitt wird an den rechten Rand geschoben, also blau
        GoalImage right = GoalImage.render(stripes(), new ImageCrop(1, 0.5, 0.25));
        assertDominant(Color.BLUE, read(right.data()).getRGB(64, 64));
    }

    @Test
    void zoomIsLimitedSoResultStaysSharp() throws IOException {
        GoalImage image = GoalImage.render(stripes(), new ImageCrop(0.5, 0.5, 0.01));

        BufferedImage result = read(image.data());
        assertEquals(GoalImage.MIN_CROP_PIXELS, result.getWidth(), "Höchstens bis 128 Bildpixel hineinzoomen");
        assertEquals(GoalImage.MIN_CROP_PIXELS, result.getHeight());
    }

    @Test
    void zoomingOutLeavesTransparentBorder() throws IOException {
        // Ausschnitt = doppelte Bildhöhe = Bildbreite: Das Bild füllt die Breite, oben und unten bleibt Rand.
        GoalImage image = GoalImage.render(stripes(), new ImageCrop(0.5, 0.5, 2));

        assertEquals("image/png", image.contentType(), "Durchsichtiger Rand braucht PNG");
        BufferedImage result = read(image.data());
        assertEquals(GoalImage.SIZE, result.getWidth());
        assertEquals(0, result.getRGB(128, 20) >>> 24, "Oben durchsichtig");
        assertEquals(0, result.getRGB(128, 235) >>> 24, "Unten durchsichtig");
        assertDominant(Color.RED, result.getRGB(20, 128));
        assertDominant(Color.GREEN, result.getRGB(128, 128));
        assertDominant(Color.BLUE, result.getRGB(235, 128));
    }

    @Test
    void zoomingOutIsLimited() throws IOException {
        // Längere Seite 1200 px → Ausschnitt höchstens 3 × 1200 px, das Bild füllt dann ein Drittel der Breite.
        BufferedImage result = read(GoalImage.render(stripes(), new ImageCrop(0.5, 0.5, ImageCrop.MAX_SIZE)).data());

        assertEquals(0, result.getRGB(80, 128) >>> 24, "Links vom Bild durchsichtig");
        assertEquals(0xFF, result.getRGB(90, 128) >>> 24, "Bild beginnt bei einem Drittel");
        assertEquals(0xFF, result.getRGB(165, 128) >>> 24);
        assertEquals(0, result.getRGB(175, 128) >>> 24, "Rechts vom Bild durchsichtig");
    }

    @Test
    void zoomedOutImageCanBeMovedInsideTheCrop() throws IOException {
        // Ausschnitt 2400 px (4 × Höhe), Mittelpunkt am linken Bildrand: Das Bild liegt in der rechten Hälfte.
        BufferedImage result = read(GoalImage.render(stripes(), new ImageCrop(0, 0.5, 4)).data());

        assertEquals(0, result.getRGB(64, 128) >>> 24, "Linke Hälfte leer");
        assertDominant(Color.RED, result.getRGB(135, 128));
        assertDominant(Color.BLUE, result.getRGB(250, 128));
    }

    @Test
    void keepsTransparencyAsPng() throws IOException {
        BufferedImage source = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = source.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillOval(50, 50, 200, 200);
        g.dispose();

        GoalPhoto photo = GoalPhoto.fromUpload(png(source));
        assertEquals("image/png", photo.contentType());
        GoalImage image = GoalImage.render(photo, ImageCrop.FULL);

        assertEquals("image/png", image.contentType());
        BufferedImage result = read(image.data());
        assertEquals(0, result.getRGB(0, 0) >>> 24, "Ecke bleibt durchsichtig");
        assertEquals(0xFF, result.getRGB(128, 128) >>> 24, "Mitte ist deckend");
    }

    @Test
    void cropValuesAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> new ImageCrop(Double.NaN, 0.5, 1));
        assertThrows(IllegalArgumentException.class, () -> new ImageCrop(0.5, 0.5, 0));
        assertThrows(IllegalArgumentException.class, () -> new ImageCrop(0.5, 0.5, Double.POSITIVE_INFINITY));
        assertEquals(new ImageCrop(0, 1, 2.5), new ImageCrop(-3, 7, 2.5), "Mittelpunkt begrenzt auf 0 bis 1");
        assertEquals(ImageCrop.MAX_SIZE, new ImageCrop(0.5, 0.5, 100).size());
    }

    // ------------------------------------------------------------------ Hilfen

    /** 1200 × 600: links rot, Mitte grün, rechts blau (je 300 / 600 / 300 px breit). */
    private static GoalPhoto stripes() throws IOException {
        BufferedImage source = new BufferedImage(1200, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = source.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 300, 600);
        g.setColor(Color.GREEN);
        g.fillRect(300, 0, 600, 600);
        g.setColor(Color.BLUE);
        g.fillRect(900, 0, 300, 600);
        g.dispose();
        return GoalPhoto.fromUpload(png(source));
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] jpeg(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", out);
        return out.toByteArray();
    }

    private static BufferedImage read(byte[] data) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(data));
    }

    /** Fügt direkt nach dem JPEG-Anfang ein minimales EXIF-Segment (APP1) mit „Orientation“ ein. */
    private static byte[] withExifOrientation(byte[] jpeg, int orientation) {
        byte[] tiff = {
                'M', 'M', 0, 42, 0, 0, 0, 8,         // Big Endian, erstes IFD bei Offset 8
                0, 1,                                // ein Eintrag
                0x01, 0x12, 0, 3, 0, 0, 0, 1,        // Tag 0x0112, Typ SHORT, Anzahl 1
                0, (byte) orientation, 0, 0,         // Wert
                0, 0, 0, 0                           // kein weiteres IFD
        };
        byte[] header = {'E', 'x', 'i', 'f', 0, 0};
        int length = 2 + header.length + tiff.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);                       // SOI
        out.write(0xFF);
        out.write(0xE1);
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.writeBytes(header);
        out.writeBytes(tiff);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    /** JPEG verfälscht Farben leicht – geprüft wird nur, welcher Kanal klar überwiegt. */
    private static void assertDominant(Color expected, int rgb) {
        Color actual = new Color(rgb);
        int[] channels = {actual.getRed(), actual.getGreen(), actual.getBlue()};
        int[] wanted = {expected.getRed(), expected.getGreen(), expected.getBlue()};
        for (int i = 0; i < 3; i++) {
            boolean high = channels[i] > 160;
            boolean low = channels[i] < 90;
            assertTrue(wanted[i] > 0 ? high : low, "Erwartet " + expected + ", war " + actual);
        }
    }
}
