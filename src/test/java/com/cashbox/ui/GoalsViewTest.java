package com.cashbox.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import com.cashbox.model.GoalIcon;
import com.cashbox.model.GoalStatus;
import com.cashbox.service.BookingService;
import com.cashbox.service.GoalDetails;
import com.cashbox.service.GoalIconChoice;
import com.cashbox.service.GoalInput;
import com.cashbox.service.GoalPhoto;
import com.cashbox.service.ImageCrop;
import com.cashbox.service.SavingsGoalService;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.browserless.SpringSecurityContextHandler;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;

/** Läuft gegen die konfigurierte Datenbank; jede Methode wird zurückgerollt. */
@SpringBootTest
@Transactional
@SuppressWarnings("unchecked")
class GoalsViewTest extends SpringBrowserlessTest {

    @Autowired SavingsGoalService service;
    @Autowired BookingService bookingService;

    private String prefix;

    @BeforeEach
    void login() {
        // Angemeldeter Benutzer, sonst verweigert die Zugriffsprüfung den Zugriff auf die View
        SecurityContextHolder.getContext().setAuthentication(
                new SpringSecurityContextHandler().createCredentials("tester", "USER"));
        prefix = "Test-" + System.nanoTime() + "-";
    }

    @Test
    void createsGoalThroughDialog() {
        navigate(GoalsView.class);

        test($(Button.class).withId("goal-new").single()).click();
        assertEquals(1, $(GoalDialog.class).all().size());
        test($(TextField.class).withId("goal-name").single()).setValue(prefix + "Urlaub");
        test($(TextField.class).withId("goal-target").single()).setValue("1500,5");
        test($(IntegerField.class).withId("goal-priority").single()).setValue(3);
        test($(Button.class).withId("goal-save").single()).click();

        assertTrue($(GoalDialog.class).all().isEmpty(), "Dialog sollte nach dem Anlegen geschlossen sein");
        GoalDetails created = find(prefix + "Urlaub");
        assertEquals(new BigDecimal("1500.50"), created.targetAmount());
        assertEquals(3, created.priority());
        assertEquals(GoalStatus.ACTIVE, created.status());
        assertEquals(1, card(created).size(), "Neues Ziel sollte im Reiter „Aktiv“ stehen");
    }

    @Test
    void missingNameKeepsDialogOpen() {
        navigate(GoalsView.class);

        test($(Button.class).withId("goal-new").single()).click();
        test($(TextField.class).withId("goal-target").single()).setValue("100");
        test($(Button.class).withId("goal-save").single()).click();

        assertFalse($(GoalDialog.class).all().isEmpty());
        assertTrue($(TextField.class).withId("goal-name").single().isInvalid());
    }

    @Test
    void duplicateNameShowsErrorInDialog() {
        service.create(new GoalInput(prefix + "Fahrrad", new BigDecimal("500"), null, null, null));
        navigate(GoalsView.class);

        test($(Button.class).withId("goal-new").single()).click();
        test($(TextField.class).withId("goal-name").single()).setValue(prefix + "FAHRRAD");
        test($(TextField.class).withId("goal-target").single()).setValue("100");
        test($(Button.class).withId("goal-save").single()).click();

        assertFalse($(GoalDialog.class).all().isEmpty());
    }

    @Test
    void pausingMovesGoalToPausedTab() {
        GoalDetails goal = service.create(new GoalInput(prefix + "Auto", new BigDecimal("8000"), null, null, null));
        navigate(GoalsView.class);

        test(card(goal).getFirst()).click();
        RadioButtonGroup<GoalStatus> status = $(RadioButtonGroup.class).withId("goal-status").single();
        assertEquals(GoalStatus.ACTIVE, status.getValue());
        test(status).selectItem(GoalStatus.PAUSED.getLabel());
        test($(Button.class).withId("goal-save").single()).click();

        assertTrue($(GoalDialog.class).all().isEmpty());
        assertEquals(GoalStatus.PAUSED, find(goal.name()).status());
        assertTrue(card(goal).isEmpty(), "Pausiertes Ziel gehört nicht mehr in „Aktiv“");
        test($(Tabs.class).single()).select(GoalStatus.PAUSED.ordinal());
        assertEquals(1, card(goal).size());
    }

    @Test
    void deletesEmptyGoalAfterConfirmation() {
        GoalDetails goal = service.create(new GoalInput(prefix + "Weg", new BigDecimal("50"), null, null, null));
        navigate(GoalsView.class);

        test(card(goal).getFirst()).click();
        test($(Button.class).withId("goal-delete").single()).click();
        test($(ConfirmDialog.class).single()).confirm();

        assertTrue($(GoalDialog.class).all().isEmpty());
        assertNull(findOrNull(goal.name()));
        assertTrue(card(goal).isEmpty());
    }

