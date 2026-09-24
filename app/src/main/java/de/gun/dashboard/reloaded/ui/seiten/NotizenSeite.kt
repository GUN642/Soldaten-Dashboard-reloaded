package de.gun.dashboard.reloaded.ui.seiten

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gun.dashboard.reloaded.daten.Checkliste
import de.gun.dashboard.reloaded.daten.ChecklistenPunkt
import de.gun.dashboard.reloaded.daten.Notiz
import de.gun.dashboard.reloaded.daten.Speicher
import de.gun.dashboard.reloaded.daten.neueId
import de.gun.dashboard.reloaded.logik.heuteDE
import de.gun.dashboard.reloaded.logik.parseDE
import de.gun.dashboard.reloaded.ui.Abstand
import de.gun.dashboard.reloaded.ui.Feld
import de.gun.dashboard.reloaded.ui.Fliesstext
import de.gun.dashboard.reloaded.ui.Frage
import de.gun.dashboard.reloaded.ui.Karte
import de.gun.dashboard.reloaded.ui.Klappbereich
import de.gun.dashboard.reloaded.ui.Knopf
import de.gun.dashboard.reloaded.ui.KnopfArt
import de.gun.dashboard.reloaded.ui.Knopfreihe
import de.gun.dashboard.reloaded.ui.Leer
import de.gun.dashboard.reloaded.ui.LocalPalette
import de.gun.dashboard.reloaded.ui.LocalSteuerung
import de.gun.dashboard.reloaded.ui.Mono
import de.gun.dashboard.reloaded.ui.RUND_KLEIN
import de.gun.dashboard.reloaded.ui.Zeile
import java.time.LocalDate

private fun listenAendern(block: (List<Checkliste>) -> List<Checkliste>) =
    Speicher.aendern { it.copy(checklisten = it.checklisten.copy(listen = block(it.checklisten.listen))) }

private fun notizenAendern(block: (List<Notiz>) -> List<Notiz>) =
    Speicher.aendern { it.copy(notizen = it.notizen.copy(eintraege = block(it.notizen.eintraege))) }

