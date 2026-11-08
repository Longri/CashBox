-- =====================================================================
--  Migration: "Angemeldet bleiben" (Remember-Me-Tokens)
--
--  Für Datenbanken, die vor dieser Änderung angelegt wurden. Neue Volumes
--  bekommen die Tabelle direkt über init/01-schema.sql. Mehrfach ausführbar.
--
--    docker exec -i cashbox-db mariadb -ucashbox -pcashbox cashbox < migrations/003-remember-me.sql
-- =====================================================================

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
