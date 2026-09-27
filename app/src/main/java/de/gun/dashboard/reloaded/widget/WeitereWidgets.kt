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
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
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
import de.gun.dashboard.reloaded.MainActivity
import de.gun.dashboard.reloaded.daten.Speicher
import de.gun.dashboard.reloaded.logik.fristenZaehlen
import de.gun.dashboard.reloaded.logik.mehrarbeitSaldo
import de.gun.dashboard.reloaded.logik.mitVorzeichen
import de.gun.dashboard.reloaded.logik.urlaubStand
import de.gun.dashboard.reloaded.logik.zahl
import de.gun.dashboard.reloaded.netz.DwdWarnDienst
import de.gun.dashboard.reloaded.netz.DwdWarnung
import de.gun.dashboard.reloaded.netz.Wetter
import de.gun.dashboard.reloaded.netz.WetterDienst
import kotlin.math.roundToInt

/** Gemeinsame Farben aller Widgets (hell/dunkel und Deckkraft aus den Widget-Einstellungen). */
private data class WidgetFarben(val hg: Color, val text: Color, val leise: Color)

private fun farben(): WidgetFarben {
    val w = Speicher.aktuell.widget
    val hell = w.modus == "hell"
    val deck = ((w.deckkraft ?: 85).coerceIn(0, 100)) / 100f
    return WidgetFarben(
        (if (hell) Color.White else Color.Black).copy(alpha = deck),
        if (hell) Color(0xFF111111) else Color.White,
        if (hell) Color(0xFF6A6A6A) else Color(0xFF9B9B9B),
    )
}

private val ROT = Color(0xFFD71921)
private val GELB = Color(0xFFFFB020)
private val GRUEN = Color(0xFF35D488)

private fun oeffnen(ziel: String): Action =
    actionStartActivity(Intent().setClassName("de.gun.dashboard.reloaded", MainActivity::class.java.name).putExtra("ziel", ziel))

/** Alle Widgets der App neu zeichnen. */
suspend fun alleWidgetsAktualisieren(ctx: Context) {
    try { AgendaWidget().updateAll(ctx) } catch (e: Exception) { }
    try { KennzahlenWidget().updateAll(ctx) } catch (e: Exception) { }
    try { WetterWidget().updateAll(ctx) } catch (e: Exception) { }
}

// ================================================================== Kennzahlen

/** Resturlaub, Mehrarbeit und Fristen als drei Kacheln. */
class KennzahlenWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Speicher.init(context)
        val d = Speicher.aktuell
        val urlaub = urlaubStand(d)
        val saldo = mehrarbeitSaldo(d)
        val (warn, ab) = fristenZaehlen(d)
        val f = farben()
        provideContent {
            Row(GlanceModifier.fillMaxSize().background(f.hg).cornerRadius(22.dp).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Kachel(zahl(urlaub.rest), "RESTURLAUB", if (urlaub.rest <= 0) ROT else if (urlaub.rest <= 5) GELB else f.text, f, oeffnen("urlaub"))
                Spacer(GlanceModifier.width(6.dp))
                Kachel(mitVorzeichen(saldo), "MEHRARBEIT", if (saldo < 0) ROT else f.text, f, oeffnen("urlaub"))
                Spacer(GlanceModifier.width(6.dp))
                Kachel(warn.toString() + if (ab > 0) "+$ab" else "", "FRISTEN", if (ab > 0) ROT else if (warn > 0) GELB else GRUEN, f, oeffnen("lehrgaenge"))
            }
        }
    }
}

@Composable
private fun androidx.glance.layout.RowScope.Kachel(wert: String, label: String, farbe: Color, f: WidgetFarben, klick: Action) {
    Column(
        GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(14.dp).background(f.text.copy(alpha = 0.07f)).padding(8.dp).clickable(klick),
        verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(wert, style = TextStyle(color = ColorProvider(farbe), fontSize = if (wert.length > 5) 18.sp else 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace), maxLines = 1)
        Text(label, style = TextStyle(color = ColorProvider(f.leise), fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace), maxLines = 1)
    }
}

class KennzahlenWidgetEmpfaenger : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KennzahlenWidget()
}

// ================================================================== Wetter

/** Wetter für den eingestellten Ort mit höchster DWD-Warnung. */
class WetterWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Speicher.init(context)
        val dash = Speicher.aktuell.dashboard
        val wetter: Wetter? = try { WetterDienst.laden(dash.ort, false) } catch (e: Exception) { null }
        val warnung: DwdWarnung? = if (!dash.dwdWarnungen) null
        else (try { DwdWarnDienst.laden(dash.ort) } catch (e: Exception) { DwdWarnDienst.aktuell.value.orEmpty() }).firstOrNull()
        val f = farben()
        provideContent { WetterInhalt(wetter, warnung, dash.ort.name, f) }
    }
}

@Composable
private fun WetterInhalt(w: Wetter?, warnung: DwdWarnung?, ort: String, f: WidgetFarben) {
    Column(GlanceModifier.fillMaxSize().background(f.hg).cornerRadius(22.dp).padding(12.dp).clickable(oeffnen("heute"))) {
        if (w == null) {
            Text("Wetter nicht verfügbar", style = TextStyle(color = ColorProvider(f.leise), fontSize = 12.sp))
            Text(ort, style = TextStyle(color = ColorProvider(f.leise), fontSize = 11.sp))
        } else {
            val tag = w.tage.firstOrNull()
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(WetterDienst.zeichen(w.code), style = TextStyle(fontSize = 28.sp))
                Spacer(GlanceModifier.width(8.dp))
                Text((w.grad?.roundToInt()?.toString() ?: "–") + "°",
                    style = TextStyle(color = ColorProvider(f.text), fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
                Spacer(GlanceModifier.defaultWeight())
                Column(horizontalAlignment = Alignment.End) {
                    Text(ort, style = TextStyle(color = ColorProvider(f.text), fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Text(WetterDienst.text(w.code), style = TextStyle(color = ColorProvider(f.leise), fontSize = 11.sp), maxLines = 1)
                    if (tag != null) Text(
                        "${tag.max?.roundToInt() ?: "–"}° / ${tag.min?.roundToInt() ?: "–"}°" + if ((tag.regenWkt ?: 0) > 0) " · ${tag.regenWkt} %" else "",
                        style = TextStyle(color = ColorProvider(f.leise), fontSize = 11.sp, fontFamily = FontFamily.Monospace), maxLines = 1
                    )
                }
            }
        }
        if (warnung != null) {
            Spacer(GlanceModifier.height(8.dp))
            val farbe = Color(DwdWarnDienst.farbe(warnung.stufe))
            Row(GlanceModifier.fillMaxWidth().cornerRadius(10.dp).background(farbe.copy(alpha = 0.18f)).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.width(3.dp).height(26.dp).background(farbe).cornerRadius(2.dp)) {}
                Spacer(GlanceModifier.width(6.dp))
                Column {
                    Text("⚠ " + warnung.ueberschrift.ifBlank { warnung.ereignis },
                        style = TextStyle(color = ColorProvider(f.text), fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 2)
                    Text(warnung.zeitraum(), style = TextStyle(color = ColorProvider(f.leise), fontSize = 10.sp), maxLines = 1)
                }
            }
        }
    }
}

class WetterWidgetEmpfaenger : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WetterWidget()
}
