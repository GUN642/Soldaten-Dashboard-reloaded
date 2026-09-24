package de.gun.dashboard.reloaded.ui.seiten

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gun.dashboard.reloaded.daten.Anhaenge
import de.gun.dashboard.reloaded.daten.Anhang
import de.gun.dashboard.reloaded.daten.Aufgabe
import de.gun.dashboard.reloaded.daten.Speicher
import de.gun.dashboard.reloaded.daten.neueId
import de.gun.dashboard.reloaded.logik.FARBE_TODO
import de.gun.dashboard.reloaded.logik.FARBE_TODO_HOCH
import de.gun.dashboard.reloaded.logik.alsDE
import de.gun.dashboard.reloaded.logik.heuteDE
import de.gun.dashboard.reloaded.logik.parseDE
import de.gun.dashboard.reloaded.logik.zeitNormieren
import de.gun.dashboard.reloaded.ui.Abstand
import de.gun.dashboard.reloaded.ui.Auswahl
import de.gun.dashboard.reloaded.ui.DatumFeld
import de.gun.dashboard.reloaded.ui.Etikett
import de.gun.dashboard.reloaded.ui.Feld
import de.gun.dashboard.reloaded.ui.Fliesstext
import de.gun.dashboard.reloaded.ui.Frage
import de.gun.dashboard.reloaded.ui.Karte
import de.gun.dashboard.reloaded.ui.Klappbereich
import de.gun.dashboard.reloaded.ui.Knopf
import de.gun.dashboard.reloaded.ui.Knopfreihe
import de.gun.dashboard.reloaded.ui.Leer
import de.gun.dashboard.reloaded.ui.LocalPalette
import de.gun.dashboard.reloaded.ui.LocalSteuerung
import de.gun.dashboard.reloaded.ui.Mono
import de.gun.dashboard.reloaded.ui.Symbol
import de.gun.dashboard.reloaded.ui.ZeitFeld
import de.gun.dashboard.reloaded.ui.Zeile
import java.time.LocalDate

private val PRIO_RANG = mapOf("hoch" to 0, "mittel" to 1, "niedrig" to 2)

fun aufgabenAendern(block: (List<Aufgabe>) -> List<Aufgabe>) =
    Speicher.aendern { it.copy(todos = it.todos.copy(eintraege = block(it.todos.eintraege))) }

