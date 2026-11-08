package com.cashbox.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Eigenes Bild eines Sparziels (Tabelle {@code savings_goal_image}). Gespeichert werden
 * <ul>
 * <li>der angezeigte, quadratische Ausschnitt ({@code data}),</li>
 * <li>das hochgeladene Quellbild in verkleinerter Form ({@code sourceData}) und</li>
 * <li>der Ausschnitt relativ zum Quellbild ({@code cropX}, {@code cropY}, {@code cropSize}),</li>
 * </ul>
 * damit sich der Ausschnitt später ändern lässt, ohne das Bild neu hochzuladen.
 * Liegt in einer eigenen Tabelle, damit Listen die Bilddaten nicht mitladen.
 * Gibt es zu einem Ziel ein Bild, wird es statt des Symbols ({@link SavingsGoal#getIcon()}) angezeigt.
 * <p>
 * Der Primärschlüssel ist die ID des Sparziels; beim Löschen des Ziels entfernt die Datenbank
 * das Bild mit ({@code ON DELETE CASCADE}).
 */
@Entity
@Table(name = "savings_goal_image")
public class SavingsGoalImage {

    @Id
    @Column(name = "goal_id")
    private Long goalId;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] data;

    @Column(name = "source_content_type", nullable = false, length = 50)
    private String sourceContentType;

    @Column(name = "source_data", nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] sourceData;

    @Column(name = "crop_x", nullable = false)
    private double cropX;

    @Column(name = "crop_y", nullable = false)
    private double cropY;

    @Column(name = "crop_size", nullable = false)
    private double cropSize;

    protected SavingsGoalImage() {
    }

    public SavingsGoalImage(Long goalId) {
        this.goalId = goalId;
    }

    public Long getGoalId() { return goalId; }
    public String getContentType() { return contentType; }
    public byte[] getData() { return data; }
    public String getSourceContentType() { return sourceContentType; }
    public byte[] getSourceData() { return sourceData; }
    public double getCropX() { return cropX; }
    public double getCropY() { return cropY; }
    public double getCropSize() { return cropSize; }

    /** Neues Quellbild. */
    public void setSource(String contentType, byte[] data) {
        this.sourceContentType = contentType;
        this.sourceData = data;
    }

    /** Neuer Ausschnitt samt dem daraus berechneten Bild. */
    public void setCrop(double x, double y, double size, String contentType, byte[] data) {
        this.cropX = x;
        this.cropY = y;
        this.cropSize = size;
        this.contentType = contentType;
        this.data = data;
    }
}
