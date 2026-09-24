package de.gun.dashboard.reloaded.ui.seiten

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gun.dashboard.reloaded.Aktualisierung
import de.gun.dashboard.reloaded.daten.Anhaenge
import de.gun.dashboard.reloaded.daten.Anhang
import de.gun.dashboard.reloaded.daten.AppDaten
import de.gun.dashboard.reloaded.daten.EigenerTermin
import de.gun.dashboard.reloaded.daten.Speicher
import de.gun.dashboard.reloaded.daten.UeberstundenEintrag
import de.gun.dashboard.reloaded.daten.UrlaubZeitraum
import de.gun.dashboard.reloaded.daten.neueId
import de.gun.dashboard.reloaded.geraet.GeraeteKalender
import de.gun.dashboard.reloaded.geraet.TerminFelder
import de.gun.dashboard.reloaded.logik.Feiertage
import de.gun.dashboard.reloaded.logik.Termin
import de.gun.dashboard.reloaded.logik.WDH_NAMEN
import de.gun.dashboard.reloaded.logik.alsDE
import de.gun.dashboard.reloaded.logik.heuteDE
import de.gun.dashboard.reloaded.logik.hhmm
import de.gun.dashboard.reloaded.logik.lang
import de.gun.dashboard.reloaded.logik.parseDE
import de.gun.dashboard.reloaded.logik.regelZuWdh
import de.gun.dashboard.reloaded.logik.wdhZuRegel
import de.gun.dashboard.reloaded.logik.zahl
import de.gun.dashboard.reloaded.logik.zeitAus
import de.gun.dashboard.reloaded.logik.zeitNormieren
import de.gun.dashboard.reloaded.ui.Abstand
import de.gun.dashboard.reloaded.ui.Auswahl
import de.gun.dashboard.reloaded.ui.DatumFeld
import de.gun.dashboard.reloaded.ui.Etikett
import de.gun.dashboard.reloaded.ui.Feld
import de.gun.dashboard.reloaded.ui.Fliesstext
import de.gun.dashboard.reloaded.ui.Frage
import de.gun.dashboard.reloaded.ui.Hinweis
import de.gun.dashboard.reloaded.ui.Knopf
import de.gun.dashboard.reloaded.ui.KnopfArt
import de.gun.dashboard.reloaded.ui.Knopfreihe
import de.gun.dashboard.reloaded.ui.LocalPalette
import de.gun.dashboard.reloaded.ui.LocalSteuerung
import de.gun.dashboard.reloaded.ui.MaskeStart
import de.gun.dashboard.reloaded.ui.Mono
import de.gun.dashboard.reloaded.ui.Pille
import de.gun.dashboard.reloaded.ui.Punkt
import de.gun.dashboard.reloaded.ui.Reiter
import de.gun.dashboard.reloaded.ui.Segmente
import de.gun.dashboard.reloaded.ui.Symbol
import de.gun.dashboard.reloaded.ui.Trenner
import de.gun.dashboard.reloaded.ui.Wahl
import de.gun.dashboard.reloaded.ui.ZeitFeld
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@Composable
private fun EbenenKopf(titel: String, onZu: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Symbol("✕", LocalPalette.current.text, onZu)
        Spacer(Modifier.width(6.dp))
        Punkt(titel.uppercase(), 18.sp, modifier = Modifier.weight(1f))
    }
}

/** Beschreibung der Zeit eines Termins. */
fun zeitText(t: Termin): String {
    val tag = t.ersterTag.lang()
    return if (t.ganztags) {
        if (t.letzterTag != t.ersterTag) "$tag\nbis ${t.letzterTag.lang()}\n(ganztägig)" else "$tag\n(ganztägig)"
    } else {
        val ende = t.ende
        if (ende != null && ende.toLocalDate() != t.start.toLocalDate() && t.letzterTag != t.ersterTag)
            "$tag, ${t.start.hhmm()} Uhr\nbis ${ende.toLocalDate().lang()}, ${ende.hhmm()} Uhr"
        else "$tag\n${t.start.hhmm()}" + (ende?.let { " – " + it.hhmm() } ?: "") + " Uhr"
    }
}