@Composable
fun AufgabenSeite() {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val ctx = LocalContext.current
    val d = aktuelleDaten()
    val alle = d.todos.eintraege
    val offen = alle.filter { !it.erledigt }.sortedWith(
        compareBy<Aufgabe>({ PRIO_RANG[it.prio] ?: 1 }, { parseDE(it.faellig) == null }, { parseDE(it.faellig) ?: LocalDate.MAX })
    )
    val erledigt = alle.filter { it.erledigt }

    var formOffen by remember { mutableStateOf(false) }
    var bearbeitet by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var faellig by remember { mutableStateOf("") }
    var uhrzeit by remember { mutableStateOf("") }
    var prio by remember { mutableStateOf("mittel") }
    var notiz by remember { mutableStateOf("") }
    var anhaenge by remember { mutableStateOf(listOf<Anhang>()) }
    var neuAnh by remember { mutableStateOf(listOf<Anhang>()) }
    var alleErledigtWeg by remember { mutableStateOf(false) }

    fun leeren() {
        bearbeitet = null; text = ""; faellig = ""; uhrzeit = ""; prio = "mittel"; notiz = ""; anhaenge = emptyList(); neuAnh = emptyList()
    }

    fun speichern() {
        if (text.isBlank()) { st.melden("Aufgabe", "Bitte eine Aufgabe eingeben."); return }
        if (faellig.isNotBlank() && parseDE(faellig) == null) { st.melden("Aufgabe", "Bitte ein gültiges Fälligkeitsdatum (TT.MM.JJJJ) eingeben, oder das Feld leer lassen."); return }
        val uz = if (uhrzeit.isBlank()) "" else zeitNormieren(uhrzeit) ?: run { st.melden("Aufgabe", "Bitte eine gültige Uhrzeit im Format HH:MM eingeben."); return }
        if (uz.isNotEmpty() && faellig.isBlank()) { st.melden("Aufgabe", "Für eine Uhrzeit wird auch ein Fälligkeitsdatum benötigt."); return }
        val id = bearbeitet
        if (id != null) {
            val alt = alle.firstOrNull { it.id == id }
            aufgabenAendern { l -> l.map { if (it.id == id) it.copy(text = text.trim(), faellig = faellig.trim(), uhrzeit = uz, prio = prio, notiz = notiz.trim(), anhaenge = anhaenge) else it } }
            alt?.let { Anhaenge.loeschen(ctx, it.anhaenge.filter { a -> a !in anhaenge }) }
        } else {
            aufgabenAendern { it + Aufgabe(neueId(), text.trim(), faellig.trim(), uz, prio, notiz.trim(), anhaenge, false, heuteDE()) }
        }
        leeren(); formOffen = false
    }

    SeitenListe {
        item {
            Karte("To-do-Liste", "01", aktion = { Mono("${offen.size} offen" + if (erledigt.isNotEmpty()) " · ${erledigt.size} erledigt" else "", p.textDim, 11.sp) }) {
                Klappbereich(if (bearbeitet != null) "Aufgabe bearbeiten" else "Aufgabe hinzufügen", formOffen, {
                    if (!it) { Anhaenge.loeschen(ctx, neuAnh); leeren() }
                    formOffen = it
                }) {
                    Feld(text, { text = it }, "Aufgabe *", platzhalter = "z. B. Lehrgangsantrag einreichen")
                    Row {
                        DatumFeld(faellig, { faellig = it }, "Fällig am", Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        ZeitFeld(uhrzeit, { uhrzeit = it }, "Uhrzeit", Modifier.weight(0.7f))
                    }
                    Knopfreihe {
                        Knopf("Heute", klein = true) { faellig = LocalDate.now().alsDE() }
                        Knopf("Morgen", klein = true) { faellig = LocalDate.now().plusDays(1).alsDE() }
                        Knopf("+1 Woche", klein = true) { faellig = LocalDate.now().plusDays(7).alsDE() }
                    }
                    Auswahl("Priorität", listOf("niedrig" to "Niedrig", "mittel" to "Mittel", "hoch" to "Hoch"), prio) { prio = it }
                    Feld(notiz, { notiz = it }, "Notizen (optional)", platzhalter = "Weitere Angaben, Ansprechpartner …", zeilen = 3)
                    Etikett("Dateianhang (optional)")
                    AnhangBereich(anhaenge, { neu -> neuAnh = neuAnh + neu.filter { it !in anhaenge }; anhaenge = neu })
                    FormKnoepfe(if (bearbeitet != null) "✓ Übernehmen" else "+ Hinzufügen", ::speichern) {
                        Anhaenge.loeschen(ctx, neuAnh); leeren(); formOffen = false
                    }
                }
                Abstand()
                Etikett("Offen")
                if (offen.isEmpty()) Leer("Keine offenen Aufgaben.")
                offen.forEach { t ->
                    AufgabenZeile(t, onBearbeiten = {
                        bearbeitet = t.id; text = t.text; faellig = t.faellig; uhrzeit = t.uhrzeit; prio = t.prio.ifBlank { "mittel" }
                        notiz = t.notiz; anhaenge = t.anhaenge; neuAnh = emptyList(); formOffen = true
                    })
                }
            }
        }
        item {
            Karte("Erledigt", "02", aktion = {
                if (erledigt.isNotEmpty()) Knopf("Alle löschen", klein = true) { alleErledigtWeg = true }
            }) {
                if (erledigt.isEmpty()) Leer("Noch nichts erledigt.")
                erledigt.forEach { t -> AufgabenZeile(t, onBearbeiten = null) }
            }
        }
    }

    if (alleErledigtWeg) Frage("Erledigte löschen", "Alle ${erledigt.size} erledigten Aufgaben wirklich löschen?", onJa = {
        val weg = erledigt
        aufgabenAendern { l -> l.filter { !it.erledigt } }
        weg.forEach { Anhaenge.loeschen(ctx, it.anhaenge) }
        alleErledigtWeg = false
    }, onNein = { alleErledigtWeg = false })
}

@Composable
private fun AufgabenZeile(t: Aufgabe, onBearbeiten: (() -> Unit)?) {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val ctx = LocalContext.current
    var auf by remember { mutableStateOf(false) }
    val f = parseDE(t.faellig)
    val ueber = !t.erledigt && f != null && f.isBefore(LocalDate.now())
    val farbe = when {
        t.erledigt -> p.textFaint
        ueber -> p.rot
        t.prio == "hoch" -> Color(FARBE_TODO_HOCH)
        else -> Color(FARBE_TODO)
    }
    Zeile(farbe, onClick = { if (t.notiz.isNotBlank() || t.anhaenge.isNotEmpty()) auf = !auf }, aktionen = {
        if (onBearbeiten != null) BearbeitenKnopf(onBearbeiten)
        LoeschKnopf {
            val vorher = Speicher.aktuell.todos.eintraege
            aufgabenAendern { l -> l.filter { it.id != t.id } }
            st.rueckgaengig("Aufgabe „${t.text}“ gelöscht", { aufgabenAendern { vorher } }) { Anhaenge.loeschen(ctx, t.anhaenge) }
        }
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(if (t.erledigt) p.gruen else Color.Transparent)
                    .border(1.5.dp, if (t.erledigt) p.gruen else p.rand, CircleShape)
                    .clickable {
                        aufgabenAendern { l -> l.map { if (it.id == t.id) it.copy(erledigt = !it.erledigt, erledigtAm = if (!it.erledigt) heuteDE() else "") else it } }
                    },
                contentAlignment = Alignment.Center,
            ) { if (t.erledigt) Mono("✓", p.bg, 13.sp, fett = true) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                androidx.compose.material3.Text(
                    t.text, color = if (t.erledigt) p.textFaint else p.text,
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = de.gun.dashboard.reloaded.ui.Schrift.text, fontSize = 15.sp,
                        textDecoration = if (t.erledigt) TextDecoration.LineThrough else TextDecoration.None,
                    ),
                )
                val meta = listOfNotNull(
                    if (t.faellig.isNotBlank()) (if (ueber) "Überfällig seit " else "Fällig: ") + t.faellig + (if (t.uhrzeit.isNotBlank()) ", ${t.uhrzeit} Uhr" else "") else null,
                    if (t.erledigt && t.erledigtAm.isNotBlank()) "Erledigt: ${t.erledigtAm}" else null,
                    if (!t.erledigt && t.prio.isNotBlank()) t.prio.replaceFirstChar { it.uppercase() } else null,
                    if (t.anhaenge.isNotEmpty()) "${t.anhaenge.size} " + if (t.anhaenge.size == 1) "Anhang" else "Anhänge" else null,
                )
                if (meta.isNotEmpty()) Fliesstext(meta.joinToString(" · "), if (ueber) p.rot else p.textDim, 12.sp)
            }
        }
        if (auf) {
            if (t.notiz.isNotBlank()) Fliesstext(t.notiz, p.textDim, 13.sp, Modifier)
            AnhangVorschau(t.anhaenge)
        }
    }
}
