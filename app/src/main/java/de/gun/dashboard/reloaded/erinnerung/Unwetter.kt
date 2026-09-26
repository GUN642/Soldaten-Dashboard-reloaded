package de.gun.dashboard.reloaded.erinnerung

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.gun.dashboard.reloaded.MainActivity
import de.gun.dashboard.reloaded.R
import de.gun.dashboard.reloaded.daten.Speicher
import de.gun.dashboard.reloaded.netz.DwdWarnDienst
import de.gun.dashboard.reloaded.netz.DwdWarnung
import java.util.concurrent.TimeUnit

/** Prüft regelmäßig im Hintergrund auf neue DWD-Unwetterwarnungen und meldet sie. */
object Unwetter {
    private const val KANAL = "unwetter"
    private const val ARBEIT = "dwd-warnungen"

    fun kanalAnlegen(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(KANAL, "Unwetterwarnungen", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Amtliche Warnungen des Deutschen Wetterdienstes für den eingestellten Ort"
                }
            )
        }
    }

    /** Hintergrundprüfung alle 30 Minuten ein- bzw. ausschalten – je nach Einstellung. */
    fun planen(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        val d = Speicher.aktuell.dashboard
        if (d.dwdWarnungen && d.dwdPush) {
            val auftrag = PeriodicWorkRequestBuilder<UnwetterArbeit>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(ARBEIT, ExistingPeriodicWorkPolicy.KEEP, auftrag)
        } else wm.cancelUniqueWork(ARBEIT)
    }

    /** Meldet alle meldenswerten Warnungen, die noch nicht gemeldet wurden. */
    fun neueMelden(ctx: Context, liste: List<DwdWarnung>) {
        val d = Speicher.aktuell.dashboard
        if (!d.dwdWarnungen || !d.dwdPush) return
        val prefs = ctx.getSharedPreferences("dwd", Context.MODE_PRIVATE)
        val gemeldet = prefs.getStringSet("gemeldet", emptySet()).orEmpty().toMutableSet()
        var neu = false
        for (w in liste.filter { DwdWarnDienst.meldenswert(it) }) {
            val schluessel = w.id.ifBlank { w.ueberschrift + w.beginn }
            if (schluessel in gemeldet) continue
            zeigen(ctx, w)
            gemeldet += schluessel; neu = true
        }
        if (neu) {
            // nur die aktuellen Kennungen behalten, damit die Liste nicht wächst
            val aktuelle = liste.map { it.id.ifBlank { it.ueberschrift + it.beginn } }.toSet()
            prefs.edit().putStringSet("gemeldet", gemeldet.filter { it in aktuelle }.toSet()).apply()
        }
    }

    private fun zeigen(ctx: Context, w: DwdWarnung) {
        if (!Erinnerungen.darfBenachrichtigen(ctx)) return
        val id = Erinnerungen.kennung("dwd:" + w.id)
        val oeffnen = PendingIntent.getActivity(
            ctx, id, Intent(ctx, MainActivity::class.java).putExtra("ziel", "heute")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = listOf(w.zeitraum(), w.beschreibung).filter { it.isNotBlank() }.joinToString("\n")
        val n = NotificationCompat.Builder(ctx, KANAL)
            .setSmallIcon(R.drawable.ic_benachrichtigung)
            .setContentTitle("⚠ " + w.ueberschrift.ifBlank { w.stufeText + ": " + w.ereignis })
            .setContentText(w.zeitraum())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(DwdWarnDienst.farbe(w.stufe).toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(oeffnen)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (e: SecurityException) { }
    }
}

class UnwetterArbeit(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val d = Speicher.aktuell.dashboard
        if (!d.dwdWarnungen || !d.dwdPush) return Result.success()
        return try {
            Unwetter.neueMelden(applicationContext, DwdWarnDienst.laden(d.ort, neu = true))
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
