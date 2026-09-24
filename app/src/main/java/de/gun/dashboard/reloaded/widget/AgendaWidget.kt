package de.gun.dashboard.reloaded.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
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
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import de.gun.dashboard.reloaded.Aktualisierung
import de.gun.dashboard.reloaded.MainActivity
import de.gun.dashboard.reloaded.daten.Speicher
import de.gun.dashboard.reloaded.geraet.GeraeteKalender
import de.gun.dashboard.reloaded.geraet.Kontakte
import de.gun.dashboard.reloaded.logik.FARBE_TODO
import de.gun.dashboard.reloaded.logik.FARBE_TODO_HOCH
import de.gun.dashboard.reloaded.logik.FARBE_UEBERFAELLIG
import de.gun.dashboard.reloaded.logik.Feiertage
import de.gun.dashboard.reloaded.logik.MONATE_KURZ
import de.gun.dashboard.reloaded.logik.WOCHENTAGE
import de.gun.dashboard.reloaded.logik.hhmm
import de.gun.dashboard.reloaded.logik.kalenderwoche
import de.gun.dashboard.reloaded.logik.parseDE
import de.gun.dashboard.reloaded.logik.termineAm
import de.gun.dashboard.reloaded.logik.zeitAus
import java.time.LocalDate

/** Eine Zeile im Widget. */
data class WidgetZeile(
    val art: String, // "tag", "termin", "aufgabe", "leer"
    val titel: String,
    val zeit: String = "",
    val unter: String = "",
    val farbe: Int = 0,
    val ziel: String = "",
)

class AgendaWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Speicher.init(context)
        // Läuft die App nicht, liegen noch keine Gerätetermine im Speicher
        if (GeraeteKalender.darfLesen(context) &&
            System.currentTimeMillis() - GeraeteKalender.stand.value > 20 * 60_000
        ) {
            GeraeteKalender.einlesen(context)
            if (Speicher.aktuell.nativ.kontaktdaten) Kontakte.einlesen(context)
        }
        val zeilen = zeilenBilden()
        val w = Speicher.aktuell.widget
        val hell = w.modus == "hell"
        val deck = ((w.deckkraft ?: 85).coerceIn(0, 100)) / 100f
        val heute = LocalDate.now()
        provideContent { Inhalt(zeilen, hell, deck, heute, w.endzeit) }
    }

    companion object {
        suspend fun aktualisieren(ctx: Context) = AgendaWidget().updateAll(ctx)

        fun zeilenBilden(): List<WidgetZeile> {
            val d = Speicher.aktuell
            val b = Aktualisierung.bestand()
            val tage = (d.widget.tage ?: 1).coerceIn(1, 14)
            val heute = LocalDate.now()
            val aus = mutableListOf<WidgetZeile>()
            for (v in 0 until tage) {
                val tag = heute.plusDays(v.toLong())
                val desTages = mutableListOf<Pair<Boolean, WidgetZeile>>()
                termineAm(b, tag, false).forEach { t ->
                    val zeit = if (t.ganztags) "" else t.start.hhmm()
                    val bis = if (!t.ganztags && t.ende != null) t.ende.hhmm() else ""
                    desTages += t.ganztags to WidgetZeile(
                        "termin", t.titel.take(60), zeit + (if (bis.isNotEmpty()) "|$bis" else ""),
                        t.ort.take(40), t.farbe, "termin"
                    )
                }
                if (d.widget.aufgaben) {
                    d.todos.eintraege.filter { !it.erledigt }.forEach { t ->
                        val f = parseDE(t.faellig) ?: return@forEach
                        val passt = if (v == 0) !f.isAfter(tag) else f == tag
                        if (!passt) return@forEach
                        val ueber = f.isBefore(heute)
                        desTages += (zeitAus(t.uhrzeit) == null) to WidgetZeile(
                            "aufgabe", "☐ " + t.text.take(60), zeitAus(t.uhrzeit)?.hhmm() ?: "",
                            if (ueber) "überfällig seit ${t.faellig}" else "",
                            if (ueber) FARBE_UEBERFAELLIG else if (t.prio == "hoch") FARBE_TODO_HOCH else FARBE_TODO, "todo"
                        )
                    }
                }
                if (desTages.isEmpty()) continue
                if (tage > 1) {
                    val fest = Feiertage.name(tag, d.feiertagsLand.land) ?: Feiertage.ferien(tag, d.ferien) ?: ""
                    aus += WidgetZeile(
                        "tag", (if (v == 0) "Heute" else if (v == 1) "Morgen" else WOCHENTAGE[tag.dayOfWeek.value - 1]) +
                            ", " + tag.dayOfMonth + ". " + MONATE_KURZ[tag.monthValue - 1], unter = fest
                    )
                }
                aus += desTages.sortedWith(compareBy({ !it.first }, { it.second.zeit })).map { it.second }
            }
            return aus.take(200)
        }
    }
}

