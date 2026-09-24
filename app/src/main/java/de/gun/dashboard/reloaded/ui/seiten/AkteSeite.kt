package de.gun.dashboard.reloaded.ui.seiten

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import de.gun.dashboard.reloaded.Aktualisierung
import de.gun.dashboard.reloaded.daten.EigenerTermin
import de.gun.dashboard.reloaded.daten.MedEintrag
import de.gun.dashboard.reloaded.daten.Medizin
import de.gun.dashboard.reloaded.daten.Speicher
import de.gun.dashboard.reloaded.daten.neueId
import de.gun.dashboard.reloaded.geraet.GeraeteKalender
import de.gun.dashboard.reloaded.geraet.TerminFelder
import de.gun.dashboard.reloaded.logik.IGF_ARTEN
import de.gun.dashboard.reloaded.logik.Status
import de.gun.dashboard.reloaded.logik.alsDE
import de.gun.dashboard.reloaded.logik.fmt
import de.gun.dashboard.reloaded.logik.heuteDE
import de.gun.dashboard.reloaded.logik.igfStatus
import de.gun.dashboard.reloaded.logik.parseDE
import de.gun.dashboard.reloaded.logik.restlaufzeit
import de.gun.dashboard.reloaded.logik.schutz
import de.gun.dashboard.reloaded.logik.statusVon
import de.gun.dashboard.reloaded.ui.Abstand
import de.gun.dashboard.reloaded.ui.Auswahl
import de.gun.dashboard.reloaded.ui.DatumFeld
import de.gun.dashboard.reloaded.ui.Feld
import de.gun.dashboard.reloaded.ui.Fliesstext
import de.gun.dashboard.reloaded.ui.Frage
import de.gun.dashboard.reloaded.ui.Hinweis
import de.gun.dashboard.reloaded.ui.Karte
import de.gun.dashboard.reloaded.ui.Klappbereich
import de.gun.dashboard.reloaded.ui.Knopf
import de.gun.dashboard.reloaded.ui.Knopfreihe
import de.gun.dashboard.reloaded.ui.Leer
import de.gun.dashboard.reloaded.ui.LocalPalette
import de.gun.dashboard.reloaded.ui.LocalSteuerung
import de.gun.dashboard.reloaded.ui.Mono
import de.gun.dashboard.reloaded.ui.Pille
import de.gun.dashboard.reloaded.ui.StatusMarke
import de.gun.dashboard.reloaded.ui.VorschlagFeld
import de.gun.dashboard.reloaded.ui.Zeile
import de.gun.dashboard.reloaded.ui.statusFarbe
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class MedTyp(val titel: String, val index: String) {
    IGF("IGF — Individuelle Grundfertigkeiten", "02"),
    ICCS("ICCS — Lernfelder", "03"),
    AVU("AVU-IGF / WFV", "04"),
    IMPF("Impfungen", "05"),
}

private val AVU_ARTEN = listOf("Hörtest", "Sehtest", "Blutentnahme", "EKG", "Belastungs-EKG", "Lungenfunktion", "Zahnarzt",
    "Fliegertauglichkeit", "Wehrmedizinische Begutachtung", "G-Untersuchung")
private val IMPF_ARTEN = listOf("Tetanus" to 10, "Diphtherie" to 10, "Polio" to 10, "Hepatitis A" to 10, "Hepatitis B" to 10, "FSME" to 5,
    "Typhus" to 3, "Tollwut" to 5, "Gelbfieber" to 0, "Masern/Mumps/Röteln" to 0, "Influenza" to 1, "COVID-19" to 1)

private fun liste(m: Medizin, t: MedTyp) = when (t) {
    MedTyp.IGF -> m.igf
    MedTyp.ICCS -> m.iccs
    MedTyp.AVU -> m.avu
    MedTyp.IMPF -> m.impfungen
}

