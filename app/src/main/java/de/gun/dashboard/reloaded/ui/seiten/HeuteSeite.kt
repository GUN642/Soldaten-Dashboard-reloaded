package de.gun.dashboard.reloaded.ui.seiten

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import de.gun.dashboard.reloaded.daten.Ort
import de.gun.dashboard.reloaded.daten.Speicher
import de.gun.dashboard.reloaded.logik.FARBE_TODO
import de.gun.dashboard.reloaded.logik.FARBE_TODO_HOCH
import de.gun.dashboard.reloaded.logik.Feiertage
import de.gun.dashboard.reloaded.logik.Termin
import de.gun.dashboard.reloaded.logik.fristenZaehlen
import de.gun.dashboard.reloaded.logik.heuteDE
import de.gun.dashboard.reloaded.logik.hhmm
import de.gun.dashboard.reloaded.logik.kalenderwoche
import de.gun.dashboard.reloaded.logik.lang
import de.gun.dashboard.reloaded.logik.kurz
import de.gun.dashboard.reloaded.logik.mehrarbeitSaldo
import de.gun.dashboard.reloaded.logik.mitVorzeichen
import de.gun.dashboard.reloaded.logik.parseDE
import de.gun.dashboard.reloaded.logik.termineAm
import de.gun.dashboard.reloaded.logik.urlaubStand
import de.gun.dashboard.reloaded.logik.zahl
import de.gun.dashboard.reloaded.logik.zwei
import de.gun.dashboard.reloaded.netz.Wetter
import de.gun.dashboard.reloaded.netz.WetterDienst
import de.gun.dashboard.reloaded.ui.Abstand
import de.gun.dashboard.reloaded.ui.Etikett
import de.gun.dashboard.reloaded.ui.Feld
import de.gun.dashboard.reloaded.ui.Fliesstext
import de.gun.dashboard.reloaded.ui.Hinweis
import de.gun.dashboard.reloaded.ui.Karte
import de.gun.dashboard.reloaded.ui.Kennzahl
import de.gun.dashboard.reloaded.ui.Knopf
import de.gun.dashboard.reloaded.ui.Knopfreihe
import de.gun.dashboard.reloaded.ui.Leer
import de.gun.dashboard.reloaded.ui.LocalPalette
import de.gun.dashboard.reloaded.ui.LocalSteuerung
import de.gun.dashboard.reloaded.ui.Mono
import de.gun.dashboard.reloaded.ui.Punkt
import de.gun.dashboard.reloaded.ui.PunktRaster
import de.gun.dashboard.reloaded.ui.RUND
import de.gun.dashboard.reloaded.ui.RUND_KLEIN
import de.gun.dashboard.reloaded.ui.Reiter
import de.gun.dashboard.reloaded.ui.Segmente
import de.gun.dashboard.reloaded.ui.Symbol
import de.gun.dashboard.reloaded.ui.Zeile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.coroutines.resume