@Composable
fun NotizenSeite() {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val d = aktuelleDaten()

    // ---- Checklisten
    var clOffen by remember { mutableStateOf(false) }
    var clBearbeitet by remember { mutableStateOf<String?>(null) }
    var clName by remember { mutableStateOf("") }
    var clPunkte by remember { mutableStateOf("") }
    val aufgeklappt = remember { mutableStateMapOf<String, Boolean>() }
    var clLoeschen by remember { mutableStateOf<Checkliste?>(null) }

    // ---- Notizen
    var nOffen by remember { mutableStateOf(false) }
    var nBearbeitet by remember { mutableStateOf<String?>(null) }
    var nTitel by remember { mutableStateOf("") }
    var nKat by remember { mutableStateOf("") }
    var nText by remember { mutableStateOf("") }
    val notizAuf = remember { mutableStateMapOf<String, Boolean>() }

    SeitenListe {
        item {
            Karte("Checklisten", "01") {
                Klappbereich(if (clBearbeitet != null) "Checkliste bearbeiten" else "Checkliste anlegen", clOffen, {
                    clOffen = it; if (!it) { clBearbeitet = null; clName = ""; clPunkte = "" }
                }) {
                    Feld(clName, { clName = it }, "Bezeichnung *", platzhalter = "z. B. Marschgepäck")
                    Feld(clPunkte, { clPunkte = it }, "Punkte (einer je Zeile) *", zeilen = 6)
                    FormKnoepfe("Speichern", {
                        val zeilen = clPunkte.lines().map { it.trim() }.filter { it.isNotEmpty() }
                        if (clName.isBlank()) { st.melden("Checkliste", "Bitte eine Bezeichnung eintragen."); return@FormKnoepfe }
                        if (zeilen.isEmpty()) { st.melden("Checkliste", "Bitte mindestens einen Punkt eintragen."); return@FormKnoepfe }
                        val id = clBearbeitet
                        if (id != null) listenAendern { l ->
                            l.map { c ->
                                if (c.id != id) c else {
                                    val vorher = c.punkte.associate { it.text to it.ab }
                                    c.copy(name = clName.trim(), punkte = zeilen.map { ChecklistenPunkt(neueId(), it, vorher[it] ?: false) })
                                }
                            }
                        } else listenAendern { it + Checkliste(neueId(), clName.trim(), heuteDE(), zeilen.map { z -> ChecklistenPunkt(neueId(), z, false) }) }
                        clOffen = false; clBearbeitet = null; clName = ""; clPunkte = ""
                    }) { clOffen = false; clBearbeitet = null; clName = ""; clPunkte = "" }
                }
                Abstand(8.dp)
                if (d.checklisten.listen.isEmpty()) Leer("Keine Checklisten. Vorlagen etwa für Marschgepäck oder Übungen — nach Gebrauch zurücksetzen.")
                d.checklisten.listen.forEach { c ->
                    val fertig = c.punkte.count { it.ab }
                    val auf = aufgeklappt[c.id] == true
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RUND_KLEIN).background(p.panelAlt).border(1.dp, p.randLeise, RUND_KLEIN)) {
                        Row(Modifier.fillMaxWidth().clickable { aufgeklappt[c.id] = !auf }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Mono(if (auf) "▾" else "▸", p.textFaint)
                            Spacer(Modifier.width(8.dp))
                            Fliesstext(c.name, fett = true, modifier = Modifier.weight(1f), zeilen = 1)
                            Mono("$fertig / ${c.punkte.size}", if (c.punkte.isNotEmpty() && fertig == c.punkte.size) p.akzent else p.textDim, 12.sp, fett = true)
                        }
                        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(p.randLeise)) {
                            val anteil = if (c.punkte.isEmpty()) 0f else fertig.toFloat() / c.punkte.size
                            Box(Modifier.fillMaxWidth(anteil).fillMaxHeight().background(if (anteil >= 1f) p.akzent else p.gruen))
                        }
                        if (auf) {
                            Column(Modifier.padding(12.dp)) {
                                c.punkte.forEach { pt ->
                                    Row(Modifier.fillMaxWidth().clickable {
                                        listenAendern { l -> l.map { x -> if (x.id != c.id) x else x.copy(punkte = x.punkte.map { if (it.id == pt.id) it.copy(ab = !it.ab) else it }) } }
                                    }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)).background(if (pt.ab) p.gruen else Color.Transparent)
                                            .border(1.5.dp, if (pt.ab) p.gruen else p.rand, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                                            if (pt.ab) Mono("✓", p.bg, 11.sp, fett = true)
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Fliesstext(pt.text, if (pt.ab) p.textFaint else p.text, 14.sp)
                                    }
                                }
                                Knopfreihe {
                                    Knopf("↺ Zurücksetzen", klein = true) {
                                        listenAendern { l -> l.map { x -> if (x.id != c.id) x else x.copy(punkte = x.punkte.map { it.copy(ab = false) }) } }
                                    }
                                    Knopf("✎ Bearbeiten", klein = true) {
                                        clBearbeitet = c.id; clName = c.name; clPunkte = c.punkte.joinToString("\n") { it.text }; clOffen = true
                                    }
                                    Knopf("✕ Löschen", klein = true, art = KnopfArt.GEFAHR) { clLoeschen = c }
                                }
                            }
                        } else Abstand(10.dp)
                    }
                }
            }
        }
        item {
            val sortiert = d.notizen.eintraege.sortedByDescending { parseDE(it.geaendert.ifBlank { it.erstellt }) ?: LocalDate.MIN }
            Karte("Persönliche Notizen", "02", aktion = { Mono("${sortiert.size} " + if (sortiert.size == 1) "Notiz" else "Notizen", p.textDim, 11.sp) }) {
                Klappbereich(if (nBearbeitet != null) "Notiz bearbeiten" else "Notiz anlegen", nOffen, {
                    nOffen = it; if (!it) { nBearbeitet = null; nTitel = ""; nKat = ""; nText = "" }
                }) {
                    Feld(nTitel, { nTitel = it }, "Titel *")
                    Feld(nKat, { nKat = it }, "Kategorie (optional)", platzhalter = "z. B. Dienst, Privat")
                    Feld(nText, { nText = it }, "Text", zeilen = 6)
                    FormKnoepfe(if (nBearbeitet != null) "Änderungen speichern" else "+ Notiz speichern", {
                        if (nTitel.isBlank()) { st.melden("Notiz", "Bitte einen Titel eintragen."); return@FormKnoepfe }
                        val id = nBearbeitet
                        if (id != null) notizenAendern { l -> l.map { if (it.id == id) it.copy(titel = nTitel.trim(), kategorie = nKat.trim(), text = nText, geaendert = heuteDE()) else it } }
                        else notizenAendern { it + Notiz(neueId(), nTitel.trim(), nKat.trim(), nText, heuteDE(), heuteDE()) }
                        nOffen = false; nBearbeitet = null; nTitel = ""; nKat = ""; nText = ""
                    }) { nOffen = false; nBearbeitet = null; nTitel = ""; nKat = ""; nText = "" }
                }
                Abstand(8.dp)
                if (sortiert.isEmpty()) Leer("Noch keine Notizen.")
                sortiert.forEach { n ->
                    val auf = notizAuf[n.id] == true
                    Zeile(p.akzent, onClick = { notizAuf[n.id] = !auf }) {
                        Fliesstext(n.titel, fett = true, zeilen = 1)
                        Mono(listOfNotNull(n.kategorie.ifBlank { null }, "Geändert: " + n.geaendert.ifBlank { n.erstellt }).joinToString(" · "), p.textFaint, 11.sp)
                        if (auf) {
                            Abstand(6.dp)
                            Fliesstext(n.text.ifBlank { "Kein weiterer Freitext hinterlegt." }, if (n.text.isBlank()) p.textFaint else p.text, 14.sp)
                            Knopfreihe {
                                Knopf("✎ Bearbeiten", klein = true) {
                                    nBearbeitet = n.id; nTitel = n.titel; nKat = n.kategorie; nText = n.text; nOffen = true
                                }
                                Knopf("✕ Löschen", klein = true, art = KnopfArt.GEFAHR) {
                                    val vorher = Speicher.aktuell.notizen.eintraege
                                    notizenAendern { l -> l.filter { it.id != n.id } }
                                    st.rueckgaengig("Notiz „${n.titel}“ gelöscht", { notizenAendern { vorher } })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    clLoeschen?.let { c ->
        Frage("Checkliste löschen", "Checkliste „${c.name}“ wirklich löschen?", onJa = {
            listenAendern { l -> l.filter { it.id != c.id } }; clLoeschen = null
        }, onNein = { clLoeschen = null })
    }
}
