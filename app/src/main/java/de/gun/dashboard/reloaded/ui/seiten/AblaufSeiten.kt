package de.gun.dashboard.reloaded.ui.seiten

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gun.dashboard.reloaded.daten.AblaufEintrag
import de.gun.dashboard.reloaded.daten.Anhaenge
import de.gun.dashboard.reloaded.daten.Anhang
import de.gun.dashboard.reloaded.daten.Dokument
import de.gun.dashboard.reloaded.daten.Speicher
import de.gun.dashboard.reloaded.daten.neueId
import de.gun.dashboard.reloaded.logik.Status
import de.gun.dashboard.reloaded.logik.datumPlus
import de.gun.dashboard.reloaded.logik.fmt
import de.gun.dashboard.reloaded.logik.parseDE
import de.gun.dashboard.reloaded.logik.restlaufzeit
import de.gun.dashboard.reloaded.logik.statusVon
import de.gun.dashboard.reloaded.ui.Abstand
import de.gun.dashboard.reloaded.ui.Auswahl
import de.gun.dashboard.reloaded.ui.DatumFeld
import de.gun.dashboard.reloaded.ui.Etikett
import de.gun.dashboard.reloaded.ui.Feld
import de.gun.dashboard.reloaded.ui.Fliesstext
import de.gun.dashboard.reloaded.ui.Frage
import de.gun.dashboard.reloaded.ui.Hinweis
import de.gun.dashboard.reloaded.ui.Karte
import de.gun.dashboard.reloaded.ui.Kennzahl
import de.gun.dashboard.reloaded.ui.Klappbereich
import de.gun.dashboard.reloaded.ui.Knopf
import de.gun.dashboard.reloaded.ui.Leer
import de.gun.dashboard.reloaded.ui.LocalPalette
import de.gun.dashboard.reloaded.ui.LocalSteuerung
import de.gun.dashboard.reloaded.ui.Mono
import de.gun.dashboard.reloaded.ui.Punkt
import de.gun.dashboard.reloaded.ui.StatusBalken
import de.gun.dashboard.reloaded.ui.StatusMarke
import de.gun.dashboard.reloaded.ui.Zeile
import de.gun.dashboard.reloaded.ui.statusFarbe
import java.time.LocalDate

private val EINHEITEN = listOf("T" to "Tage", "M" to "Monate", "J" to "Jahre")

@Composable
private fun Uebersicht(bisListe: List<String>) {
    val p = LocalPalette.current
    val g = bisListe.count { statusVon(it) == Status.GUELTIG }
    val w = bisListe.count { statusVon(it) == Status.WARNUNG }
    val a = bisListe.count { statusVon(it) == Status.ABGELAUFEN }
    Karte {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Kennzahl(g.toString(), "Gültig", p.gruen, Modifier.weight(1f))
            Kennzahl(w.toString(), "Bald", p.warn, Modifier.weight(1f))
            Kennzahl(a.toString(), "Abgelaufen", p.rot, Modifier.weight(1f))
        }
        Abstand(10.dp)
        StatusBalken(g, w, a)
        Hinweis("Warnstufe „gelb“ ab sechs Monate vor Ablauf.")
    }
}

/** Gültig-von + Dauer -> Vorschlag für Gültig-bis. */
@Composable
private fun DauerRechner(von: String, wert: String, onWert: (String) -> Unit, einheit: String, onEinheit: (String) -> Unit, onUebernehmen: (String) -> Unit) {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Feld(wert, onWert, "Dauer bis Ablauf", Modifier.weight(0.8f), tastatur = KeyboardType.Number)
        Spacer(Modifier.width(8.dp))
        Auswahl("Einheit", EINHEITEN, einheit, Modifier.weight(1f), onEinheit)
    }
    val vorschlag = datumPlus(von, wert, einheit)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Mono("Vorschlag: ", p.textDim, 12.sp)
        Punkt(vorschlag ?: "—", 15.sp, p.akzent, Modifier.weight(1f))
        Knopf("Übernehmen", klein = true) {
            if (vorschlag != null) onUebernehmen(vorschlag)
            else st.melden("Dauer", "Bitte zuerst ein gültiges Datum bei „Gültig von“ sowie eine Dauer eingeben.")
        }
    }
}

// ================================================================== Lehrgänge