@Composable
fun HeuteSeite() {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val b = rememberBestand()
    val d = b.daten
    var jetzt by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); jetzt = LocalDateTime.now() } }
    val heute = jetzt.toLocalDate()
    val termine = remember(b, heute) { termineAm(b, heute, false) }
    val urlaub = remember(d) { urlaubStand(d) }
    val saldo = remember(d) { mehrarbeitSaldo(d) }
    val (warn, ab) = remember(d) { fristenZaehlen(d) }

    SeitenListe {
        item {
            Box(Modifier.fillMaxWidth().clip(RUND).background(p.panel).border(1.dp, p.rand, RUND)) {
                if (p.punktRaster) PunktRaster(Modifier.matchParentSize(), p.randLeise, 11.dp)
                Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Etikett(heute.lang())
                    Punkt(zwei(jetzt.hour) + ":" + zwei(jetzt.minute), 52.sp)
                    Mono("KW " + kalenderwoche(heute), p.akzent, 13.sp, fett = true)
                    Feiertage.name(heute, d.feiertagsLand.land)?.let { Mono(it, p.textDim, 11.sp) }
                    Feiertage.ferien(heute, d.ferien)?.let { Mono(it, p.textDim, 11.sp) }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Kennzahl(zahl(urlaub.rest), "Resturlaub Tage",
                    if (urlaub.rest <= 0) p.rot else if (urlaub.rest <= 5) p.warn else p.text, Modifier.weight(1f)) { st.reiter = Reiter.URLAUB }
                Kennzahl(mitVorzeichen(saldo), "Mehrarbeit Std", if (saldo < 0) p.rot else p.text, Modifier.weight(1f)) { st.reiter = Reiter.URLAUB }
                Kennzahl(warn.toString() + if (ab > 0) "+$ab" else "", "Fristen",
                    if (ab > 0) p.rot else if (warn > 0) p.warn else p.gruen, Modifier.weight(1f)) { st.reiter = Reiter.LEHRGAENGE }
            }
        }
        item {
            Karte("Termine heute", "01", aktion = { Mono("${termine.size}", p.textDim) }) {
                if (termine.isEmpty()) Leer("Keine Termine heute.")
                termine.forEach { t -> TerminZeile(t, jetzt) { st.terminDetail = t } }
                Knopfreihe {
                    Knopf("+ Termin", klein = true) { st.maske = de.gun.dashboard.reloaded.ui.MaskeStart(datum = heute) }
                    Knopf("Kalender", klein = true) { st.reiter = Reiter.KALENDER; st.kalenderTag = heute }
                }
            }
        }
        item { HeuteAufgaben() }
        item { WetterKarte() }
    }
}

@Composable
fun TerminZeile(t: Termin, jetzt: LocalDateTime = LocalDateTime.now(), mitDatum: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    val laeuft = !t.ganztags && !t.start.isAfter(jetzt) && !(t.ende ?: t.start).isBefore(jetzt)
    Zeile(Color(t.farbe), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(54.dp)) {
                if (t.ganztags) Mono("GANZT.", p.textFaint, 10.sp, fett = true)
                else {
                    Mono(t.start.hhmm(), if (laeuft) p.akzent else p.text, 13.sp, fett = true)
                    t.ende?.let { Mono(it.hhmm(), p.textFaint, 11.sp) }
                }
            }
            Column(Modifier.weight(1f)) {
                Fliesstext(t.titel, fett = true, zeilen = 2)
                Fliesstext(
                    listOfNotNull(
                        if (mitDatum) t.start.toLocalDate().kurz() else null,
                        t.quelleName, t.ort.ifBlank { null }, if (laeuft) "läuft gerade" else null
                    ).joinToString(" · "),
                    p.textDim, 12.sp, zeilen = 1
                )
            }
        }
    }
}

