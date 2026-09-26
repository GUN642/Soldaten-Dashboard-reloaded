package de.gun.dashboard.reloaded

import android.app.Application
import android.content.Context
import de.gun.dashboard.reloaded.daten.Speicher
import de.gun.dashboard.reloaded.erinnerung.Erinnerungen
import de.gun.dashboard.reloaded.geraet.GeraeteKalender
import de.gun.dashboard.reloaded.geraet.Kontakte
import de.gun.dashboard.reloaded.logik.TerminBestand
import de.gun.dashboard.reloaded.logik.jahreswechsel
import de.gun.dashboard.reloaded.widget.AgendaWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Speicher.init(this)
        Erinnerungen.kanalAnlegen(this)
        de.gun.dashboard.reloaded.erinnerung.Unwetter.kanalAnlegen(this)
        de.gun.dashboard.reloaded.erinnerung.Unwetter.planen(this)
        Speicher.beiAenderung = { Aktualisierung.nachAenderung(this) }
        // Automatischer Urlaubszugang zum Jahreswechsel
        if (jahreswechsel(Speicher.aktuell) != Speicher.aktuell) Speicher.aendern { jahreswechsel(it) }
    }
}

/** Hält Gerätedaten, Widget und Erinnerungen auf dem aktuellen Stand. */
object Aktualisierung {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var folgeJob: Job? = null
    private var ladeJob: Job? = null

    fun bestand(): TerminBestand = TerminBestand(
        Speicher.aktuell, GeraeteKalender.kalender.value, GeraeteKalender.termine.value, Kontakte.anlaesse.value
    )

    /** Nach einer Datenänderung: Widget und Erinnerungen gebündelt neu. */
    fun nachAenderung(ctx: Context) {
        val app = ctx.applicationContext
        folgeJob?.cancel()
        folgeJob = scope.launch {
            delay(1200)
            folgeArbeiten(app)
        }
    }

    private suspend fun folgeArbeiten(app: Context) {
        try { AgendaWidget.aktualisieren(app) } catch (e: Exception) { }
        try { Erinnerungen.planen(app) } catch (e: Exception) { }
    }

    /** Gerätekalender und Kontakte neu einlesen (bei Start und Rückkehr in die App). */
    fun geraetLaden(ctx: Context, danach: (() -> Unit)? = null) {
        val app = ctx.applicationContext
        if (ladeJob?.isActive == true) return
        ladeJob = scope.launch {
            GeraeteKalender.einlesen(app)
            if (Speicher.aktuell.nativ.kontaktdaten) Kontakte.einlesen(app)
            folgeArbeiten(app)
            danach?.invoke()
        }
    }

    suspend fun geraetLadenJetzt(ctx: Context) {
        val app = ctx.applicationContext
        GeraeteKalender.einlesen(app)
        if (Speicher.aktuell.nativ.kontaktdaten) Kontakte.einlesen(app)
        folgeArbeiten(app)
    }
}
