package de.gun.dashboard.reloaded.ui.seiten

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gun.dashboard.reloaded.daten.AvzZeitraum
import de.gun.dashboard.reloaded.daten.DuzDienst
import de.gun.dashboard.reloaded.daten.MedEintrag
import de.gun.dashboard.reloaded.daten.Speicher
import de.gun.dashboard.reloaded.daten.neueId
import de.gun.dashboard.reloaded.logik.Bft
import de.gun.dashboard.reloaded.logik.Dtg
import de.gun.dashboard.reloaded.logik.DuzRechner
import de.gun.dashboard.reloaded.logik.EINHEITEN
import de.gun.dashboard.reloaded.logik.Koordinaten
import de.gun.dashboard.reloaded.logik.alsDE
import de.gun.dashboard.reloaded.logik.einheitRechnen
import de.gun.dashboard.reloaded.logik.fmt
import de.gun.dashboard.reloaded.logik.heuteDE
import de.gun.dashboard.reloaded.logik.parseDE
import de.gun.dashboard.reloaded.logik.schutz
import de.gun.dashboard.reloaded.logik.zahl
import de.gun.dashboard.reloaded.logik.zeitAus
import de.gun.dashboard.reloaded.logik.zeitNormieren
import de.gun.dashboard.reloaded.logik.zwei
import de.gun.dashboard.reloaded.ui.Abstand
import de.gun.dashboard.reloaded.ui.Auswahl
import de.gun.dashboard.reloaded.ui.DatumFeld
import de.gun.dashboard.reloaded.ui.Etikett
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
import de.gun.dashboard.reloaded.ui.Punkt
import de.gun.dashboard.reloaded.ui.Reiter
import de.gun.dashboard.reloaded.ui.Segmente
import de.gun.dashboard.reloaded.ui.ZeitFeld
import de.gun.dashboard.reloaded.ui.Zeile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

fun kopieren(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Dashboard", text))
}

@Composable
fun ToolsSeite() {
    SeitenListe {
        item { ZuluKarte() }
        item { KoordinatenKarte() }
        item { EinheitenKarte() }
        item { DuzKarte() }
        item { BftKarte() }
        item { AvzKarte() }
    }
}

// ------------------------------------------------------------------ Zulu & DTG

