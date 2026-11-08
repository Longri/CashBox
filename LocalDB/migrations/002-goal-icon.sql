-- =====================================================================
--  Migration: Symbol bzw. eigenes Bild für Sparziele
--
--  Für Datenbanken, die vor dieser Änderung angelegt wurden. Neue Volumes
--  bekommen beides direkt über init/01-schema.sql. Mehrfach ausführbar.
--
--    docker exec -i cashbox-db mariadb -ucashbox -pcashbox cashbox < migrations/002-goal-icon.sql
-- =====================================================================

ALTER TABLE savings_goal
    ADD COLUMN IF NOT EXISTS icon VARCHAR(40) NULL AFTER description;

CREATE TABLE IF NOT EXISTS savings_goal_image (
    goal_id              BIGINT        NOT NULL,

    -- Angezeigtes Bild: der gewählte Ausschnitt, quadratisch, max. 256 px
    content_type         VARCHAR(50)   NOT NULL,
    data                 MEDIUMBLOB    NOT NULL,

    -- Hochgeladenes Bild (richtig gedreht, auf max. 1600 px verkleinert).
    -- Daraus wird der Ausschnitt neu berechnet, wenn man ihn später ändert.
    source_content_type  VARCHAR(50)   NOT NULL,
    source_data          MEDIUMBLOB    NOT NULL,

    -- Ausschnitt relativ zum Quellbild: Mittelpunkt (0..1 der Breite bzw. Höhe)
    -- und Kantenlänge als Anteil der kürzeren Bildseite: 1 = Bild füllt den
    -- Ausschnitt, < 1 hineingezoomt, > 1 herausgezoomt (Rand bleibt durchsichtig)
    crop_x               DOUBLE        NOT NULL,
    crop_y               DOUBLE        NOT NULL,
    crop_size            DOUBLE        NOT NULL,

    PRIMARY KEY (goal_id),
    CONSTRAINT fk_goal_image_goal
        FOREIGN KEY (goal_id) REFERENCES savings_goal (id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT,
    CONSTRAINT chk_goal_image_crop CHECK (
        crop_x BETWEEN 0 AND 1 AND crop_y BETWEEN 0 AND 1 AND crop_size > 0 AND crop_size <= 16)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
