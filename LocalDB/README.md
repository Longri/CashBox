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
Beim ersten Start (leeres Volume) legt MariaDB Datenbank, Tabellen, Constraints, die View
`v_account_consistency` und das Startkonto „Sparbuch" automatisch über `init/01-schema.sql` an.
Das Skript läuft **nur bei leerem Volume**. Nach Schema-Änderungen:

```bash
docker compose down -v && docker compose up -d
```

Hibernate läuft mit `ddl-auto=validate` und prüft das Schema nur gegen.