@Composable
private fun ZuluKarte() {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val ctx = LocalContext.current
    var jetzt by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); jetzt = ZonedDateTime.now() } }
    val z = jetzt.withZoneSameInstant(ZoneOffset.UTC)
    val versatz = jetzt.offset.totalSeconds / 60
    var datum by remember { mutableStateOf(heuteDE()) }
    var zeit by remember { mutableStateOf("") }
    var dtg by remember { mutableStateOf("") }
    var ergebnis by remember { mutableStateOf("") }

    Karte("Zulu-Zeit & DTG", "01") {
        Row {
            Column(Modifier.weight(1f)) {
                Etikett("Zulu (UTC)")
                Punkt(zwei(z.hour) + ":" + zwei(z.minute) + ":" + zwei(z.second), 28.sp, p.akzent)
                Mono(z.toLocalDate().alsDE(), p.textDim, 11.sp)
            }
            Column(Modifier.weight(1f)) {
                Etikett("Ortszeit (${Dtg.zone(versatz)}, UTC${if (versatz >= 0) "+" else "-"}${Math.abs(versatz / 60)})")
                Punkt(zwei(jetzt.hour) + ":" + zwei(jetzt.minute) + ":" + zwei(jetzt.second), 28.sp)
                Mono(jetzt.toLocalDate().alsDE(), p.textDim, 11.sp)
            }
        }
        Abstand()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Etikett("DTG jetzt")
                Mono(Dtg.bilden(jetzt, true), p.text, 18.sp, fett = true)
            }
            Knopf("Kopieren", klein = true) { kopieren(ctx, Dtg.bilden(ZonedDateTime.now(), true)); st.kurz("DTG kopiert") }
        }
        Abstand()
        Etikett("Umrechnen")
        Row {
            DatumFeld(datum, { datum = it }, "Datum", Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            ZeitFeld(zeit, { zeit = it }, "Ortszeit", Modifier.weight(0.7f))
        }
        Knopf("→ DTG", klein = true) {
            val d = parseDE(datum); val t = zeitAus(zeit)
            if (d == null || t == null) { ergebnis = "Datum und Uhrzeit eintragen."; return@Knopf }
            val zp = d.atTime(t).atZone(ZoneId.systemDefault())
            dtg = Dtg.bilden(zp, true)
            val u = zp.withZoneSameInstant(ZoneOffset.UTC)
            ergebnis = "Zulu: ${Dtg.bilden(zp, true)} · Ortszeit: ${Dtg.bilden(zp, false)}\nUTC ${u.toLocalDate().alsDE()} ${zwei(u.hour)}:${zwei(u.minute)}"
        }
        Feld(dtg, { dtg = it }, "DTG", platzhalter = "z. B. 101430ZAUG26")
        Knopf("→ Ortszeit", klein = true) {
            val r = Dtg.lesen(dtg)
            if (r == null) { ergebnis = "DTG nicht lesbar. Aufbau: 101430ZAUG26"; return@Knopf }
            datum = r.toLocalDate().alsDE(); zeit = zwei(r.hour) + ":" + zwei(r.minute)
            ergebnis = "Ortszeit: ${r.toLocalDate().alsDE()} ${zwei(r.hour)}:${zwei(r.minute)}\nZulu: ${Dtg.bilden(r, true)}"
        }
        if (ergebnis.isNotBlank()) Fliesstext(ergebnis, p.text, 14.sp, Modifier.padding(top = 8.dp))
    }
}

// ------------------------------------------------------------------ Koordinaten