@Composable
fun TerminDetailEbene(t: Termin) {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val d = aktuelleDaten()
    val eigener = d.kalender.eigene.firstOrNull { it.id == t.eigenerId }
    var frage by remember { mutableStateOf(false) }
    var serienFrage by remember { mutableStateOf(false) }

    fun fertig() {
        st.terminDetail = null
        scope.launch { Aktualisierung.geraetLadenJetzt(ctx) }
    }

    suspend fun geraetLoeschen(nurDieses: Boolean) {
        try {
            val id = t.eventId ?: return
            if (nurDieses) GeraeteKalender.vorkommenLoeschen(ctx, id, t.rohBeginn ?: return)
            else {
                GeraeteKalender.loeschen(ctx, id)
                eigener?.let { e ->
                    Speicher.aendern { it.copy(kalender = it.kalender.copy(eigene = it.kalender.eigene.filter { x -> x.id != e.id })) }
                    Anhaenge.loeschen(ctx, e.anhaenge)
                }
            }
            st.kurz("Termin gelöscht")
            fertig()
        } catch (e: Exception) {
            st.melden("Löschen fehlgeschlagen", (e.message ?: "") + "\n\nMöglich ist das nur bei Kalendern mit Schreibrecht.")
        }
    }

    Column(Modifier.fillMaxSize()) {
        EbenenKopf("Termin") { st.terminDetail = null }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(t.farbe)))
            Abstand(14.dp)
            Fliesstext(t.titel, groesse = 22.sp, fett = true)
            Abstand()
            DetailZeile("Zeit", zeitText(t))
            if (t.ort.isNotBlank()) DetailZeile("Ort", t.ort)
            DetailZeile("Kalender", t.quelleName)
            if (t.istSerie) DetailZeile("Wiederholung", regelZuWdh(t.rrule)?.let { WDH_NAMEN[it] } ?: "Serie")
            val notiz = t.notiz.ifBlank { eigener?.notiz ?: "" }
            if (notiz.isNotBlank()) DetailZeile("Notiz", notiz)
            if (eigener != null && eigener.anhaenge.isNotEmpty()) {
                Etikett("Anhänge", modifier = Modifier.padding(top = 10.dp))
                AnhangVorschau(eigener.anhaenge)
            }
            Abstand()
            val bearbeitbar = !t.istKontakt && !t.istTodo && (t.eigenerId != null || (t.eventId != null && t.schreibbar))
            when {
                t.istTodo -> Hinweis("Diese Aufgabe stammt aus der To-do-Liste.")
                t.istKontakt -> Hinweis("Dieser Anlass stammt aus den Kontakten und lässt sich dort ändern.")
                t.eventId != null && !t.schreibbar -> Hinweis("Dieser Termin stammt aus einem nur lesbaren Kalender des Geräts.")
                t.eventId == null && t.eigenerId == null -> Hinweis("Dieser Termin stammt aus einer eingebundenen Quelle und lässt sich hier nur ansehen.")
                t.eventId != null && eigener == null -> Hinweis("Dieser Termin stammt aus einem Kalender des Geräts. Ändern und löschen wirkt sich dort aus.")
            }
            Knopfreihe {
                if (t.istTodo) Knopf("Zur Aufgabe") { st.terminDetail = null; st.reiter = Reiter.TODO }
                if (bearbeitbar) {
                    Knopf("✎ Bearbeiten") { st.terminDetail = null; st.maske = MaskeStart(termin = t) }
                    Knopf("✕ Löschen", art = KnopfArt.GEFAHR) { if (t.eventId != null && t.istSerie) serienFrage = true else frage = true }
                }
            }
            Abstand(40.dp)
        }
    }

    if (frage) Frage(
        "Termin löschen",
        "Termin „${t.titel}“ wirklich löschen?" + if (t.eventId != null) "\n\nEr wird im Kalender des Geräts entfernt. Sobald sich das Konto abgleicht, verschwindet er auch dort." else "",
        onJa = {
            frage = false
            if (t.eventId != null) scope.launch { geraetLoeschen(false) }
            else if (eigener != null) {
                val vorher = Speicher.aktuell.kalender.eigene
                Speicher.aendern { it.copy(kalender = it.kalender.copy(eigene = it.kalender.eigene.filter { x -> x.id != eigener.id })) }
                st.terminDetail = null
                st.rueckgaengig("Termin „${t.titel}“ gelöscht", {
                    Speicher.aendern { it.copy(kalender = it.kalender.copy(eigene = vorher)) }
                }) { Anhaenge.loeschen(ctx, eigener.anhaenge) }
            }
        },
        onNein = { frage = false }
    )
    if (serienFrage) Wahl(
        "Serientermin löschen",
        "Termin „${t.titel}“\n${t.ersterTag.lang()}\n\nDieser Termin gehört zu einer Serie. Was soll gelöscht werden?",
        listOf(
            Triple("Nur diesen Termin", KnopfArt.NORMAL) { serienFrage = false; scope.launch { geraetLoeschen(true) } },
            Triple("Alle Termine der Serie", KnopfArt.GEFAHR) { serienFrage = false; scope.launch { geraetLoeschen(false) } },
        ),
    ) { serienFrage = false }
}

