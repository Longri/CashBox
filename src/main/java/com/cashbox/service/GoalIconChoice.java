package com.cashbox.service;

import java.util.Objects;

import com.cashbox.model.GoalIcon;

/**
 * Was beim Speichern eines Sparziels mit Symbol und eigenem Bild geschehen soll.
 */
public sealed interface GoalIconChoice {

    /** Symbol und Bild bleiben, wie sie sind (ein neues Ziel bekommt das Standardsymbol). */
    GoalIconChoice UNCHANGED = new Unchanged();

    /** Symbol und Bild bleiben, wie sie sind. */
    record Unchanged() implements GoalIconChoice {
    }

    /** Standardsymbol verwenden ({@code null} = {@link GoalIcon#DEFAULT}); ein eigenes Bild wird entfernt. */
    record Symbol(GoalIcon icon) implements GoalIconChoice {
    }

    /** Neues eigenes Bild mit gewähltem Ausschnitt; ersetzt ein vorhandenes. */
    record Image(GoalPhoto photo, ImageCrop crop) implements GoalIconChoice {
        public Image {
            Objects.requireNonNull(photo, "photo");
            Objects.requireNonNull(crop, "crop");
        }
    }

    /** Nur den Ausschnitt des gespeicherten Bilds ändern – ohne neuen Upload. */
    record Crop(ImageCrop crop) implements GoalIconChoice {
        public Crop {
            Objects.requireNonNull(crop, "crop");
        }
    }
}