private fun mit(m: Medizin, t: MedTyp, l: List<MedEintrag>) = when (t) {
    MedTyp.IGF -> m.copy(igf = l)
    MedTyp.ICCS -> m.copy(iccs = l)
    MedTyp.AVU -> m.copy(avu = l)
    MedTyp.IMPF -> m.copy(impfungen = l)
}

fun medAendern(t: MedTyp, block: (List<MedEintrag>) -> List<MedEintrag>) =
    Speicher.aendern { a -> a.copy(medizin = mit(a.medizin, t, block(liste(a.medizin, t)))) }

private fun bisAusIntervall(datum: String, intervall: String): String? {
    val d = parseDE(datum) ?: return null
    val m = intervall.removeSuffix("M").toLongOrNull() ?: return null
    return d.plusMonths(m).alsDE()
}

@Composable
fun AkteSeite() {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val ctx = LocalContext.current
    val d = aktuelleDaten()
    val m = d.medizin
    var pflichtOffen by remember { mutableStateOf(false) }

    SeitenListe {
        item {
            Karte("Person", "01") {
                val pr = m.person
                fun setzen(block: (de.gun.dashboard.reloaded.daten.Person) -> de.gun.dashboard.reloaded.daten.Person) =
                    Speicher.aendern { a -> a.copy(medizin = a.medizin.copy(person = block(a.medizin.person))) }
                Feld(pr.name, { v -> setzen { it.copy(name = v) } }, "Name", platzhalter = "Nachname, Vorname")
                Feld(pr.dienstgrad, { v -> setzen { it.copy(dienstgrad = v) } }, "Dienstgrad", platzhalter = "z. B. Hauptfeldwebel")
                Row {
                    Feld(pr.einheit, { v -> setzen { it.copy(einheit = v) } }, "Einheit (optional)", Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    DatumFeld(pr.geburt, { v -> setzen { it.copy(geburt = v) } }, "Geburtsdatum", Modifier.weight(1f))
                }
            }
        }
        item {
            val s = igfStatus(m.igf, m.igfPflicht)
            Karte(MedTyp.IGF.titel, MedTyp.IGF.index) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Mono("●", p.statusFarbe(s.status), 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Fliesstext(s.text, p.statusFarbe(s.status), 14.sp, Modifier.weight(1f), fett = true)
                    Knopf("⚙ Bestandteile", klein = true) { pflichtOffen = !pflichtOffen }
                }
                if (s.fehlend.isNotEmpty()) Hinweis("Betrifft: " + s.fehlend.joinToString(", "))
                if (pflichtOffen) {
                    Hinweis("Welche Bestandteile für den Status zählen. Vorbelegt sind die vier, die laut Zentralanweisung B1-224/0-2 für alle gelten.")
                    Knopfreihe {
                        IGF_ARTEN.forEach { art ->
                            Pille(art, art in m.igfPflicht) {
                                Speicher.aendern { a ->
                                    val l = a.medizin.igfPflicht
                                    a.copy(medizin = a.medizin.copy(igfPflicht = if (art in l) l - art else l + art))
                                }
                            }
                        }
                    }
                }
                Abstand()
                MedBereich(MedTyp.IGF, m.igf)
                if (m.igf.isNotEmpty() || m.avu.isNotEmpty() || m.impfungen.isNotEmpty()) Knopfreihe {
                    Knopf("⎙ Übersicht drucken", klein = true) { htmlDrucken(ctx, igfUebersichtHtml(Speicher.aktuell.medizin), "IGF-Übersicht") }
                    Knopf("✉ Teilen", klein = true) {
                        val name = "IGF-Uebersicht" + (if (m.person.name.isNotBlank()) "_" + m.person.name.replace(Regex("[^\\wäöüÄÖÜß]"), "_") else "") + "_" + LocalDate.now() + ".html"
                        dateiTeilen(ctx, igfUebersichtHtml(Speicher.aktuell.medizin), name, "text/html")
                    }
                }
            }
        }
        item { Karte(MedTyp.ICCS.titel, MedTyp.ICCS.index) { MedBereich(MedTyp.ICCS, m.iccs) } }
        item { Karte(MedTyp.AVU.titel, MedTyp.AVU.index) { MedBereich(MedTyp.AVU, m.avu) } }
        item { Karte(MedTyp.IMPF.titel, MedTyp.IMPF.index) { MedBereich(MedTyp.IMPF, m.impfungen) } }
    }
}

