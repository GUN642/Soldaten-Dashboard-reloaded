package de.gun.dashboard.reloaded.ui.seiten

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gun.dashboard.reloaded.logik.Feiertage
import de.gun.dashboard.reloaded.logik.MONATE_KURZ
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

private val RAND_LINKS = 34.dp
private val STUNDE = 56.dp

/** Ein Block im Zeitraster: Minuten ab Tagesbeginn, Spalte innerhalb überlappender Termine. */
private data class Block(val termin: Termin, val tag: Int, val von: Int, val bis: Int, var spalte: Int = 0, var spalten: Int = 1)

private data class WochenDaten(val ganztags: List<List<Termin>>, val bloecke: List<Block>)

/** Termine einer Woche auf Kopfzeile (ganztägig/mehrtägig) und Zeitraster verteilen. */
private fun wochenDaten(liste: List<Termin>, ws: LocalDate): WochenDaten {
    val ganz = List(7) { mutableListOf<Termin>() }
    val bloecke = mutableListOf<Block>()
    for (t in liste) {
        val mehrtaegig = t.letzterTag != t.ersterTag
        for (i in 0..6) {
            val tag = ws.plusDays(i.toLong())
            if (!t.liegtAuf(tag)) continue
            if (t.ganztags || mehrtaegig) { ganz[i] += t; continue }
            val von = t.start.hour * 60 + t.start.minute
            val ende = t.ende?.let { if (it.toLocalDate().isAfter(tag)) 24 * 60 else it.hour * 60 + it.minute } ?: (von + 30)
            bloecke += Block(t, i, von, maxOf(ende, von + 20))
        }
    }
    // Überlappungen je Tag nebeneinander anordnen
    for (i in 0..6) {
        val tagB = bloecke.filter { it.tag == i }.sortedWith(compareBy({ it.von }, { -it.bis }))
        var gruppe = mutableListOf<Block>()
        var gruppenEnde = -1
        fun abschliessen() {
            val enden = mutableListOf<Int>()
            for (b in gruppe) {
                val frei = enden.indexOfFirst { it <= b.von }
                if (frei >= 0) { b.spalte = frei; enden[frei] = b.bis } else { b.spalte = enden.size; enden += b.bis }
            }
            gruppe.forEach { it.spalten = enden.size }
        }
        for (b in tagB) {
            if (b.von >= gruppenEnde && gruppe.isNotEmpty()) { abschliessen(); gruppe = mutableListOf() }
            gruppe += b; gruppenEnde = maxOf(gruppenEnde, b.bis)
        }
        if (gruppe.isNotEmpty()) abschliessen()
    }
    return WochenDaten(ganz.map { l -> l.sortedWith(compareBy({ !it.ganztags }, { it.start })) }, bloecke)
}

/** Woche als Zeitraster: Kopf mit ganztägigen Terminen, darunter Stunden mit Terminblöcken und Jetzt-Linie. */
@Composable
fun WochenAnsicht(b: TerminBestand, kopf: @Composable () -> Unit) {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val anker = remember { wochenStart(LocalDate.now()) }
    val mitte = 1200
    val pager = rememberPagerState(initialPage = mitte + ChronoUnit.WEEKS.between(anker, wochenStart(st.kalenderTag ?: LocalDate.now())).toInt()) { 2400 }
    var woche by remember { mutableStateOf(anker.plusWeeks((pager.currentPage - mitte).toLong())) }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { woche = anker.plusWeeks((it - mitte).toLong()) } }
    LaunchedEffect(woche) {
        val ziel = mitte + ChronoUnit.WEEKS.between(anker, woche).toInt()
        if (ziel != pager.currentPage) pager.animateScrollToPage(ziel)
    }

    Column(Modifier.fillMaxSize()) {
        kopf()
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Symbol("‹", p.text) { woche = woche.minusWeeks(1) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val ende = woche.plusDays(6)
                Punkt(
                    MONATE_KURZ[woche.monthValue - 1].uppercase() +
                        (if (ende.monthValue != woche.monthValue) " / " + MONATE_KURZ[ende.monthValue - 1].uppercase() else "") + " " + ende.year,
                    18.sp
                )
            }
            Symbol("›", p.text) { woche = woche.plusWeeks(1) }
            Symbol("◎", p.akzent) { woche = wochenStart(LocalDate.now()) }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f), beyondViewportPageCount = 1, key = { it }) { seite ->
            val ws = anker.plusWeeks((seite - mitte).toLong())
            val we = ws.plusDays(6)
            val sofort = seite == pager.currentPage
            val daten by produceState(if (sofort) wochenDaten(terminFenster(b, ws, we), ws) else null, b, ws) {
                value = withContext(Dispatchers.Default) { wochenDaten(terminFenster(b, ws, we), ws) }
            }
            WochenSeite(ws, daten, b)
        }
    }
}