@Composable
fun LehrgaengeSeite() {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val d = aktuelleDaten()
    var offen by remember { mutableStateOf(false) }
    var bearbeitet by remember { mutableStateOf<String?>(null) }
    var art by remember { mutableStateOf("") }
    var von by remember { mutableStateOf("") }
    var wert by remember { mutableStateOf("") }
    var einheit by remember { mutableStateOf("J") }
    var bis by remember { mutableStateOf("") }
    var notiz by remember { mutableStateOf("") }
    var loeschen by remember { mutableStateOf<AblaufEintrag?>(null) }

    fun leeren() { bearbeitet = null; art = ""; von = ""; wert = ""; einheit = "J"; bis = ""; notiz = "" }

    SeitenListe {
        item { Uebersicht(d.ablaufregister.map { it.gueltigBis }) }
        item {
            Karte(if (bearbeitet != null) "Eintrag bearbeiten" else "Lehrgänge", "01") {
                Klappbereich(if (bearbeitet != null) "Eintrag bearbeiten" else "Eintrag hinzufügen", offen, { offen = it; if (!it) leeren() }) {
                    Feld(art, { art = it }, "Art *", platzhalter = "z. B. Erste Hilfe")
                    DatumFeld(von, { von = it }, "Gültig von (optional)")
                    DauerRechner(von, wert, { wert = it }, einheit, { einheit = it }) { bis = it }
                    DatumFeld(bis, { bis = it }, "Gültig bis *")
                    Feld(notiz, { notiz = it }, "Notiz")
                    FormKnoepfe(if (bearbeitet != null) "Änderungen speichern" else "+ Eintrag hinzufügen", {
                        if (art.isBlank()) { st.melden("Eintrag", "Bitte eine Art eintragen."); return@FormKnoepfe }
                        if (von.isNotBlank() && parseDE(von) == null) { st.melden("Eintrag", "„Gültig von“ ist kein gültiges Datum."); return@FormKnoepfe }
                        if (parseDE(bis) == null) { st.melden("Eintrag", "„Gültig bis“ ist erforderlich (TT.MM.JJJJ)."); return@FormKnoepfe }
                        val e = AblaufEintrag(bearbeitet ?: neueId(), art.trim(), von.trim(), wert, einheit, bis.trim(), notiz.trim())
                        Speicher.aendern { a ->
                            a.copy(ablaufregister = if (bearbeitet != null) a.ablaufregister.map { if (it.id == e.id) e else it } else a.ablaufregister + e)
                        }
                        leeren(); offen = false
                    }) { leeren(); offen = false }
                }
                Abstand()
                val sortiert = d.ablaufregister.sortedBy { parseDE(it.gueltigBis) ?: LocalDate.MAX }
                if (sortiert.isEmpty()) Leer("Noch keine Einträge.")
                sortiert.forEach { e ->
                    val s = statusVon(e.gueltigBis)
                    Zeile(p.statusFarbe(s), aktionen = {
                        BearbeitenKnopf {
                            bearbeitet = e.id; art = e.art; von = e.gueltigVon; wert = e.dauerWert; einheit = e.dauerEinheit.ifBlank { "J" }
                            bis = e.gueltigBis; notiz = e.notiz; offen = true
                        }
                        LoeschKnopf { loeschen = e }
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Fliesstext(e.art, fett = true, modifier = Modifier.weight(1f), zeilen = 2)
                            StatusMarke(s)
                        }
                        Mono("Gültig bis ${fmt(e.gueltigBis)} · ${restlaufzeit(e.gueltigBis)}", p.textDim, 12.sp)
                        if (e.gueltigVon.isNotBlank() || e.dauerWert.isNotBlank()) Mono(
                            "Von ${fmt(e.gueltigVon)}" + if (e.dauerWert.isNotBlank()) " · Dauer ${e.dauerWert} ${EINHEITEN.firstOrNull { it.first == e.dauerEinheit }?.second ?: ""}" else "",
                            p.textFaint, 11.sp
                        )
                        if (e.notiz.isNotBlank()) Fliesstext(e.notiz, p.textDim, 12.sp)
                    }
                }
            }
        }
    }
    loeschen?.let { e ->
        Frage("Eintrag löschen", "„${e.art}“ wirklich löschen?", onJa = {
            Speicher.aendern { a -> a.copy(ablaufregister = a.ablaufregister.filter { it.id != e.id }) }; loeschen = null
        }, onNein = { loeschen = null })
    }
}

// ================================================================== Dokumente

