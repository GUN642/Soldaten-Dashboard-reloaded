# Soldaten Dashboard Reloaded

Das Soldaten Dashboard – komplett neu als **native Android-App** (Kotlin, Jetpack Compose)
im **Nothing-Stil**: Punktschrift, Punktraster, klare Linien, Schwarz/Weiß mit einem Akzent.

Läuft **parallel** zum alten Soldaten Dashboard (eigene App-ID `de.gun.dashboard.reloaded`).
Die Daten lassen sich über die Sicherungsdatei übernehmen.

## Funktionen

| Reiter | Inhalt |
|---|---|
| **Heute** | Uhrzeit, KW, Feiertag/Ferien, Resturlaub, Mehrarbeit, Fristen, Termine und Aufgaben des Tages, Wetter (Open-Meteo oder meteoblue) |
| **Kalender** | Monatsraster mit durchgehenden Balken für mehrtägige Termine, Wischen zwischen Monaten, lange drücken = neuer Termin, Vollbild per Tipp auf den Monat, Feiertage und Schulferien |
| **To-do** | Aufgaben mit Fälligkeit, Uhrzeit, Priorität, Notiz und Anhängen, Rückgängig beim Löschen |
| **Notizen** | Checklisten (abhaken, zurücksetzen) und persönliche Notizen |
| **Urlaub/Mehrarbeit** | Urlaubskonto (Werktage ohne Feiertage des Bundeslands, geplant/scharf, Zugänge, automatischer Jahreswechsel) und Überstundenkonto |
| **Lehrgänge** | Ablaufregister mit Ampel (gelb ab 6 Monate vor Ablauf) und Dauer-Rechner |
| **Dokumente** | Ausweise, Pässe, Führerscheine mit Kopien (Foto/PDF) |
| **Akte** | Person, IGF mit Pflichtbestandteilen, ICCS, AVU/WFV, Impfungen, Übersicht drucken/teilen |
| **Tools** | Zulu-Zeit & DTG, Koordinaten (WGS84 ⇄ UTM ⇄ MGRS), Einheiten, DUZ-Rechner (§ 3 EZulV), BFT-Bewertung, AVZ |

Außerdem: Suche über alle Bereiche, Erinnerungen (Termine, Aufgaben, Fristen, Sicherung),
Homescreen-Widget (Agenda), Einrichtungsassistent, Update-Prüfung über GitHub-Releases.

### Kalender

Die App liest und schreibt **direkt die Kalender des Geräts** (Google, Outlook, Samsung …).
Liegt dort dein Outlook-Konto, überträgt Android neue Termine selbst in die Cloud.
Serientermine werden von Android aufgelöst; für Serien, bei denen das nicht klappt
(z. B. über FamilyWall/Apple eingespielt), rechnet die App die Vorkommen selbst aus.
Einzelne Vorkommen einer Serie lassen sich gezielt löschen. Zusätzlich lassen sich
ICS-Abos (webcal) und ICS-Dateien einbinden, Geburtstage kommen aus den Kontakten.

### Designs

Im Menü unter **Design**:

* **Nothing** – tiefschwarz (AMOLED)
* **Nothing hell** – weiß
* **Graphit** – gedämpftes Dunkelgrau
* **Aulumu** – warmes Schwarz
* **System** – folgt Hell/Dunkel des Geräts

Dazu sieben Akzentfarben (Nothing-Rot, Orange, Amber, Grün, Blau, Violett, Mono) sowie
Punktschrift und Punktraster zum Ein- und Ausschalten.

## Daten aus dem alten Dashboard übernehmen

1. Im **alten** Soldaten Dashboard: Menü → Sicherung → **Export .json**
2. Datei auf dem Handy ablegen (oder in Google Drive)
3. In **Reloaded**: Menü → Sicherung → **Importieren** → Datei wählen

Übernommen werden alle Bereiche einschließlich Anhängen. Eine in Reloaded erstellte
Sicherung lässt sich umgekehrt auch im alten Dashboard einlesen.

## APK bauen und installieren

Der Build läuft über **GitHub Actions** (`.github/workflows/build-apk.yml`), bei jedem Push:

* Unter **Actions** den Lauf öffnen → unten unter **Artifacts** liegt
  `SoldatenDashboardReloaded-APK` (ZIP mit der APK).
* Auf `main`/`master` entsteht zusätzlich ein **Release** mit der APK – daraus
  liest die App ihre Update-Prüfung.

Installation: APK auf das Handy, antippen, Installation aus dieser Quelle erlauben.

### Signierschlüssel

`keystore/reloaded.keystore` ist ein fester Schlüssel für den Eigengebrauch. Dadurch lässt
sich jede neue Version über die vorhandene installieren, ohne Datenverlust.
**Nicht ersetzen**, sonst verlangt das nächste Update eine Deinstallation.
Für den Play Store wäre ein eigener, geheim gehaltener Schlüssel nötig.

### Version erhöhen

In `app/build.gradle.kts` `appVersionName` und `appVersionCode` anheben.

## Aufbau

```
app/src/main/java/de/gun/dashboard/reloaded/
  daten/      Datenmodell (kompatibel zur alten Sicherung), Speicher, Anhänge, Sicherung
  logik/      Datum, Feiertage, ICS/Wiederholungen, Termine, Konten, Werkzeuge (DTG, MGRS, BFT, DUZ)
  geraet/     Gerätekalender (CalendarContract), Kontakte
  netz/       Wetter, Schulferien, Update-Prüfung
  erinnerung/ Benachrichtigungen (AlarmManager)
  widget/     Homescreen-Widget (Glance)
  ui/         Design (Themen, Schriften, Bausteine) und alle Seiten
```

Alle Angaben ohne Gewähr; die App ersetzt keine offizielle Nachweisführung.
Schriften: Doto, JetBrains Mono, Space Grotesk (SIL Open Font License).
