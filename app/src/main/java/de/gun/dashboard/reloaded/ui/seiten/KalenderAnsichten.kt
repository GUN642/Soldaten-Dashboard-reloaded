package de.gun.dashboard.reloaded.ui.seiten

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gun.dashboard.reloaded.daten.Speicher
import de.gun.dashboard.reloaded.logik.Feiertage
import de.gun.dashboard.reloaded.logik.MONATE
import de.gun.dashboard.reloaded.logik.Termin
import de.gun.dashboard.reloaded.logik.TerminBestand
import de.gun.dashboard.reloaded.logik.WOCHENTAGE
import de.gun.dashboard.reloaded.logik.kalenderwoche
import de.gun.dashboard.reloaded.logik.terminFenster
import de.gun.dashboard.reloaded.logik.wochenStart
import de.gun.dashboard.reloaded.ui.LocalPalette
import de.gun.dashboard.reloaded.ui.LocalSteuerung
import de.gun.dashboard.reloaded.ui.MaskeStart
import de.gun.dashboard.reloaded.ui.Mono
import de.gun.dashboard.reloaded.ui.Punkt
import de.gun.dashboard.reloaded.ui.Reiter
import de.gun.dashboard.reloaded.ui.Schrift
import de.gun.dashboard.reloaded.ui.Symbol
import de.gun.dashboard.reloaded.ui.textAuf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit

val KALENDER_ANSICHTEN = listOf("monat" to "Monat", "woche" to "Woche", "agenda" to "Agenda")

/** Kompakter Umschalter Monat / Woche / Agenda. */
@Composable
fun AnsichtWahl(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val aktuell = aktuelleDaten().kalender.ansicht
    Row(
        modifier.clip(RoundedCornerShape(50)).border(1.dp, p.rand, RoundedCornerShape(50)).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        KALENDER_ANSICHTEN.forEach { (id, name) ->
            val an = id == aktuell
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(if (an) p.text else Color.Transparent)
                    .clickable { Speicher.aendern { it.copy(kalender = it.kalender.copy(ansicht = id)) } }
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(name.uppercase(), color = if (an) p.bg else p.textDim,
                    style = TextStyle(fontFamily = Schrift.mono, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp))
            }
        }
    }
}

private fun termineNachTag(liste: List<Termin>, von: LocalDate, bis: LocalDate): Map<LocalDate, List<Termin>> {
    val tage = generateSequence(von) { it.plusDays(1) }.takeWhile { !it.isAfter(bis) }.toList()
    return tage.associateWith { tag ->
        liste.filter { it.liegtAuf(tag) }.sortedWith(compareBy({ !it.ganztags }, { it.start }))
    }
}

@Composable
private fun TagKopf(tag: LocalDate, b: TerminBestand) {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val heute = tag == LocalDate.now()
    val ft = Feiertage.name(tag, b.daten.feiertagsLand.land)
    val fer = Feiertage.ferien(tag, b.daten.ferien)
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(if (heute) p.akzent else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(tag.dayOfMonth.toString(), color = if (heute) textAuf(p.akzent) else if (ft != null || tag.dayOfWeek.value >= 6) p.akzent else p.text,
                style = TextStyle(fontFamily = Schrift.mono, fontSize = 14.sp, fontWeight = FontWeight.Bold))
        }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Mono(WOCHENTAGE[tag.dayOfWeek.value - 1].uppercase() + " · " + MONATE[tag.monthValue - 1] + (if (tag.year != LocalDate.now().year) " " + tag.year else ""),
                if (heute) p.akzent else p.textDim, 11.sp, fett = true)
            listOfNotNull(ft, fer).takeIf { it.isNotEmpty() }?.let { Mono(it.joinToString(" · "), p.textFaint, 10.sp) }
        }
        Symbol("+", p.textDim) { st.maske = MaskeStart(datum = tag) }
    }
}

@Composable
private fun TerminEintrag(t: Termin) {
    val st = LocalSteuerung.current
    TerminZeile(t) { if (t.istTodo) st.reiter = Reiter.TODO else st.terminDetail = t }
}

/** Agenda: nur Tage mit Terminen, ab heute fortlaufend (lädt beim Scrollen nach). */
@Composable
fun AgendaAnsicht(b: TerminBestand, kopf: @Composable () -> Unit) {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val start = remember { LocalDate.now() }
    var tage by remember { mutableStateOf(90L) }
    fun berechnen(bis: LocalDate) = termineNachTag(terminFenster(b, start, bis), start, bis).filter { it.value.isNotEmpty() }.toList()
    // erste 30 Tage sofort anzeigen, der Rest folgt im Hintergrund
    val eintraege by produceState(remember(b) { berechnen(start.plusDays(30)) }, b, tage) {
        val bis = start.plusDays(tage)
        value = withContext(Dispatchers.Default) { berechnen(bis) }
    }
    val liste = rememberLazyListState()
    // am Ende angekommen: weitere 90 Tage laden (höchstens zwei Jahre)
    LaunchedEffect(liste, eintraege) {
        snapshotFlow { liste.layoutInfo.visibleItemsInfo.lastOrNull()?.index to liste.layoutInfo.totalItemsCount }
            .collect { (letzter, gesamt) -> if (letzter != null && gesamt > 0 && letzter >= gesamt - 2 && tage < 730) tage += 90 }
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            kopf()
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Punkt("AGENDA", 18.sp, modifier = Modifier.weight(1f))
                Mono("ab heute", p.textDim, 11.sp)
            }
            val e = eintraege
            LazyColumn(Modifier.weight(1f), state = liste, contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 96.dp)) {
                if (e.isEmpty()) item { Mono("Keine Termine in den nächsten $tage Tagen.", p.textDim, 12.sp, Modifier.padding(16.dp)) }
                else {
                    var monat = -1
                    e.forEach { (tag, termine) ->
                        val mSchluessel = tag.year * 12 + tag.monthValue
                        if (mSchluessel != monat) {
                            monat = mSchluessel
                            item(key = "m$mSchluessel") {
                                Mono(MONATE[tag.monthValue - 1].uppercase() + " " + tag.year, p.akzent, 12.sp, fett = true,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 2.dp))
                            }
                        }
                        item(key = tag.toString()) {
                            Column {
                                TagKopf(tag, b)
                                termine.forEach { TerminEintrag(it) }
                            }
                        }
                    }
                    if (tage >= 730) item { Mono("Ende der Vorschau (2 Jahre).", p.textFaint, 11.sp, Modifier.padding(16.dp)) }
                }
            }
        }
        NeuKnopf(Modifier.align(Alignment.BottomEnd)) { st.maske = MaskeStart(datum = LocalDate.now()) }
    }
}

@Composable
private fun NeuKnopf(modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    FloatingActionButton(
        onClick = onClick, containerColor = p.akzent, contentColor = textAuf(p.akzent), shape = CircleShape,
        modifier = modifier.navigationBarsPadding().padding(16.dp).size(46.dp),
    ) { Text("+", fontSize = 22.sp, fontFamily = Schrift.mono) }
}
