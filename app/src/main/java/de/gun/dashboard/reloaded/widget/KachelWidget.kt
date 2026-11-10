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
import androidx.glance.appwidget.lazy.LazyColumn
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

/** Eine Kachel: Uhrzeit und Titel; ziel "tag" kennzeichnet eine Tagesüberschrift. */
data class Kachel(val zeit: String, val titel: String, val ziel: String)

/** Termine als kompakte Kacheln untereinander – im Stil der VOID-Favoriten. */
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
        /** Aus den Agenda-Zeilen; „Heute“ bekommt keine eigene Überschrift. */
        fun kachelnBilden(zeilen: List<WidgetZeile>, endzeit: Boolean = Speicher.aktuell.widget.endzeit): List<Kachel> {
            val aus = mutableListOf<Kachel>()
            for (z in zeilen) {
                if (z.art == "tag") {
                    if (!z.titel.startsWith("Heute")) aus += Kachel("", z.titel + if (z.unter.isNotEmpty()) " · " + z.unter else "", "tag")
                    continue
                }
                val teile = z.zeit.split("|")
                val uhr = when {
                    teile[0].isEmpty() -> "ganztägig"
                    endzeit && teile.size > 1 -> teile[0] + "–" + teile[1]
                    else -> teile[0]
                }
                aus += Kachel(uhr, z.titel.removePrefix("☐ "), z.ziel)
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

    Column(GlanceModifier.fillMaxSize().background(hg).cornerRadius(26.dp).padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)) {
        Row(GlanceModifier.fillMaxWidth().clickable(appAktion("kalender")), verticalAlignment = Alignment.CenterVertically) {
            Text("TERMINE", style = TextStyle(color = ColorProvider(text), fontSize = 21.sp, fontWeight = FontWeight.Bold))
            Text(".", style = TextStyle(color = ColorProvider(rot), fontSize = 21.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.width(10.dp))
            Text(
                WOCHENTAGE[heute.dayOfWeek.value - 1].uppercase() + " " + heute.dayOfMonth + ". " +
                    MONATE_KURZ[heute.monthValue - 1].uppercase() + " · KW " + kalenderwoche(heute),
                style = TextStyle(color = ColorProvider(leise), fontSize = 12.sp), maxLines = 1,
                modifier = GlanceModifier.defaultWeight()
            )
            Box(
                GlanceModifier.size(38.dp).cornerRadius(19.dp).background(rot).clickable(appAktion("neuerTermin")),
                contentAlignment = Alignment.Center
            ) {
                Text("+", style = TextStyle(color = ColorProvider(Color.White), fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        if (kacheln.isEmpty()) {
            Box(GlanceModifier.fillMaxWidth().cornerRadius(14.dp).background(kachelHg).padding(horizontal = 12.dp, vertical = 9.dp).clickable(appAktion("kalender"))) {
                Text("Keine Termine.", style = TextStyle(color = ColorProvider(leise), fontSize = 13.sp))
            }
        } else {
            LazyColumn(GlanceModifier.fillMaxSize()) {
                items(kacheln) { k -> KachelAnzeigen(k, kachelHg, text, leise, rot, endzeit) }
            }
        }
    }
}

@Composable
private fun KachelAnzeigen(k: Kachel, hg: Color, text: Color, leise: Color, rot: Color, endzeit: Boolean) {
    if (k.ziel == "tag") {
        Text(
            k.titel.uppercase(), maxLines = 1, modifier = GlanceModifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
            style = TextStyle(color = ColorProvider(leise), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        )
        return
    }
    // Abstand zwischen den Kacheln über eine umschließende Box
    Box(GlanceModifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(
            GlanceModifier.fillMaxWidth().height(34.dp).cornerRadius(12.dp).background(hg).padding(horizontal = 10.dp)
                .clickable(appAktion(if (k.ziel == "todo") "todo" else "kalender")),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("●", style = TextStyle(color = ColorProvider(rot), fontSize = 8.sp))
            Spacer(GlanceModifier.width(8.dp))
            Text(k.zeit, style = TextStyle(color = ColorProvider(leise), fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                maxLines = 1, modifier = GlanceModifier.width(if (endzeit) 82.dp else 46.dp))
            Text(k.titel, style = TextStyle(color = ColorProvider(text), fontSize = 14.sp), maxLines = 1)
        }
    }
}

class KachelWidgetEmpfaenger : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KachelWidget()
}
