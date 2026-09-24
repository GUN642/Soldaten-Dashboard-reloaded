package de.gun.dashboard.reloaded.ui.seiten

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gun.dashboard.reloaded.daten.Speicher
import de.gun.dashboard.reloaded.daten.UeberstundenEintrag
import de.gun.dashboard.reloaded.daten.UrlaubZeitraum
import de.gun.dashboard.reloaded.daten.UrlaubZugang
import de.gun.dashboard.reloaded.daten.neueId
import de.gun.dashboard.reloaded.logik.Feiertage
import de.gun.dashboard.reloaded.logik.heuteDE
import de.gun.dashboard.reloaded.logik.mehrarbeitSaldo
import de.gun.dashboard.reloaded.logik.mitVorzeichen
import de.gun.dashboard.reloaded.logik.parseDE
import de.gun.dashboard.reloaded.logik.urlaubStand
import de.gun.dashboard.reloaded.logik.zahl
import de.gun.dashboard.reloaded.ui.Abstand
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
import de.gun.dashboard.reloaded.ui.KnopfArt
import de.gun.dashboard.reloaded.ui.Leer
import de.gun.dashboard.reloaded.ui.LocalPalette
import de.gun.dashboard.reloaded.ui.LocalSteuerung
import de.gun.dashboard.reloaded.ui.Mono
import de.gun.dashboard.reloaded.ui.Pille
import de.gun.dashboard.reloaded.ui.Punkt
import de.gun.dashboard.reloaded.ui.Symbol
import de.gun.dashboard.reloaded.ui.Zeile
import java.time.LocalDate