    @Test
    void goalWithBookingsOffersNoDelete() {
        GoalDetails goal = service.create(new GoalInput(prefix + "Mit Geld", new BigDecimal("50"), null, null, null));
        bookingService.deposit(new BigDecimal("10"), goal.id(), null);
        navigate(GoalsView.class);

        test(card(goal).getFirst()).click();

        assertTrue($(Button.class).withId("goal-delete").all().isEmpty());
        assertEquals(1, $(Paragraph.class).withId("goal-delete-hint").all().size());
        assertEquals("Bereits zugewiesen: " + Money.format(new BigDecimal("10.00")),
                $(TextField.class).withId("goal-target").single().getHelperText());
    }

    @Test
    void movingGoalReordersCardsAndShowsHandles() {
        GoalDetails a = service.create(new GoalInput(prefix + "A", new BigDecimal("10"), null, null, null));
        GoalDetails b = service.create(new GoalInput(prefix + "B", new BigDecimal("10"), null, null, null));
        navigate(GoalsView.class);
        assertEquals(1, $(NativeButton.class).withId("goal-handle-" + b.id()).all().size(),
                "Bei mehreren Zielen hat jede Karte einen Ziehgriff");

        List<Long> before = shownIds();
        $(GoalsView.class).single().moveGoal(b.id(), before.indexOf(a.id()), false);

        List<Long> after = shownIds();
        assertEquals(after.indexOf(b.id()) + 1, after.indexOf(a.id()), "B steht jetzt direkt vor A");
        assertEquals(after, service.findAll().stream()
                .filter(g -> g.status() == GoalStatus.ACTIVE).map(GoalDetails::id).toList());
        assertEquals(after.indexOf(b.id()) + 1, find(b.name()).priority());
    }

    @Test
    void choosesSymbolThroughIconDialog() {
        GoalDetails goal = service.create(new GoalInput(prefix + "Reise", new BigDecimal("100"), null, null, null));
        navigate(GoalsView.class);
        assertEquals(GoalIcon.DEFAULT.name(), shownIcon(card(goal).getFirst()), "Ohne Auswahl: Standardsymbol");

        test(card(goal).getFirst()).click();
        NativeButton iconButton = $(NativeButton.class).withId("goal-icon").single();
        test(iconButton).click();
        assertEquals(1, $(GoalIconDialog.class).all().size());
        assertEquals("true", option(GoalIcon.DEFAULT).getElement().getAttribute("aria-pressed"),
                "Aktuelles Symbol ist markiert");
        test(option(GoalIcon.AIRPLANE)).click();

        assertTrue($(GoalIconDialog.class).all().isEmpty(), "Auswahl schließt den Symboldialog");
        assertEquals(GoalIcon.AIRPLANE.name(), shownIcon(iconButton), "Vorschau im Sparziel-Dialog");
        assertNull(find(goal.name()).icon(), "Gespeichert wird erst mit dem Sparziel");
        test($(Button.class).withId("goal-save").single()).click();

        assertEquals(GoalIcon.AIRPLANE, find(goal.name()).icon());
        assertEquals(GoalIcon.AIRPLANE.name(), shownIcon(card(goal).getFirst()));
    }

    @Test
    void cancellingGoalDialogDiscardsChosenSymbol() {
        GoalDetails goal = service.create(new GoalInput(prefix + "Rad", new BigDecimal("100"), null, null, null));
        navigate(GoalsView.class);

        test(card(goal).getFirst()).click();
        test($(NativeButton.class).withId("goal-icon").single()).click();
        test(option(GoalIcon.TROPHY)).click();
        $(GoalDialog.class).single().close();

        assertNull(find(goal.name()).icon());
        assertEquals(GoalIcon.DEFAULT.name(), shownIcon(card(goal).getFirst()));
    }

    @Test
    void uploadedImageGetsCroppedBeforeSaving() throws IOException {
        navigate(GoalsView.class);
        test($(Button.class).withId("goal-new").single()).click();
        test($(TextField.class).withId("goal-name").single()).setValue(prefix + "Foto");
        test($(TextField.class).withId("goal-target").single()).setValue("100");
        NativeButton iconButton = $(NativeButton.class).withId("goal-icon").single();
        test(iconButton).click();

        test($(Upload.class).withId("goal-icon-upload").single()).upload("foto.png", "image/png", png(640, 480));

        assertTrue($(GoalIconDialog.class).all().isEmpty(), "Nach dem Upload folgt der Ausschnitt");
        GoalImageCropDialog cropDialog = $(GoalImageCropDialog.class).single();
        assertEquals(ImageCrop.FULL, cropDialog.getCropper().getCrop());
        cropDialog.getCropper().cropChanged(0.3, 0.4, 0.5); // wie nach dem Ziehen im Browser
        test($(Button.class).withId("goal-crop-apply").single()).click();

        assertTrue($(GoalImageCropDialog.class).all().isEmpty());
        assertEquals("IMAGE", shownIcon(iconButton), "Vorschau zeigt das Bild");
        test($(Button.class).withId("goal-save").single()).click();

        GoalDetails created = find(prefix + "Foto");
        assertTrue(created.hasImage());
        assertEquals(new ImageCrop(0.3, 0.4, 0.5), service.findPhoto(created.id()).orElseThrow().crop());
        assertEquals("IMAGE", shownIcon(card(created).getFirst()));
    }

