# LocalDB

Lokale MariaDB 11 für die Entwicklung von CashBox.

## Start / Stop
```bash
cd LocalDB
docker compose up -d        # starten
docker compose down         # stoppen (Daten bleiben erhalten)
docker compose down -v      # stoppen und Daten löschen
```

## Zugang
MariaDB: `localhost:3306`, Benutzer `cashbox` / Passwort `cashbox`, Datenbank `cashbox`

JDBC-URL: `jdbc:mariadb://localhost:3306/cashbox`

Werte lassen sich über `.env` (Vorlage: `.env.example`) ändern.

## Schema
Die Datenbank startet leer. Tabellen, Constraints, die View `v_account_consistency` und das
Startkonto „Sparbuch" legt die Anwendung beim ersten Start selbst an (Flyway, Skripte unter
`src/main/resources/db/migration`). Flyway merkt sich in der Tabelle `flyway_schema_history`,
was schon eingespielt ist, und spielt bei jedem Start nur neue Skripte nach.

Schemaänderung: neue Datei `V2__kurze_beschreibung.sql` (dann `V3__…`) anlegen. Bereits
eingespielte Dateien nie mehr ändern, sonst bricht der Start mit einem Prüfsummenfehler ab.

Hibernate läuft mit `ddl-auto=validate` und prüft das Schema nur gegen.
