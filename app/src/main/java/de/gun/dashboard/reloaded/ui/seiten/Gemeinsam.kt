package de.gun.dashboard.reloaded.ui.seiten

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import de.gun.dashboard.reloaded.daten.Anhaenge
import de.gun.dashboard.reloaded.daten.Anhang
import de.gun.dashboard.reloaded.daten.Speicher
import de.gun.dashboard.reloaded.daten.groesseText
import de.gun.dashboard.reloaded.geraet.GeraeteKalender
import de.gun.dashboard.reloaded.geraet.Kontakte
import de.gun.dashboard.reloaded.logik.TerminBestand
import de.gun.dashboard.reloaded.ui.Fliesstext
import de.gun.dashboard.reloaded.ui.Knopf
import de.gun.dashboard.reloaded.ui.KnopfArt
import de.gun.dashboard.reloaded.ui.LocalPalette
import de.gun.dashboard.reloaded.ui.LocalSteuerung
import de.gun.dashboard.reloaded.ui.Mono
import de.gun.dashboard.reloaded.ui.RUND_KLEIN
import de.gun.dashboard.reloaded.ui.Symbol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Aktueller Datenbestand als beobachteter Zustand. */
@Composable
fun aktuelleDaten(): de.gun.dashboard.reloaded.daten.AppDaten {
    val d by Speicher.daten.collectAsState()
    return d
}

@Composable
fun rememberBestand(): TerminBestand {
    val d by Speicher.daten.collectAsState()
    val k by GeraeteKalender.kalender.collectAsState()
    val t by GeraeteKalender.termine.collectAsState()
    val a by Kontakte.anlaesse.collectAsState()
    return remember(d, k, t, a) { TerminBestand(d, k, t, a) }
}

/** Standard-Seite: senkrechte Liste mit Abständen. */
@Composable
fun SeitenListe(zustand: LazyListState = rememberLazyListState(), inhalt: LazyListScope.() -> Unit) {
    LazyColumn(
        state = zustand,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = inhalt,
    )
}

/** Anhänge eines Formulars: hinzufügen, öffnen, entfernen. */
@Composable
fun AnhangBereich(liste: List<Anhang>, onListe: (List<Anhang>) -> Unit, nurBilderUndPdf: Boolean = false) {
    val ctx = LocalContext.current
    val st = LocalSteuerung.current
    val scope = rememberCoroutineScope()
    val waehlen = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            try {
                onListe(liste + Anhaenge.uebernehmen(ctx, uri))
            } catch (e: Exception) {
                st.melden("Anhang", e.message ?: "Die Datei konnte nicht übernommen werden.")
            }
        }
    }
    Column {
        AnhangVorschau(liste, onEntfernen = { a -> onListe(liste - a) })
        Knopf("+ Datei / Foto", klein = true) {
            waehlen.launch(if (nurBilderUndPdf) arrayOf("image/*", "application/pdf") else arrayOf("*/*"))
        }
    }
}

/** Vorschaubilder bzw. Dateikacheln; Antippen öffnet die Datei. */
@Composable
fun AnhangVorschau(liste: List<Anhang>, onEntfernen: ((Anhang) -> Unit)? = null, klein: Boolean = false) {
    if (liste.isEmpty()) return
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val st = LocalSteuerung.current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        liste.forEach { a ->
            Box(
                Modifier.size(if (klein) 52.dp else 84.dp).clip(RUND_KLEIN).background(p.panelAlt).border(1.dp, p.rand, RUND_KLEIN)
                    .clickable { if (!Anhaenge.oeffnen(ctx, a)) st.melden("Anhang", "Die Datei ließ sich nicht öffnen.") }
            ) {
                var bild by remember(a) { mutableStateOf<ImageBitmap?>(null) }
                if (a.typ.startsWith("image/")) {
                    LaunchedEffect(a) {
                        bild = withContext(Dispatchers.IO) {
                            try {
                                val bytes = Anhaenge.bytes(ctx, a) ?: return@withContext null
                                val opt = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opt)?.asImageBitmap()
                            } catch (e: Throwable) { null }
                        }
                    }
                }
                val b = bild
                if (b != null) Image(b, a.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Column(Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Mono(if (a.typ == "application/pdf") "PDF" else "DATEI", p.akzent, 13.sp, fett = true)
                    if (!klein) {
                        Fliesstext(a.name, p.textDim, 10.sp, zeilen = 2)
                        Mono(groesseText(a.groesse), p.textFaint, 9.sp)
                    }
                }
                if (onEntfernen != null) {
                    Box(Modifier.align(Alignment.TopEnd).padding(2.dp).clip(RUND_KLEIN).background(p.bg.copy(alpha = 0.7f))) {
                        Symbol("✕", p.rot) { onEntfernen(a) }
                    }
                }
            }
        }
    }
}

/** Text als Datei ausgeben und über das Teilen-Menü anbieten. */
fun dateiTeilen(ctx: Context, text: String, name: String, typ: String) {
    val ordner = File(ctx.cacheDir, "ausgabe").apply { mkdirs() }
    val f = File(ordner, name)
    f.writeText(text, Charsets.UTF_8)
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".dateien", f)
    val i = Intent(Intent.ACTION_SEND).setType(typ).putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(i, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** HTML über das Druckmodul von Android ausgeben (inkl. „Als PDF speichern"). */
fun htmlDrucken(ctx: Context, html: String, titel: String) {
    val web = WebView(ctx)
    web.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            val pm = ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager
            pm.print(titel, view.createPrintDocumentAdapter(titel), PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
        }
    }
    web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    gehalteneWebViews += web
}

/** WebViews müssen bis zum Druck erhalten bleiben. */
private val gehalteneWebViews = mutableListOf<WebView>()

@Composable
fun FussAbstand() = Box(Modifier.navigationBarsPadding())

@Composable
fun LoeschKnopf(onClick: () -> Unit) = Symbol("✕", LocalPalette.current.rot, onClick)

@Composable
fun BearbeitenKnopf(onClick: () -> Unit) = Symbol("✎", LocalPalette.current.textDim, onClick)

@Composable
fun FormKnoepfe(speichernText: String, onSpeichern: () -> Unit, onAbbrechen: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        Knopf("Abbrechen", art = KnopfArt.NORMAL, klein = true, onClick = onAbbrechen)
        Knopf(speichernText, art = KnopfArt.PRIMAER, klein = true, onClick = onSpeichern)
    }
}