@Composable
private fun MedBereich(typ: MedTyp, eintraege: List<MedEintrag>) {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var offen by remember { mutableStateOf(false) }
    var e by remember { mutableStateOf(MedEintrag()) }
    var loeschen by remember { mutableStateOf<MedEintrag?>(null) }

    fun neu() = when (typ) {
        MedTyp.IGF -> MedEintrag(datum = heuteDE(), intervall = "12M", gueltigBis = bisAusIntervall(heuteDE(), "12M") ?: "")
        MedTyp.ICCS -> MedEintrag(art = "ICCS Lernfeld 1", datum = heuteDE(), intervall = "24M", gueltigBis = bisAusIntervall(heuteDE(), "24M") ?: "")
        MedTyp.AVU -> MedEintrag(datum = heuteDE(), intervall = "12M", gueltigBis = bisAusIntervall(heuteDE(), "12M") ?: "")
        MedTyp.IMPF -> MedEintrag(datum = heuteDE())
    }

    // Vorlage aus der BFT-Bewertung übernehmen
    if (typ == MedTyp.IGF) LaunchedEffect(st.igfVorlage) {
        st.igfVorlage?.let { v -> e = v; offen = true; st.igfVorlage = null }
    }

    fun mitDatumOderIntervall(n: MedEintrag): MedEintrag = when {
        typ == MedTyp.IMPF -> {
            val j = n.jahre
            val d = parseDE(n.datum)
            if (d != null && j != null) n.copy(gueltigBis = if (j <= 0) "" else d.plusYears(j.toLong()).alsDE()) else n
        }
        n.intervall == "frei" -> if (typ == MedTyp.ICCS) n.copy(gueltigBis = "") else n
        else -> bisAusIntervall(n.datum, n.intervall)?.let { n.copy(gueltigBis = it) } ?: n
    }

    Klappbereich(if (e.id.isNotBlank()) "Eintrag bearbeiten" else "Eintrag hinzufügen", offen, {
        offen = it; e = if (it && e.id.isBlank() && e.datum.isBlank()) neu() else if (!it) MedEintrag() else e
    }) {
        when (typ) {
            MedTyp.IGF -> VorschlagFeld(e.art, { e = e.copy(art = it) }, "Prüfung *", IGF_ARTEN.map { it to it })
            MedTyp.ICCS -> Auswahl("Lernfeld *", (1..6).map { "ICCS Lernfeld $it" to "Lernfeld $it" }, e.art) { e = e.copy(art = it) }
            MedTyp.AVU -> VorschlagFeld(e.art, { e = e.copy(art = it) }, "Untersuchung *", AVU_ARTEN.map { it to it })
            MedTyp.IMPF -> VorschlagFeld(e.art, { e = e.copy(art = it) }, "Impfung *",
                IMPF_ARTEN.map { (n, j) -> n to "$n (" + (if (j == 0) "unbegrenzt" else "$j J.") + ")" }) { gewaehlt ->
                IMPF_ARTEN.firstOrNull { it.first == gewaehlt }?.let { (_, j) -> e = mitDatumOderIntervall(e.copy(art = gewaehlt, jahre = j)) }
            }
        }
        DatumFeld(e.datum, { e = mitDatumOderIntervall(e.copy(datum = it)) }, if (typ == MedTyp.IMPF) "Geimpft am *" else "Datum *")
        if (typ == MedTyp.IMPF) {
            Row {
                Feld(e.jahre?.toString() ?: "", { v -> e = mitDatumOderIntervall(e.copy(jahre = v.trim().toIntOrNull())) }, "Gültig (Jahre)",
                    Modifier.weight(0.7f), tastatur = KeyboardType.Number)
                Spacer(Modifier.width(8.dp))
                DatumFeld(e.gueltigBis, { e = e.copy(gueltigBis = it) }, "Ablaufdatum", Modifier.weight(1f))
            }
            Feld(e.dosis, { e = e.copy(dosis = it) }, "Dosis / Charge (optional)")
        } else {
            val intervalle = when (typ) {
                MedTyp.AVU -> listOf("6M" to "6 Monate", "12M" to "12 Monate", "24M" to "24 Monate", "36M" to "36 Monate", "60M" to "60 Monate", "frei" to "eigenes Datum")
                MedTyp.ICCS -> listOf("12M" to "12 Monate", "24M" to "24 Monate", "36M" to "36 Monate", "frei" to "ohne Ablauf")
                else -> listOf("12M" to "12 Monate", "24M" to "24 Monate", "36M" to "36 Monate", "frei" to "eigenes Datum")
            }
            Row {
                Auswahl("Gültigkeit", intervalle, e.intervall.ifBlank { "frei" }, Modifier.weight(1f)) { e = mitDatumOderIntervall(e.copy(intervall = it)) }
                Spacer(Modifier.width(8.dp))
                if (!(typ == MedTyp.ICCS && e.intervall == "frei"))
                    DatumFeld(e.gueltigBis, { e = e.copy(gueltigBis = it) }, "Gültig bis", Modifier.weight(1f))
            }
        }
        when (typ) {
            MedTyp.AVU -> {
                Feld(e.befund, { e = e.copy(befund = it) }, "Befund / Ergebnis")
                Feld(e.stelle, { e = e.copy(stelle = it) }, "Stelle", platzhalter = "z. B. SanVersZ")
            }
            MedTyp.IMPF -> {}
            else -> Feld(e.ergebnis, { e = e.copy(ergebnis = it) }, "Ergebnis / Note", platzhalter = "z. B. bestanden, 245 Punkte")
        }
        Feld(e.notiz, { e = e.copy(notiz = it) }, if (typ == MedTyp.AVU) "Notiz / Zusatzfeld" else "Notiz")
        FormKnoepfe("Speichern", {
            if (e.art.isBlank()) { st.melden("Eintrag", "Bitte eine Bezeichnung eintragen."); return@FormKnoepfe }
            if (parseDE(e.datum) == null) { st.melden("Eintrag", "Bitte ein gültiges Datum (TT.MM.JJJJ) eintragen."); return@FormKnoepfe }
            if (e.gueltigBis.isNotBlank() && parseDE(e.gueltigBis) == null) { st.melden("Eintrag", "Das Ablaufdatum ist kein gültiges Datum."); return@FormKnoepfe }
            val fertig = if (e.id.isBlank()) e.copy(id = neueId()) else e
            medAendern(typ) { l -> if (l.any { it.id == fertig.id }) l.map { if (it.id == fertig.id) fertig else it } else l + fertig }
            e = MedEintrag(); offen = false
        }) { e = MedEintrag(); offen = false }
        if (typ == MedTyp.IGF) Knopf("📅 Termin in Kalender eintragen", klein = true) {
            val tag = parseDE(e.datum)
            if (e.art.isBlank() || tag == null) { st.melden("Kalender", "Bitte Prüfung und Datum eintragen."); return@Knopf }
            scope.launch {
                try {
                    val d = Speicher.aktuell
                    val notiz = listOf(e.ergebnis, e.notiz).filter { it.isNotBlank() }.joinToString(" · ")
                    var nativ = ""
                    val ziel = d.nativ.zielKalenderId
                    if (ziel.isNotBlank() && GeraeteKalender.darfSchreiben(ctx)) {
                        nativ = GeraeteKalender.anlegen(ctx, ziel, TerminFelder("IGF: " + e.art, "", notiz, true, tag.atStartOfDay(), tag.atStartOfDay(), "")).toString()
                    }
                    Speicher.aendern { a -> a.copy(kalender = a.kalender.copy(eigene = a.kalender.eigene + EigenerTermin(
                        id = neueId(), uid = neueId() + "@dienst-cockpit", erstellt = heuteDE(), titel = "IGF: " + e.art,
                        von = e.datum, bis = e.datum, ganztags = true, notiz = notiz, kalenderId = ziel, nativId = nativ, nativ = nativ.isNotBlank()
                    ))) }
                    Aktualisierung.geraetLadenJetzt(ctx)
                    st.kurz("Termin im Kalender angelegt")
                } catch (ex: Exception) { st.melden("Kalender", ex.message ?: "Fehler") }
            }
        }
    }
    Abstand(8.dp)
    val sortiert = eintraege.sortedWith(compareBy({ parseDE(it.gueltigBis) ?: LocalDate.MAX }, { -(parseDE(it.datum)?.toEpochDay() ?: 0) }))
    if (sortiert.isEmpty()) Leer("Noch keine Einträge.")
    sortiert.forEach { x ->
        var s = if (x.gueltigBis.isNotBlank()) statusVon(x.gueltigBis) else Status.UNBEKANNT
        // IGF kennt keine Vorwarnstufe: gültig oder abgelaufen
        if (typ == MedTyp.IGF && s == Status.WARNUNG) s = Status.GUELTIG
        Zeile(p.statusFarbe(s), aktionen = {
            BearbeitenKnopf { e = x; offen = true }
            LoeschKnopf { loeschen = x }
        }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Fliesstext(x.art, fett = true, modifier = Modifier.weight(1f), zeilen = 2)
                if (x.gueltigBis.isNotBlank()) StatusMarke(s)
            }
            val meta = when (typ) {
                MedTyp.IMPF -> "Geimpft: ${fmt(x.datum)}" + (if (x.gueltigBis.isNotBlank()) " · gültig bis ${x.gueltigBis}" else if (x.jahre == 0) " · unbegrenzt gültig" else "") +
                    (x.jahre?.takeIf { it > 0 }?.let { " · $it Jahre" } ?: "")
                else -> "Datum: ${fmt(x.datum)}" + if (x.gueltigBis.isNotBlank()) " · gültig bis ${x.gueltigBis}" else if (typ == MedTyp.ICCS) " · ohne Ablauf" else ""
            }
            Mono(meta + (restlaufzeit(x.gueltigBis).takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), p.textDim, 12.sp)
            val notiz = listOf(x.befund, x.stelle, x.ergebnis, x.dosis, x.notiz).filter { it.isNotBlank() }.joinToString(" · ")
            if (notiz.isNotBlank()) Fliesstext(notiz, p.textDim, 12.sp)
        }
    }
    loeschen?.let { x ->
        Frage("Eintrag löschen", "„${x.art}“ wirklich löschen?", onJa = { medAendern(typ) { l -> l.filter { it.id != x.id } }; loeschen = null }, onNein = { loeschen = null })
    }
}

