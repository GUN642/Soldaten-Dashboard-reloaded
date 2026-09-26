package de.gun.dashboard.reloaded.ui.seiten

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gun.dashboard.reloaded.erinnerung.Unwetter
import de.gun.dashboard.reloaded.netz.DwdWarnDienst
import de.gun.dashboard.reloaded.netz.DwdWarnung
import de.gun.dashboard.reloaded.ui.Abstand
import de.gun.dashboard.reloaded.ui.Fliesstext
import de.gun.dashboard.reloaded.ui.Karte
import de.gun.dashboard.reloaded.ui.LocalPalette
import de.gun.dashboard.reloaded.ui.Mono
import de.gun.dashboard.reloaded.ui.RUND_KLEIN
import de.gun.dashboard.reloaded.ui.Symbol
import kotlinx.coroutines.delay

/**
 * Amtliche DWD-Warnungen für den Wetter-Ort. Die Karte erscheint nur, wenn Warnungen vorliegen;
 * geladen wird beim Öffnen und danach alle zehn Minuten, solange HEUTE sichtbar ist.
 */
@Composable
fun UnwetterKarte() {
    val ctx = LocalContext.current
    val dash = aktuelleDaten().dashboard
    val liste by DwdWarnDienst.aktuell.collectAsState()
    var neuZaehler by remember { mutableStateOf(0) }

    LaunchedEffect(dash.ort, dash.dwdWarnungen, neuZaehler) {
        if (!dash.dwdWarnungen) return@LaunchedEffect
        while (true) {
            try {
                val l = DwdWarnDienst.laden(dash.ort, neu = neuZaehler > 0)
                Unwetter.neueMelden(ctx, l)
            } catch (e: Exception) { }
            delay(10 * 60_000L)
        }
    }

    val warnungen = liste.orEmpty()
    if (!dash.dwdWarnungen || warnungen.isEmpty()) return
    val p = LocalPalette.current
    val hoechste = warnungen.maxOf { it.stufe }
    Karte(
        "⚠ Wetterwarnung",
        modifier = Modifier.border(2.dp, Color(DwdWarnDienst.farbe(hoechste)), de.gun.dashboard.reloaded.ui.RUND),
        aktion = { Symbol("↻") { neuZaehler++ } },
    ) {
        warnungen.forEachIndexed { i, w ->
            if (i > 0) Abstand(8.dp)
            WarnZeile(w)
        }
        Abstand(8.dp)
        Mono("Quelle: Deutscher Wetterdienst · ${dash.ort.name}", p.textFaint, 10.sp)
    }
}

@Composable
private fun WarnZeile(w: DwdWarnung) {
    val p = LocalPalette.current
    var offen by remember(w.id) { mutableStateOf(false) }
    val farbe = Color(DwdWarnDienst.farbe(w.stufe))
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RUND_KLEIN).background(p.panelAlt)
            .border(1.dp, p.randLeise, RUND_KLEIN).clickable { offen = !offen }
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(farbe))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Mono(w.stufeText.uppercase() + " · STUFE ${w.stufe}", farbe, 10.sp, fett = true)
            Fliesstext(w.ueberschrift.ifBlank { w.ereignis }, fett = true, groesse = 14.sp)
            val zeit = w.zeitraum()
            if (zeit.isNotBlank()) Mono(zeit, p.textDim, 11.sp)
            if (offen) {
                if (w.beschreibung.isNotBlank()) { Abstand(6.dp); Fliesstext(w.beschreibung, p.text, 13.sp) }
                if (w.hinweise.isNotBlank()) { Abstand(6.dp); Fliesstext(w.hinweise, p.textDim, 12.sp) }
                if (w.gebiet.isNotBlank()) { Abstand(4.dp); Mono(w.gebiet, p.textFaint, 10.sp) }
            } else Mono("Antippen für Details", p.textFaint, 10.sp)
        }
    }
}