@Composable
private fun WochenSeite(ws: LocalDate, daten: WochenDaten?, b: TerminBestand) {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val haptik = LocalHapticFeedback.current
    val dichte = LocalDensity.current
    val heute = LocalDate.now()
    var jetzt by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); jetzt = LocalTime.now() } }
    // Startposition: zwei Stunden vor jetzt bzw. 7 Uhr
    val startStunde = if (!heute.isBefore(ws) && !heute.isAfter(ws.plusDays(6))) (jetzt.hour - 2).coerceIn(0, 16) else 7
    val scroll = rememberScrollState(with(dichte) { (STUNDE * startStunde).roundToPx() })

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val spaltenB = (maxWidth - RAND_LINKS) / 7
        Column(Modifier.fillMaxSize()) {
            // ---------- Kopf: Wochentage, Datum, ganztägige Termine
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Spacer(Modifier.width(RAND_LINKS))
                for (i in 0..6) {
                    val tag = ws.plusDays(i.toLong())
                    Box(Modifier.width(spaltenB), contentAlignment = Alignment.Center) {
                        Mono(WOCHENTAGE[i].uppercase() + ".", if (i == 6 || Feiertage.name(tag, b.daten.feiertagsLand.land) != null) p.akzent else p.textFaint, 11.sp, fett = true)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)) {
                Column(Modifier.width(RAND_LINKS), horizontalAlignment = Alignment.CenterHorizontally) {
                    Mono("W", p.textFaint, 10.sp, fett = true)
                    Mono(kalenderwoche(ws).toString(), p.text, 11.sp, fett = true)
                }
                for (i in 0..6) {
                    val tag = ws.plusDays(i.toLong())
                    val istHeute = tag == heute
                    val ganz = daten?.ganztags?.getOrNull(i).orEmpty()
                    val ft = Feiertage.name(tag, b.daten.feiertagsLand.land)
                    Column(
                        Modifier.width(spaltenB).padding(horizontal = 1.5.dp).heightIn(min = 64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (ft != null) p.rot.copy(alpha = 0.30f) else p.panel)
                            .then(
                                if (istHeute) Modifier.border(1.5.dp, p.rand, RoundedCornerShape(8.dp))
                                else if (ft != null) Modifier.border(1.dp, p.rot.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                                else Modifier
                            )
                            .pointerInput(tag) { detectTapGestures(onLongPress = { haptik.performHapticFeedback(HapticFeedbackType.LongPress); st.maske = MaskeStart(datum = tag) }) }
                            .padding(bottom = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.padding(top = 5.dp, bottom = 3.dp).clip(RoundedCornerShape(6.dp))
                                .background(if (istHeute) p.akzent else Color.Transparent).padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(tag.dayOfMonth.toString(), color = if (istHeute) textAuf(p.akzent) else p.text,
                                style = TextStyle(fontFamily = Schrift.mono, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                        }
                        ganz.take(3).forEach { t -> GanzTagChip(t) }
                        if (ganz.size > 3) Mono("+${ganz.size - 3}", p.textDim, 9.sp)
                    }
                }
            }
            // ---------- Zeitraster
            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
                val hoehe = STUNDE * 24
                val linie = p.randLeise
                val punkte = p.randLeise.copy(alpha = p.randLeise.alpha * 0.8f)
                Canvas(Modifier.fillMaxWidth().height(hoehe)) {
                    val links = RAND_LINKS.toPx()
                    val h = STUNDE.toPx()
                    for (s in 0..24) {
                        drawLine(linie, Offset(links, s * h), Offset(size.width, s * h), 1f)
                        if (s < 24) drawLine(punkte, Offset(links, s * h + h / 2), Offset(size.width, s * h + h / 2), 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 10f)))
                    }
                }
                // Stundenbeschriftung
                for (s in 1..23) {
                    Box(Modifier.offset(y = STUNDE * s - 7.dp).width(RAND_LINKS - 4.dp), contentAlignment = Alignment.CenterEnd) {
                        Mono(s.toString(), p.textDim, 10.sp)
                    }
                }
                // Lange drücken auf eine freie Stelle: Termin zu dieser Zeit anlegen
                Box(
                    Modifier.offset(x = RAND_LINKS).width(spaltenB * 7).height(hoehe).pointerInput(ws) {
                        detectTapGestures(onLongPress = { pos ->
                            val tag = (pos.x / spaltenB.toPx()).toInt().coerceIn(0, 6)
                            val min = ((pos.y / STUNDE.toPx()) * 60).toInt()
                            haptik.performHapticFeedback(HapticFeedbackType.LongPress)
                            st.maske = MaskeStart(datum = ws.plusDays(tag.toLong()), uhrzeit = LocalTime.of((min / 60).coerceIn(0, 23), if (min % 60 >= 30) 30 else 0))
                        })
                    }
                )
                // Feiertage: ganze Spalte leicht rot
                for (i in 0..6) {
                    if (Feiertage.name(ws.plusDays(i.toLong()), b.daten.feiertagsLand.land) != null)
                        Box(Modifier.offset(x = RAND_LINKS + spaltenB * i).width(spaltenB).height(hoehe).background(p.rot.copy(alpha = 0.10f)))
                }
                // Terminblöcke
                daten?.bloecke?.forEach { bl -> TerminBlock(bl, spaltenB) }
                // Jetzt-Linie
                val tagIdx = ChronoUnit.DAYS.between(ws, heute).toInt()
                if (tagIdx in 0..6) {
                    val y = STUNDE * ((jetzt.hour * 60 + jetzt.minute) / 60f)
                    Box(Modifier.offset(y = y - 7.dp).width(RAND_LINKS), contentAlignment = Alignment.CenterEnd) {
                        Mono("%02d:%02d".format(jetzt.hour, jetzt.minute), p.akzent, 9.sp, fett = true, modifier = Modifier.padding(end = 2.dp))
                    }
                    Box(Modifier.offset(x = RAND_LINKS, y = y).width(spaltenB * 7).height(1.dp).background(p.akzent.copy(alpha = 0.35f)))
                    Box(Modifier.offset(x = RAND_LINKS + spaltenB * tagIdx, y = y - 1.dp).width(spaltenB).height(2.dp).background(p.akzent))
                    Box(Modifier.offset(x = RAND_LINKS + spaltenB * tagIdx - 3.dp, y = y - 4.dp).size(8.dp).clip(RoundedCornerShape(4.dp)).background(p.akzent))
                }
            }
        }
    }
}