@Composable
fun DokumenteSeite() {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val ctx = LocalContext.current
    val d = aktuelleDaten()
    var offen by remember { mutableStateOf(false) }
    var bearbeitet by remember { mutableStateOf<String?>(null) }
    var art by remember { mutableStateOf("") }
    var inhaber by remember { mutableStateOf("") }
    var nummer by remember { mutableStateOf("") }
    var von by remember { mutableStateOf("") }
    var wert by remember { mutableStateOf("") }
    var einheit by remember { mutableStateOf("J") }
    var bis by remember { mutableStateOf("") }
    var notiz by remember { mutableStateOf("") }
    var dateien by remember { mutableStateOf(listOf<Anhang>()) }
    var neu by remember { mutableStateOf(listOf<Anhang>()) }
    var loeschen by remember { mutableStateOf<Dokument?>(null) }

    fun leeren() {
        bearbeitet = null; art = ""; inhaber = ""; nummer = ""; von = ""; wert = ""; einheit = "J"; bis = ""; notiz = ""; dateien = emptyList(); neu = emptyList()
    }

    SeitenListe {
        item { Uebersicht(d.dokumente.map { it.gueltigBis }) }
        item {
            Karte("Dokumente", "01") {
                Hinweis("Ausweise, Pässe, Führerscheine – für dich und die Familie.")
                Abstand(8.dp)
                Klappbereich(if (bearbeitet != null) "Dokument bearbeiten" else "Dokument hinzufügen", offen, {
                    if (!it) { Anhaenge.loeschen(ctx, neu); leeren() }
                    offen = it
                }) {
                    VorschlagFeldArt(art) { art = it }
                    Feld(inhaber, { inhaber = it }, "Inhaber (optional)", platzhalter = "z. B. Ich, Ehefrau, Kind")
                    Feld(nummer, { nummer = it }, "Dokumentnummer (optional)")
                    DatumFeld(von, { von = it }, "Gültig von (optional)")
                    DauerRechner(von, wert, { wert = it }, einheit, { einheit = it }) { bis = it }
                    DatumFeld(bis, { bis = it }, "Gültig bis *")
                    Feld(notiz, { notiz = it }, "Notiz", platzhalter = "z. B. ausstellende Behörde")
                    Etikett("Kopie hinterlegen (optional)")
                    AnhangBereich(dateien, { l -> neu = neu + l.filter { it !in dateien }; dateien = l }, nurBilderUndPdf = true)
                    Hinweis("Foto oder Scan. Bilder werden vor dem Speichern verkleinert.")
                    FormKnoepfe(if (bearbeitet != null) "Änderungen speichern" else "+ Dokument hinzufügen", {
                        if (art.isBlank()) { st.melden("Dokument", "Bitte eine Art eintragen."); return@FormKnoepfe }
                        if (von.isNotBlank() && parseDE(von) == null) { st.melden("Dokument", "„Gültig von“ ist kein gültiges Datum."); return@FormKnoepfe }
                        if (parseDE(bis) == null) { st.melden("Dokument", "„Gültig bis“ ist erforderlich (TT.MM.JJJJ)."); return@FormKnoepfe }
                        val id = bearbeitet
                        val alt = d.dokumente.firstOrNull { it.id == id }
                        val e = Dokument(id ?: neueId(), art.trim(), inhaber.trim(), nummer.trim(), von.trim(), wert, einheit, bis.trim(), notiz.trim(), dateien)
                        Speicher.aendern { a -> a.copy(dokumente = if (id != null) a.dokumente.map { if (it.id == id) e else it } else a.dokumente + e) }
                        alt?.let { Anhaenge.loeschen(ctx, it.dateien.filter { x -> x !in dateien }) }
                        leeren(); offen = false
                    }) { Anhaenge.loeschen(ctx, neu); leeren(); offen = false }
                }
                Abstand()
                val sortiert = d.dokumente.sortedBy { parseDE(it.gueltigBis) ?: LocalDate.MAX }
                if (sortiert.isEmpty()) Leer("Noch keine Dokumente.")
                sortiert.forEach { e ->
                    val s = statusVon(e.gueltigBis)
                    Zeile(p.statusFarbe(s), aktionen = {
                        BearbeitenKnopf {
                            bearbeitet = e.id; art = e.art; inhaber = e.inhaber; nummer = e.nummer; von = e.gueltigVon; wert = e.dauerWert
                            einheit = e.dauerEinheit.ifBlank { "J" }; bis = e.gueltigBis; notiz = e.notiz; dateien = e.dateien; neu = emptyList(); offen = true
                        }
                        LoeschKnopf { loeschen = e }
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Fliesstext(e.art, fett = true, modifier = Modifier.weight(1f), zeilen = 2)
                            StatusMarke(s)
                        }
                        Mono(listOf("Inhaber: " + fmt(e.inhaber), "Nr. " + fmt(e.nummer)).joinToString(" · "), p.textDim, 12.sp)
                        Mono("Gültig bis ${fmt(e.gueltigBis)} · ${restlaufzeit(e.gueltigBis)}", p.textDim, 12.sp)
                        if (e.notiz.isNotBlank()) Fliesstext(e.notiz, p.textDim, 12.sp)
                        AnhangVorschau(e.dateien)
                    }
                }
            }
        }
    }
    loeschen?.let { e ->
        Frage("Dokument löschen", "„${e.art}“ wirklich löschen?" + if (e.dateien.isNotEmpty()) "\n\nDabei werden ${e.dateien.size} hinterlegte Kopie(n) mitgelöscht." else "", onJa = {
            Speicher.aendern { a -> a.copy(dokumente = a.dokumente.filter { it.id != e.id }) }
            Anhaenge.loeschen(ctx, e.dateien)
            loeschen = null
        }, onNein = { loeschen = null })
    }
}

@Composable
private fun VorschlagFeldArt(wert: String, onWert: (String) -> Unit) {
    de.gun.dashboard.reloaded.ui.VorschlagFeld(
        wert, onWert, "Art *",
        listOf("Personalausweis", "Reisepass", "Führerschein", "Truppenausweis", "Dienstpass", "Kinderreisepass", "Aufenthaltstitel", "EU-Führerschein", "Internationaler Führerschein")
            .map { it to it }
    )
}
