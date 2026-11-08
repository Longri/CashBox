package com.cashbox.ui;

import java.util.function.Consumer;

import com.cashbox.service.GoalPhoto;
import com.cashbox.service.ImageCrop;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * „Ausschnitt wählen“ – nach dem Hochladen eines Bilds und später über „Ausschnitt ändern“.
 * Der Ausschnitt geht an den {@link GoalDialog} zurück und wird erst mit dem Sparziel gespeichert.
 */
class GoalImageCropDialog extends Dialog {

    private final GoalImageCropper cropper;

    /**
     * @param onApply bekommt den gewählten Ausschnitt; der Dialog schließt sich danach
     */
    GoalImageCropDialog(GoalPhoto photo, ImageCrop initial, Consumer<ImageCrop> onApply) {
        addClassNames("form-dialog", "crop-dialog");
        setHeaderTitle("Ausschnitt wählen");
        setCloseOnEsc(true);
        // Ein Ziehen, das außerhalb des Dialogs endet, soll ihn nicht schließen.
        setCloseOnOutsideClick(false);

        Button close = new Button(VaadinIcon.CLOSE.create(), e -> close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        close.setAriaLabel("Schließen");
        getHeader().add(close);

        cropper = new GoalImageCropper(photo, initial);
        cropper.setWidthFull();
        Paragraph hint = new Paragraph("Ziehen zum Verschieben. Zoomen mit dem Regler, dem Mausrad "
                + "oder mit zwei Fingern – herausgezoomt passt auch ein ganzes Logo in den Kreis. "
                + "Im Kreis siehst du, was später angezeigt wird.");
        hint.addClassName("form-hint");

        VerticalLayout content = new VerticalLayout(cropper, hint);
        content.setPadding(false);
        content.setSpacing(false);
        content.addClassName("crop-dialog__content");
        add(content);

        Button cancel = new Button("Abbrechen", e -> close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        cancel.addClassName("dialog-footer-button");
        Button apply = new Button("Übernehmen", e -> {
            close();
            onApply.accept(cropper.getCrop());
        });
        apply.setId("goal-crop-apply");
        apply.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        apply.addClassName("dialog-footer-button");
        getFooter().add(cancel, apply);
    }

    GoalImageCropper getCropper() {
        return cropper;
    }
}
