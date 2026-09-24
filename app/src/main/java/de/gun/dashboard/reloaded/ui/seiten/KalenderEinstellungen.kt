package de.gun.dashboard.reloaded.ui.seiten

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import de.gun.dashboard.reloaded.daten.KalenderQuelle
import de.gun.dashboard.reloaded.daten.Speicher
import de.gun.dashboard.reloaded.daten.neueId
import de.gun.dashboard.reloaded.geraet.GeraeteKalender
import de.gun.dashboard.reloaded.geraet.Kontakte
import de.gun.dashboard.reloaded.logik.FARBE_KONTAKTE
import de.gun.dashboard.reloaded.logik.farbeAusHex
import de.gun.dashboard.reloaded.logik.heuteDE
import de.gun.dashboard.reloaded.logik.icsLesen
import de.gun.dashboard.reloaded.logik.icsSchreiben
import de.gun.dashboard.reloaded.logik.zahl
import de.gun.dashboard.reloaded.netz.FerienDienst
import de.gun.dashboard.reloaded.netz.Netz
import de.gun.dashboard.reloaded.ui.Abstand
import de.gun.dashboard.reloaded.ui.Auswahl
import de.gun.dashboard.reloaded.ui.Etikett
import de.gun.dashboard.reloaded.ui.FarbReihe
import de.gun.dashboard.reloaded.ui.Feld
import de.gun.dashboard.reloaded.ui.Fliesstext
import de.gun.dashboard.reloaded.ui.Frage
import de.gun.dashboard.reloaded.ui.Hinweis
import de.gun.dashboard.reloaded.ui.Karte
import de.gun.dashboard.reloaded.ui.Klappbereich
import de.gun.dashboard.reloaded.ui.Knopf
import de.gun.dashboard.reloaded.ui.KnopfArt
import de.gun.dashboard.reloaded.ui.Knopfreihe
import de.gun.dashboard.reloaded.ui.Leer
import de.gun.dashboard.reloaded.ui.LocalPalette
import de.gun.dashboard.reloaded.ui.LocalSteuerung
import de.gun.dashboard.reloaded.ui.Mono
import de.gun.dashboard.reloaded.ui.PALETTE_KALENDER
import de.gun.dashboard.reloaded.ui.Pille
import de.gun.dashboard.reloaded.ui.Punkt
import de.gun.dashboard.reloaded.ui.Regler
import de.gun.dashboard.reloaded.ui.Symbol
import de.gun.dashboard.reloaded.ui.Zeile
import de.gun.dashboard.reloaded.ui.alleRechteKalender
import kotlinx.coroutines.launch

/** Ferien des Jahres nachladen, falls nicht vorhanden. */
suspend fun ferienSicherstellen(jahr: Int, alleNeu: Boolean = false): Int {
    val d = Speicher.aktuell
    if (!d.ferien.an) return 0
    val land = d.feiertagsLand.land
    val vorhanden = if (d.ferien.land == land && !alleNeu) d.ferien.jahre else emptyMap()
    vorhanden[jahr.toString()]?.let { return it.size }
    return try {
        val abschnitte = FerienDienst.laden(land, jahr)
        Speicher.aendern {
            val basis = if (it.ferien.land == land && !alleNeu) it.ferien.jahre else emptyMap()
            it.copy(ferien = it.ferien.copy(land = land, jahre = basis + (jahr.toString() to abschnitte)))
        }
        abschnitte.size
    } catch (e: Exception) {
        -1
    }
}

