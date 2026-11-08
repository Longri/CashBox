-- =====================================================================
--  Spar-App - Datenbankschema (MariaDB 11.x)
--
--  Abgeleitet aus den JPA-Entitäten:
--    de.sparapp.model.Account
--    de.sparapp.model.SavingsGoal
--    de.sparapp.model.Booking
--
--  Invariante (im Service zu prüfen, nicht per DB erzwingbar):
--    account.current_balance = account.unassigned_buffer
--                            + SUMME(savings_goal.assigned_amount)
--
--  Beträge: DECIMAL(19,2)  -> passt zu BigDecimal(precision=19, scale=2)
--  Zeitstempel: DATETIME(6) -> passt zu java.time.Instant (UTC!)
--                Die JDBC-Verbindung MUSS auf UTC stehen, sonst
--                verschieben sich die Zeiten.
-- =====================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS booking;
DROP TABLE IF EXISTS savings_goal_image;
DROP TABLE IF EXISTS savings_goal;
DROP TABLE IF EXISTS account;

SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------
--  account - das eine reale Sparkonto (Single Source of Truth)
-- ---------------------------------------------------------------------
CREATE TABLE account (
    id                 BIGINT          NOT NULL AUTO_INCREMENT,
    name               VARCHAR(100)    NOT NULL,

    -- Tatsächlicher Kontostand des realen Sparbuchs
    current_balance    DECIMAL(19,2)   NOT NULL DEFAULT 0.00,

    -- Geld, das noch keinem Sparziel zugewiesen ist
    unassigned_buffer  DECIMAL(19,2)   NOT NULL DEFAULT 0.00,

    -- Optimistic Locking (@Version). NOT NULL DEFAULT 0: Hibernate kann eine
    -- NULL-Version nicht hochzählen (z. B. bei Zeilen, die per Adminer angelegt wurden).
    version            BIGINT          NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    CONSTRAINT chk_account_balance_not_negative
        CHECK (current_balance >= 0),
    CONSTRAINT chk_account_buffer_not_negative
        CHECK (unassigned_buffer >= 0),
    CONSTRAINT chk_account_buffer_le_balance
        CHECK (unassigned_buffer <= current_balance)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
--  savings_goal - virtuelle Untertöpfe ("Envelopes")
-- ---------------------------------------------------------------------
CREATE TABLE savings_goal (
    id               BIGINT          NOT NULL AUTO_INCREMENT,
    account_id       BIGINT          NOT NULL,

    -- Kurzer Anzeigename fürs Dashboard
    name             VARCHAR(100)    NOT NULL,

    -- Längeres Notizfeld: Details, Links, Gedanken zum Ziel
    description      TEXT            NULL,

    -- Optionales Standardsymbol (Name aus GoalIcon); NULL = Sparschwein.
    -- Ein eigenes Bild steht in savings_goal_image und hat Vorrang.
    icon             VARCHAR(40)     NULL,

    target_amount    DECIMAL(19,2)   NOT NULL,
    assigned_amount  DECIMAL(19,2)   NOT NULL DEFAULT 0.00,

    -- Optional. Kleinere Zahl = höhere Priorität (Sortierung im Verteil-Dialog)
    priority         INT             NULL,

    -- Optionales Wunschdatum
    target_date      DATE            NULL,

    created_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    -- ACTIVE | REACHED | PAUSED  (EnumType.STRING)
    status           VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',

    -- Optimistic Locking (@Version), siehe account.version
    version          BIGINT          NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    CONSTRAINT fk_goal_account
        FOREIGN KEY (account_id) REFERENCES account (id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT,
    CONSTRAINT chk_goal_target_positive
        CHECK (target_amount > 0),
    CONSTRAINT chk_goal_assigned_not_negative
        CHECK (assigned_amount >= 0),
    CONSTRAINT chk_goal_status
        CHECK (status IN ('ACTIVE', 'REACHED', 'PAUSED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_goal_account         ON savings_goal (account_id);
CREATE INDEX idx_goal_status          ON savings_goal (account_id, status);
CREATE INDEX idx_goal_priority        ON savings_goal (account_id, priority);

-- Namen pro Konto eindeutig halten - verhindert zwei Töpfe "Motorrad"
CREATE UNIQUE INDEX uq_goal_name_per_account ON savings_goal (account_id, name);

-- ---------------------------------------------------------------------
--  savings_goal_image - optionales eigenes Bild eines Sparziels: angezeigter
--  Ausschnitt plus Quellbild, damit sich der Ausschnitt später ohne neuen
--  Upload ändern lässt. Eigene Tabelle, damit Listen die Bilddaten nicht
--  mitladen. Wird mit dem Sparziel gelöscht.
-- ---------------------------------------------------------------------
CREATE TABLE savings_goal_image (
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

-- ---------------------------------------------------------------------
--  booking - unveränderliche Historie aller Ein-, Um- und Auszahlungen
--
--  source_goal_id IS NULL -> Quelle ist der Puffer (bei DEPOSIT: von außen)
--  target_goal_id IS NULL -> Ziel   ist der Puffer (bei WITHDRAWAL: nach außen)
--
--  DEPOSIT    : source=NULL, target=NULL   (von außen in den Puffer)
--  TRANSFER   : source=NULL, target=Ziel   (Puffer -> Sparziel)
--  TRANSFER   : source=Ziel, target=NULL   (Sparziel -> Puffer)
--  TRANSFER   : source=A,    target=B      (Sparziel -> Sparziel)
--  WITHDRAWAL : source=NULL, target=NULL   (Betrag aus dem Puffer nach außen)
--  WITHDRAWAL : source=Ziel, target=NULL   (Sparziel komplett nach außen)
-- ---------------------------------------------------------------------
CREATE TABLE booking (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    account_id      BIGINT          NOT NULL,

    booked_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    -- Immer positiv. Richtung ergibt sich aus type + source/target
    amount          DECIMAL(19,2)   NOT NULL,

    source_goal_id  BIGINT          NULL,
    target_goal_id  BIGINT          NULL,

    -- DEPOSIT | TRANSFER | WITHDRAWAL  (EnumType.STRING)
    type            VARCHAR(20)     NOT NULL,

    note            VARCHAR(255)    NULL,

    PRIMARY KEY (id),
    CONSTRAINT fk_booking_account
        FOREIGN KEY (account_id) REFERENCES account (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    -- RESTRICT ist Absicht: ein Ziel mit Historie darf nicht einfach
    -- gelöscht werden. Ziele werden auf REACHED/PAUSED gesetzt, nicht entfernt.
    CONSTRAINT fk_booking_source_goal
        FOREIGN KEY (source_goal_id) REFERENCES savings_goal (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,
    CONSTRAINT fk_booking_target_goal
        FOREIGN KEY (target_goal_id) REFERENCES savings_goal (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT chk_booking_amount_positive
        CHECK (amount > 0),
    CONSTRAINT chk_booking_type
        CHECK (type IN ('DEPOSIT', 'TRANSFER', 'WITHDRAWAL')),

    -- Quelle und Ziel dürfen nicht identisch sein
    CONSTRAINT chk_booking_source_ne_target
        CHECK (source_goal_id IS NULL
               OR target_goal_id IS NULL
               OR source_goal_id <> target_goal_id),

    -- Erlaubte Kombinationen je Buchungsart erzwingen
    CONSTRAINT chk_booking_type_combination CHECK (
        (type = 'DEPOSIT'    AND source_goal_id IS NULL AND target_goal_id IS NULL)
     OR (type = 'WITHDRAWAL' AND target_goal_id IS NULL)
     OR (type = 'TRANSFER'   AND NOT (source_goal_id IS NULL AND target_goal_id IS NULL))
    )
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_booking_account_time ON booking (account_id, booked_at DESC);
CREATE INDEX idx_booking_source       ON booking (source_goal_id);
CREATE INDEX idx_booking_target       ON booking (target_goal_id);

-- ---------------------------------------------------------------------
--  Hilfs-View: Prüfsumme. Muss immer difference = 0.00 liefern.
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW v_account_consistency AS
SELECT
    a.id                                              AS account_id,
    a.name                                            AS account_name,
    a.current_balance                                 AS current_balance,
    a.unassigned_buffer                               AS unassigned_buffer,
    COALESCE(SUM(g.assigned_amount), 0.00)            AS assigned_total,
    a.current_balance
        - a.unassigned_buffer
        - COALESCE(SUM(g.assigned_amount), 0.00)      AS difference
FROM account a
LEFT JOIN savings_goal g ON g.account_id = a.id
GROUP BY a.id, a.name, a.current_balance, a.unassigned_buffer;

-- ---------------------------------------------------------------------
--  Startdatensatz: das eine reale Konto
-- ---------------------------------------------------------------------
INSERT INTO account (name, current_balance, unassigned_buffer, version)
VALUES ('Sparbuch', 0.00, 0.00, 0);

-- ---------------------------------------------------------------------
--  app_user - Benutzer der Anwendung (Login).
--  Der Admin-Account steht NICHT hier, sondern kommt aus der .env
--  (ADMIN_USER / ADMIN_PASSWORD). Passwörter: BCrypt-Hash.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app_user (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    username       VARCHAR(100)  NOT NULL,
    password_hash  VARCHAR(100)  NOT NULL,
    enabled        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_app_user_username UNIQUE (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
--  persistent_logins - "Angemeldet bleiben" (Remember-Me von Spring Security).
--  Eine Zeile pro angemeldetem Browser; das Cookie enthält series + token.
--  Spaltennamen sind von Spring vorgegeben (JdbcTokenRepositoryImpl).
--  Abgelaufene Zeilen räumt RememberMeTokenCleanup nachts auf.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS persistent_logins (
    -- Kanonischer Benutzername (app_user.username bzw. ADMIN_USER), kein Fremdschlüssel,
    -- weil der Admin nicht in app_user steht
    username   VARCHAR(100)  NOT NULL,
    -- Zufallswerte (Base64); binär verglichen, damit Groß-/Kleinschreibung zählt
    series     VARCHAR(64)   CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    token      VARCHAR(64)   CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    last_used  DATETIME(6)   NOT NULL,
    PRIMARY KEY (series),
    KEY idx_persistent_logins_username (username),
    KEY idx_persistent_logins_last_used (last_used)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
