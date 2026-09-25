package de.gun.dashboard.reloaded.ui.seiten

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gun.dashboard.reloaded.Aktualisierung
import de.gun.dashboard.reloaded.BuildConfig
import de.gun.dashboard.reloaded.daten.BUNDESLAENDER
import de.gun.dashboard.reloaded.daten.Einrichtung
import de.gun.dashboard.reloaded.daten.Speicher
import de.gun.dashboard.reloaded.geraet.GeraeteKalender
import de.gun.dashboard.reloaded.logik.IGF_ARTEN
import de.gun.dashboard.reloaded.logik.hhmm
import de.gun.dashboard.reloaded.logik.alsDE
import de.gun.dashboard.reloaded.logik.heuteDE
import de.gun.dashboard.reloaded.logik.statusVon
import de.gun.dashboard.reloaded.logik.terminFenster
import de.gun.dashboard.reloaded.logik.zahl
import de.gun.dashboard.reloaded.logik.mitVorzeichen
import de.gun.dashboard.reloaded.ui.Abstand
import de.gun.dashboard.reloaded.ui.Auswahl
import de.gun.dashboard.reloaded.ui.Etikett
import de.gun.dashboard.reloaded.ui.Feld
import de.gun.dashboard.reloaded.ui.Fliesstext
import de.gun.dashboard.reloaded.ui.Hinweis
import de.gun.dashboard.reloaded.ui.Knopf
import de.gun.dashboard.reloaded.ui.KnopfArt
import de.gun.dashboard.reloaded.ui.Knopfreihe
import de.gun.dashboard.reloaded.ui.Leer
import de.gun.dashboard.reloaded.ui.LocalPalette
import de.gun.dashboard.reloaded.ui.LocalSteuerung
import de.gun.dashboard.reloaded.ui.Mono
import de.gun.dashboard.reloaded.ui.Pille
import de.gun.dashboard.reloaded.ui.Punkt
import de.gun.dashboard.reloaded.ui.PunktRaster
import de.gun.dashboard.reloaded.ui.Reiter
import de.gun.dashboard.reloaded.ui.Schrift
import de.gun.dashboard.reloaded.ui.Symbol
import de.gun.dashboard.reloaded.ui.Zeile
import kotlinx.coroutines.launch
import java.time.LocalDate

// ================================================================== Suche

private data class Treffer(val titel: String, val meta: String, val ziel: Reiter, val termin: de.gun.dashboard.reloaded.logik.Termin? = null)

