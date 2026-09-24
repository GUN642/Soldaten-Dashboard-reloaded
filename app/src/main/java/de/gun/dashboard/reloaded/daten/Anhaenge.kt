package de.gun.dashboard.reloaded.daten

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Anhänge liegen als echte Dateien unter files/anhaenge. Bilder werden beim
 * Übernehmen auf eine gut lesbare, sparsame Größe gerechnet (längste Kante
 * 1600 px, JPEG).
 */
object Anhaenge {
    private const val ORDNER = "anhaenge"
    private const val KANTE = 1600
    const val MAX_BYTES = 15L * 1024 * 1024

    private fun ordner(ctx: Context) = File(ctx.filesDir, ORDNER).apply { mkdirs() }

    fun datei(ctx: Context, a: Anhang): File? {
        val name = a.dateiname ?: return null
        return File(ctx.filesDir, name).takeIf { it.exists() }
    }

    private fun endung(typ: String) = when {
        typ.startsWith("image/") -> "jpg"
        typ == "application/pdf" -> "pdf"
        else -> "bin"
    }

    private fun neuerName(typ: String) =
        ORDNER + "/" + System.currentTimeMillis() + "_" + (1000..9999).random() + "." + endung(typ)

    /** Übernimmt eine vom Nutzer gewählte Datei. */
    suspend fun uebernehmen(ctx: Context, uri: Uri): Anhang = withContext(Dispatchers.IO) {
        val cr = ctx.contentResolver
        var name = "Datei"
        var groesse = -1L
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) groesse = c.getLong(1)
            }
        }
        val typ = cr.getType(uri) ?: "application/octet-stream"
        ordner(ctx)
        if (typ.startsWith("image/")) {
            val roh = cr.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("Datei nicht lesbar.")
            val bytes = bildVerkleinern(roh) ?: roh
            val ziel = neuerName("image/jpeg")
            File(ctx.filesDir, ziel).writeBytes(bytes)
            return@withContext Anhang(name = name, groesse = bytes.size.toDouble(), typ = "image/jpeg", dateiname = ziel)
        }
        if (groesse > MAX_BYTES) throw IllegalStateException("Die Datei ist zu groß (höchstens 15 MB).")
        val ziel = neuerName(typ)
        val f = File(ctx.filesDir, ziel)
        cr.openInputStream(uri)?.use { ein -> f.outputStream().use { ein.copyTo(it) } }
            ?: throw IllegalStateException("Datei nicht lesbar.")
        Anhang(name = name, groesse = f.length().toDouble(), typ = typ, dateiname = ziel)
    }

    private fun bildVerkleinern(roh: ByteArray): ByteArray? = try {
        val grenzen = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(roh, 0, roh.size, grenzen)
        var probe = 1
        while (maxOf(grenzen.outWidth, grenzen.outHeight) / (probe * 2) >= KANTE) probe *= 2
        val bild = BitmapFactory.decodeByteArray(roh, 0, roh.size, BitmapFactory.Options().apply { inSampleSize = probe })
        val faktor = minOf(1.0, KANTE.toDouble() / maxOf(bild.width, bild.height))
        val b = maxOf(1, (bild.width * faktor).toInt())
        val h = maxOf(1, (bild.height * faktor).toInt())
        val ziel = Bitmap.createBitmap(b, h, Bitmap.Config.ARGB_8888)
        Canvas(ziel).apply {
            drawColor(Color.WHITE)
            drawBitmap(Bitmap.createScaledBitmap(bild, b, h, true), 0f, 0f, null)
        }
        ByteArrayOutputStream().also { ziel.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
    } catch (e: Throwable) {
        null
    }

    fun loeschen(ctx: Context, liste: List<Anhang>) {
        liste.forEach { a -> a.dateiname?.let { File(ctx.filesDir, it).delete() } }
    }

    /** Datei, die sich öffnen oder teilen lässt (auch für alte Anhänge mit "daten"). */
    fun lesbareDatei(ctx: Context, a: Anhang): File? {
        datei(ctx, a)?.let { return it }
        val daten = a.daten ?: return null
        val nutzlast = daten.substringAfter(",", daten)
        val bytes = try { Base64.decode(nutzlast, Base64.DEFAULT) } catch (e: Exception) { return null }
        val aus = File(ctx.cacheDir, "ausgabe").apply { mkdirs() }
        val f = File(aus, a.name.ifBlank { "anhang" }.replace(Regex("[^\\wäöüÄÖÜß.\\- ]"), "_"))
        f.writeBytes(bytes)
        return f
    }

    fun bytes(ctx: Context, a: Anhang): ByteArray? = lesbareDatei(ctx, a)?.readBytes()

    fun oeffnen(ctx: Context, a: Anhang): Boolean {
        val f = lesbareDatei(ctx, a) ?: return false
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".dateien", ausgabeKopie(ctx, f, a.name))
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, a.typ.ifBlank { "*/*" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(Intent.createChooser(i, a.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        } catch (e: Exception) { false }
    }

    /** Kopie im Ausgabeordner mit sprechendem Dateinamen. */
    private fun ausgabeKopie(ctx: Context, f: File, name: String): File {
        val aus = File(ctx.cacheDir, "ausgabe").apply { mkdirs() }
        if (f.parentFile == aus) return f
        val ziel = File(aus, name.ifBlank { f.name }.replace(Regex("[^\\wäöüÄÖÜß.\\- ]"), "_"))
        f.copyTo(ziel, overwrite = true)
        return ziel
    }

    /** Für die Sicherungsdatei: Inhalt als data:-URL einbetten. */
    fun fuerExport(ctx: Context, liste: List<Anhang>): List<Anhang> = liste.map { a ->
        if (a.daten != null) a.copy(dateiname = null)
        else {
            val b = datei(ctx, a)?.readBytes()
            if (b == null) a.copy(dateiname = null)
            else a.copy(daten = "data:" + a.typ.ifBlank { "application/octet-stream" } + ";base64," +
                Base64.encodeToString(b, Base64.NO_WRAP), dateiname = null)
        }
    }

    /** Beim Import eingebettete Inhalte wieder als Datei ablegen. */
    fun ausImport(ctx: Context, liste: List<Anhang>): List<Anhang> = liste.map { a ->
        val daten = a.daten ?: return@map a.copy(dateiname = null)
        try {
            val bytes = Base64.decode(daten.substringAfter(",", daten), Base64.DEFAULT)
            ordner(ctx)
            val ziel = neuerName(a.typ)
            File(ctx.filesDir, ziel).writeBytes(bytes)
            a.copy(daten = null, dateiname = ziel, groesse = bytes.size.toDouble())
        } catch (e: Exception) {
            a
        }
    }

    /** Dateien ohne Verweis entfernen (etwa nach abgebrochener Eingabe). */
    fun verwaisteAufraeumen(ctx: Context, d: AppDaten) {
        val benutzt = HashSet<String>()
        (d.dokumente.flatMap { it.dateien } + d.todos.eintraege.flatMap { it.anhaenge } +
            d.kalender.eigene.flatMap { it.anhaenge }).forEach { a -> a.dateiname?.let { benutzt += it.substringAfterLast('/') } }
        ordner(ctx).listFiles()?.forEach { if (it.name !in benutzt) it.delete() }
    }
}

fun groesseText(bytes: Double): String = when {
    bytes < 1024 -> "${bytes.toLong()} B"
    bytes < 1024 * 1024 -> "${(bytes / 1024).toLong()} KB"
    else -> String.format(java.util.Locale.GERMANY, "%.1f MB", bytes / 1024 / 1024)
}
