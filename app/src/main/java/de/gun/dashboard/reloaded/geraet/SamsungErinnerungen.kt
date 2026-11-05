package de.gun.dashboard.reloaded.geraet

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Prüft, ob die Samsung-Erinnerungen-App ihre Daten für andere Apps herausgibt.
 * Samsung dokumentiert keine Schnittstelle; die Prüfung liest nur Aufbau und Anzahl, keine Inhalte.
 */
object SamsungErinnerungen {
    const val PAKET = "com.samsung.android.app.reminder"
    const val RECHT = "com.samsung.android.app.reminder.permission.READ"
    private val PFADE = listOf("", "/reminder", "/reminders", "/item", "/items", "/card", "/cards", "/todo", "/tasks", "/alarm")

    fun installiert(ctx: Context): Boolean =
        runCatching { ctx.packageManager.getPackageInfo(PAKET, 0) }.isSuccess

    /** Muss das Leserecht zur Laufzeit angefragt werden (Schutzstufe „gefährlich“, noch nicht erteilt)? */
    fun mussAnfragen(ctx: Context): Boolean {
        if (hatRecht(ctx)) return false
        return runCatching { stufe(ctx.packageManager.getPermissionInfo(RECHT, 0)) == PermissionInfo.PROTECTION_DANGEROUS }.getOrDefault(false)
    }

    fun hatRecht(ctx: Context) = ContextCompat.checkSelfPermission(ctx, RECHT) == PackageManager.PERMISSION_GRANTED

    private fun stufe(p: PermissionInfo): Int =
        if (Build.VERSION.SDK_INT >= 28) p.protection else p.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE

    private fun stufeName(p: PermissionInfo) = when (stufe(p)) {
        PermissionInfo.PROTECTION_NORMAL -> "normal"
        PermissionInfo.PROTECTION_DANGEROUS -> "gefährlich (Anfrage)"
        PermissionInfo.PROTECTION_SIGNATURE -> "nur Samsung (Signatur)"
        else -> "intern (${stufe(p)})"
    }

    /** Ergebnis als Text zum Kopieren. Läuft blockierend – nicht im Hauptthread aufrufen. */
    fun pruefen(ctx: Context): String {
        val sb = StringBuilder()
        val pm = ctx.packageManager
        sb.appendLine("Gerät: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        val info = try {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(PAKET, PackageManager.GET_PROVIDERS or PackageManager.GET_PERMISSIONS)
        } catch (e: Exception) {
            sb.appendLine("Samsung Erinnerungen: nicht installiert oder nicht sichtbar.")
            sb.appendLine()
            samsungKalender(ctx, sb)
            return sb.toString().trim()
        }
        sb.appendLine("Samsung Erinnerungen: Version ${info.versionName}")
        sb.appendLine("Leserecht erteilt: " + if (hatRecht(ctx)) "ja" else "nein")
        info.permissions?.forEach { sb.appendLine("Recht ${it.name.removePrefix("$PAKET.permission.")}: ${stufeName(it)}") }
        sb.appendLine()
        val provider = info.providers.orEmpty()
        if (provider.isEmpty()) sb.appendLine("Keine Datenquellen (Provider) gemeldet.")
        provider.forEach { pi ->
            sb.appendLine("Provider ${pi.authority}")
            sb.appendLine("  exportiert: ${if (pi.exported) "ja" else "nein"} · Lesen: ${pi.readPermission ?: pi.permission ?: "frei"}")
            if (pi.exported) pi.authority.split(";").forEach { a -> versuchen(ctx, a, sb) }
        }
        sb.appendLine()
        samsungKalender(ctx, sb)
        return sb.toString().trim()
    }

    private fun versuchen(ctx: Context, authority: String, sb: StringBuilder) {
        var ohneZugriff = 0
        for (pfad in PFADE) {
            val r = abfragen(ctx, Uri.parse("content://$authority$pfad"))
            if (r.startsWith("kein Zugriff")) { ohneZugriff++; if (ohneZugriff > 1) continue }
            sb.appendLine("  ${pfad.ifEmpty { "/" }}: $r")
        }
    }

    private fun abfragen(ctx: Context, uri: Uri): String = try {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            "OK · ${c.count} Einträge · Spalten: " + c.columnNames.take(40).joinToString(", ")
        } ?: "keine Antwort"
    } catch (e: SecurityException) {
        "kein Zugriff"
    } catch (e: Exception) {
        e.javaClass.simpleName + ": " + (e.message ?: "").take(100)
    }

    /** Samsung Kalender führt eigene Aufgaben im Kalenderspeicher. */
    private fun samsungKalender(ctx: Context, sb: StringBuilder) {
        sb.appendLine("Kalender-Aufgaben (Samsung Kalender):")
        sb.appendLine("  " + abfragen(ctx, Uri.parse("content://com.android.calendar/syncTasks")))
    }
}