/** Druckfähige IGF-Übersicht (wie im alten Dashboard). */
fun igfUebersichtHtml(m: Medizin): String {
    val pr = m.person
    fun tabelle(titel: String, liste: List<MedEintrag>, spalten: List<Pair<String, (MedEintrag) -> String>>): String {
        if (liste.isEmpty()) return ""
        val kopf = spalten.joinToString("") { "<th>${it.first}</th>" }
        val zeilen = liste.sortedBy { parseDE(it.gueltigBis) ?: parseDE(it.datum) ?: LocalDate.MIN }.joinToString("") { e ->
            val s = if (e.gueltigBis.isNotBlank()) statusVon(e.gueltigBis) else Status.UNBEKANNT
            val k = when (s) { Status.ABGELAUFEN -> " class=\"ab\""; Status.WARNUNG -> " class=\"wa\""; else -> "" }
            "<tr$k>" + spalten.joinToString("") { "<td>" + it.second(e).ifBlank { "—" } + "</td>" } + "</tr>"
        }
        return "<h2>$titel</h2><table><thead><tr>$kopf</tr></thead><tbody>$zeilen</tbody></table>"
    }
    val std = listOf<Pair<String, (MedEintrag) -> String>>(
        "Prüfung" to { it: MedEintrag -> schutz(it.art) }, "Datum" to { it: MedEintrag -> fmt(it.datum) }, "Gültig bis" to { it: MedEintrag -> fmt(it.gueltigBis) },
        "Ergebnis" to { it: MedEintrag -> schutz(it.ergebnis) }, "Notiz" to { it: MedEintrag -> schutz(it.notiz) })
    return "<!DOCTYPE html><html lang=\"de\"><head><meta charset=\"UTF-8\"><title>IGF-Übersicht</title><style>" +
        "body{font-family:Arial,Helvetica,sans-serif;font-size:11pt;color:#111;margin:18mm 15mm;}h1{font-size:16pt;margin:0 0 2mm;}" +
        "h2{font-size:12pt;margin:8mm 0 2mm;border-bottom:1px solid #999;padding-bottom:1mm;}.kopf{border:1px solid #999;padding:3mm;margin-bottom:4mm;}" +
        ".kopf div{margin:0.8mm 0;}.kopf b{display:inline-block;min-width:32mm;}table{width:100%;border-collapse:collapse;margin-bottom:3mm;}" +
        "th,td{border:1px solid #999;padding:1.6mm 2mm;text-align:left;font-size:10pt;}th{background:#eee;}tr.ab td{background:#fde8e8;}" +
        "tr.wa td{background:#fdf6e3;}.fuss{margin-top:6mm;font-size:8.5pt;color:#555;border-top:1px solid #ccc;padding-top:2mm;}</style></head><body>" +
        "<h1>IGF-Übersicht</h1><div class=\"kopf\">" +
        listOf("Name" to pr.name, "Dienstgrad" to pr.dienstgrad, "Einheit" to pr.einheit, "Geburtsdatum" to pr.geburt)
            .joinToString("") { "<div><b>${it.first}:</b> ${schutz(it.second.ifBlank { "—" })}</div>" } +
        "<div><b>Stand:</b> ${heuteDE()}</div></div>" +
        tabelle("Individuelle Grundfertigkeiten", m.igf, std) +
        tabelle("ICCS — Lernfelder", m.iccs, std) +
        tabelle("AVU-IGF / WFV", m.avu, listOf("Untersuchung" to { it: MedEintrag -> schutz(it.art) }, "Datum" to { it: MedEintrag -> fmt(it.datum) }, "Gültig bis" to { it: MedEintrag -> fmt(it.gueltigBis) },
            "Befund" to { it: MedEintrag -> schutz(it.befund) }, "Stelle" to { it: MedEintrag -> schutz(it.stelle) })) +
        tabelle("Impfungen", m.impfungen, listOf("Impfung" to { it: MedEintrag -> schutz(it.art) }, "Geimpft am" to { it: MedEintrag -> fmt(it.datum) },
            "Gültig" to { e: MedEintrag -> e.jahre?.let { if (it == 0) "unbegrenzt" else "$it Jahre" } ?: "—" }, "Ablauf" to { it: MedEintrag -> fmt(it.gueltigBis) },
            "Notiz" to { it: MedEintrag -> schutz(listOf(it.dosis, it.notiz).filter { x -> x.isNotBlank() }.joinToString(" · ")) })) +
        "<div class=\"fuss\">Rot hinterlegt: abgelaufen · gelb hinterlegt: läuft in unter sechs Monaten ab.<br>" +
        "Erstellt mit Soldaten Dashboard Reloaded. Persönliche Aufstellung ohne Gewähr — ersetzt keine offizielle Nachweisführung.</div></body></html>"
}
