# CashBox
CashBox ist eine Java/Spring-Boot-Anwendung, mit der sich das Guthaben eines realen
Sparkontos auf virtuelle Spartöpfe (Sparziele) verteilen lässt. Jede Ein-, Aus- und
Umbuchung wird als unveränderliche Buchung protokolliert; nicht zugeordnetes Geld
bleibt in einem Puffer. Die Konsistenz (Kontostand = Puffer + Summe der Sparziele)
wird sowohl in der Anwendung als auch in der Datenbank geprüft.

Stack: Java 21, Spring Boot, Vaadin, JPA/Hibernate, Flyway, MariaDB 11, Docker

## Betrieb mit Docker
Das Image `ghcr.io/longri/cashbox` gibt es für `linux/amd64` und `linux/arm64`
(z. B. Raspberry Pi oder NAS). Auf dem Server braucht man nur Docker und die beiden
Dateien aus [`deploy/`](deploy):

```bash
mkdir cashbox && cd cashbox
curl -fsSLO https://raw.githubusercontent.com/Longri/CashBox/main/deploy/docker-compose.yml
curl -fsSL -o .env https://raw.githubusercontent.com/Longri/CashBox/main/deploy/.env.example
nano .env                  # Passwörter setzen
docker compose up -d
```

CashBox läuft dann auf `http://<server>:8080`. Die Datenbanktabellen legt die Anwendung
beim ersten Start selbst an.

- **Update:** `docker compose pull && docker compose up -d`. Neue Schemaänderungen spielt
  die Anwendung beim Start selbst ein.
- **Backup:**
  `docker compose exec db sh -c 'mariadb-dump -ucashbox -p"$MARIADB_PASSWORD" cashbox' > cashbox-$(date +%F).sql`
- **Aus dem Internet erreichbar:** nur hinter einem Reverse-Proxy mit HTTPS (z. B. Caddy,
  Traefik oder nginx), sonst gehen Passwort und Anmelde-Cookie im Klartext übers Netz.

Image selbst bauen: `docker build -t cashbox .`

## Entwicklung
Lokale Datenbank: siehe [`LocalDB/README.md`](LocalDB/README.md). Admin-Zugang in `.env`
(Vorlage `.env.example`), danach `./mvnw` starten. Tests (`./mvnw test`) laufen gegen die
lokale MariaDB.

## Neue Version veröffentlichen
Der Workflow [`.github/workflows/docker.yml`](.github/workflows/docker.yml) testet jeden Push
gegen eine frische MariaDB und baut das Image. Veröffentlicht wird es bei einem Versions-Tag:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Daraus entstehen `ghcr.io/longri/cashbox:1.0.0`, `:1.0` und `:latest`.