@Composable
fun KalenderEinstellungen() {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val d = aktuelleDaten()
    val kalender by GeraeteKalender.kalender.collectAsState()
    val termine by GeraeteKalender.termine.collectAsState()
    val anlaesse by Kontakte.anlaesse.collectAsState()
    var loeschQuelle by remember { mutableStateOf<KalenderQuelle?>(null) }

    val rechte = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        scope.launch { Aktualisierung.geraetLadenJetzt(ctx) }
    }

    SeitenListe {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Symbol("‹", p.text) { st.kalenderEinstellungen = false }
                Punkt("KALENDER-EINSTELLUNGEN", 18.sp)
            }
        }
        item {
            Karte("Gerätekalender", "01") {
                val darf = GeraeteKalender.darfLesen(ctx)
                Fliesstext(
                    if (!darf) "Kalenderzugriff fehlt." else "${kalender.size} Kalender · ${termine.size} Termine" +
                        (if (anlaesse.isNotEmpty()) " · ${anlaesse.size} Kontaktdaten" else ""), p.textDim, 13.sp
                )
                Hinweis("Termine werden direkt im Gerätekalender gespeichert. Liegt dort dein Outlook- oder Google-Konto, überträgt Android sie selbst in die Cloud.")
                Knopfreihe {
                    Knopf(if (darf) "↻ Neu einlesen" else "Zugriff erlauben", klein = true) {
                        if (darf) scope.launch { Aktualisierung.geraetLadenJetzt(ctx); st.kurz("Eingelesen") }
                        else rechte.launch(alleRechteKalender())
                    }
                    if (d.nativ.entfernt.isNotEmpty()) Knopf("Entfernte zurückholen (${d.nativ.entfernt.size})", klein = true) {
                        Speicher.aendern { it.copy(nativ = it.nativ.copy(entfernt = emptyList())) }
                    }
                }
                Abstand()
                Auswahl(
                    "Standardkalender für neue Termine",
                    listOf("" to "— bitte auswählen —") + kalender.filter { it.schreibbar }.map { it.id to it.titel + " [" + it.dienst + "]" },
                    d.nativ.zielKalenderId
                ) { id -> Speicher.aendern { it.copy(nativ = it.nativ.copy(zielKalenderId = id)) } }
                Abstand(8.dp)
                Knopfreihe {
                    Pille("Geburtstage", d.nativ.geburtstage) { Speicher.aendern { it.copy(nativ = it.nativ.copy(geburtstage = !it.nativ.geburtstage)) } }
                    Pille("Kontakt-Anlässe", d.nativ.kontaktdaten) {
                        val an = !d.nativ.kontaktdaten
                        Speicher.aendern { it.copy(nativ = it.nativ.copy(kontaktdaten = an)) }
                        if (an) { if (Kontakte.darfLesen(ctx)) scope.launch { Kontakte.einlesen(ctx) } else rechte.launch(arrayOf(Manifest.permission.READ_CONTACTS)) }
                    }
                }
            }
        }
        // Kalenderliste, gruppiert nach Konto
        val reihenfolge = listOf("Google", "Outlook", "iCloud", "Samsung", "Lokal", "Abonniert")
        val sortiert = kalender.filter { it.id !in d.nativ.entfernt }
            .sortedWith(compareBy({ reihenfolge.indexOf(it.dienst).let { i -> if (i < 0) 99 else i } }, { it.kontoName }, { it.titel }))
        val gruppen = sortiert.groupBy { it.dienst }
        gruppen.forEach { (dienst, liste) ->
            item {
                Karte(dienst, liste.size.toString()) {
                    liste.forEach { k ->
                        val sichtbar = k.id !in d.nativ.versteckt
                        val farbe = d.farbenEigen[k.id] ?: String.format("#%06x", k.farbe and 0xFFFFFF)
                        KalenderEintrag(
                            k.titel, k.kontoName.ifBlank { dienst } + (if (!k.schreibbar) " · nur lesend" else "") +
                                (if (k.id == d.nativ.zielKalenderId) " · Standard" else ""),
                            farbe, sichtbar,
                            onSichtbar = {
                                Speicher.aendern {
                                    val v = it.nativ.versteckt
                                    it.copy(nativ = it.nativ.copy(versteckt = if (k.id in v) v - k.id else v + k.id))
                                }
                            },
                            onFarbe = { f -> Speicher.aendern { it.copy(farbenEigen = it.farbenEigen + (k.id to f)) } },
                            onFarbeZurueck = if (d.farbenEigen.containsKey(k.id)) ({ Speicher.aendern { it.copy(farbenEigen = it.farbenEigen - k.id) } }) else null,
                            onEntfernen = { Speicher.aendern { it.copy(nativ = it.nativ.copy(entfernt = it.nativ.entfernt + k.id)) } },
                        )
                    }
                }
            }
        }
        if (anlaesse.isNotEmpty() && "kontakte" !in d.nativ.entfernt) item {
            Karte("Kontakte", "·") {
                KalenderEintrag(
                    "Geburtstage & Anlässe", "aus den Kontakten · ${anlaesse.size}",
                    d.farbenEigen["kontakte"] ?: String.format("#%06x", FARBE_KONTAKTE and 0xFFFFFF),
                    "kontakte" !in d.nativ.versteckt,
                    onSichtbar = {
                        Speicher.aendern {
                            val v = it.nativ.versteckt
                            it.copy(nativ = it.nativ.copy(versteckt = if ("kontakte" in v) v - "kontakte" else v + "kontakte"))
                        }
                    },
                    onFarbe = { f -> Speicher.aendern { it.copy(farbenEigen = it.farbenEigen + ("kontakte" to f)) } },
                    onFarbeZurueck = null,
                    onEntfernen = { Speicher.aendern { it.copy(nativ = it.nativ.copy(entfernt = it.nativ.entfernt + "kontakte")) } },
                )
            }
        }
        item { QuellenKarte { loeschQuelle = it } }
        item {
            Karte("Anzeige", "03") {
                Etikett("Schriftgröße im Kalender: ${d.kalender.schriftgroesse ?: 100} %")
                var wert by remember { mutableStateOf((d.kalender.schriftgroesse ?: 100).toFloat()) }
                Regler(wert, 70f..150f, 15, { wert = it }) {
                    val v = (Math.round(wert / 5) * 5)
                    Speicher.aendern { it.copy(kalender = it.kalender.copy(schriftgroesse = v)) }
                }
                Knopfreihe {
                    Pille("Aufgaben im Kalender", d.kalender.todosImKalender) {
                        Speicher.aendern { it.copy(kalender = it.kalender.copy(todosImKalender = !it.kalender.todosImKalender)) }
                    }
                    if (d.kalender.todosImKalender) Pille("Auch erledigte", d.kalender.erledigteImKalender) {
                        Speicher.aendern { it.copy(kalender = it.kalender.copy(erledigteImKalender = !it.kalender.erledigteImKalender)) }
                    }
                    Pille("Schulferien", d.ferien.an) {
                        val an = !d.ferien.an
                        Speicher.aendern { it.copy(ferien = it.ferien.copy(an = an)) }
                        if (an) scope.launch {
                            val j = java.time.LocalDate.now().year
                            for (x in j..j + 2) ferienSicherstellen(x)
                        }
                    }
                }
                Abstand()
                var fvd by remember { mutableStateOf(zahl(d.kalender.fvdStunden)) }
                Feld(fvd, { fvd = it; it.replace(",", ".").toDoubleOrNull()?.let { v -> Speicher.aendern { a -> a.copy(kalender = a.kalender.copy(fvdStunden = v)) } } },
                    "FvD: Stunden je Werktag (ganztägig)", tastatur = KeyboardType.Decimal)
            }
        }
        if (d.kalender.eigene.isNotEmpty()) item {
            Karte("Eigene Termine", "04") {
                Fliesstext("${d.kalender.eigene.size} in dieser App angelegte Termine.", p.textDim, 13.sp)
                Knopfreihe {
                    Knopf("↓ Alle als .ics teilen", klein = true) {
                        dateiTeilen(ctx, icsSchreiben(d.kalender.eigene), "SoldatenDashboard_Termine.ics", "text/calendar")
                    }
                }
            }
        }
    }

    loeschQuelle?.let { q ->
        Frage("Kalender entfernen", "Kalender „${q.name}“ wirklich entfernen?", onJa = {
            Speicher.aendern { it.copy(kalender = it.kalender.copy(quellen = it.kalender.quellen.filter { x -> x.id != q.id })) }
            loeschQuelle = null
        }, onNein = { loeschQuelle = null })
    }
}