@Composable
private fun Inhalt(zeilen: List<WidgetZeile>, hell: Boolean, deck: Float, heute: LocalDate, endzeit: Boolean) {
    val hg = (if (hell) Color.White else Color.Black).copy(alpha = deck)
    val text = if (hell) Color(0xFF111111) else Color.White
    val leise = if (hell) Color(0xFF6A6A6A) else Color(0xFF9B9B9B)
    val rot = Color(0xFFD71921)
    val oeffnen = actionStartActivity(Intent().setClassName("de.gun.dashboard.reloaded", MainActivity::class.java.name).putExtra("ziel", "heute"))

    Column(GlanceModifier.fillMaxSize().background(hg).cornerRadius(22.dp).padding(14.dp).clickable(oeffnen)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("●", style = TextStyle(color = ColorProvider(rot), fontSize = 10.sp))
            Spacer(GlanceModifier.width(6.dp))
            Text(
                WOCHENTAGE[heute.dayOfWeek.value - 1].uppercase() + " " + heute.dayOfMonth + ". " + MONATE_KURZ[heute.monthValue - 1].uppercase(),
                style = TextStyle(color = ColorProvider(text), fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            )
            Spacer(GlanceModifier.defaultWeight())
            Text("KW " + kalenderwoche(heute), style = TextStyle(color = ColorProvider(leise), fontSize = 11.sp, fontFamily = FontFamily.Monospace))
        }
        Spacer(GlanceModifier.height(8.dp))
        if (zeilen.isEmpty()) {
            Text("Keine Termine.", style = TextStyle(color = ColorProvider(leise), fontSize = 13.sp))
        } else {
            LazyColumn(GlanceModifier.fillMaxSize()) {
                items(zeilen) { z -> ZeileAnzeigen(z, text, leise, endzeit) }
            }
        }
    }
}

@Composable
private fun ZeileAnzeigen(z: WidgetZeile, text: Color, leise: Color, endzeit: Boolean) {
    val ziel = actionStartActivity(Intent().setClassName("de.gun.dashboard.reloaded", MainActivity::class.java.name)
        .putExtra("ziel", if (z.ziel == "todo") "todo" else "kalender"))
    if (z.art == "tag") {
        Row(GlanceModifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(z.titel.uppercase(), style = TextStyle(color = ColorProvider(leise), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
            if (z.unter.isNotEmpty()) {
                Spacer(GlanceModifier.width(6.dp))
                Text("· " + z.unter, style = TextStyle(color = ColorProvider(leise), fontSize = 10.sp), maxLines = 1)
            }
        }
        return
    }
    val teile = z.zeit.split("|")
    val zeit = when {
        teile[0].isEmpty() -> "ganzt."
        endzeit && teile.size > 1 -> teile[0] + "–" + teile[1]
        else -> teile[0]
    }
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).clickable(ziel), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.width(3.dp).height(18.dp).background(Color(z.farbe or 0xFF000000.toInt())).cornerRadius(2.dp)) {}
        Spacer(GlanceModifier.width(8.dp))
        Text(zeit, style = TextStyle(color = ColorProvider(leise), fontSize = 11.sp, fontFamily = FontFamily.Monospace),
            modifier = GlanceModifier.width(if (endzeit) 78.dp else 44.dp), maxLines = 1)
        Text(
            z.titel + if (z.unter.isNotEmpty()) " · " + z.unter else "",
            style = TextStyle(color = ColorProvider(text), fontSize = 13.sp), maxLines = 1
        )
    }
}

class AgendaWidgetEmpfaenger : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaWidget()
}
