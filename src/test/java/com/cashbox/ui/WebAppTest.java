package com.cashbox.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import javax.imageio.ImageIO;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Web-App für den Home-Bildschirm über echtes HTTP: Das iPhone holt Manifest und Icon ohne
 * Anmelde-Cookie, also muss beides auch ohne Anmeldung erreichbar sein.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "vaadin.launch-browser=false")
class WebAppTest {

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @LocalServerPort int port;

    @Test
    void manifestIsPublicAndOpensWithoutBrowserBars() throws Exception {
        HttpResponse<String> manifest = get("/manifest.webmanifest", HttpResponse.BodyHandlers.ofString());

        assertEquals(200, manifest.statusCode());
        assertTrue(manifest.body().contains("\"name\":\"CashBox\""), manifest.body());
        assertTrue(manifest.body().contains("\"display\":\"standalone\""), manifest.body());
    }

    @Test
    void logoIsPublicForLoginPage() throws Exception {
        HttpResponse<String> logo = get("/" + AppLogo.SRC, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, logo.statusCode());
        assertTrue(logo.headers().firstValue("Content-Type").orElse("").startsWith("image/svg+xml"));
        assertTrue(logo.body().contains("<svg"), "Anmeldeseite muss das Logo ohne Anmeldung laden können");
    }

    @Test
    void loginPageLinksManifestAndReadableStatusBar() throws Exception {
        Document login = loginPage();

        assertNotNull(login.selectFirst("link[rel=manifest]"), "Ohne Manifest-Link keine Web-App");
        Element statusBar = login.selectFirst("meta[name=" + StatusBarBootstrapListener.STATUS_BAR_STYLE + "]");
        assertNotNull(statusBar);
        assertEquals(StatusBarBootstrapListener.READABLE, statusBar.attr("content"));
    }

    @Test
    void appleTouchIconIsPublicTransparentPng() throws Exception {
        Element link = loginPage().selectFirst("link[rel=apple-touch-icon]");
        assertNotNull(link, "Ohne apple-touch-icon nimmt das iPhone ein Bildschirmfoto als Icon");

        HttpResponse<byte[]> icon = get("/" + link.attr("href"), HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, icon.statusCode());
        assertEquals("image/png", icon.headers().firstValue("Content-Type").orElse(""));
        BufferedImage image = image(icon.body());
        assertEquals(180, image.getWidth());
        assertEquals(180, image.getHeight());
        // Gewollt wie die SVG: Hintergrund durchsichtig (iOS legt das Icon dann auf Schwarz), Sparbüchsen deckend.
        assertEquals(0, alpha(image, 0, 0), "Hintergrund des Icons ist durchsichtig");
        assertEquals(255, alpha(image, 90, 90), "Mittlere Sparbüchse ist deckend");
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }

    private Document loginPage() throws Exception {
        HttpResponse<String> response = get("/login", HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        return Jsoup.parse(response.body());
    }

    private <T> HttpResponse<T> get(String path, HttpResponse.BodyHandler<T> body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build();
        return http.send(request, body);
    }

    private static BufferedImage image(byte[] png) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(image, "Kein lesbares PNG");
        return image;
    }
}
