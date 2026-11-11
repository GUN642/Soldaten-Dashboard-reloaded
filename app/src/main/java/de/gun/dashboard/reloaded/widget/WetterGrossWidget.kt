package de.gun.dashboard.reloaded.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
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
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import de.gun.dashboard.reloaded.MainActivity
import de.gun.dashboard.reloaded.daten.Speicher
import de.gun.dashboard.reloaded.logik.WOCHENTAGE
import de.gun.dashboard.reloaded.netz.DwdWarnDienst
import de.gun.dashboard.reloaded.netz.DwdWarnung
import de.gun.dashboard.reloaded.netz.Wetter
import de.gun.dashboard.reloaded.netz.WetterDienst
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** Frei skalierbares Wetter: einfaches Wetter (Open-Meteo) oder das eingestellte meteoblue-Widget. */
class WetterGrossWidget : GlanceAppWidget() {
    // Inhalt richtet sich nach der tatsächlichen Größe auf dem Startbildschirm
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Speicher.init(context)
        val d = Speicher.aktuell
        val dash = d.dashboard
        val w = d.widget
        val hell = w.modus == "hell"
        val deck = ((w.deckkraft ?: 85).coerceIn(0, 100)) / 100f
        val quelle = wetterQuelle(w.wetterQuelle, dash.quelle, dash.wetterUrl)
        if (quelle == "meteoblue") {
            try { MeteoblueBild.aktualisieren(context, dash.wetterUrl) } catch (e: Exception) { }
            val bild = MeteoblueBild.gespeichert(context, dash.wetterUrl, w.meteoblueKuerzen ?: 0)
            provideContent { MeteoblueInhalt(bild, hell, deck) }
        } else {
            val wetter: Wetter? = try { WetterDienst.laden(dash.ort, false) } catch (e: Exception) { null }
            val warnung: DwdWarnung? = if (!dash.dwdWarnungen) null
            else (try { DwdWarnDienst.laden(dash.ort) } catch (e: Exception) { DwdWarnDienst.aktuell.value.orEmpty() }).firstOrNull()
            provideContent { EinfachInhalt(wetter, warnung, dash.ort.name, hell, deck) }
        }
    }

    companion object {
        /** "app" folgt der Einstellung im Reiter Heute; meteoblue nur mit eingetragener Adresse. */
        fun wetterQuelle(widget: String, app: String, url: String): String {
            val q = if (widget == "openmeteo" || widget == "meteoblue") widget else app
            return if (q == "meteoblue" && url.isNotBlank()) "meteoblue" else "openmeteo"
        }

        /** Wie viele Stunden und Tage passen in die Größe? */
        fun aufteilung(breite: Dp, hoehe: Dp): Pair<Int, Int> {
            val stunden = if (hoehe < 125.dp) 0 else ((breite.value - 24) / 50).toInt().coerceIn(3, 10)
            val rest = hoehe.value - 24 - 58 - (if (stunden > 0) 74 else 0)
            val tage = if (hoehe < 190.dp) 0 else (rest / 26).toInt().coerceIn(0, 7)
            return stunden to tage
        }
    }
}

private fun heuteOeffnen() =
    actionStartActivity(Intent().setClassName("de.gun.dashboard.reloaded", MainActivity::class.java.name).putExtra("ziel", "heute"))