@Composable
private fun DetailZeile(label: String, wert: String) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Etikett(label)
        Fliesstext(wert, p.text, 15.sp)
    }
}

// ================================================================== Eingabemaske

@Composable
fun TerminMaskeEbene(start: MaskeStart) {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val d = aktuelleDaten()
    val kalender by GeraeteKalender.kalender.collectAsState()
    val schreibbar = kalender.filter { it.schreibbar && it.id !in d.nativ.entfernt }
    val t = start.termin
    val eigener = t?.eigenerId?.let { id -> d.kalender.eigene.firstOrNull { it.id == id } }
    val bearbeiten = t != null

    // Anfangswerte
    var titel by remember { mutableStateOf(t?.titel ?: "") }
    var ganztags by remember { mutableStateOf(t?.ganztags ?: false) }
    var von by remember { mutableStateOf((t?.ersterTag ?: start.datum ?: LocalDate.now()).alsDE()) }
    var bis by remember { mutableStateOf(t?.let { if (it.letzterTag != it.ersterTag) it.letzterTag.alsDE() else "" } ?: "") }
    var zeitVon by remember { mutableStateOf(t?.takeIf { !it.ganztags }?.start?.hhmm() ?: "08:00") }
    var zeitBis by remember { mutableStateOf(t?.takeIf { !it.ganztags }?.ende?.hhmm() ?: "16:00") }
    var ort by remember { mutableStateOf(t?.ort ?: "") }
    var notiz by remember { mutableStateOf(t?.notiz?.ifBlank { eigener?.notiz ?: "" } ?: "") }
    var anhaenge by remember { mutableStateOf(eigener?.anhaenge ?: emptyList()) }
    var kalId by remember {
        mutableStateOf(
            t?.quelleId?.takeIf { q -> schreibbar.any { it.id == q } }
                ?: eigener?.kalenderId?.takeIf { it.isNotBlank() }
                ?: d.nativ.zielKalenderId.takeIf { z -> schreibbar.any { it.id == z } }
                ?: schreibbar.firstOrNull()?.id ?: ""
        )
    }
    // Wiederholung: "__behalten" = unbekannte Regel unverändert lassen
    var wdh by remember {
        mutableStateOf(
            when {
                t == null -> ""
                t.rrule != null -> regelZuWdh(t.rrule) ?: "__behalten"
                eigener != null && t.eventId == null -> eigener.wiederholung
                t.eventId != null -> "__behalten"
                else -> ""
            }
        )
    }
    LaunchedEffect(t?.eventId) {
        val id = t?.eventId ?: return@LaunchedEffect
        if (t.rrule == null) {
            val r = GeraeteKalender.regelLesen(ctx, id)
            wdh = if (r.isNullOrBlank()) "" else regelZuWdh(r) ?: "__behalten"
        }
    }
    var anrechnung by remember { mutableStateOf("keine") }
    var nurGeplant by remember { mutableStateOf(true) }
    var speichert by remember { mutableStateOf(false) }
    var neueAnhaenge by remember { mutableStateOf(listOf<Anhang>()) }

    val kalName = schreibbar.firstOrNull { it.id == kalId }?.titel?.lowercase() ?: ""
    val verrechnungMoeglich = !bearbeiten && Regex("urlaub|\\beu\\b|abwesen|fvd").containsMatchIn(kalName)

    fun schliessen(gespeichert: Boolean) {
        if (!gespeichert) Anhaenge.loeschen(ctx, neueAnhaenge)
        st.maske = null
    }

    fun speichern() {
        if (titel.isBlank()) { st.melden("Termin", "Bitte einen Titel eingeben."); return }
        val s = parseDE(von) ?: run { st.melden("Termin", "Bitte ein gültiges Startdatum (TT.MM.JJJJ) eingeben."); return }
        val e = if (bis.isBlank()) s else parseDE(bis) ?: run { st.melden("Termin", "Bitte ein gültiges Enddatum eingeben oder das Feld leer lassen."); return }
        if (e.isBefore(s)) { st.melden("Termin", "Das Enddatum darf nicht vor dem Startdatum liegen."); return }
        val zv = if (ganztags) "" else zeitNormieren(zeitVon) ?: run { st.melden("Termin", "Bitte eine gültige Uhrzeit (HH:MM) für den Beginn eingeben."); return }
        val zb = if (ganztags) "" else zeitNormieren(zeitBis) ?: run { st.melden("Termin", "Bitte eine gültige Uhrzeit (HH:MM) für das Ende eingeben."); return }
        speichert = true
        scope.launch {
            try {
                var sDat = s
                var eDat = e
                // Serie bearbeitet, Datum unverändert: ursprünglichen Serienbeginn behalten
                if (t?.eventId != null && t.istSerie && s == t.ersterTag) {
                    GeraeteKalender.ursprungLesen(ctx, t.eventId)?.let { ms ->
                        val ur = if (t.ganztags) Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        else Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
                        val laenge = ChronoUnit.DAYS.between(s, e)
                        sDat = ur; eDat = ur.plusDays(laenge)
                    }
                }
                val regel = when (wdh) {
                    "__behalten" -> t?.eventId?.let { GeraeteKalender.regelLesen(ctx, it) } ?: ""
                    else -> wdhZuRegel(wdh)
                }
                val felder = TerminFelder(
                    titel.trim(), ort.trim(), notiz.trim(), ganztags,
                    if (ganztags) sDat.atStartOfDay() else sDat.atTime(zeitAus(zv)!!),
                    if (ganztags) eDat.atStartOfDay() else eDat.atTime(zeitAus(zb)!!),
                    regel ?: "",
                )
                val wdhWert = if (wdh == "__behalten") (regelZuWdh(regel) ?: "") else wdh

                if (t != null) {
                    // ---- Ändern
                    var nativNeu: String? = null
                    if (t.eventId != null) {
                        if (kalId.isNotBlank() && kalId != t.quelleId) {
                            // Kalenderwechsel: im neuen Kalender anlegen, im alten löschen
                            nativNeu = GeraeteKalender.anlegen(ctx, kalId, felder).toString()
                            GeraeteKalender.loeschen(ctx, t.eventId)
                        } else GeraeteKalender.aendern(ctx, t.eventId, null, felder)
                    }
                    if (eigener != null) {
                        Speicher.aendern { a ->
                            a.copy(kalender = a.kalender.copy(eigene = a.kalender.eigene.map {
                                if (it.id == eigener.id) it.copy(
                                    titel = titel.trim(), von = s.alsDE(), bis = e.alsDE(), ganztags = ganztags, zeitVon = zv, zeitBis = zb,
                                    ort = ort.trim(), notiz = notiz.trim(), anhaenge = anhaenge, wiederholung = wdhWert,
                                    kalenderId = kalId, sequence = (it.sequence ?: 0) + 1, nativId = nativNeu ?: it.nativId,
                                ) else it
                            }))
                        }
                        // Entfernte Anhänge löschen
                        Anhaenge.loeschen(ctx, eigener.anhaenge.filter { it !in anhaenge })
                    } else if (anhaenge.isNotEmpty() && t.eventId != null) {
                        // Anhänge an einen Gerätetermin: als eigener Eintrag mitführen
                        Speicher.aendern { a -> a.copy(kalender = a.kalender.copy(eigene = a.kalender.eigene + EigenerTermin(
                            id = neueId(), uid = neueId() + "@dienst-cockpit", erstellt = heuteDE(), titel = titel.trim(), von = s.alsDE(),
                            bis = e.alsDE(), ganztags = ganztags, zeitVon = zv, zeitBis = zb, ort = ort.trim(), notiz = notiz.trim(),
                            anhaenge = anhaenge, kalenderId = kalId, wiederholung = wdhWert, nativId = nativNeu ?: t.eventId.toString(), nativ = true,
                        ))) }
                    }
                } else {
                    // ---- Neu anlegen
                    var nativId = ""
                    if (kalId.isNotBlank() && GeraeteKalender.darfSchreiben(ctx)) {
                        nativId = GeraeteKalender.anlegen(ctx, kalId, felder).toString()
                    }
                    val neu = EigenerTermin(
                        id = neueId(), uid = neueId() + "@dienst-cockpit", sequence = 0, erstellt = heuteDE(), titel = titel.trim(),
                        von = s.alsDE(), bis = e.alsDE(), ganztags = ganztags, zeitVon = zv, zeitBis = zb, ort = ort.trim(),
                        notiz = notiz.trim(), anhaenge = anhaenge, kalenderId = kalId, wiederholung = wdhWert,
                        nativId = nativId, nativ = nativId.isNotBlank(),
                    )
                    Speicher.aendern { a ->
                        anrechnen(a.copy(kalender = a.kalender.copy(eigene = a.kalender.eigene + neu)), neu, anrechnung, nurGeplant) { st.melden("Anrechnung", it) }
                    }
                }
                Aktualisierung.geraetLadenJetzt(ctx)
                speichert = false
                schliessen(true)
                st.kurz(if (bearbeiten) "Änderungen gespeichert" else "Termin angelegt")
            } catch (ex: Exception) {
                speichert = false
                st.melden("Speichern fehlgeschlagen", ex.message ?: ex.toString())
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        EbenenKopf(if (bearbeiten) "Termin bearbeiten" else "Neuer Termin") { schliessen(false) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Feld(titel, { titel = it }, "Titel *", platzhalter = "z. B. Besprechung Staffel")
            if (schreibbar.isNotEmpty()) {
                Auswahl("Kalender", schreibbar.map { it.id to it.titel + "  [" + it.dienst + "]" + if (it.id == d.nativ.zielKalenderId) " (Standard)" else "" }, kalId) { kalId = it }
            } else Hinweis(
                if (GeraeteKalender.darfLesen(ctx)) "Kein beschreibbarer Gerätekalender gefunden – der Termin wird nur in dieser App gespeichert."
                else "Ohne Kalenderzugriff wird der Termin nur in dieser App gespeichert (Zugriff im Kalender unter ⚙ erteilen)."
            )
            Abstand(6.dp)
            Pille("Ganztägig", ganztags) { ganztags = !ganztags }
            Row {
                DatumFeld(von, { von = it }, "Startdatum *", Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                DatumFeld(bis, { bis = it }, "Enddatum", Modifier.weight(1f))
            }
            if (!ganztags) Row {
                ZeitFeld(zeitVon, { zeitVon = it }, "Uhrzeit von", Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                ZeitFeld(zeitBis, { zeitBis = it }, "Uhrzeit bis", Modifier.weight(1f))
            }
            Feld(ort, { ort = it }, "Ort (optional)")
            Auswahl(
                "Wiederholung",
                (if (wdh == "__behalten") listOf("__behalten" to "unverändert lassen") else emptyList()) + WDH_NAMEN.map { it.key to it.value },
                wdh
            ) { wdh = it }
            Feld(notiz, { notiz = it }, "Notiz (optional, mehrzeilig)", zeilen = 3)

            if (verrechnungMoeglich) {
                Trenner()
                Etikett("Auf Urlaub / Mehrarbeit anrechnen")
                Segmente(listOf("keine" to "Nein", "eu" to "EU — Tage", "fvd" to "FvD — Stunden"), anrechnung) { anrechnung = it }
                if (anrechnung != "keine") Pille("Nur geplant", nurGeplant, Modifier.padding(top = 6.dp)) { nurGeplant = !nurGeplant }
                Hinweis(
                    when (anrechnung) {
                        "eu" -> if (nurGeplant) "Wird als geplanter Urlaub eingetragen — erst nach „Scharf schalten“ abgezogen."
                        else "Wird sofort als fester Urlaub abgezogen (Werktage ohne Feiertage)."
                        "fvd" -> if (nurGeplant) "Wird als geplanter FvD im Überstundenkonto vermerkt — bei Uhrzeit mit der tatsächlichen Dauer."
                        else "Die Stunden werden vom Überstundenkonto abgezogen — bei Uhrzeit genau die tatsächliche Dauer."
                        else -> "Wird beim Speichern automatisch im Reiter „Urlaub/Mehrarbeit“ eingetragen."
                    }
                )
            }
            Trenner()
            Etikett("Dateianhang (optional)")
            AnhangBereich(anhaenge, { neu ->
                neueAnhaenge = neueAnhaenge + neu.filter { it !in anhaenge }
                anhaenge = neu
            })
            Hinweis("Anhänge werden in dieser App gespeichert und beim Termin angezeigt, nicht in den Gerätekalender übertragen.")
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                Knopf(if (speichert) "Speichert …" else if (bearbeiten) "✓ Änderungen übernehmen" else "+ Termin anlegen",
                    Modifier.weight(1f), KnopfArt.PRIMAER, aktiv = !speichert) { speichern() }
                Spacer(Modifier.width(8.dp))
                Knopf("Abbrechen") { schliessen(false) }
            }
            Abstand(60.dp)
        }
    }
}

/** Trägt einen neuen Termin im Urlaubs- bzw. Überstundenkonto ein. */
fun anrechnen(a: AppDaten, t: EigenerTermin, art: String, geplant: Boolean, hinweis: (String) -> Unit): AppDaten {
    if (art == "keine") return a
    val von = parseDE(t.von) ?: return a
    val bis = parseDE(t.bis) ?: von
    val land = a.feiertagsLand.land
    val werktage = Feiertage.arbeitstage(von, bis, land)
    if (art == "eu") {
        if (werktage <= 0) { hinweis("Der Zeitraum enthält keine Werktage — es wurde nichts angerechnet."); return a }
        return a.copy(urlaub = a.urlaub.copy(zeitraeume = a.urlaub.zeitraeume + UrlaubZeitraum(
            neueId(), t.von, t.bis, werktage.toDouble(), t.titel, if (geplant) "geplant" else "eingetragen", "termin", t.uid
        )))
    }
    var dauer: Double
    if (!t.ganztags && t.zeitVon.isNotBlank() && t.zeitBis.isNotBlank()) {
        val v = zeitAus(t.zeitVon)!!
        val b = zeitAus(t.zeitBis)!!
        var min = (b.hour * 60 + b.minute) - (v.hour * 60 + v.minute)
        if (min <= 0) min += 24 * 60
        dauer = Math.round(min / 60.0 * 100) / 100.0
        if (werktage > 1) dauer *= werktage
    } else {
        if (werktage <= 0) { hinweis("Der Zeitraum enthält keine Werktage — es wurde nichts angerechnet."); return a }
        dauer = a.kalender.fvdStunden * werktage
    }
    if (dauer <= 0) return a
    return a.copy(ueberstunden = a.ueberstunden.copy(eintraege = a.ueberstunden.eintraege + UeberstundenEintrag(
        neueId(), t.von, -Math.abs(dauer), (if (geplant) "FvD geplant: " else "FvD: ") + t.titel, geplant, "termin", t.uid
    )))
}
