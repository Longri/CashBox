package com.cashbox.ui;

import java.io.ByteArrayInputStream;
import java.util.Locale;

import com.cashbox.service.GoalImage;
import com.cashbox.service.GoalPhoto;
import com.cashbox.service.ImageCrop;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;

import tools.jackson.databind.JsonNode;

/**
 * Editor für den Bildausschnitt: das Quellbild in einer quadratischen Bühne, darüber ein Kreis für
 * den Ausschnitt. Verschieben per Ziehen, Zoomen per Regler, Knöpfen, Mausrad oder zwei Fingern –
 * auch unter 100 %, dann ist das Bild kleiner als der Kreis –
 * das Verhalten steckt in {@code image-cropper.js}. Nach jeder Geste meldet der Browser den Ausschnitt
 * ({@code crop-change}); {@link #getCrop()} liefert damit immer den zuletzt gezeigten Stand.
 */
@Tag("cashbox-image-cropper")
@JavaScript(GoalImageCropper.SCRIPT)
class GoalImageCropper extends Component implements HasSize {

    /** Statische Datei aus {@code META-INF/resources}; in der {@code SecurityConfig} freigegeben. */
    static final String SCRIPT = "context://image-cropper.js";

    private ImageCrop crop;

    GoalImageCropper(GoalPhoto photo, ImageCrop initial) {
        this.crop = initial;
        Element host = getElement();
        host.setAttribute("crop-x", format(initial.centerX()));
        host.setAttribute("crop-y", format(initial.centerY()));
        host.setAttribute("crop-size", format(initial.size()));
        host.setAttribute("min-crop", String.valueOf(GoalImage.MIN_CROP_PIXELS));
        host.setAttribute("zoom-out", format(GoalImage.MAX_ZOOM_OUT));
        host.setAttribute("max-size", format(ImageCrop.MAX_SIZE));

        Image image = new Image(DownloadHandler.fromInputStream(event -> new DownloadResponse(
                new ByteArrayInputStream(photo.data()), "quellbild." + photo.fileExtension(),
                photo.contentType(), photo.size())).inline(), "Hochgeladenes Bild");
        image.addClassName("image-cropper__image");
        image.getElement().setAttribute("draggable", "false");
        Div backdrop = new Div();
        backdrop.addClassName("image-cropper__backdrop");
        Div frame = new Div();
        frame.addClassName("image-cropper__frame");
        Div stage = new Div(backdrop, image, frame);
        stage.setId("goal-crop-stage");
        stage.addClassName("image-cropper__stage");
        stage.getElement().setAttribute("tabindex", "0");
        stage.getElement().setAttribute("role", "group");
        stage.getElement().setAttribute("aria-label",
                "Bildausschnitt. Ziehen oder Pfeiltasten verschieben, Plus und Minus zoomen.");

        Element slider = new Element("input");
        slider.setAttribute("type", "range");
        slider.setAttribute("class", "image-cropper__zoom");
        slider.setAttribute("aria-label", "Zoom");
        Div controls = new Div(zoomButton("image-cropper__zoom-out", VaadinIcon.MINUS, "Herauszoomen"));
        controls.getElement().appendChild(slider);
        controls.add(zoomButton("image-cropper__zoom-in", VaadinIcon.PLUS, "Hineinzoomen"));
        controls.addClassName("image-cropper__controls");

        host.appendChild(stage.getElement(), controls.getElement());

        host.addEventListener("crop-change", e -> {
                    JsonNode data = e.getEventData();
                    cropChanged(data.get("event.detail.x").asDouble(), data.get("event.detail.y").asDouble(),
                            data.get("event.detail.size").asDouble());
                })
                .addEventData("event.detail.x")
                .addEventData("event.detail.y")
                .addEventData("event.detail.size");
    }

    /** Der zuletzt im Browser gewählte Ausschnitt. */
    ImageCrop getCrop() {
        return crop;
    }

    /** Wie nach einer Geste im Browser (auch für Tests ohne Browser). */
    void cropChanged(double x, double y, double size) {
        try {
            crop = new ImageCrop(x, y, size);
        } catch (IllegalArgumentException ignored) {
            // Unsinnige Werte aus dem Browser: letzten gültigen Ausschnitt behalten.
        }
    }

    /** Knöpfe wirken nur im Browser (siehe Skript), daher ohne Server-Listener. */
    private static NativeButton zoomButton(String className, VaadinIcon icon, String label) {
        NativeButton button = new NativeButton();
        button.add(icon.create());
        button.addClassName("image-cropper__zoom-button");
        button.addClassName(className);
        button.setAriaLabel(label);
        button.getElement().setAttribute("type", "button");
        button.getElement().setAttribute("title", label);
        return button;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }
}
