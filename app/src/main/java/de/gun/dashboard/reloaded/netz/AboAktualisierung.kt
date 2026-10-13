package de.gun.dashboard.reloaded.netz

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.gun.dashboard.reloaded.daten.Speicher
import de.gun.dashboard.reloaded.logik.heuteDE
import de.gun.dashboard.reloaded.logik.icsLesen
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/** Abonnierte Kalenderquellen (ICS-Links) einmal täglich neu laden. */
object AboAktualisierung {
    private const val ARBEIT = "kalender-abos"
    private val sperre = Mutex()

    /** Tägliche Hintergrundaktualisierung einplanen (nur mit Netz). */
    fun planen(ctx: Context) {
        val auftrag = PeriodicWorkRequestBuilder<AboArbeit>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(ARBEIT, ExistingPeriodicWorkPolicy.KEEP, auftrag)
    }

    /**
     * Lädt alle Abos mit Link, die heute noch nicht aktualisiert wurden ([alle] = true: unabhängig davon).
     * Liefert (aktualisiert, fehlgeschlagen). Eine fehlerhafte Quelle behält ihre bisherigen Termine.
     */
    suspend fun aktualisieren(alle: Boolean = false): Pair<Int, Int> = sperre.withLock {
        val heute = heuteDE()
        val faellig = Speicher.aktuell.kalender.quellen.filter { it.url.isNotBlank() && (alle || it.aktualisiert != heute) }
        var ok = 0
        var fehler = 0
        for (q in faellig) {
            try {
                val ev = icsLesen(Netz.text(q.url.replace(Regex("^webcal://", RegexOption.IGNORE_CASE), "https://")))
                Speicher.aendern { a ->
                    a.copy(kalender = a.kalender.copy(quellen = a.kalender.quellen.map {
                        if (it.id == q.id) it.copy(events = ev, aktualisiert = heute) else it
                    }))
                }
                ok++
            } catch (e: Exception) {
                fehler++
            }
        }
        ok to fehler
    }
}

class AboArbeit(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Speicher.init(applicationContext)
        val (_, fehler) = AboAktualisierung.aktualisieren()
        // Bei Netzproblemen später erneut versuchen
        return if (fehler > 0) Result.retry() else Result.success()
    }
}