    @Test
    void cancellingCropDiscardsUpload() throws IOException {
        GoalDetails goal = service.create(new GoalInput(prefix + "Bleibt", new BigDecimal("100"), null, null, null));
        navigate(GoalsView.class);
        test(card(goal).getFirst()).click();
        test($(NativeButton.class).withId("goal-icon").single()).click();
        test($(Upload.class).withId("goal-icon-upload").single()).upload("foto.png", "image/png", png(300, 300));

        $(GoalImageCropDialog.class).single().close();
        test($(Button.class).withId("goal-save").single()).click();

        assertFalse(find(goal.name()).hasImage());
    }

    @Test
    void changesCropOfStoredImageWithoutUpload() throws IOException {
        GoalDetails goal = service.create(new GoalInput(prefix + "Ausschnitt", new BigDecimal("100"), null, null,
                null, new GoalIconChoice.Image(GoalPhoto.fromUpload(png(640, 480)), ImageCrop.FULL)));
        navigate(GoalsView.class);
        assertEquals("IMAGE", shownIcon(card(goal).getFirst()));

        test(card(goal).getFirst()).click();
        test($(NativeButton.class).withId("goal-icon").single()).click();
        test($(Button.class).withId("goal-icon-crop").single()).click();
        GoalImageCropDialog cropDialog = $(GoalImageCropDialog.class).single();
        assertEquals(ImageCrop.FULL, cropDialog.getCropper().getCrop(), "Startet mit dem gespeicherten Ausschnitt");
        cropDialog.getCropper().cropChanged(0.6, 0.5, 0.4);
        test($(Button.class).withId("goal-crop-apply").single()).click();

        assertEquals(ImageCrop.FULL, service.findPhoto(goal.id()).orElseThrow().crop(),
                "Gespeichert wird erst mit dem Sparziel");
        test($(NativeButton.class).withId("goal-icon").single()).click();
        test($(Button.class).withId("goal-icon-crop").single()).click();
        assertEquals(new ImageCrop(0.6, 0.5, 0.4), $(GoalImageCropDialog.class).single().getCropper().getCrop(),
                "Erneutes Ändern startet beim noch ungespeicherten Ausschnitt");
        test($(Button.class).withId("goal-crop-apply").single()).click();
        test($(Button.class).withId("goal-save").single()).click();

        assertEquals(new ImageCrop(0.6, 0.5, 0.4), service.findPhoto(goal.id()).orElseThrow().crop());
    }

    @Test
    void uploadOfNoImageShowsError() {
        navigate(GoalsView.class);
        test($(Button.class).withId("goal-new").single()).click();
        test($(NativeButton.class).withId("goal-icon").single()).click();

        test($(Upload.class).withId("goal-icon-upload").single()).upload("text.png", "image/png",
                "kein Bild".getBytes());

        assertEquals(1, $(GoalIconDialog.class).all().size(), "Auswahldialog bleibt offen");
        assertTrue($(GoalImageCropDialog.class).all().isEmpty());
        assertTrue($(Div.class).withClassName("form-error").all().stream()
                .anyMatch(d -> d.isVisible() && d.getText().contains("JPG")));
    }

    @Test
    void everySymbolExistsInVaadinIcons() {
        for (GoalIcon icon : GoalIcon.values()) {
            VaadinIcon.valueOf(icon.name()); // wirft, wenn es das Symbol nicht gibt
        }
    }

    // ------------------------------------------------------------------ Hilfen

    /** Farbverlauf als PNG, damit verschiedene Ausschnitte verschieden aussehen. */
    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, (x * 255 / width) << 16 | (y * 255 / height) << 8 | 0x40);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private NativeButton option(GoalIcon icon) {
        return $(NativeButton.class).withId("goal-icon-option-" + icon.name()).single();
    }

    /** Welches Symbol der Avatar in einer Karte bzw. im Symbol-Knopf zeigt ({@code IMAGE} bei eigenem Bild). */
    private static String shownIcon(NativeButton container) {
        return container.getChildren().filter(GoalAvatar.class::isInstance).findFirst().orElseThrow()
                .getElement().getAttribute("data-icon");
    }

    private List<NativeButton> card(GoalDetails goal) {
        return $(NativeButton.class).withId("goal-card-" + goal.id()).all();
    }

    /** IDs der Karten in der angezeigten Reihenfolge. */
    private List<Long> shownIds() {
        return $(Div.class).withId("goal-list").single().getChildren()
                .map(item -> Long.valueOf(item.getElement().getAttribute("data-goal-id")))
                .toList();
    }

    private GoalDetails find(String name) {
        GoalDetails goal = findOrNull(name);
        assertTrue(goal != null, "Sparziel „" + name + "“ nicht gefunden");
        return goal;
    }

    private GoalDetails findOrNull(String name) {
        return service.findAll().stream().filter(g -> g.name().equals(name)).findFirst().orElse(null);
    }
}