@Composable
private fun KalenderEintrag(
    titel: String, unter: String, farbe: String, sichtbar: Boolean,
    onSichtbar: () -> Unit, onFarbe: (String) -> Unit, onFarbeZurueck: (() -> Unit)?, onEntfernen: () -> Unit,
) {
    val p = LocalPalette.current
    var farbenOffen by remember { mutableStateOf(false) }
    Zeile(Color(farbeAusHex(farbe)), onClick = { farbenOffen = !farbenOffen }, aktionen = {
        Pille(if (sichtbar) "an" else "aus", sichtbar, onClick = onSichtbar)
        Symbol("✕", p.textFaint, onEntfernen)
    }) {
        Fliesstext(titel, fett = true, zeilen = 1, farbe = if (sichtbar) p.text else p.textFaint)
        Mono(unter, p.textFaint, 11.sp)
        if (farbenOffen) {
            FarbReihe(PALETTE_KALENDER, farbe, onFarbe)
            if (onFarbeZurueck != null) Knopf("Gerätefarbe", klein = true, art = KnopfArt.LEISE, onClick = onFarbeZurueck)
        }
    }
}

@Composable
private fun QuellenKarte(onLoeschen: (KalenderQuelle) -> Unit) {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val d = aktuelleDaten()
    var offen by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var farbe by remember { mutableStateOf(PALETTE_KALENDER[0]) }
    var urlaubRolle by remember { mutableStateOf(false) }
    var laedt by remember { mutableStateOf(false) }

    fun anlegen(text: String, quelleUrl: String) {
        val ev = icsLesen(text)
        val q = KalenderQuelle(neueId(), name.trim(), farbe, urlaubRolle, quelleUrl, true, ev, heuteDE())
        Speicher.aendern { it.copy(kalender = it.kalender.copy(quellen = it.kalender.quellen + q)) }
        st.kurz("Kalender „${q.name}“: ${ev.size} Termine")
        name = ""; url = ""; offen = false
    }

    val datei = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                anlegen(text, "")
            } catch (e: Exception) { st.melden("ICS-Datei", "Die Datei konnte nicht gelesen werden.") }
        }
    }

    Karte("Kalenderquellen (ICS / Abo)", "02") {
        if (d.kalender.quellen.isEmpty()) Leer("Keine eingebundenen Kalender.")
        d.kalender.quellen.forEach { q ->
            Zeile(Color(farbeAusHex(d.farbenEigen[q.id] ?: q.farbe)), aktionen = {
                if (q.url.isNotBlank()) Symbol("↻") {
                    scope.launch {
                        try {
                            val ev = icsLesen(Netz.text(q.url.replace(Regex("^webcal://", RegexOption.IGNORE_CASE), "https://")))
                            Speicher.aendern { a ->
                                a.copy(kalender = a.kalender.copy(quellen = a.kalender.quellen.map {
                                    if (it.id == q.id) it.copy(events = ev, aktualisiert = heuteDE()) else it
                                }))
                            }
                            st.kurz("Kalender aktualisiert: ${ev.size} Termine")
                        } catch (e: Exception) { st.melden("Aktualisierung fehlgeschlagen", e.message ?: "") }
                    }
                }
                Symbol("✕", p.rot) { onLoeschen(q) }
            }) {
                Fliesstext(q.name + if (q.istUrlaub) " · Urlaubskalender" else "", fett = true, zeilen = 1)
                Mono((if (q.url.isNotBlank()) "Abo · lesend" else "Datei · lesend") + " · ${q.events.size} Termine · Stand ${q.aktualisiert}" +
                    if (!q.aktiv) " · ausgeblendet" else "", p.textFaint, 11.sp)
                Knopfreihe {
                    Pille(if (q.aktiv) "sichtbar" else "ausgeblendet", q.aktiv) {
                        Speicher.aendern { a -> a.copy(kalender = a.kalender.copy(quellen = a.kalender.quellen.map { if (it.id == q.id) it.copy(aktiv = !it.aktiv) else it })) }
                    }
                }
                FarbReihe(PALETTE_KALENDER, q.farbe) { f ->
                    Speicher.aendern { a -> a.copy(kalender = a.kalender.copy(quellen = a.kalender.quellen.map { if (it.id == q.id) it.copy(farbe = f) else it })) }
                }
            }
        }
        Abstand(8.dp)
        Klappbereich("Kalender hinzufügen", offen, { offen = it }) {
            Feld(name, { name = it }, "Name *", platzhalter = "z. B. Kalender Ehefrau")
            Etikett("Farbe")
            FarbReihe(PALETTE_KALENDER, farbe) { farbe = it }
            Abstand(8.dp)
            Pille("Urlaubskalender", urlaubRolle) { urlaubRolle = !urlaubRolle }
            Feld(url, { url = it }, "Abo-Link (ICS / webcal)", platzhalter = "https://… oder webcal://…")
            Knopfreihe {
                Knopf(if (laedt) "Lädt …" else "Abo hinzufügen", art = KnopfArt.PRIMAER, klein = true, aktiv = !laedt) {
                    if (name.isBlank()) { st.melden("Kalender", "Bitte einen Namen eingeben."); return@Knopf }
                    if (url.isBlank()) { st.melden("Kalender", "Bitte einen Abo-Link eintragen oder eine ICS-Datei wählen."); return@Knopf }
                    laedt = true
                    scope.launch {
                        try {
                            val ziel = url.trim().replace(Regex("^webcal://", RegexOption.IGNORE_CASE), "https://")
                            anlegen(Netz.text(ziel), url.trim())
                        } catch (e: Exception) {
                            st.melden("Kalender konnte nicht geladen werden", (e.message ?: "") + "\n\nEnthält die Adresse einen Tippfehler, oder braucht die Gegenstelle eine Anmeldung?")
                        }
                        laedt = false
                    }
                }
                Knopf("ICS-Datei wählen", klein = true) {
                    if (name.isBlank()) { st.melden("Kalender", "Bitte zuerst einen Namen eingeben."); return@Knopf }
                    datei.launch(arrayOf("text/calendar", "text/*", "application/octet-stream", "*/*"))
                }
            }
        }
    }
}