@Composable
private fun HeuteAufgaben() {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val heute = LocalDate.now()
    val alle = aktuelleDaten().todos.eintraege
    val offen = alle.filter { !it.erledigt }
    val faellig = offen.filter { t -> parseDE(t.faellig)?.let { !it.isAfter(heute) } ?: false }
    val ohne = offen.filter { it.faellig.isBlank() }.take(3)
    Karte("Aufgaben", "02", aktion = { Mono(if (faellig.isNotEmpty()) "${faellig.size} fällig" else "${offen.size} offen", p.textDim) }) {
        if (faellig.isEmpty() && ohne.isEmpty()) Leer("Keine offenen Aufgaben für heute.")
        (faellig + ohne).forEach { t ->
            val f = parseDE(t.faellig)
            val ueber = f != null && f.isBefore(heute)
            Zeile(
                if (ueber) p.rot else Color(if (t.prio == "hoch") FARBE_TODO_HOCH else FARBE_TODO),
                onClick = { st.reiter = Reiter.TODO },
                aktionen = {
                    Symbol("✓", p.gruen) {
                        Speicher.aendern { a ->
                            a.copy(todos = a.todos.copy(eintraege = a.todos.eintraege.map {
                                if (it.id == t.id) it.copy(erledigt = true, erledigtAm = heuteDE()) else it
                            }))
                        }
                        st.kurz("Erledigt: " + t.text)
                    }
                }
            ) {
                Fliesstext(t.text, fett = true, zeilen = 2)
                Fliesstext(
                    (if (ueber) "Überfällig seit ${t.faellig}" else if (f != null) "heute fällig" else "ohne Termin") +
                        (if (t.uhrzeit.isNotBlank()) " · ${t.uhrzeit} Uhr" else "") +
                        (if (t.prio == "hoch") " · hohe Priorität" else ""),
                    if (ueber) p.rot else p.textDim, 12.sp
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Wetter

@Composable
private fun WetterKarte() {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dash = aktuelleDaten().dashboard
    var wetter by remember { mutableStateOf<Wetter?>(null) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var laedt by remember { mutableStateOf(false) }
    var einstellen by remember { mutableStateOf(false) }
    var neuZaehler by remember { mutableStateOf(0) }

    LaunchedEffect(dash.ort, dash.quelle, neuZaehler) {
        if (dash.quelle != "openmeteo") return@LaunchedEffect
        laedt = true; fehler = null
        try { wetter = WetterDienst.laden(dash.ort, neuZaehler > 0) } catch (e: Exception) { fehler = e.message ?: "Nicht erreichbar" }
        laedt = false
    }

    Karte("Wetter", "03", aktion = {
        Symbol("↻") { neuZaehler++ }
        Symbol("⚙") { einstellen = !einstellen }
    }) {
        if (einstellen) {
            WetterEinstellungen { einstellen = false }
            Abstand()
        }
        if (dash.quelle == "meteoblue" && dash.wetterUrl.isNotBlank()) {
            // Höhe an den tatsächlichen Inhalt anpassen, Hintergrund transparent – sonst bleibt unten ein weißer Rest
            var hoehe by remember(dash.wetterUrl) { mutableStateOf(500) }
            AndroidView(
                factory = { c ->
                    WebView(c).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        settings.javaScriptEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                view.evaluateJavascript(
                                    "(function(){document.documentElement.style.background='transparent';" +
                                        "document.body.style.background='transparent';document.body.style.margin='0';" +
                                        "var h=0;var k=document.body.children;for(var i=0;i<k.length;i++){var r=k[i].getBoundingClientRect();" +
                                        "h=Math.max(h,r.bottom+window.scrollY);}return Math.ceil(h||document.body.scrollHeight);})()"
                                ) { r -> r?.trim('"')?.toDoubleOrNull()?.let { if (it > 50) hoehe = it.toInt() } }
                            }
                        }
                        loadUrl(dash.wetterUrl)
                    }
                },
                update = { if (neuZaehler > 0) it.reload() },
                modifier = Modifier.fillMaxWidth().height(hoehe.dp).clip(RUND_KLEIN),
            )
            return@Karte
        }
        val w = wetter
        when {
            laedt && w == null -> Hinweis("Wetter wird geladen …")
            fehler != null && w == null -> { Hinweis("Das Wetter konnte nicht geladen werden: $fehler"); Knopf("Erneut versuchen", klein = true) { neuZaehler++ } }
            w != null -> WetterAnzeige(w)
        }
    }
}

/**
 * Wettersymbol als Emoji. Das Nebel-Emoji wird von vielen Schriften als eckiges Bild gezeichnet;
 * Nebel daher als Wolke mit gezeichneten Nebelstreifen.
 */
@Composable
private fun WetterSymbol(code: Int?, groesse: TextUnit, modifier: Modifier = Modifier) {
    val nebel = code == 45 || code == 48
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Box {
            Fliesstext(WetterDienst.zeichen(code), groesse = groesse)
            if (nebel) {
                val farbe = LocalPalette.current.textDim
                Canvas(Modifier.matchParentSize()) {
                    val dicke = size.height * 0.07f
                    listOf(0.72f, 0.86f).forEachIndexed { i, y ->
                        val einzug = size.width * (0.08f + i * 0.12f)
                        drawLine(farbe, Offset(einzug, size.height * y), Offset(size.width - einzug * 0.6f, size.height * y), dicke, StrokeCap.Round)
                    }
                }
            }
        }
    }
}

@Composable
private fun WetterAnzeige(w: Wetter) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        WetterSymbol(w.code, 40.sp)
        Spacer(Modifier.width(10.dp))
        Punkt((w.grad?.let { Math.round(it).toString() } ?: "—") + "°", 46.sp)
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Fliesstext(w.ort, fett = true, zeilen = 1)
            Fliesstext(WetterDienst.text(w.code), p.textDim, 13.sp)
            Mono(listOfNotNull(
                w.gefuehlt?.let { "gefühlt ${Math.round(it)}°" }, w.wind?.let { "Wind ${Math.round(it)} km/h" }, w.feuchte?.let { "${Math.round(it)} %" }
            ).joinToString(" · "), p.textFaint, 11.sp)
        }
    }
    Abstand(10.dp)
    // Nächste zwölf Stunden
    val jetzt = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)
    val start = w.stunden.indexOfFirst { runCatching { !LocalDateTime.parse(it.zeit).isBefore(jetzt) }.getOrDefault(false) }.coerceAtLeast(0)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        w.stunden.drop(start).take(12).forEachIndexed { i, s ->
            val zeit = runCatching { LocalDateTime.parse(s.zeit) }.getOrNull()
            Column(
                Modifier.clip(RUND_KLEIN).background(if (i == 0) p.akzentDim else p.panelAlt).padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Mono(zeit?.let { zwei(it.hour) } ?: "", p.textFaint, 10.sp)
                WetterSymbol(s.code, 18.sp)
                Mono((s.grad?.let { Math.round(it).toString() } ?: "—") + "°", p.text, 12.sp, fett = true)
                Mono(if ((s.regenWkt ?: 0) > 0) "${s.regenWkt}%" else if ((s.regenMm ?: 0.0) > 0) String.format("%.1f", s.regenMm) else " ", p.neutral, 9.sp)
            }
        }
    }
    Abstand(10.dp)
    w.tage.forEachIndexed { i, t ->
        val tag = runCatching { LocalDate.parse(t.datum) }.getOrNull()
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Mono(if (i == 0) "Heute" else tag?.let { de.gun.dashboard.reloaded.logik.WOCHENTAGE[it.dayOfWeek.value - 1] + " " + zwei(it.dayOfMonth) + "." } ?: "",
                p.text, 12.sp, Modifier.width(70.dp), fett = i == 0)
            WetterSymbol(t.code, 18.sp, Modifier.width(34.dp))
            Mono(if ((t.regenWkt ?: 0) > 0) "${t.regenWkt} %" else "", p.neutral, 11.sp, Modifier.weight(1f))
            Mono((t.max?.let { Math.round(it).toString() } ?: "—") + "° / " + (t.min?.let { Math.round(it).toString() } ?: "—") + "°", p.text, 12.sp)
        }
    }
    w.tage.firstOrNull()?.let { t ->
        val auf = t.aufgang?.let { runCatching { LocalDateTime.parse(it).hhmm() }.getOrNull() }
        val unter = t.untergang?.let { runCatching { LocalDateTime.parse(it).hhmm() }.getOrNull() }
        if (auf != null && unter != null) Mono("↑ $auf   ↓ $unter", p.textDim, 12.sp, Modifier.padding(top = 6.dp))
    }
    Hinweis("Daten: Open-Meteo · Modell automatisch")
}