@Composable
fun UrlaubSeite() {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val d = aktuelleDaten()
    val land = d.feiertagsLand.land
    val stand = urlaubStand(d)
    val saldo = mehrarbeitSaldo(d)

    var uOffen by remember { mutableStateOf(false) }
    var uVon by remember { mutableStateOf("") }
    var uBis by remember { mutableStateOf("") }
    var uNotiz by remember { mutableStateOf("") }
    var uGeplant by remember { mutableStateOf(true) }
    var zeigeZugaenge by remember { mutableStateOf(false) }
    var zDatum by remember { mutableStateOf(heuteDE()) }
    var zTage by remember { mutableStateOf("") }
    var zNotiz by remember { mutableStateOf("") }
    var anspruch by remember(d.urlaub.jahresanspruch) { mutableStateOf(zahl(d.urlaub.jahresanspruch)) }

    var oOffen by remember { mutableStateOf(false) }
    var oDatum by remember { mutableStateOf(heuteDE()) }
    var oStunden by remember { mutableStateOf("") }
    var oNotiz by remember { mutableStateOf("") }
    var oStart by remember(d.ueberstunden.startwert) { mutableStateOf(zahl(d.ueberstunden.startwert)) }
    var zeigeStart by remember { mutableStateOf(false) }

    var frage by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    SeitenListe {
        item { androidx.compose.foundation.layout.Column {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Kennzahl(zahl(stand.rest), "Resturlaub (Tage)", if (stand.rest <= 0) p.rot else if (stand.rest <= 5) p.warn else p.gruen, Modifier.weight(1f))
                Kennzahl(mitVorzeichen(saldo), "Mehrarbeit (Std)", if (saldo < 0) p.rot else if (saldo == 0.0) p.textDim else p.gruen, Modifier.weight(1f))
            }
            if (stand.restGeplant != stand.rest) Mono("inkl. geplant: ${zahl(stand.restGeplant)} Tage", if (stand.restGeplant < 0) p.rot else p.neutral, 12.sp,
                Modifier.padding(start = 6.dp, top = 6.dp))
        } }
        item {
            Karte("Urlaubskonto", "01") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Kennzahl(zahl(stand.anspruch), "Startwert", modifier = Modifier.weight(1f))
                    Kennzahl("+" + zahl(stand.zugaenge), "Zugänge", modifier = Modifier.weight(1f))
                    Kennzahl(zahl(stand.genommen), "Genommen", modifier = Modifier.weight(1f))
                }
                Abstand(8.dp)
                Knopf(if (zeigeZugaenge) "▾ Startwert & Zugänge ausblenden" else "▸ Startwert & Zugänge anzeigen", art = KnopfArt.LEISE, klein = true) { zeigeZugaenge = !zeigeZugaenge }
                if (zeigeZugaenge) {
                    Feld(anspruch, { v -> anspruch = v; v.replace(",", ".").toDoubleOrNull()?.let { n -> Speicher.aendern { it.copy(urlaub = it.urlaub.copy(jahresanspruch = n)) } } },
                        "Startwert (Tage)", tastatur = KeyboardType.Decimal)
                    Hinweis("Einmaliger Ausgangswert, z. B. Urlaubstage (EU) = 24. Am 01.01. jedes neuen Jahres wird automatisch ein Zugang von 30 Tagen ergänzt.")
                    Abstand(8.dp)
                    Etikett("Zugang hinzufügen")
                    Row {
                        DatumFeld(zDatum, { zDatum = it }, "Datum", Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Feld(zTage, { zTage = it }, "Tage", Modifier.weight(0.6f), tastatur = KeyboardType.Number)
                    }
                    Feld(zNotiz, { zNotiz = it }, "Notiz", platzhalter = "z. B. Korrektur")
                    Knopf("+ Zugang", klein = true) {
                        val tage = zTage.replace(",", ".").toDoubleOrNull()
                        if (parseDE(zDatum) == null) { st.melden("Zugang", "Bitte ein gültiges Datum eingeben."); return@Knopf }
                        if (tage == null || tage == 0.0) { st.melden("Zugang", "Bitte eine gültige Anzahl Tage eingeben."); return@Knopf }
                        Speicher.aendern { it.copy(urlaub = it.urlaub.copy(zugaenge = it.urlaub.zugaenge + UrlaubZugang(neueId(), zDatum, tage, zNotiz.trim()))) }
                        zDatum = heuteDE(); zTage = ""; zNotiz = ""
                    }
                    d.urlaub.zugaenge.sortedBy { parseDE(it.datum) ?: LocalDate.MIN }.forEach { z ->
                        Zeile(p.gruen, aktionen = {
                            LoeschKnopf { frage = "Diesen Zugang wirklich löschen?" to { Speicher.aendern { it.copy(urlaub = it.urlaub.copy(zugaenge = it.urlaub.zugaenge.filter { x -> x.id != z.id })) } } }
                        }) {
                            Fliesstext("${z.datum} · +${zahl(z.tage)} Tage", fett = true)
                            if (z.notiz.isNotBlank()) Fliesstext(z.notiz, p.textDim, 12.sp)
                        }
                    }
                }
                Abstand()
                Klappbereich("Urlaub eintragen", uOffen, { uOffen = it }) {
                    Row {
                        DatumFeld(uVon, { uVon = it }, "Startdatum *", Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        DatumFeld(uBis, { uBis = it }, "Enddatum", Modifier.weight(1f))
                    }
                    Feld(uNotiz, { uNotiz = it }, "Notiz")
                    val v = parseDE(uVon)
                    val b = if (uBis.isBlank()) v else parseDE(uBis)
                    val tage = if (v != null && b != null && !b.isBefore(v)) Feiertage.arbeitstage(v, b, land) else null
                    Row {
                        Mono("Berechnete Urlaubstage (Mo–Fr ohne Feiertage): ", p.textDim, 12.sp)
                        Punkt(tage?.toString() ?: "—", 16.sp, p.akzent)
                    }
                    if (v != null && b != null) {
                        val ft = Feiertage.beschriftung(v, b, land)
                        if (ft.isNotEmpty()) Hinweis("Feiertag im Zeitraum: $ft", farbe = p.warn)
                    }
                    Abstand(6.dp)
                    Pille("Geplant", uGeplant) { uGeplant = !uGeplant }
                    FormKnoepfe(if (uGeplant) "+ Eintragen (geplant)" else "+ Eintragen", {
                        if (v == null) { st.melden("Urlaub", "Bitte ein gültiges Startdatum (TT.MM.JJJJ) eingeben."); return@FormKnoepfe }
                        if (uBis.isNotBlank() && b == null) { st.melden("Urlaub", "Bitte ein gültiges Enddatum eingeben oder das Feld leer lassen."); return@FormKnoepfe }
                        if (b!!.isBefore(v)) { st.melden("Urlaub", "Das Enddatum darf nicht vor dem Startdatum liegen."); return@FormKnoepfe }
                        Speicher.aendern {
                            it.copy(urlaub = it.urlaub.copy(zeitraeume = it.urlaub.zeitraeume + UrlaubZeitraum(
                                neueId(), uVon.trim(), uBis.trim().ifBlank { uVon.trim() }, Feiertage.arbeitstage(v, b, land).toDouble(),
                                uNotiz.trim(), if (uGeplant) "geplant" else "eingetragen"
                            )))
                        }
                        uVon = ""; uBis = ""; uNotiz = ""; uOffen = false
                    }) { uOffen = false }
                }
                Abstand(8.dp)
                val liste = d.urlaub.zeitraeume.sortedBy { parseDE(it.von) ?: LocalDate.MIN }
                if (liste.isEmpty()) Leer("Noch kein Urlaub eingetragen.")
                liste.forEach { z ->
                    val geplant = z.status == "geplant"
                    val v = parseDE(z.von)
                    val b = parseDE(z.bis) ?: v
                    val ft = if (v != null && b != null) Feiertage.beschriftung(v, b, land) else ""
                    Zeile(if (geplant) p.neutral else p.rot, aktionen = {
                        if (geplant) Symbol("✓", p.gruen) {
                            frage = "Geplanten Urlaub „${z.von}${if (z.bis != z.von) " – " + z.bis else ""}“ jetzt scharf schalten (vom Resturlaub abziehen)?" to {
                                Speicher.aendern { it.copy(urlaub = it.urlaub.copy(zeitraeume = it.urlaub.zeitraeume.map { x -> if (x.id == z.id) x.copy(status = "eingetragen") else x })) }
                            }
                        }
                        LoeschKnopf { frage = "Diesen Urlaubseintrag wirklich löschen?" to { Speicher.aendern { it.copy(urlaub = it.urlaub.copy(zeitraeume = it.urlaub.zeitraeume.filter { x -> x.id != z.id })) } } }
                    }) {
                        Fliesstext(z.von + (if (z.bis.isNotBlank() && z.bis != z.von) " – " + z.bis else "") + " · ${zahl(z.tage)} Tage" + if (geplant) "  · GEPLANT" else "", fett = true)
                        if (z.notiz.isNotBlank()) Fliesstext(z.notiz, p.textDim, 12.sp)
                        if (ft.isNotEmpty()) Fliesstext("Feiertag: $ft", p.warn, 11.sp)
                    }
                }
            }
        }
        item {
            Karte("Überstundenkonto", "02") {
                Knopf(if (zeigeStart) "▾ Startwert ausblenden" else "▸ Startwert anzeigen", art = KnopfArt.LEISE, klein = true) { zeigeStart = !zeigeStart }
                if (zeigeStart) {
                    Feld(oStart, { v -> oStart = v; v.replace(",", ".").toDoubleOrNull()?.let { n -> Speicher.aendern { it.copy(ueberstunden = it.ueberstunden.copy(startwert = n)) } } },
                        "Startwert (Stunden)", tastatur = KeyboardType.Decimal)
                    Hinweis("Ausgangsbestand, etwa der Übertrag aus dem Vorjahr. Wird zum Stand hinzugerechnet.")
                }
                Klappbereich("Stunden eintragen", oOffen, { oOffen = it }) {
                    Row {
                        DatumFeld(oDatum, { oDatum = it }, "Datum *", Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Feld(oStunden, { oStunden = it }, "Stunden (+/−)", Modifier.weight(0.7f), tastatur = KeyboardType.Text, platzhalter = "2 oder -3,5")
                    }
                    Feld(oNotiz, { oNotiz = it }, "Notiz")
                    parseDE(oDatum)?.let { Feiertage.name(it, land) }?.let { Hinweis("Hinweis: $oDatum ist ein Feiertag ($it).", farbe = p.warn) }
                    FormKnoepfe("+ Eintragen", {
                        if (parseDE(oDatum) == null) { st.melden("Mehrarbeit", "Bitte ein gültiges Datum (TT.MM.JJJJ) eingeben."); return@FormKnoepfe }
                        val h = oStunden.replace(",", ".").trim().toDoubleOrNull()
                        if (h == null || h == 0.0) { st.melden("Mehrarbeit", "Bitte eine gültige Stundenzahl (ungleich 0) eingeben."); return@FormKnoepfe }
                        Speicher.aendern { it.copy(ueberstunden = it.ueberstunden.copy(eintraege = it.ueberstunden.eintraege + UeberstundenEintrag(neueId(), oDatum.trim(), h, oNotiz.trim()))) }
                        oStunden = ""; oNotiz = ""; oDatum = heuteDE(); oOffen = false
                    }) { oOffen = false }
                }
                Abstand(8.dp)
                val liste = d.ueberstunden.eintraege.sortedBy { parseDE(it.datum) ?: LocalDate.MIN }
                if (liste.isEmpty()) Leer("Noch keine Einträge.")
                liste.forEach { e ->
                    val ft = parseDE(e.datum)?.let { Feiertage.name(it, land) }
                    Zeile(if (e.stunden >= 0) p.gruen else p.rot, aktionen = {
                        if (e.geplant) Symbol("✓", p.gruen) {
                            Speicher.aendern { it.copy(ueberstunden = it.ueberstunden.copy(eintraege = it.ueberstunden.eintraege.map { x ->
                                if (x.id == e.id) x.copy(geplant = false, notiz = x.notiz.replace("FvD geplant:", "FvD:")) else x
                            })) }
                        }
                        LoeschKnopf { frage = "Diesen Eintrag wirklich löschen?" to { Speicher.aendern { it.copy(ueberstunden = it.ueberstunden.copy(eintraege = it.ueberstunden.eintraege.filter { x -> x.id != e.id })) } } }
                    }) {
                        Fliesstext("${e.datum} · ${mitVorzeichen(e.stunden)} Std" + if (e.geplant) "  · GEPLANT" else "", fett = true)
                        if (e.notiz.isNotBlank()) Fliesstext(e.notiz, p.textDim, 12.sp)
                        if (ft != null) Fliesstext("Feiertag: $ft", p.warn, 11.sp)
                    }
                }
            }
        }
    }

    frage?.let { (text, aktion) ->
        Frage("Bestätigen", text, ja = "Ja", gefahr = text.contains("löschen"), onJa = { aktion(); frage = null }, onNein = { frage = null })
    }
}
