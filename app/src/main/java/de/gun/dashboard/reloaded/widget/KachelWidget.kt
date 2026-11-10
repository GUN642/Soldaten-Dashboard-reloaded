package de.gun.dashboard.reloaded.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.GridCells
import androidx.glance.appwidget.lazy.LazyVerticalGrid
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import de.gun.dashboard.reloaded.MainActivity
import de.gun.dashboard.reloaded.daten.Speicher
import de.gun.dashboard.reloaded.geraet.GeraeteKalender
import de.gun.dashboard.reloaded.geraet.Kontakte
import de.gun.dashboard.reloaded.logik.MONATE_KURZ
import de.gun.dashboard.reloaded.logik.WOCHENTAGE
import de.gun.dashboard.reloaded.logik.kalenderwoche
import java.time.LocalDate

/** Eine Kachel: Zeitangabe (mit Tag, falls nicht heute) und Titel. */
data class Kachel(val zeit: String, val titel: String, val ziel: String)

/** Termine als Kacheln in zwei Spalten – im Stil der VOID-Favoriten. */
class KachelWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Speicher.init(context)
        if (GeraeteKalender.darfLesen(context) &&
            System.currentTimeMillis() - GeraeteKalender.stand.value > 20 * 60_000
        ) {
            GeraeteKalender.einlesen(context)
            if (Speicher.aktuell.nativ.kontaktdaten) Kontakte.einlesen(context)
        }
        val kacheln = kachelnBilden(AgendaWidget.zeilenBilden())
        val w = Speicher.aktuell.widget
        val hell = w.modus == "hell"
        val deck = ((w.deckkraft ?: 85).coerceIn(0, 100)) / 100f
        provideContent { KachelInhalt(kacheln, hell, deck, LocalDate.now(), w.endzeit) }
    }

    companion object {
        /** Aus den Agenda-Zeilen: Tagesüberschriften wandern in die Zeitangabe der Kacheln. */
        fun kachelnBilden(zeilen: List<WidgetZeile>, endzeit: Boolean = Speicher.aktuell.widget.endzeit): List<Kachel> {
            var tag = ""
            val aus = mutableListOf<Kachel>()
            for (z in zeilen) {
                if (z.art == "tag") {
                    tag = z.titel.substringBefore(",").let { if (it == "Heute") "" else it }
                    continue
                }
                val teile = z.zeit.split("|")
                val uhr = when {
                    teile[0].isEmpty() -> "ganztägig"
                    endzeit && teile.size > 1 -> teile[0] + "–" + teile[1]
                    else -> teile[0]
                }
                aus += Kachel(if (tag.isEmpty()) uhr else "$tag · $uhr", z.titel.removePrefix("☐ "), z.ziel)
            }
            return aus
        }
    }
}

private fun appAktion(ziel: String): Action = actionStartActivity(
    Intent().setClassName("de.gun.dashboard.reloaded", MainActivity::class.java.name).putExtra("ziel", ziel)
)

@Composable
private fun KachelInhalt(kacheln: List<Kachel>, hell: Boolean, deck: Float, heute: LocalDate, endzeit: Boolean) {
    val hg = (if (hell) Color.White else Color.Black).copy(alpha = deck)
    val kachelHg = if (hell) Color(0xFFEDEDED) else Color(0xFF1E1E1E)
    val text = if (hell) Color(0xFF111111) else Color.White
    val leise = if (hell) Color(0xFF6A6A6A) else Color(0xFF9B9B9B)
    val rot = Color(0xFFD71921)

    Column(GlanceModifier.fillMaxSize().background(hg).cornerRadius(28.dp).padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
        Row(GlanceModifier.fillMaxWidth().clickable(appAktion("kalender")), verticalAlignment = Alignment.CenterVertically) {
            Text("TERMINE", style = TextStyle(color = ColorProvider(text), fontSize = 26.sp, fontWeight = FontWeight.Bold))
            Text(".", style = TextStyle(color = ColorProvider(rot), fontSize = 26.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.width(10.dp))
            Text(
                WOCHENTAGE[heute.dayOfWeek.value - 1].uppercase() + " " + heute.dayOfMonth + ". " +
                    MONATE_KURZ[heute.monthValue - 1].uppercase() + " · KW " + kalenderwoche(heute),
                style = TextStyle(color = ColorProvider(leise), fontSize = 12.sp), maxLines = 1,
                modifier = GlanceModifier.defaultWeight()
            )
            Box(
                GlanceModifier.size(46.dp).cornerRadius(23.dp).background(rot).clickable(appAktion("neuerTermin")),
                contentAlignment = Alignment.Center
            ) {
                Text("+", style = TextStyle(color = ColorProvider(Color.White), fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
            }
        }
        Spacer(GlanceModifier.height(10.dp))
        if (kacheln.isEmpty()) {
            Box(GlanceModifier.fillMaxWidth().cornerRadius(18.dp).background(kachelHg).padding(14.dp).clickable(appAktion("kalender"))) {
                Text("Keine Termine.", style = TextStyle(color = ColorProvider(leise), fontSize = 14.sp))
            }
        } else {
            LazyVerticalGrid(GridCells.Fixed(2), GlanceModifier.fillMaxSize()) {
                items(kacheln) { k -> KachelAnzeigen(k, kachelHg, text, leise, rot) }
            }
        }
    }
}

@Composable
private fun KachelAnzeigen(k: Kachel, hg: Color, text: Color, leise: Color, rot: Color) {
    // Außenabstand über eine umschließende Box, da Glance kein Grid-Spacing kennt
    Box(GlanceModifier.fillMaxWidth().padding(end = 6.dp, bottom = 6.dp)) {
        Row(
            GlanceModifier.fillMaxWidth().height(64.dp).cornerRadius(18.dp).background(hg).padding(horizontal = 12.dp)
                .clickable(appAktion(if (k.ziel == "todo") "todo" else "kalender")),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("●", style = TextStyle(color = ColorProvider(rot), fontSize = 9.sp))
            Spacer(GlanceModifier.width(9.dp))
            Column {
                Text(k.zeit, style = TextStyle(color = ColorProvider(leise), fontSize = 11.sp, fontFamily = FontFamily.Monospace), maxLines = 1)
                Text(k.titel, style = TextStyle(color = ColorProvider(text), fontSize = 15.sp), maxLines = 1)
            }
        }
    }
}

class KachelWidgetEmpfaenger : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KachelWidget()
}