@Composable
private fun WetterEinstellungen(fertig: () -> Unit) {
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dash = aktuelleDaten().dashboard
    var suche by remember { mutableStateOf("") }
    var treffer by remember { mutableStateOf<List<Ort>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var url by remember { mutableStateOf(dash.wetterUrl) }

    fun ortSetzen(o: Ort) {
        Speicher.aendern { it.copy(dashboard = it.dashboard.copy(ort = o, quelle = "openmeteo")) }
        treffer = emptyList(); suche = ""; fertig()
    }

    val standortRecht = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { erg ->
        if (erg.values.any { it }) scope.launch {
            status = "Standort wird bestimmt …"
            val ort = standortHolen(ctx)
            if (ort == null) { status = "Standort nicht verfügbar."; return@launch }
            val name = WetterDienst.ortsname(ort.first, ort.second)
                ?: ("Standort " + String.format("%.3f, %.3f", ort.first, ort.second))
            ortSetzen(Ort(name, ort.first, ort.second))
        } else status = "Die Standortberechtigung wurde nicht erteilt."
    }

    Column(Modifier.fillMaxWidth().clip(RUND_KLEIN).background(p.panelAlt).padding(12.dp)) {
        Segmente(listOf("openmeteo" to "Einfach", "meteoblue" to "meteoblue"), dash.quelle) { q ->
            Speicher.aendern { it.copy(dashboard = it.dashboard.copy(quelle = q)) }
        }
        Abstand(8.dp)
        if (dash.quelle == "openmeteo") {
            Hinweis("Aktuell: ${dash.ort.name} (${String.format("%.3f", dash.ort.breite)}, ${String.format("%.3f", dash.ort.laenge)})")
            Feld(suche, { suche = it }, "Ort suchen", platzhalter = "z. B. Ulm")
            Knopfreihe {
                Knopf("Suchen", klein = true) {
                    if (suche.isBlank()) return@Knopf
                    status = "Suche läuft …"
                    scope.launch {
                        treffer = try { WetterDienst.ortSuchen(suche.trim()) } catch (e: Exception) { status = "Die Suche ist nicht erreichbar."; emptyList() }
                        status = if (treffer.isEmpty()) "Kein Ort gefunden." else ""
                    }
                }
                Knopf("⌖ Mein Standort", klein = true) {
                    standortRecht.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                }
            }
            if (status.isNotEmpty()) Hinweis(status)
            treffer.forEach { o ->
                Fliesstext(o.name, modifier = Modifier.fillMaxWidth().clickable { ortSetzen(o) }.padding(vertical = 8.dp))
            }
        } else {
            Feld(url, { url = it }, "meteoblue-Widget-Adresse", platzhalter = "https://www.meteoblue.com/de/wetter/widget/…")
            Knopfreihe {
                Knopf("Übernehmen", klein = true) {
                    var w = url.trim()
                    Regex("src\\s*=\\s*[\"']([^\"']+)[\"']").find(w)?.let { w = it.groupValues[1] }
                    if (w.startsWith("//")) w = "https:$w"
                    if (!Regex("^https://(www\\.)?meteoblue\\.com/", RegexOption.IGNORE_CASE).containsMatchIn(w)) {
                        st.melden("Wetter", "Das sieht nicht nach einer meteoblue-Adresse aus.")
                    } else {
                        Speicher.aendern { it.copy(dashboard = it.dashboard.copy(wetterUrl = w, quelle = "meteoblue", wetterGeleert = false)) }
                        fertig()
                    }
                }
                Knopf("Leeren", klein = true) {
                    url = ""
                    Speicher.aendern { it.copy(dashboard = it.dashboard.copy(wetterUrl = "", quelle = "openmeteo", wetterGeleert = true)) }
                }
            }
        }
    }
}

/** Aktueller Standort (Breite, Länge) über den LocationManager. */
@SuppressLint("MissingPermission")
suspend fun standortHolen(ctx: Context): Pair<Double, Double>? {
    val fein = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val grob = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!fein && !grob) return null
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val anbieter = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
    if (Build.VERSION.SDK_INT >= 30) {
        for (a in anbieter.filter { it != LocationManager.PASSIVE_PROVIDER }) {
            val l: Location? = kotlinx.coroutines.withTimeoutOrNull(15_000) {
                suspendCancellableCoroutine { k ->
                    try {
                        lm.getCurrentLocation(a, null, ContextCompat.getMainExecutor(ctx)) { k.resume(it) }
                    } catch (e: Exception) { k.resume(null) }
                }
            }
            if (l != null) return l.latitude to l.longitude
        }
    }
    val letzte = anbieter.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    return letzte?.let { it.latitude to it.longitude }
}
