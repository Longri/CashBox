package com.cashbox.ui;

import java.io.ByteArrayInputStream;

import com.cashbox.model.GoalIcon;
import com.cashbox.service.GoalImage;
import com.cashbox.service.SavingsGoalService;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.server.HttpStatusCode;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;

/**
 * Rundes Symbol bzw. eigenes Bild eines Sparziels. Rein dekorativ ({@code aria-hidden}),
 * der Name des Ziels steht immer daneben.
 */
class GoalAvatar extends Div {

    enum Size {
        /** Zeile in der Übersicht. */
        SMALL("goal-avatar--s"),
        /** Karte auf der Sparziel-Seite. */
        MEDIUM("goal-avatar--m"),
        /** Sparziel-Dialog. */
        LARGE("goal-avatar--l");

        private final String className;

        Size(String className) {
            this.className = className;
        }
    }

    GoalAvatar(Size size) {
        addClassNames("goal-avatar", size.className);
        getElement().setAttribute("aria-hidden", "true");
    }

    /** Avatar eines gespeicherten Ziels; das Bild wird erst geladen, wenn der Browser es anfordert. */
    static GoalAvatar of(Long goalId, GoalIcon icon, boolean hasImage, SavingsGoalService service, Size size) {
        GoalAvatar avatar = new GoalAvatar(size);
        avatar.showStored(goalId, icon, hasImage, service);
        return avatar;
    }

    /** Zeigt, was für ein gespeichertes Ziel hinterlegt ist: eigenes Bild oder Symbol. */
    void showStored(Long goalId, GoalIcon icon, boolean hasImage, SavingsGoalService service) {
        if (hasImage) {
            showImage(storedImage(goalId, service));
        } else {
            showIcon(icon);
        }
    }

    /** @param icon {@code null} = {@link GoalIcon#DEFAULT} */
    void showIcon(GoalIcon icon) {
        GoalIcon shown = icon == null ? GoalIcon.DEFAULT : icon;
        removeAll();
        removeClassName("goal-avatar--image");
        add(VaadinIcon.valueOf(shown.name()).create());
        getElement().setAttribute("data-icon", shown.name());
    }

    void showImage(DownloadHandler image) {
        removeAll();
        addClassName("goal-avatar--image");
        add(new Image(image, ""));
        getElement().setAttribute("data-icon", "IMAGE");
    }

    /** Ein frisch hochgeladenes, noch nicht gespeichertes Bild (Vorschau im Dialog). */
    void showImage(GoalImage image) {
        showImage(DownloadHandler.fromInputStream(event -> response(image, "vorschau")).inline());
    }

    private static DownloadHandler storedImage(Long goalId, SavingsGoalService service) {
        return DownloadHandler.fromInputStream(event -> service.findImage(goalId)
                .map(image -> response(image, "sparziel-" + goalId))
                .orElseGet(() -> DownloadResponse.error(HttpStatusCode.NOT_FOUND))).inline();
    }

    private static DownloadResponse response(GoalImage image, String baseName) {
        return new DownloadResponse(new ByteArrayInputStream(image.data()),
                baseName + "." + image.fileExtension(), image.contentType(), image.size());
    }
}
