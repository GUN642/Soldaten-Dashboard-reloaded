package de.gun.dashboard.reloaded.ui.seiten

import de.gun.dashboard.reloaded.netz.UpdateLader
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gun.dashboard.reloaded.Aktualisierung
import de.gun.dashboard.reloaded.BuildConfig
import de.gun.dashboard.reloaded.daten.BUNDESLAENDER
import de.gun.dashboard.reloaded.daten.Sicherung
import de.gun.dashboard.reloaded.daten.Speicher
import de.gun.dashboard.reloaded.erinnerung.Erinnerungen
import de.gun.dashboard.reloaded.logik.heuteDE
import de.gun.dashboard.reloaded.logik.parseDE
import de.gun.dashboard.reloaded.logik.zeitNormieren
import de.gun.dashboard.reloaded.netz.UpdateDienst
import de.gun.dashboard.reloaded.ui.AKZENTE
import de.gun.dashboard.reloaded.ui.Abstand
import de.gun.dashboard.reloaded.ui.Auswahl
import de.gun.dashboard.reloaded.ui.Etikett
import de.gun.dashboard.reloaded.ui.Feld
import de.gun.dashboard.reloaded.ui.Fliesstext
import de.gun.dashboard.reloaded.ui.Hinweis
import de.gun.dashboard.reloaded.ui.Karte
import de.gun.dashboard.reloaded.ui.Knopf
import de.gun.dashboard.reloaded.ui.KnopfArt
import de.gun.dashboard.reloaded.ui.Knopfreihe
import de.gun.dashboard.reloaded.ui.LocalPalette
import de.gun.dashboard.reloaded.ui.LocalSteuerung
import de.gun.dashboard.reloaded.ui.Mono
import de.gun.dashboard.reloaded.ui.Pille
import de.gun.dashboard.reloaded.ui.Punkt
import de.gun.dashboard.reloaded.ui.PunktRaster
import de.gun.dashboard.reloaded.ui.RUND_KLEIN
import de.gun.dashboard.reloaded.ui.Regler
import de.gun.dashboard.reloaded.ui.Segmente
import de.gun.dashboard.reloaded.ui.Symbol
import de.gun.dashboard.reloaded.ui.THEMEN
import de.gun.dashboard.reloaded.ui.alleRechte
import de.gun.dashboard.reloaded.ui.palette
import de.gun.dashboard.reloaded.ui.titelSchrift
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MenueEbene() {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val d = aktuelleDaten()
    var importFrage by remember { mutableStateOf<Sicherung.ImportErgebnis?>(null) }
    var updateStatus by remember { mutableStateOf("") }
    var benStatus by remember { mutableStateOf("") }

    fun exportText(): String = Sicherung.exportieren(ctx, Speicher.aktuell)
    fun dateiname() = "SoldatenDashboard_v${BuildConfig.VERSION_NAME}_${LocalDate.now()}.json"
    fun gesichert() = Speicher.aendern { it.copy(sicherung = it.sicherung.copy(letzte = heuteDE())) }

    val speichernUnter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) scope.launch {
            try {
                val text = withContext(Dispatchers.IO) { exportText() }
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } }
                gesichert(); st.kurz("Sicherung gespeichert")
            } catch (e: Exception) { st.melden("Sicherung", "Die Sicherung konnte nicht erstellt werden.\n\n" + e.message) }
        }
    }
    val oeffnen = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            try {
                val erg = withContext(Dispatchers.IO) {
                    val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                    Sicherung.importieren(ctx, text, Speicher.aktuell)
                }
                importFrage = erg
            } catch (e: Exception) {
                st.melden("Einlesen fehlgeschlagen", e.message ?: "Die Datei scheint keine gültige Sicherung zu sein.")
            }
        }
    }
    val rechte = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        Aktualisierung.geraetLaden(ctx)
        benStatus = if (Erinnerungen.darfBenachrichtigen(ctx)) "Benachrichtigungen sind erlaubt." else "Benachrichtigungen wurden nicht erlaubt."
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Symbol("✕", p.text) { st.menueOffen = false }
            Spacer(Modifier.width(6.dp))
            Punkt("MENÜ", 20.sp)
        }
        SeitenListe {
            // ------------------------------------------------ Design
            item {
                Karte("Design", "01") {
                    Etikett("Thema")
                    Column(Modifier.fillMaxWidth()) {
                        THEMEN.chunked(2).forEach { reihe ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                reihe.forEach { t ->
                                    val vorschau = palette(d.design.copy(thema = t.id), true)
                                    val aktiv = d.design.thema == t.id
                                    Column(
                                        Modifier.weight(1f).clip(RUND_KLEIN).background(vorschau.bg)
                                            .border(if (aktiv) 2.dp else 1.dp, if (aktiv) p.akzent else p.rand, RUND_KLEIN)
                                            .clickable { Speicher.aendern { it.copy(design = it.design.copy(thema = t.id)) } }
                                    ) {
                                        Box(Modifier.fillMaxWidth().height(34.dp)) {
                                            PunktRaster(Modifier.fillMaxSize(), vorschau.randLeise, 7.dp)
                                            Box(Modifier.padding(10.dp).size(10.dp).clip(CircleShape).background(vorschau.akzent))
                                        }
                                        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                            androidx.compose.material3.Text(t.name.uppercase(), color = vorschau.text,
                                                style = androidx.compose.ui.text.TextStyle(fontFamily = p.titelSchrift, fontSize = 15.sp,
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold))
                                            androidx.compose.material3.Text(t.beschreibung, color = vorschau.textDim, fontSize = 11.sp)
                                        }
                                    }
                                }
                                if (reihe.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    Abstand()
                    Etikett("Akzentfarbe")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        AKZENTE.forEach { a ->
                            val c = if (p.hell) a.hell else a.dunkel
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                                Speicher.aendern { it.copy(design = it.design.copy(akzent = a.id)) }
                            }) {
                                Box(Modifier.size(34.dp).clip(CircleShape).background(c)
                                    .border(2.dp, if (d.design.akzent == a.id) p.text else Color.Transparent, CircleShape))
                                Mono(a.name, if (d.design.akzent == a.id) p.text else p.textFaint, 9.sp)
                            }
                        }
                    }
                    Abstand()
                    Knopfreihe {
                        Pille("Punktschrift", d.design.punktSchrift) { Speicher.aendern { it.copy(design = it.design.copy(punktSchrift = !it.design.punktSchrift)) } }
                        Pille("Punktraster", d.design.punktRaster) { Speicher.aendern { it.copy(design = it.design.copy(punktRaster = !it.design.punktRaster)) } }
                        Pille("Reiterleiste unten", d.design.reiterUnten) { Speicher.aendern { it.copy(design = it.design.copy(reiterUnten = !it.design.reiterUnten)) } }
                    }
                }
            }
            // ------------------------------------------------ Sicherung
            item {
                Karte("Sicherung", "02") {
                    val letzte = parseDE(d.sicherung.letzte)
                    val tage = letzte?.let { java.time.temporal.ChronoUnit.DAYS.between(it, LocalDate.now()) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(when {
                            tage == null || tage >= 90 -> p.rot
                            tage >= 30 -> p.warn
                            else -> p.gruen
                        }))
                        Spacer(Modifier.width(8.dp))
                        Fliesstext(
                            if (tage == null) "Noch keine eigene Sicherung erstellt."
                            else "Letzte eigene Sicherung: " + (if (tage <= 0) "heute" else if (tage == 1L) "gestern" else "vor $tage Tagen") + " (${d.sicherung.letzte})",
                            p.textDim, 13.sp
                        )
                    }
                    Knopfreihe {
                        Knopf("↓ Exportieren (Teilen)", art = KnopfArt.PRIMAER) {
                            scope.launch {
                                try {
                                    val text = withContext(Dispatchers.IO) { exportText() }
                                    dateiTeilen(ctx, text, dateiname(), "application/json")
                                    gesichert()
                                } catch (e: Exception) { st.melden("Sicherung", "Die Sicherung konnte nicht erstellt werden.\n\n" + e.message) }
                            }
                        }
                        Knopf("↓ Speichern unter …") { speichernUnter.launch(dateiname()) }
                        Knopf("↑ Importieren") { oeffnen.launch(arrayOf("application/json", "text/*", "application/octet-stream", "*/*")) }
                    }
                    Hinweis("Das Format ist dasselbe wie beim alten Soldaten Dashboard: Eine dort exportierte .json lässt sich hier einlesen – und umgekehrt.")
                    Hinweis("Beim Teilen lässt sich z. B. Google Drive als Ziel wählen. Zusätzlich sichert Android die Daten selbsttätig ins Google-Konto.")
                }
            }
            // ------------------------------------------------ Benachrichtigungen
            item {
                Karte("Benachrichtigungen", "03") {
                    Pille("Erinnerungen aktiv", d.ben.an) { Speicher.aendern { it.copy(ben = it.ben.copy(an = !it.ben.an)) } }
                    Knopfreihe {
                        Knopf("Erlauben", klein = true) {
                            if (Build.VERSION.SDK_INT >= 33) rechte.launch(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS))
                            else benStatus = "Benachrichtigungen sind erlaubt."
                        }
                        Knopf("↻ Neu planen", klein = true) {
                            val n = Erinnerungen.planen(ctx)
                            benStatus = if (n > 0) "$n Erinnerung" + (if (n == 1) "" else "en") + " geplant." else "Keine anstehenden Erinnerungen."
                        }
                    }
                    if (benStatus.isNotBlank()) Hinweis(benStatus)
                    Row {
                        var vorlauf by remember { mutableStateOf(d.ben.vorlaufMin.toString()) }
                        Feld(vorlauf, { v -> vorlauf = v; v.toIntOrNull()?.takeIf { it >= 0 }?.let { n -> Speicher.aendern { it.copy(ben = it.ben.copy(vorlaufMin = n)) } } },
                            "Termin: Minuten vorher", Modifier.weight(1f), tastatur = KeyboardType.Number)
                        Spacer(Modifier.width(8.dp))
                        var gz by remember { mutableStateOf(d.ben.ganztagsZeit) }
                        Feld(gz, { gz = it }, "Ganztägig: Vortag um", Modifier.weight(1f), beiVerlassen = {
                            zeitNormieren(gz)?.let { n -> gz = n; Speicher.aendern { it.copy(ben = it.ben.copy(ganztagsZeit = n)) } }
                        })
                    }
                    Hinweis("Für selbst angelegte Termine, ablaufende Lehrgänge, Dokumente und Akte-Fristen sowie fällige Aufgaben.")
                }
            }
            // ------------------------------------------------ Bundesland
            item {
                Karte("Bundesland & Kalender", "04") {
                    Auswahl("Bundesland (Feiertage)", BUNDESLAENDER.map { it.key to it.value }, d.feiertagsLand.land) { land ->
                        Speicher.aendern { it.copy(feiertagsLand = it.feiertagsLand.copy(land = land), ferien = it.ferien.copy(jahre = emptyMap(), land = land)) }
                        scope.launch { val j = LocalDate.now().year; for (x in j..j + 2) ferienSicherstellen(x) }
                    }
                    Hinweis("Bestimmt die Feiertage im Kalender und welche Tage bei der Urlaubsberechnung nicht zählen.")
                    Knopfreihe {
                        Pille("Schulferien anzeigen", d.ferien.an) {
                            val an = !d.ferien.an
                            Speicher.aendern { it.copy(ferien = it.ferien.copy(an = an)) }
                            if (an) scope.launch { val j = LocalDate.now().year; for (x in j..j + 2) ferienSicherstellen(x) }
                        }
                        Knopf("↻ Ferien laden", klein = true) {
                            scope.launch {
                                Speicher.aendern { it.copy(ferien = it.ferien.copy(jahre = emptyMap())) }
                                val j = LocalDate.now().year
                                val n = (j..j + 2).sumOf { maxOf(0, ferienSicherstellen(it, true)) }
                                st.kurz(if (n > 0) "$n Ferienabschnitte geladen" else "Ferien nicht abrufbar – besteht eine Netzverbindung?")
                            }
                        }
                    }
                    val anzahl = d.ferien.jahre.values.sumOf { it.size }
                    Hinweis(if (anzahl > 0) "$anzahl Ferienabschnitte gespeichert (${d.ferien.jahre.keys.sorted().joinToString(", ")})" else "Ferien werden beim Einschalten aus dem Netz geladen.")
                    Knopfreihe {
                        Knopf("Kalender-Einstellungen", klein = true) {
                            st.menueOffen = false; st.reiter = de.gun.dashboard.reloaded.ui.Reiter.KALENDER; st.kalenderEinstellungen = true
                        }
                        Knopf("Berechtigungen erteilen", klein = true) { rechte.launch(alleRechte()) }
                        Knopf("App-Einstellungen", klein = true, art = KnopfArt.LEISE) {
                            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                }
            }
            // ------------------------------------------------ Widget
            item {
                Karte("Widget", "05") {
                    Segmente(listOf("dunkel" to "Dunkel", "hell" to "Hell"), d.widget.modus) { m -> Speicher.aendern { it.copy(widget = it.widget.copy(modus = m)) } }
                    Abstand(8.dp)
                    var deck by remember { mutableStateOf((d.widget.deckkraft ?: 85).toFloat()) }
                    Etikett("Deckkraft ${deck.toInt()} %")
                    Regler(deck, 0f..100f, 19, { deck = it }) { Speicher.aendern { it.copy(widget = it.widget.copy(deckkraft = deck.toInt())) } }
                    Auswahl("Zeitraum", listOf("1" to "nur heute", "2" to "heute und morgen", "3" to "3 Tage", "7" to "7 Tage", "14" to "14 Tage"), (d.widget.tage ?: 1).toString()) { t ->
                        Speicher.aendern { it.copy(widget = it.widget.copy(tage = t.toInt())) }
                    }
                    Knopfreihe {
                        Pille("Aufgaben mit anzeigen", d.widget.aufgaben) { Speicher.aendern { it.copy(widget = it.widget.copy(aufgaben = !it.widget.aufgaben)) } }
                        Pille("Endzeit mit anzeigen", d.widget.endzeit) { Speicher.aendern { it.copy(widget = it.widget.copy(endzeit = !it.widget.endzeit)) } }
                    }
                    Hinweis("Hinzufügen: auf dem Startbildschirm lange drücken → Widgets → Dashboard Reloaded.")
                }
            }
            // ------------------------------------------------ Updates & Info
            item {
                Karte("Updates & Info", "06") {
                    Knopfreihe {
                        Knopf("🔄 Nach Update suchen", klein = true) {
                            updateStatus = "Prüfe …"
                            scope.launch {
                                updateStatus = try {
                                    val u = UpdateDienst.pruefen(d.update.repo)
                                    if (u == null) "Diese Version (${BuildConfig.VERSION_NAME}) ist aktuell."
                                    else {
                                        UpdateLader.starten(ctx, u)
                                        "Version ${u.version} wird heruntergeladen und anschließend installiert."
                                    }
                                } catch (e: Exception) { "Die Update-Prüfung ist fehlgeschlagen: " + (e.message ?: "") }
                            }
                        }
                        Knopf("📜 Changelog", klein = true) { st.changelogOffen = true }
                        Knopf("🧭 Einrichtung", klein = true) { st.menueOffen = false; st.einrichtungOffen = true }
                    }
                    if (updateStatus.isNotBlank()) Hinweis(updateStatus)
                    Abstand()
                    Hinweis(
                        "Urlaub: Werktage Mo–Fr, Feiertage ${BUNDESLAENDER[d.feiertagsLand.land]} ausgenommen.\n" +
                            "Lehrgänge und Dokumente: Warnstufe „gelb“ ab 6 Monate vor Ablauf.\n" +
                            "Alle Angaben ohne Gewähr, ersetzen keine offizielle Nachweisführung.\n\n" +
                            "Soldaten Dashboard Reloaded · Version ${BuildConfig.VERSION_NAME} · Daten liegen nur auf diesem Gerät.\n" +
                            "Kontakt: GUN · gun_642@proton.me"
                    )
                }
            }
        }
    }

    importFrage?.let { erg ->
        de.gun.dashboard.reloaded.ui.Frage(
            "Sicherung einlesen",
            "Gefunden: " + erg.bereiche.joinToString(", ") + "\n\nDiese Bereiche werden durch den Inhalt der Datei ersetzt." +
                (if (erg.probleme.isNotEmpty()) "\n\nNicht lesbar: " + erg.probleme.joinToString("; ") else ""),
            ja = "Einlesen", gefahr = false,
            onJa = {
                Speicher.ersetzen(erg.daten)
                importFrage = null
                st.melden("Sicherung eingelesen", "Die Daten wurden übernommen.")
            },
            onNein = { importFrage = null },
        )
    }
}
