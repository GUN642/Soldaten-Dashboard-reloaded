package de.gun.dashboard.reloaded.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

/**
 * Widgets können keine Webseiten zeigen. Die meteoblue-Seite wird deshalb unsichtbar geladen
 * und als Bild aufgenommen; das Widget zeigt das zuletzt aufgenommene Bild.
 */
object MeteoblueBild {
    private const val DATEI = "meteoblue_widget.png"
    private const val ALTER_MAX = 30 * 60_000L

    private fun datei(ctx: Context) = File(ctx.filesDir, DATEI)
    private fun urlDatei(ctx: Context) = File(ctx.filesDir, "$DATEI.url")

    /** Zuletzt aufgenommenes Bild samt Zeitpunkt – nur, wenn es zur eingestellten Adresse gehört. */
    fun gespeichert(ctx: Context, url: String): Pair<Bitmap, Long>? {
        val f = datei(ctx)
        if (!f.exists() || runCatching { urlDatei(ctx).readText() }.getOrNull() != url) return null
        val b = runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull() ?: return null
        return b to f.lastModified()
    }

    /** Neu aufnehmen, wenn das Bild fehlt oder älter als 30 Minuten ist. */
    suspend fun aktualisieren(ctx: Context, url: String) {
        val alt = gespeichert(ctx, url)
        if (alt != null && System.currentTimeMillis() - alt.second < ALTER_MAX) return
        val bild = aufnehmen(ctx.applicationContext, url) ?: return
        runCatching {
            datei(ctx).outputStream().use { bild.compress(Bitmap.CompressFormat.PNG, 100, it) }
            urlDatei(ctx).writeText(url)
        }
        bild.recycle()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun aufnehmen(ctx: Context, url: String): Bitmap? = withTimeoutOrNull(30_000) {
        suspendCancellableCoroutine { cont ->
            val haupt = Handler(Looper.getMainLooper())
            haupt.post {
                val dichte = ctx.resources.displayMetrics.density
                // 380 CSS-Pixel Breite: das meteoblue-Widget ist auf etwa diese Breite ausgelegt
                val breite = (380 * dichte).toInt().coerceAtMost(1200)
                val web = WebView(ctx)
                fun fertig(b: Bitmap?) {
                    if (cont.isActive) cont.resume(b)
                    web.destroy()
                }
                cont.invokeOnCancellation { haupt.post { runCatching { web.destroy() } } }
                web.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                web.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                web.settings.javaScriptEnabled = true
                fun anordnen(h: Int) {
                    web.measure(View.MeasureSpec.makeMeasureSpec(breite, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                    web.layout(0, 0, breite, h)
                }
                anordnen((600 * dichte).toInt())
                web.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, adresse: String?) {
                        // Wie in der App: transparenter Hintergrund, Höhe = tatsächlicher Inhalt
                        view.evaluateJavascript(
                            "(function(){document.documentElement.style.background='transparent';" +
                                "document.body.style.background='transparent';document.body.style.margin='0';" +
                                "var h=0;var k=document.body.children;for(var i=0;i<k.length;i++){var r=k[i].getBoundingClientRect();" +
                                "h=Math.max(h,r.bottom+window.scrollY);}return Math.ceil(h||document.body.scrollHeight);})()"
                        ) { r ->
                            val css = r?.trim('"')?.toDoubleOrNull()?.takeIf { it > 50 } ?: 500.0
                            val hoehe = (css * dichte).toInt().coerceIn(100, 2000)
                            anordnen(hoehe)
                            // Bilder und Schriften nachzeichnen lassen
                            haupt.postDelayed({
                                val b = runCatching {
                                    Bitmap.createBitmap(breite, hoehe, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
                                }.getOrNull()
                                fertig(b)
                            }, 2500)
                        }
                    }
                }
                web.loadUrl(url)
            }
        }
    }
}