@Composable
fun SucheEbene() {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val b = rememberBestand()
    val d = b.daten
    var begriff by remember { mutableStateOf("") }

    val gruppen = remember(begriff, b) {
        val q = begriff.trim().lowercase()
        if (q.length < 2) return@remember emptyList<Pair<String, List<Treffer>>>()
        fun passt(vararg f: String?) = f.any { (it ?: "").lowercase().contains(q) }
        val heute = LocalDate.now()
        val l = mutableListOf<Pair<String, List<Treffer>>>()
        terminFenster(b, LocalDate.of(heute.year - 1, 1, 1), LocalDate.of(heute.year + 1, 12, 31), false)
            .filter { passt(it.titel, it.ort, it.notiz, it.quelleName) }.take(25)
            .map { Treffer(it.titel, it.ersterTag.alsDE() + (if (it.ganztags) " · ganztägig" else " · " + it.start.hhmm()) + (if (it.ort.isNotBlank()) " · " + it.ort else "") + " · " + it.quelleName, Reiter.KALENDER, it) }
            .takeIf { it.isNotEmpty() }?.let { l += "Termine" to it }
        d.todos.eintraege.filter { passt(it.text, it.notiz) }
            .map { Treffer(it.text, (if (it.faellig.isNotBlank()) "fällig ${it.faellig}" else "ohne Termin") + (if (it.erledigt) " · erledigt" else ""), Reiter.TODO) }
            .takeIf { it.isNotEmpty() }?.let { l += "Aufgaben" to it }
        d.notizen.eintraege.filter { passt(it.titel, it.text, it.kategorie) }
            .map { Treffer(it.titel, (if (it.kategorie.isNotBlank()) it.kategorie + " · " else "") + it.text.replace(Regex("\\s+"), " ").take(70), Reiter.NOTIZEN) }
            .takeIf { it.isNotEmpty() }?.let { l += "Notizen" to it }
        val cl = mutableListOf<Treffer>()
        d.checklisten.listen.forEach { c ->
            if (passt(c.name)) cl += Treffer(c.name, "${c.punkte.size} Punkte", Reiter.NOTIZEN)
            c.punkte.filter { passt(it.text) }.forEach { cl += Treffer(it.text, "in Checkliste „${c.name}“" + if (it.ab) " · abgehakt" else "", Reiter.NOTIZEN) }
        }
        if (cl.isNotEmpty()) l += "Checklisten" to cl
        d.ablaufregister.filter { passt(it.art, it.notiz) }
            .map { Treffer(it.art, "gültig bis ${it.gueltigBis} · ${statusVon(it.gueltigBis).label}", Reiter.LEHRGAENGE) }
            .takeIf { it.isNotEmpty() }?.let { l += "Lehrgänge" to it }
        d.dokumente.filter { passt(it.art, it.inhaber, it.nummer, it.notiz) }
            .map { Treffer(it.art + (if (it.inhaber.isNotBlank()) " · " + it.inhaber else ""), "gültig bis ${it.gueltigBis} · ${statusVon(it.gueltigBis).label}", Reiter.DOKUMENTE) }
            .takeIf { it.isNotEmpty() }?.let { l += "Dokumente" to it }
        val ur = d.urlaub.zeitraeume.filter { passt(it.notiz, it.von, it.bis) }
            .map { Treffer(it.notiz.ifBlank { "Urlaub" }, it.von + (if (it.bis != it.von) " – " + it.bis else "") + " · ${zahl(it.tage)} Tage" + if (it.status == "geplant") " · geplant" else "", Reiter.URLAUB) } +
            d.ueberstunden.eintraege.filter { passt(it.notiz) }.map { Treffer(it.notiz.ifBlank { "Mehrarbeit" }, it.datum + " · " + mitVorzeichen(it.stunden) + " Stunden", Reiter.URLAUB) }
        if (ur.isNotEmpty()) l += "Urlaub und Mehrarbeit" to ur
        val akte = listOf("IGF" to d.medizin.igf, "ICCS" to d.medizin.iccs, "AVU/WFV" to d.medizin.avu, "Impfung" to d.medizin.impfungen).flatMap { (bez, li) ->
            li.filter { passt(it.art, it.notiz, it.ergebnis, it.befund, it.stelle) }
                .map { Treffer(it.art, "$bez · ${it.datum}" + (if (it.gueltigBis.isNotBlank()) " · gültig bis " + it.gueltigBis else ""), Reiter.AKTE) }
        }
        if (akte.isNotEmpty()) l += "Akte" to akte
        l
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Symbol("✕", p.text) { st.sucheOffen = false }
            Spacer(Modifier.width(6.dp))
            Punkt("SUCHEN", 20.sp)
        }
        Column(Modifier.padding(horizontal = 14.dp)) {
            Feld(begriff, { begriff = it }, "Begriff eingeben …")
            Hinweis("Durchsucht Termine, Aufgaben, Notizen, Checklisten, Lehrgänge, Dokumente, Urlaub und die Akte.")
        }
        SeitenListe {
            if (begriff.trim().length < 2) item { Hinweis("Mindestens zwei Zeichen eingeben.") }
            else if (gruppen.isEmpty()) item { Leer("Nichts gefunden zu „${begriff.trim()}“.") }
            gruppen.forEach { (name, treffer) ->
                item { Etikett("$name (${treffer.size})", p.akzent) }
                treffer.forEach { t ->
                    item {
                        Zeile(p.rand, onClick = {
                            st.sucheOffen = false
                            st.reiter = t.ziel
                            t.termin?.let { st.terminDetail = it }
                        }) {
                            Hervorgehoben(t.titel, begriff.trim(), true)
                            Hervorgehoben(t.meta, begriff.trim(), false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hervorgehoben(text: String, q: String, titel: Boolean) {
    val p = LocalPalette.current
    val i = text.lowercase().indexOf(q.lowercase())
    val s = buildAnnotatedString {
        if (i < 0 || q.isEmpty()) append(text)
        else {
            append(text.substring(0, i))
            withStyle(SpanStyle(background = p.akzentDim, color = p.akzent, fontWeight = FontWeight.Bold)) { append(text.substring(i, i + q.length)) }
            append(text.substring(i + q.length))
        }
    }
    androidx.compose.material3.Text(
        s, color = if (titel) p.text else p.textDim, maxLines = if (titel) 2 else 1,
        style = androidx.compose.ui.text.TextStyle(fontFamily = Schrift.text, fontSize = if (titel) 15.sp else 12.sp, fontWeight = if (titel) FontWeight.Bold else FontWeight.Normal)
    )
}

// ================================================================== Einrichtung

@Composable
fun EinrichtungEbene(rechteAnfragen: () -> Unit) {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val d = aktuelleDaten()
    val kalender by GeraeteKalender.kalender.collectAsState()
    var schritt by remember { mutableStateOf(1) }
    var land by remember { mutableStateOf(d.feiertagsLand.land) }
    var ziel by remember { mutableStateOf(d.nativ.zielKalenderId) }
    var igf by remember { mutableStateOf(d.medizin.igfPflicht) }
    val schritte = 4

    fun fertig(uebersprungen: Boolean) {
        Speicher.aendern {
            var a = it.copy(einrichtung = Einrichtung(true, heuteDE()))
            if (!uebersprungen) a = a.copy(
                feiertagsLand = a.feiertagsLand.copy(land = land),
                nativ = a.nativ.copy(zielKalenderId = ziel),
                medizin = a.medizin.copy(igfPflicht = igf),
            )
            a
        }
        st.einrichtungOffen = false
        if (!uebersprungen) scope.launch { val j = LocalDate.now().year; for (x in j..j + 2) ferienSicherstellen(x) }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            if (p.punktRaster) PunktRaster(Modifier.matchParentSize(), p.randLeise)
            Column {
                Etikett("Einrichtung · Schritt $schritt von $schritte")
                Punkt("WILLKOMMEN", 30.sp)
                Row(Modifier.padding(top = 8.dp)) {
                    (1..schritte).forEach { i ->
                        Box(Modifier.padding(end = 6.dp).size(if (i == schritt) 12.dp else 8.dp).clip(CircleShape)
                            .background(if (i <= schritt) p.akzent else p.rand))
                    }
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            when (schritt) {
                1 -> {
                    Punkt("DATEN ÜBERNEHMEN", 18.sp)
                    Abstand(8.dp)
                    Fliesstext("Du nutzt schon das alte Soldaten Dashboard? Dort im Menü unter „Sicherung“ auf „Export .json“ tippen und die Datei hier im Menü unter „Sicherung → Importieren“ einlesen. Alle Bereiche werden übernommen – auch Anhänge.", p.textDim)
                    Knopfreihe { Knopf("Jetzt importieren", art = KnopfArt.PRIMAER, klein = true) { fertig(true); st.menueOffen = true } }
                    Hinweis("Die neue App läuft neben der alten; beide lassen sich parallel nutzen.")
                }
                2 -> {
                    Punkt("BUNDESLAND", 18.sp)
                    Abstand(8.dp)
                    Fliesstext("Bestimmt die gesetzlichen Feiertage. Sie werden bei der Urlaubsberechnung abgezogen und im Kalender angezeigt.", p.textDim)
                    Auswahl("Bundesland (Feiertage)", BUNDESLAENDER.map { it.key to it.value }, land) { land = it }
                }
                3 -> {
                    Punkt("KALENDER", 18.sp)
                    Abstand(8.dp)
                    Fliesstext("Die App liest die Kalender des Geräts (Google, Outlook, Samsung …) und schreibt neue Termine direkt hinein. Dafür braucht sie Zugriff auf Kalender und Kontakte (für Geburtstage) sowie – für Erinnerungen – auf Benachrichtigungen.", p.textDim)
                    Knopfreihe {
                        Knopf("Zugriff erlauben", art = KnopfArt.PRIMAER, klein = true) { rechteAnfragen() }
                        Knopf("Kalender einlesen", klein = true) { scope.launch { Aktualisierung.geraetLadenJetzt(ctx) } }
                    }
                    val schreibbar = kalender.filter { it.schreibbar }
                    if (schreibbar.isNotEmpty()) {
                        Hinweis("${kalender.size} Kalender gefunden.")
                        Auswahl("Standardkalender für neue Termine", listOf("" to "— keiner —") + schreibbar.map { it.id to it.titel + " [" + it.dienst + "]" }, ziel) { ziel = it }
                    } else Hinweis("Noch keine Kalender eingelesen. Dieser Schritt lässt sich überspringen und später nachholen.")
                }
                4 -> {
                    Punkt("IGF-PFLICHT", 18.sp)
                    Abstand(8.dp)
                    Fliesstext("Welche Bestandteile zählen für dich zur Individuellen Grundfertigkeit? Vorbelegt sind die vier, die laut Zentralanweisung B1-224/0-2 für alle gelten.", p.textDim)
                    Knopfreihe { IGF_ARTEN.forEach { a -> Pille(a, a in igf) { igf = if (a in igf) igf - a else igf + a } } }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Knopf("Später", art = KnopfArt.LEISE, klein = true) { fertig(true) }
            Spacer(Modifier.weight(1f))
            if (schritt > 1) Knopf("Zurück", klein = true) { schritt-- }
            Spacer(Modifier.width(8.dp))
            Knopf(if (schritt == schritte) "Fertig" else "Weiter", art = KnopfArt.PRIMAER) {
                if (schritt < schritte) schritt++ else fertig(false)
            }
        }
    }
}

// ================================================================== Changelog

private val CHANGELOG = listOf(
    "1.1.1" to listOf(
        "Android Auto: Termin mit Ort antippen startet die Navigation.",
        "meteoblue-Wetter: kein weißer Bereich mehr unten, Höhe passt sich dem Inhalt an.",
        "Open-Meteo-Wetter: Nebel wird nicht mehr als eckiges Bild angezeigt.",
    ),
    "1.1.0" to listOf(
        "Neu: Android Auto (erste Testversion) – Wetter heute, Termine heute & morgen, Aufgaben heute.",
        "Hinweis: Die App ist nicht über den Play Store freigegeben; in Android Auto über AAEnabler bzw. „Unbekannte Quellen“ im Entwicklermodus freischalten.",
    ),
    "1.0.5" to listOf(
        "Termine des gewählten Tages erscheinen wieder sichtbar – als Einblendung am unteren Rand über dem Kalender, mit + zum Anlegen.",
    ),
    "1.0.4" to listOf(
        "Kalender blättert flüssig: Der Monat folgt dem Finger, schon kurzes Wischen reicht.",
        "Termine der Nachbarmonate werden im Hintergrund vorberechnet und zwischengespeichert – kein Ruckeln beim Wechsel.",
    ),
    "1.0.3" to listOf(
        "Update-Prüfung bei jedem App-Start statt nur alle zwei Tage.",
    ),
    "1.0.2" to listOf(
        "Tipp auf Monat/Jahr öffnet den Kalender im Vollbild (ohne Kopfzeile, Reiter und Systemleisten); ✕ oder Zurück beendet es.",
        "Deutlich kleinere Knöpfe, z. B. „Eintrag hinzufügen“, Speichern/Abbrechen und der +-Knopf im Kalender.",
    ),
    "1.0.1" to listOf(
        "Lehrgänge und Dokumente als Kacheln in zwei Spalten.",
        "Deutlich kompaktere Eingabefelder in allen Formularen.",
        "Kalender füllt die ganze Höhe – kein Leerraum mehr unten.",
        "Uhr und Datum auf HEUTE zentriert.",
    ),
    "1.0.0" to listOf(
        "Komplett neu als native Android-App (Kotlin, Jetpack Compose).",
        "Nothing-Design mit Punktschrift und Punktraster; Themen Nothing, Nothing hell, Graphit, Aulumu und System, sieben Akzentfarben.",
        "Alle Bereiche des Soldaten Dashboards: Heute, Kalender, To-do, Notizen & Checklisten, Urlaub/Mehrarbeit, Lehrgänge, Dokumente, Akte, Tools.",
        "Gerätekalender direkt über Android (inkl. Serien, Einzel-Löschung von Vorkommen, Ersatzrechnung für nicht aufgelöste Serien).",
        "Sicherungsdatei im selben Format wie das alte Dashboard – Import und Export in beide Richtungen.",
        "Neues Widget, Erinnerungen, Suche über alle Bereiche, Einrichtungsassistent.",
        "Läuft parallel zum alten Soldaten Dashboard (eigene App-ID).",
    ),
)

@Composable
fun ChangelogEbene() {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Symbol("✕", p.text) { st.changelogOffen = false }
            Spacer(Modifier.width(6.dp))
            Punkt("CHANGELOG", 20.sp)
        }
        SeitenListe {
            CHANGELOG.forEach { (v, punkte) ->
                item {
                    de.gun.dashboard.reloaded.ui.Karte("Version $v", if (v == BuildConfig.VERSION_NAME) "●" else null) {
                        punkte.forEach { Fliesstext("· $it", p.textDim, 14.sp, Modifier.padding(vertical = 3.dp)) }
                    }
                }
            }
        }
    }
}