@Composable
private fun KoordinatenKarte() {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var breite by remember { mutableStateOf("") }
    var laenge by remember { mutableStateOf("") }
    var mgrs by remember { mutableStateOf("") }
    var stellen by remember { mutableStateOf("5") }
    var ergebnis by remember { mutableStateOf("") }

    fun ausgabe(b: Double, l: Double, m: String, zusatz: String = "") {
        val u = Koordinaten.nachUtm(b, l)
        ergebnis = m + "\n" + String.format(java.util.Locale.US, "%.6f, %.6f", b, l) + "\n" +
            Koordinaten.gradSchreiben(b, true) + "   " + Koordinaten.gradSchreiben(l, false) +
            (u?.let { "\nUTM ${it.zone}${it.band ?: ""} E ${Math.round(it.ost)} N ${Math.round(it.nord)}" } ?: "") + zusatz
    }
    fun nachMgrs() {
        val b = Koordinaten.gradLesen(breite, true); val l = Koordinaten.gradLesen(laenge, false)
        if (b == null || l == null) { ergebnis = "Breite und Länge konnten nicht gelesen werden."; return }
        val m = Koordinaten.nachMgrs(b, l, stellen.toInt()) ?: run { ergebnis = "Außerhalb des MGRS-Bereichs (80° Süd bis 84° Nord)."; return }
        mgrs = m; breite = String.format(java.util.Locale.US, "%.6f", b); laenge = String.format(java.util.Locale.US, "%.6f", l)
        ausgabe(b, l, m)
    }
    val rechte = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { erg ->
        if (erg.values.any { it }) scope.launch {
            ergebnis = "Standort wird bestimmt …"
            val o = standortHolen(ctx) ?: run { ergebnis = "Standort nicht verfügbar."; return@launch }
            breite = String.format(java.util.Locale.US, "%.6f", o.first); laenge = String.format(java.util.Locale.US, "%.6f", o.second)
            nachMgrs()
        } else ergebnis = "Die Standortberechtigung wurde nicht erteilt."
    }

    Karte("Koordinaten", "02") {
        Row {
            Feld(breite, { breite = it }, "Breite (Lat)", Modifier.weight(1f), platzhalter = "52.516275")
            Spacer(Modifier.width(8.dp))
            Feld(laenge, { laenge = it }, "Länge (Lon)", Modifier.weight(1f), platzhalter = "13.377704")
        }
        Feld(mgrs, { mgrs = it }, "MGRS", platzhalter = "33U UU 89918 19699")
        Auswahl("Genauigkeit", listOf("5" to "MGRS 1 m (5 Stellen)", "4" to "MGRS 10 m (4 Stellen)", "3" to "MGRS 100 m (3 Stellen)", "2" to "MGRS 1 km (2 Stellen)"), stellen) {
            stellen = it; if (breite.isNotBlank() && laenge.isNotBlank()) nachMgrs()
        }
        Knopfreihe {
            Knopf("→ MGRS", klein = true) { nachMgrs() }
            Knopf("→ Grad", klein = true) {
                val g = Koordinaten.ausMgrs(mgrs) ?: run { ergebnis = "MGRS nicht lesbar. Aufbau: 33U UU 89918 19699"; return@Knopf }
                breite = String.format(java.util.Locale.US, "%.6f", g.first); laenge = String.format(java.util.Locale.US, "%.6f", g.second)
                ausgabe(g.first, g.second, Koordinaten.nachMgrs(g.first, g.second, stellen.toInt()) ?: mgrs)
            }
            Knopf("⌖ Standort", klein = true) { rechte.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
            if (ergebnis.isNotBlank()) Knopf("Kopieren", klein = true) { kopieren(ctx, ergebnis); st.kurz("Kopiert") }
        }
        if (ergebnis.isNotBlank()) Mono(ergebnis, p.text, 13.sp, Modifier.padding(top = 8.dp))
    }
}

// ------------------------------------------------------------------ Einheiten

@Composable
private fun EinheitenKarte() {
    val p = LocalPalette.current
    val werte = remember { mutableStateMapOf<String, String>() }
    Karte("Einheiten", "03") {
        EINHEITEN.forEachIndexed { i, e ->
            Etikett(e.titel, modifier = Modifier.padding(top = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Feld(werte["a$i"] ?: "", { v -> werte["a$i"] = v; werte["b$i"] = einheitRechnen(e, true, v) }, e.a, Modifier.weight(1f), tastatur = KeyboardType.Decimal)
                Mono(" ⇄ ", p.textFaint)
                Feld(werte["b$i"] ?: "", { v -> werte["b$i"] = v; werte["a$i"] = einheitRechnen(e, false, v) }, e.b, Modifier.weight(1f), tastatur = KeyboardType.Decimal)
            }
        }
    }
}

// ------------------------------------------------------------------ DUZ

@Composable
private fun DuzKarte() {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val ctx = LocalContext.current
    val d = aktuelleDaten()
    val land = d.feiertagsLand.land
    var offen by remember { mutableStateOf(false) }
    var bearbeitet by remember { mutableStateOf<String?>(null) }
    var datum by remember { mutableStateOf(heuteDE()) }
    var von by remember { mutableStateOf("08:00") }
    var bis by remember { mutableStateOf("16:00") }
    var notiz by remember { mutableStateOf("") }
    var saetzeOffen by remember { mutableStateOf(false) }
    var loeschen by remember { mutableStateOf<DuzDienst?>(null) }

    Karte("DUZ-Rechner", "04") {
        Hinweis("Dienst zu ungünstigen Zeiten nach § 3 EZulV: Sonn-/Feiertage ganztägig, Samstag ab 13 Uhr, Karsamstag/Pfingstsamstag/24./31.12. ab 12 Uhr, sonst 20–6 Uhr.")
        Abstand(8.dp)
        Klappbereich(if (bearbeitet != null) "Dienst bearbeiten" else "Dienst hinzufügen", offen, { offen = it; if (!it) bearbeitet = null }) {
            DatumFeld(datum, { datum = it }, "Datum (Dienstbeginn) *")
            Row {
                ZeitFeld(von, { von = it }, "Von *", Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                ZeitFeld(bis, { bis = it }, "Bis *", Modifier.weight(1f))
            }
            Feld(notiz, { notiz = it }, "Bezeichnung", platzhalter = "z. B. Wache, Übung")
            FormKnoepfe("Speichern", {
                val zv = zeitNormieren(von); val zb = zeitNormieren(bis)
                if (parseDE(datum) == null) { st.melden("DUZ", "Bitte ein gültiges Datum eintragen."); return@FormKnoepfe }
                if (zv == null || zb == null) { st.melden("DUZ", "Bitte Beginn und Ende eintragen."); return@FormKnoepfe }
                val e = DuzDienst(bearbeitet ?: neueId(), datum.trim(), zv, zb, notiz.trim())
                Speicher.aendern { a -> a.copy(duz = a.duz.copy(dienste = if (bearbeitet != null) a.duz.dienste.map { if (it.id == e.id) e else it } else a.duz.dienste + e)) }
                offen = false; bearbeitet = null; notiz = ""
            }) { offen = false; bearbeitet = null }
        }
        Knopf(if (saetzeOffen) "▾ Sätze (€/Std.)" else "▸ Sätze (€/Std.)", art = KnopfArt.LEISE, klein = true) { saetzeOffen = !saetzeOffen }
        if (saetzeOffen) {
            DuzRechner.LABEL.forEach { (k, name) ->
                var w by remember(k) { mutableStateOf(d.duz.saetze[k]?.let { zahl(it) } ?: "") }
                Feld(w, { v ->
                    w = v
                    val z = v.replace(",", ".").toDoubleOrNull()
                    Speicher.aendern { a -> a.copy(duz = a.duz.copy(saetze = if (z != null && z > 0) a.duz.saetze + (k to z) else a.duz.saetze - k)) }
                }, "$name €/Std.", tastatur = KeyboardType.Decimal)
            }
            Hinweis("Die Beträge ändern sich gelegentlich per Verordnung und werden deshalb selbst eingetragen.")
        }
        Abstand(8.dp)
        val liste = d.duz.dienste.sortedByDescending { parseDE(it.datum) ?: LocalDate.MIN }
        if (liste.isEmpty()) Leer("Noch keine Dienste erfasst.")
        val gesamt = mutableMapOf("sonntag" to 0, "samstag" to 0, "fest" to 0, "nacht" to 0)
        liste.forEach { e ->
            val z = DuzRechner.zerlegen(e.datum, e.von, e.bis, land)
            z?.let { m -> gesamt.keys.forEach { k -> gesamt[k] = gesamt.getValue(k) + (m[k] ?: 0) } }
            Zeile(p.akzent, aktionen = {
                BearbeitenKnopf { bearbeitet = e.id; datum = e.datum; von = e.von; bis = e.bis; notiz = e.notiz; offen = true }
                LoeschKnopf { loeschen = e }
            }) {
                Fliesstext("${fmt(e.datum)}" + (if (e.notiz.isNotBlank()) " · ${e.notiz}" else "") + " · ${e.von}–${e.bis}", fett = true)
                val teile = DuzRechner.LABEL.keys.filter { (z?.get(it) ?: 0) > 0 }.map { DuzRechner.LABEL[it] + ": " + DuzRechner.stunden(z!![it]!!) }
                Fliesstext(if (teile.isEmpty()) "keine Zulagenzeit in diesem Zeitraum" else teile.joinToString(" · "), p.textDim, 12.sp)
            }
        }
        if (liste.isNotEmpty()) {
            val min = gesamt.values.sum()
            val betrag = gesamt.entries.sumOf { (k, v) -> (d.duz.saetze[k] ?: 0.0) * v / 60.0 }
            Abstand(8.dp)
            Fliesstext("Gesamt zulagefähig: ${DuzRechner.stunden(min)}" + if (betrag > 0) " · " + String.format(java.util.Locale.GERMANY, "%.2f €", betrag) else "", fett = true)
            Hinweis("Anspruch besteht nach § 3 EZulV erst ab mehr als 5 Stunden im Kalendermonat.")
            Knopfreihe {
                Knopf("⎙ Übersicht drucken", klein = true) { htmlDrucken(ctx, duzHtml(Speicher.aktuell), "DUZ-Übersicht") }
                Knopf("✉ Teilen", klein = true) { dateiTeilen(ctx, duzHtml(Speicher.aktuell), "DUZ-Uebersicht_${LocalDate.now()}.html", "text/html") }
            }
        }
    }
    loeschen?.let { e ->
        Frage("Dienst löschen", "Diesen Dienst wirklich löschen?", onJa = {
            Speicher.aendern { a -> a.copy(duz = a.duz.copy(dienste = a.duz.dienste.filter { it.id != e.id })) }; loeschen = null
        }, onNein = { loeschen = null })
    }
}

private fun duzHtml(d: de.gun.dashboard.reloaded.daten.AppDaten): String {
    val land = d.feiertagsLand.land
    var gesamt = 0
    val zeilen = d.duz.dienste.sortedBy { parseDE(it.datum) ?: LocalDate.MIN }.joinToString("") { e ->
        val z = DuzRechner.zerlegen(e.datum, e.von, e.bis, land) ?: return@joinToString ""
        gesamt += DuzRechner.LABEL.keys.sumOf { z[it] ?: 0 }
        val teile = DuzRechner.LABEL.keys.filter { (z[it] ?: 0) > 0 }.joinToString(", ") { DuzRechner.LABEL[it] + " " + DuzRechner.stunden(z[it]!!) }
        "<tr><td>${schutz(e.datum)}</td><td>${schutz(e.von + "–" + e.bis)}</td><td>${schutz(e.notiz)}</td><td>${schutz(teile.ifBlank { "—" })}</td></tr>"
    }
    return "<html><head><meta charset='utf-8'><title>DUZ-Übersicht</title><style>body{font-family:Arial,sans-serif;padding:20px;color:#111;}" +
        "h1{font-size:18px;}table{width:100%;border-collapse:collapse;margin-top:12px;}th,td{border:1px solid #999;padding:6px 8px;font-size:12px;text-align:left;}" +
        "th{background:#eee;}.fuss{margin-top:14px;font-size:11px;color:#555;}</style></head><body>" +
        "<h1>DUZ-Übersicht — ${schutz(d.medizin.person.name)}</h1><p>Erstellt am ${heuteDE()} · Gesamt zulagefähig: ${DuzRechner.stunden(gesamt)}</p>" +
        "<table><tr><th>Datum</th><th>Zeit</th><th>Bezeichnung</th><th>Kategorien</th></tr>$zeilen</table>" +
        "<div class='fuss'>Berechnung der Zeitfenster nach § 3 Erschwerniszulagenverordnung (EZulV), ohne Gewähr. Ein Anspruch besteht erst ab mehr als " +
        "5 Stunden im Kalendermonat. Maßgeblich ist die Abrechnung der Besoldungsstelle.</div></body></html>"
}

// ------------------------------------------------------------------ BFT

@Composable
private fun BftKarte() {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val d = aktuelleDaten()
    val g = d.medizin.bft
    val person = d.medizin.person
    var sprint by remember { mutableStateOf("") }
    var klimm by remember { mutableStateOf("") }
    var lauf by remember { mutableStateOf("") }
    var grenzen by remember { mutableStateOf(false) }

    val weiblich = person.geschlecht == "w"
    val alter = person.alter
    val sp = sprint.replace(",", ".").toDoubleOrNull()
    val kl = klimm.replace(",", ".").toDoubleOrNull()
    val lf = Bft.laufSekunden(lauf)
    val maxLauf = Bft.laufSekunden(g.maxLauf) ?: 390
    val bestLauf = Bft.laufSekunden(g.bestLauf) ?: 270

    data class Teil(val name: String, val anzeige: String, val erfuellt: Boolean, val punkte: Int?)
    val teile = listOfNotNull(
        sp?.let { Teil("11×10 m Sprint", String.format(java.util.Locale.GERMANY, "%.2f s", it), it <= g.maxSprint, Bft.mitZuschlag(Bft.basisPunkte(it, g.maxSprint, g.bestSprint), false, weiblich, alter)) },
        kl?.let { Teil("Klimmhang", String.format(java.util.Locale.GERMANY, "%.1f s", it), it >= g.minKlimm, Bft.mitZuschlag(Bft.basisPunkte(it, g.minKlimm, g.bestKlimm), true, weiblich, alter)) },
        lf?.let { Teil("1000 m Lauf", Bft.sekundenText(it), it <= maxLauf, Bft.mitZuschlag(Bft.basisPunkte(it.toDouble(), maxLauf.toDouble(), bestLauf.toDouble()), false, weiblich, alter)) },
    )
    val vollstaendig = teile.size == 3
    val bestanden = vollstaendig && teile.all { it.erfuellt }
    val punkte = if (vollstaendig && teile.all { it.punkte != null }) Math.round(teile.sumOf { it.punkte!! } / 3.0).toInt() else null

    Karte("BFT-Bewertung", "05") {
        Row {
            Feld(sprint, { sprint = it }, "Sprint (s)", Modifier.weight(1f), tastatur = KeyboardType.Decimal, platzhalter = "52,40")
            Spacer(Modifier.width(6.dp))
            Feld(klimm, { klimm = it }, "Klimmhang (s)", Modifier.weight(1f), tastatur = KeyboardType.Decimal, platzhalter = "22")
            Spacer(Modifier.width(6.dp))
            Feld(lauf, { lauf = it }, "1000 m", Modifier.weight(1f), tastatur = KeyboardType.Number, platzhalter = "512")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Feld(alter?.toString() ?: "", { v -> Speicher.aendern { a -> a.copy(medizin = a.medizin.copy(person = a.medizin.person.copy(alter = v.trim().toIntOrNull()))) } },
                "Alter", Modifier.weight(0.6f), tastatur = KeyboardType.Number)
            Spacer(Modifier.width(8.dp))
            Segmente(listOf("m" to "männlich", "w" to "weiblich"), person.geschlecht.ifBlank { "m" }, Modifier.weight(1f)) { gs ->
                Speicher.aendern { a -> a.copy(medizin = a.medizin.copy(person = a.medizin.person.copy(geschlecht = gs))) }
            }
        }
        Abstand(8.dp)
        if (teile.isEmpty()) Hinweis("Werte eintragen — die Bewertung erscheint hier.")
        else {
            when {
                !vollstaendig -> Fliesstext("${teile.count { it.erfuellt }} von ${teile.size} erfassten Disziplinen erfüllt — noch nicht vollständig", p.textDim)
                !bestanden -> Fliesstext("Nicht bestanden — Mindestleistung in einer Disziplin verfehlt", p.rot, fett = true)
                else -> {
                    val n = Bft.note(punkte ?: 0)
                    Punkt("BESTANDEN · ${punkte} P · NOTE ${Bft.noteText(n)}", 16.sp, p.gruen)
                    Fliesstext(Bft.noteWort(n), p.textDim)
                }
            }
            teile.forEach { t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Fliesstext(t.name, modifier = Modifier.weight(1f))
                    Mono(t.anzeige + (t.punkte?.let { " · $it P" } ?: "") + if (t.erfuellt) "  ✓" else "  ✗", if (t.erfuellt) p.gruen else p.rot, 13.sp, fett = true)
                }
            }
            val zusatz = listOfNotNull(if (weiblich) "Zuschlag weiblich" else null,
                alter?.takeIf { it >= 36 }?.let { "Alterszuschlag " + String.format(java.util.Locale.GERMANY, "%.1f", (it - 35) * 0.5) + " %" })
            Hinweis((if (zusatz.isNotEmpty()) zusatz.joinToString(" · ") + " · " else "") + "Berechnung nach öffentlich zugänglichen Angaben, ohne Gewähr — maßgeblich ist die offizielle Wertung.")
        }
        Knopfreihe {
            Knopf("In IGF übernehmen", klein = true) {
                val teileText = listOfNotNull(sp?.let { "Sprint $it s" }, kl?.let { "Klimmhang $it s" }, lf?.let { "1000 m " + Bft.sekundenText(it) }).joinToString(" · ")
                st.igfVorlage = MedEintrag(
                    art = "BFT", datum = heuteDE(), intervall = "12M", gueltigBis = LocalDate.now().plusMonths(12).alsDE(),
                    ergebnis = if (teile.isEmpty()) "" else (if (bestanden) "bestanden" else "nicht bestanden") + (punkte?.let { " · $it P · Note " + Bft.noteText(Bft.note(it)) } ?: ""),
                    notiz = teileText,
                )
                st.reiter = Reiter.AKTE
            }
            Knopf(if (grenzen) "Grenzwerte ausblenden" else "Grenzwerte", klein = true, art = KnopfArt.LEISE) { grenzen = !grenzen }
        }
        if (grenzen) {
            fun setzen(block: (de.gun.dashboard.reloaded.daten.BftGrenzen) -> de.gun.dashboard.reloaded.daten.BftGrenzen) =
                Speicher.aendern { a -> a.copy(medizin = a.medizin.copy(bft = block(a.medizin.bft))) }
            GrenzFeld("Sprint: 100 Punkte bei (s)", zahl(g.maxSprint)) { v -> v.toDoubleOrNull()?.let { x -> setzen { it.copy(maxSprint = x) } } }
            GrenzFeld("Sprint: 400 Punkte bei (s)", zahl(g.bestSprint)) { v -> v.toDoubleOrNull()?.let { x -> setzen { it.copy(bestSprint = x) } } }
            GrenzFeld("Klimmhang: 100 Punkte bei (s)", zahl(g.minKlimm)) { v -> v.toDoubleOrNull()?.let { x -> setzen { it.copy(minKlimm = x) } } }
            GrenzFeld("Klimmhang: 400 Punkte bei (s)", zahl(g.bestKlimm)) { v -> v.toDoubleOrNull()?.let { x -> setzen { it.copy(bestKlimm = x) } } }
            GrenzFeld("1000 m: 100 Punkte bei (m:ss)", g.maxLauf) { v -> Bft.laufSekunden(v)?.let { x -> setzen { it.copy(maxLauf = Bft.sekundenText(x)) } } }
            GrenzFeld("1000 m: 400 Punkte bei (m:ss)", g.bestLauf) { v -> Bft.laufSekunden(v)?.let { x -> setzen { it.copy(bestLauf = Bft.sekundenText(x)) } } }
        }
    }
}

@Composable
private fun GrenzFeld(label: String, wert: String, onFertig: (String) -> Unit) {
    var w by remember(wert) { mutableStateOf(wert) }
    Feld(w, { w = it }, label, beiVerlassen = { onFertig(w.replace(",", ".").trim()) })
}

// ------------------------------------------------------------------ AVZ

@Composable
private fun AvzKarte() {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val d = aktuelleDaten()
    var offen by remember { mutableStateOf(false) }
    var e by remember { mutableStateOf(AvzZeitraum()) }
    var satz by remember { mutableStateOf("") }
    var loeschen by remember { mutableStateOf<AvzZeitraum?>(null) }

    Karte("Auslandsverwendungszuschlag", "06") {
        Hinweis("Die Tage werden automatisch gezählt. Den Tagessatz der Stufe trägst du selbst ein – die Zuordnung legt das BMVg fest.")
        Abstand(8.dp)
        Klappbereich(if (e.id.isNotBlank()) "Zeitraum bearbeiten" else "Zeitraum hinzufügen", offen, { offen = it; if (!it) { e = AvzZeitraum(); satz = "" } }) {
            Row {
                DatumFeld(e.von, { e = e.copy(von = it) }, "Von *", Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                DatumFeld(e.bis, { e = e.copy(bis = it) }, "Bis *", Modifier.weight(1f))
            }
            Feld(e.einsatz, { e = e.copy(einsatz = it) }, "Einsatzgebiet", platzhalter = "z. B. Litauen")
            Row {
                Auswahl("AVZ-Stufe", (1..6).map { "$it" to "Stufe $it" }, e.stufe, Modifier.weight(1f)) { e = e.copy(stufe = it) }
                Spacer(Modifier.width(8.dp))
                Feld(satz, { satz = it }, "Tagessatz (€)", Modifier.weight(1f), tastatur = KeyboardType.Decimal)
            }
            Feld(e.notiz, { e = e.copy(notiz = it) }, "Notiz")
            FormKnoepfe("Speichern", {
                val v = parseDE(e.von); val b = parseDE(e.bis)
                if (v == null || b == null) { st.melden("AVZ", "Bitte gültige Daten für Von und Bis eintragen."); return@FormKnoepfe }
                if (b.isBefore(v)) { st.melden("AVZ", "Das Enddatum liegt vor dem Anfangsdatum."); return@FormKnoepfe }
                val tage = (java.time.temporal.ChronoUnit.DAYS.between(v, b) + 1).toInt()
                val neu = e.copy(id = e.id.ifBlank { neueId() }, tage = tage, satz = satz.replace(",", ".").toDoubleOrNull())
                Speicher.aendern { a -> a.copy(avz = a.avz.copy(zeitraeume = if (a.avz.zeitraeume.any { it.id == neu.id }) a.avz.zeitraeume.map { if (it.id == neu.id) neu else it } else a.avz.zeitraeume + neu)) }
                e = AvzZeitraum(); satz = ""; offen = false
            }) { e = AvzZeitraum(); satz = ""; offen = false }
        }
        Abstand(8.dp)
        val liste = d.avz.zeitraeume.sortedByDescending { parseDE(it.von) ?: LocalDate.MIN }
        if (liste.isEmpty()) Leer("Noch keine Zeiträume.")
        liste.forEach { z ->
            val tage = z.tage ?: 0
            Zeile(p.akzent, aktionen = {
                BearbeitenKnopf { e = z; satz = z.satz?.let { zahl(it) } ?: ""; offen = true }
                LoeschKnopf { loeschen = z }
            }) {
                Fliesstext((z.einsatz.ifBlank { "Auslandsverwendung" }) + " · Stufe " + z.stufe, fett = true)
                Fliesstext("${fmt(z.von)} – ${fmt(z.bis)} · $tage " + (if (tage == 1) "Tag" else "Tage") +
                    (z.satz?.let { " · " + String.format(java.util.Locale.GERMANY, "%.2f €", it * tage) } ?: "") +
                    (if (z.notiz.isNotBlank()) " · " + z.notiz else ""), p.textDim, 12.sp)
            }
        }
        if (liste.isNotEmpty()) {
            val tage = liste.sumOf { it.tage ?: 0 }
            val betrag = liste.sumOf { (it.satz ?: 0.0) * (it.tage ?: 0) }
            Abstand(8.dp)
            Fliesstext("Gesamt: $tage " + (if (tage == 1) "Tag" else "Tage") + if (betrag > 0) " · " + String.format(java.util.Locale.GERMANY, "%.2f €", betrag) +
                (if (liste.any { it.satz == null }) " (nur Zeiträume mit Satz)" else "") else "", fett = true)
        }
    }
    loeschen?.let { z ->
        Frage("Zeitraum löschen", "Zeitraum „${z.einsatz.ifBlank { "Auslandsverwendung" }}“ wirklich löschen?", onJa = {
            Speicher.aendern { a -> a.copy(avz = a.avz.copy(zeitraeume = a.avz.zeitraeume.filter { it.id != z.id })) }; loeschen = null
        }, onNein = { loeschen = null })
    }
}