@Composable
private fun MeteoblueInhalt(bild: Pair<Bitmap, Long>?, hell: Boolean, deck: Float) {
    val hg = (if (hell) Color.White else Color.Black).copy(alpha = deck)
    val leise = if (hell) Color(0xFF6A6A6A) else Color(0xFF9B9B9B)
    Box(GlanceModifier.fillMaxSize().background(hg).cornerRadius(22.dp).padding(8.dp).clickable(heuteOeffnen()), contentAlignment = Alignment.Center) {
        if (bild == null) {
            Text("meteoblue wird geladen …", style = TextStyle(color = ColorProvider(leise), fontSize = 12.sp))
        } else {
            Image(ImageProvider(bild.first), "meteoblue Wetter", GlanceModifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}

@Composable
private fun EinfachInhalt(w: Wetter?, warnung: DwdWarnung?, ort: String, hell: Boolean, deck: Float) {
    val hg = (if (hell) Color.White else Color.Black).copy(alpha = deck)
    val text = if (hell) Color(0xFF111111) else Color.White
    val leise = if (hell) Color(0xFF6A6A6A) else Color(0xFF9B9B9B)
    val groesse = LocalSize.current
    val (anzStunden, anzTage) = WetterGrossWidget.aufteilung(groesse.width, groesse.height)
    val klein = groesse.height < 100.dp || groesse.width < 160.dp

    Column(GlanceModifier.fillMaxSize().background(hg).cornerRadius(22.dp).padding(12.dp).clickable(heuteOeffnen())) {
        if (w == null) {
            Text("Wetter nicht verfügbar", style = TextStyle(color = ColorProvider(leise), fontSize = 12.sp))
            Text(ort, style = TextStyle(color = ColorProvider(leise), fontSize = 11.sp))
            return@Column
        }
        val tag = w.tage.firstOrNull()
        // Kopf: Symbol, Temperatur, Ort und Tageswerte
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(WetterDienst.zeichen(w.code), style = TextStyle(fontSize = if (klein) 24.sp else 32.sp))
            Spacer(GlanceModifier.width(6.dp))
            Text((w.grad?.roundToInt()?.toString() ?: "–") + "°",
                style = TextStyle(color = ColorProvider(text), fontSize = if (klein) 26.sp else 36.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
            Spacer(GlanceModifier.defaultWeight())
            if (groesse.width >= 150.dp) Column(horizontalAlignment = Alignment.End) {
                Text(ort, style = TextStyle(color = ColorProvider(text), fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Text(WetterDienst.text(w.code), style = TextStyle(color = ColorProvider(leise), fontSize = 11.sp), maxLines = 1)
                if (tag != null) Text(
                    "${tag.max?.roundToInt() ?: "–"}° / ${tag.min?.roundToInt() ?: "–"}°" + if ((tag.regenWkt ?: 0) > 0) " · ${tag.regenWkt} %" else "",
                    style = TextStyle(color = ColorProvider(leise), fontSize = 11.sp, fontFamily = FontFamily.Monospace), maxLines = 1
                )
            }
        }
        // Nächste Stunden
        if (anzStunden > 0) {
            val jetzt = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)
            val stunden = w.stunden.filter { s -> runCatching { !LocalDateTime.parse(s.zeit).isBefore(jetzt) }.getOrDefault(false) }.take(anzStunden)
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth().cornerRadius(14.dp).background(text.copy(alpha = 0.07f)).padding(vertical = 6.dp)) {
                stunden.forEach { s ->
                    Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.zeit.substringAfter("T").take(2), style = TextStyle(color = ColorProvider(leise), fontSize = 10.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center))
                        Text(WetterDienst.zeichen(s.code), style = TextStyle(fontSize = 16.sp, textAlign = TextAlign.Center))
                        Text((s.grad?.roundToInt()?.toString() ?: "–") + "°", style = TextStyle(color = ColorProvider(text), fontSize = 12.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center))
                    }
                }
            }
        }
        // Kommende Tage
        if (anzTage > 0) {
            Spacer(GlanceModifier.height(6.dp))
            w.tage.drop(1).take(anzTage).forEach { t ->
                val datum = runCatching { LocalDate.parse(t.datum) }.getOrNull()
                Row(GlanceModifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(datum?.let { WOCHENTAGE[it.dayOfWeek.value - 1] } ?: "", style = TextStyle(color = ColorProvider(text), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.width(34.dp))
                    Text(WetterDienst.zeichen(t.code), style = TextStyle(fontSize = 15.sp), modifier = GlanceModifier.width(30.dp))
                    Text(if ((t.regenWkt ?: 0) > 0) "${t.regenWkt} %" else "", style = TextStyle(color = ColorProvider(leise), fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                        modifier = GlanceModifier.defaultWeight())
                    Text("${t.max?.roundToInt() ?: "–"}° / ${t.min?.roundToInt() ?: "–"}°", style = TextStyle(color = ColorProvider(text), fontSize = 12.sp, fontFamily = FontFamily.Monospace))
                }
            }
        }
        // DWD-Warnung
        if (warnung != null && groesse.height >= 100.dp) {
            Spacer(GlanceModifier.height(6.dp))
            val farbe = Color(DwdWarnDienst.farbe(warnung.stufe))
            Row(GlanceModifier.fillMaxWidth().cornerRadius(10.dp).background(farbe.copy(alpha = 0.18f)).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.width(3.dp).height(22.dp).background(farbe).cornerRadius(2.dp)) {}
                Spacer(GlanceModifier.width(6.dp))
                Text("⚠ " + warnung.ueberschrift.ifBlank { warnung.ereignis },
                    style = TextStyle(color = ColorProvider(text), fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
        }
    }
}

class WetterGrossWidgetEmpfaenger : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WetterGrossWidget()
}