@Composable
private fun GanzTagChip(t: Termin) {
    val st = LocalSteuerung.current
    val farbe = Color(t.farbe)
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 1.dp).clip(RoundedCornerShape(4.dp)).background(farbe)
            .clickable { if (t.istTodo) st.reiter = Reiter.TODO else st.terminDetail = t }
            .padding(horizontal = 3.dp, vertical = 1.dp)
    ) {
        Text(t.titel, color = textAuf(farbe), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
            style = TextStyle(fontFamily = Schrift.text, fontSize = 9.5.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun TerminBlock(bl: Block, spaltenB: Dp) {
    val st = LocalSteuerung.current
    val farbe = Color(bl.termin.farbe)
    val breite = spaltenB / bl.spalten
    val y = STUNDE * (bl.von / 60f)
    val h = STUNDE * ((bl.bis - bl.von) / 60f)
    Box(
        Modifier.offset(x = RAND_LINKS + spaltenB * bl.tag + breite * bl.spalte + 1.dp, y = y + 1.dp)
            .width(breite - 2.dp).height(h - 2.dp)
            .clip(RoundedCornerShape(5.dp)).background(farbe)
            .clickable { if (bl.termin.istTodo) st.reiter = Reiter.TODO else st.terminDetail = bl.termin }
            .padding(horizontal = 3.dp, vertical = 2.dp)
    ) {
        Column {
            Text(bl.termin.titel, color = textAuf(farbe), maxLines = if (h > 40.dp) 4 else 1, overflow = TextOverflow.Clip,
                style = TextStyle(fontFamily = Schrift.text, fontSize = 10.sp, fontWeight = FontWeight.Medium, lineHeight = 12.sp))
            if (h > 56.dp && bl.termin.ort.isNotBlank())
                Text(bl.termin.ort, color = textAuf(farbe).copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Clip,
                    style = TextStyle(fontFamily = Schrift.text, fontSize = 9.sp))
        }
    }
}
