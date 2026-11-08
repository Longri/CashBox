# CashBox
CashBox ist eine Java/Spring-Boot-Anwendung, mit der sich das Guthaben eines realen
Sparkontos auf virtuelle Spartöpfe (Sparziele) verteilen lässt. Jede Ein-, Aus- und
Umbuchung wird als unveränderliche Buchung protokolliert; nicht zugeordnetes Geld
bleibt in einem Puffer. Die Konsistenz (Kontostand = Puffer + Summe der Sparziele)
wird sowohl in der Anwendung als auch in der Datenbank geprüft.

Stack: Java 21, Spring Boot, Vaadin, JPA/Hibernate, Flyway, MariaDB 11, Docker

- [Installation auf einem Server](#installation-auf-einem-server)
- [Betrieb: Update, Backup, Wiederherstellen](#betrieb)
- [Entwicklung](#entwicklung)
- [Neue Version veröffentlichen](#neue-version-veröffentlichen)

## Installation auf einem Server
CashBox läuft als Docker-Container zusammen mit einer MariaDB. Das fertige Image
`ghcr.io/longri/cashbox` gibt es für `linux/amd64` (normale Server und PCs) und
`linux/arm64` (z. B. Raspberry Pi oder NAS mit 64-Bit-System).

### Voraussetzungen
Docker mit dem Compose-Plugin. Ob beides da ist, zeigt:

```bash
docker compose version
```

Falls nicht: [Docker installieren](https://docs.docker.com/engine/install/).

### 1. Ordner anlegen und Dateien holen
Auf dem Server braucht man nur die beiden Dateien aus [`deploy/`](deploy):

```bash
mkdir cashbox && cd cashbox
curl -fsSLO https://raw.githubusercontent.com/Longri/CashBox/main/deploy/docker-compose.yml
curl -fsSL -o .env https://raw.githubusercontent.com/Longri/CashBox/main/deploy/.env.example
chmod 600 .env
```

### 2. Einstellungen in `.env` setzen
```bash
nano .env
```

| Variable | Bedeutung |
|---|---|
| `ADMIN_USER`, `ADMIN_PASSWORD` | Admin-Zugang der Anwendung. Der Admin steht nicht in der Datenbank, sondern nur hier. |
| `DB_PASSWORD` | Passwort, mit dem die Anwendung auf die Datenbank zugreift |
| `DB_ROOT_PASSWORD` | Root-Passwort der Datenbank |
| `REMEMBER_ME_DAYS` | So viele Tage bleibt man nach dem Login angemeldet (`0` = jedes Mal neu anmelden) |
| `CASHBOX_PORT` | Port, unter dem CashBox erreichbar ist (Vorgabe `8080`) |
| `CASHBOX_VERSION` | `latest` oder eine feste Version wie `1.0.0` |
| `TZ` | Zeitzone, Vorgabe `Europe/Berlin` |

Alle drei Passwörter ändern! Ein sicheres Passwort erzeugt z. B.:

```bash
openssl rand -hex 24
```

> **Wichtig:** `DB_PASSWORD` und `DB_ROOT_PASSWORD` werden beim ersten Start in die neue
> Datenbank geschrieben. Ändert man sie später nur in der `.env`, passt die Datenbank nicht
> mehr dazu und die Anwendung kann sich nicht verbinden. Die `.env` also gut aufbewahren.

### 3. Starten
```bash
docker compose up -d
```

Beim ersten Start lädt Docker die Images, die Datenbank richtet sich ein und die Anwendung
legt ihre Tabellen an. Den Stand zeigen:

```bash
docker compose ps
```

```bash
docker compose logs -f app
```

Fertig ist es, wenn im Log `Started Application` steht (Anzeige mit `Strg+C` beenden,
CashBox läuft weiter).

### 4. Anmelden
Im Browser `http://<server>:8080` öffnen und mit `ADMIN_USER` / `ADMIN_PASSWORD` anmelden.
Weitere Benutzer legt der Admin unter **Benutzer** an.

Admin-Passwort ändern: neuen Wert in die `.env` schreiben und `docker compose up -d`
ausführen.

### 5. Aus dem Internet erreichbar machen (HTTPS)
CashBox selbst spricht nur HTTP. Ohne HTTPS gingen Passwort und Anmelde-Cookie im Klartext
übers Netz, deshalb gehört ein Reverse-Proxy davor, z. B. [Caddy](https://caddyserver.com),
das sich die Zertifikate selbst besorgt. Voraussetzung: Eine Domain zeigt auf den Server,
Port 80 und 443 sind offen.

In der `.env` CashBox nur noch lokal erreichbar machen, damit niemand am Proxy vorbei kommt:

```
CASHBOX_PORT=127.0.0.1:8080
```

Dann `docker compose up -d` und im `Caddyfile` des direkt auf dem Server installierten Caddy:

```
cashbox.example.de {
    reverse_proxy localhost:8080
}
```

### 6. Als App auf den Home-Bildschirm legen
CashBox bringt ein Web-App-Manifest und ein Icon mit. Auf dem iPhone geht das in Safari und,
ab iOS 16.4, auch in Chrome: Teilen-Symbol → „Zum Home-Bildschirm“ → „Hinzufügen“. Ab iOS 26
den Schalter „Als Web-App öffnen“ eingeschaltet lassen. Das Icon öffnet CashBox dann ohne
Adressleiste. Auf Android steht im Chrome-Menü „App installieren“ oder „Zum Startbildschirm
hinzufügen“.

Voraussetzung ist HTTPS (siehe 5.), denn Browser behandeln eine Seite nur über HTTPS als
vollwertige Web-App.

Die App vom Home-Bildschirm hat eigene Cookies, getrennt vom Browser. Man meldet sich dort
also einmal an und bleibt dann so lange angemeldet, wie `REMEMBER_ME_DAYS` angibt.

## Betrieb
Alle Befehle im Ordner mit der `docker-compose.yml` ausführen.

### Update auf eine neue Version
Vorher ein [Backup](#backup) machen, dann:

```bash
docker compose pull && docker compose up -d
```

Neue Datenbankänderungen spielt die Anwendung beim Start selbst ein. Mit
`CASHBOX_VERSION=latest` kommt immer die neueste Version; mit einer festen Nummer
(z. B. `1.0.0`) bleibt es bei dieser, bis man sie in der `.env` ändert.

### Stoppen und starten
```bash
docker compose stop
```

```bash
docker compose start
```

`docker compose down` entfernt die Container, die Daten bleiben erhalten.

> **Achtung:** `docker compose down -v` löscht zusätzlich die Datenbank mit allen Daten.

### Backup
Schreibt den kompletten Datenstand in eine Datei, ohne CashBox anzuhalten:

```bash
docker compose exec -T db sh -c 'mariadb-dump --single-transaction -ucashbox -p"$MARIADB_PASSWORD" cashbox' > cashbox-$(date +%F).sql
```

Automatisch jede Nacht um 2:30 Uhr per `crontab -e` (Ordner `backup` vorher anlegen,
Pfad anpassen; in der Crontab muss `%` als `\%` stehen):

```
30 2 * * * cd /pfad/zu/cashbox && docker compose exec -T db sh -c 'mariadb-dump --single-transaction -ucashbox -p"$MARIADB_PASSWORD" cashbox' > backup/cashbox-$(date +\%F).sql
```

Backups zusätzlich auf einem anderen Gerät ablegen.

### Wiederherstellen
Ersetzt den aktuellen Datenstand komplett durch den aus der Sicherung:

```bash
docker compose stop app
```

```bash
docker compose exec -T db sh -c 'mariadb -ucashbox -p"$MARIADB_PASSWORD" cashbox' < cashbox-2026-11-08.sql
```

```bash
docker compose start app
```

### Logs ansehen
```bash
docker compose logs -f app
```

## Entwicklung
Lokale Datenbank: siehe [`LocalDB/README.md`](LocalDB/README.md). Admin-Zugang in `.env`
(Vorlage `.env.example`), danach `./mvnw` starten. Tests (`./mvnw test`) laufen gegen die
lokale MariaDB.

Datenbankänderungen kommen als neue Datei `src/main/resources/db/migration/V2__beschreibung.sql`
(dann `V3__…`). Bereits veröffentlichte Dateien nie mehr ändern.

Image lokal bauen: `docker build -t cashbox .`

## Neue Version veröffentlichen
Der Workflow [`.github/workflows/docker.yml`](.github/workflows/docker.yml) testet jeden Push
gegen eine frische MariaDB und baut das Image. Veröffentlicht wird es erst bei einem
Versions-Tag.

### 1. Änderungen pushen
Committen und auf `main` pushen. Unter
[Actions](https://github.com/Longri/CashBox/actions) prüfen, dass der Lauf grün ist.

### 2. Versionsnummer wählen
Format `vHAUPT.NEBEN.FIX`, z. B. `v1.0.1` für Fehlerbehebungen, `v1.1.0` für neue
Funktionen, `v2.0.0` für Änderungen, bei denen bestehende Installationen angepasst werden
müssen.

### 3. Tag anlegen und pushen
```bash
git tag v1.1.0
```

```bash
git push origin v1.1.0
```

Ein normaler Push überträgt keine Tags, der Tag muss immer extra gepusht werden. In einem
Git-Programm (SmartGit, IntelliJ usw.) geht das per Rechtsklick auf den Tag → Push.

GitHub nimmt beim Push im Terminal kein Passwort an. Möglich sind:
- ein Git-Programm, das mit dem GitHub-Konto angemeldet ist,
- die [GitHub CLI](https://cli.github.com): einmal `gh auth login` und die Frage nach
  Git-Zugangsdaten mit „Yes“ beantworten,
- ein Personal Access Token (GitHub → Settings → Developer settings → Fine-grained tokens,
  nur Repository CashBox, Berechtigungen **Contents** und **Workflows** auf „Read and write“),
  der bei der Passwortabfrage eingegeben wird. macOS merkt ihn sich im Schlüsselbund.

### 4. Warten, bis das Image da ist
Unter [Actions](https://github.com/Longri/CashBox/actions) läuft „Docker-Image“ für den Tag.
Nach einigen Minuten gibt es `ghcr.io/longri/cashbox:1.1.0`, `:1.1` und `:latest`. Die Server
holen die neue Version dann mit dem [Update](#update-auf-eine-neue-version).
