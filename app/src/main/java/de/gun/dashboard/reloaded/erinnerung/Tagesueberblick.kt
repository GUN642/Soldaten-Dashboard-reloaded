package de.gun.dashboard.reloaded.erinnerung

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import de.gun.dashboard.reloaded.MainActivity
import de.gun.dashboard.reloaded.R
import de.gun.dashboard.reloaded.daten.Speicher
import de.gun.dashboard.reloaded.geraet.GeraeteKalender
import de.gun.dashboard.reloaded.geraet.Kontakte
import de.gun.dashboard.reloaded.logik.TerminBestand
import de.gun.dashboard.reloaded.logik.WOCHENTAGE
import de.gun.dashboard.reloaded.logik.fristenZaehlen
import de.gun.dashboard.reloaded.logik.hhmm
import de.gun.dashboard.reloaded.logik.naechsterUeberblick
import de.gun.dashboard.reloaded.logik.parseDE
import de.gun.dashboard.reloaded.logik.termineAm
import de.gun.dashboard.reloaded.netz.DwdWarnDienst
import de.gun.dashboard.reloaded.netz.Wetter
import de.gun.dashboard.reloaded.netz.WetterDienst
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

/** Morgendlicher Überblick: Wetter, Termine, Aufgaben und Fristen des Tages als eine Benachrichtigung. */
object Tagesueberblick {
    private const val KANAL = "tagesueberblick"
    private const val PREFS = "tagesueberblick"
    private const val ID = 4711
    const val AKTION = "de.gun.dashboard.reloaded.TAGESUEBERBLICK"

    fun kanalAnlegen(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(KANAL, "Tagesüberblick", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Morgens: Wetter, Termine und Aufgaben des Tages"
                }
            )
        }
    }

    private fun zuletzt(ctx: Context): LocalDate? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("tag", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Nächste Weckzeit des Handy-Weckers (falls die Wecker-App sie an Android meldet). */
    fun weckzeit(ctx: Context): LocalDateTime? {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return null
        val info = am.nextAlarmClock ?: return null
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(info.triggerTime), ZoneId.systemDefault())
    }

    private fun absicht(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(ctx, ID, Intent(ctx, TagesueberblickEmpfaenger::class.java).setAction(AKTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Plant den nächsten Überblick neu; liefert den Zeitpunkt (oder null, wenn aus). */
    fun planen(ctx: Context): LocalDateTime? {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return null
        am.cancel(absicht(ctx))
        val zeit = naechsterUeberblick(LocalDateTime.now(), Speicher.aktuell.tagesueberblick, weckzeit(ctx), zuletzt(ctx)) ?: return null
        val ms = zeit.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, absicht(ctx))
        return zeit
    }

    /** Inhalt zusammenstellen und anzeigen. [test] = über „Jetzt anzeigen“ ausgelöst (zählt nicht als gezeigt). */
    suspend fun zeigen(ctx: Context, test: Boolean = false) {
        val e = Speicher.aktuell.tagesueberblick
        val d = Speicher.aktuell
        val heute = LocalDate.now()
        if (GeraeteKalender.darfLesen(ctx)) {
            try { GeraeteKalender.einlesen(ctx) } catch (x: Exception) { }
            if (d.nativ.kontaktdaten) try { Kontakte.einlesen(ctx) } catch (x: Exception) { }
        }
        val b = TerminBestand(Speicher.aktuell, GeraeteKalender.kalender.value, GeraeteKalender.termine.value, Kontakte.anlaesse.value)

        val zeilen = mutableListOf<String>()
        val kurz = mutableListOf<String>()

        // Wetter und Warnung (mit Zeitlimit – ohne Netz kommt der Überblick trotzdem)
        if (e.wetter) {
            if (d.dashboard.dwdWarnungen) {
                val w = withTimeoutOrNull(8_000) { runCatching { DwdWarnDienst.laden(d.dashboard.ort, true) }.getOrNull() }.orEmpty().firstOrNull()
                if (w != null) zeilen += "⚠ " + w.ueberschrift.ifBlank { w.stufeText + ": " + w.ereignis } + (w.zeitraum().takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
            }
            val wetter = withTimeoutOrNull(8_000) { runCatching { WetterDienst.laden(d.dashboard.ort, true) }.getOrNull() }
            if (wetter != null) {
                val t = wetter.tage.firstOrNull()
                val jetzt = wetter.grad?.roundToInt()?.let { "$it°" } ?: "–"
                kurz += WetterDienst.zeichen(wetter.code) + " " + jetzt
                zeilen += "Wetter ${wetter.ort}: ${WetterDienst.zeichen(wetter.code)} $jetzt · " +
                    listOfNotNull(
                        t?.max?.let { "max ${it.roundToInt()}°" }, t?.min?.let { "min ${it.roundToInt()}°" },
                        t?.regenWkt?.takeIf { it > 0 }?.let { "Regen $it %" },
                    ).joinToString(" · ")
                tageszeiten(wetter, heute)?.let { zeilen += it }
            }
        }

        // Termine heute (ohne Aufgaben – die kommen extra)
        if (e.termine) {
            val termine = termineAm(b, heute, false)
            kurz += when (termine.size) { 0 -> "keine Termine"; 1 -> "1 Termin"; else -> "${termine.size} Termine" }
            zeilen += ""
            zeilen += if (termine.isEmpty()) "Termine: keine" else "Termine:"
            termine.take(8).forEach { t ->
                zeilen += "  " + (if (t.ganztags) "ganztägig" else t.start.hhmm()) + "  " + t.titel + (if (t.ort.isNotBlank()) " · ${t.ort}" else "")
            }
            if (termine.size > 8) zeilen += "  + ${termine.size - 8} weitere"
        }

        // Aufgaben: heute fällig und überfällig
        if (e.aufgaben) {
            val auf = d.todos.eintraege.filter { !it.erledigt && (parseDE(it.faellig)?.let { f -> !f.isAfter(heute) } ?: false) }
                .sortedBy { parseDE(it.faellig) }
            if (auf.isNotEmpty()) kurz += if (auf.size == 1) "1 Aufgabe" else "${auf.size} Aufgaben"
            zeilen += ""
            zeilen += if (auf.isEmpty()) "Aufgaben: keine fällig" else "Aufgaben:"
            auf.take(6).forEach { a ->
                val f = parseDE(a.faellig)
                zeilen += "  ☐ " + a.text + (if (a.uhrzeit.isNotBlank()) " · ${a.uhrzeit}" else "") +
                    (if (f != null && f.isBefore(heute)) " (überfällig seit ${a.faellig})" else "")
            }
            if (auf.size > 6) zeilen += "  + ${auf.size - 6} weitere"
        }

        // Fristen (Lehrgänge, Dokumente, Akte)
        if (e.fristen) {
            val (warn, ab) = fristenZaehlen(d)
            if (warn + ab > 0) {
                zeilen += ""
                zeilen += "Fristen: " + listOfNotNull(
                    if (ab > 0) "$ab abgelaufen" else null, if (warn > 0) "$warn laufen bald ab" else null
                ).joinToString(", ")
            }
        }

        // Ausblick auf morgen
        if (e.morgen) {
            val morgen = termineAm(b, heute.plusDays(1), false)
            val erster = morgen.firstOrNull { !it.ganztags } ?: morgen.firstOrNull()
            if (erster != null) {
                zeilen += ""
                zeilen += "Morgen: " + (if (erster.ganztags) "" else erster.start.hhmm() + " ") + erster.titel +
                    if (morgen.size > 1) " (+${morgen.size - 1})" else ""
            }
        }

        val titel = "Guten Morgen · " + WOCHENTAGE[heute.dayOfWeek.value - 1] + " " + "%02d.%02d.".format(heute.dayOfMonth, heute.monthValue)
        anzeigen(ctx, titel, kurz.joinToString(" · ").ifBlank { "Dein Tag im Überblick" }, zeilen.joinToString("\n").trim())

        if (!test) ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("tag", heute.toString()).apply()
    }

    /** "morgens ☀ · mittags ⛅ · abends 🌧" aus den Stundenwerten (jeweils ungünstigstes Wetter). */
    private fun tageszeiten(w: Wetter, tag: LocalDate): String? {
        val fenster = listOf("morgens" to 6..10, "mittags" to 11..16, "abends" to 17..21)
        val stunden = w.stunden.mapNotNull { s -> runCatching { LocalDateTime.parse(s.zeit) }.getOrNull()?.let { it to s } }
            .filter { it.first.toLocalDate() == tag }
        if (stunden.isEmpty()) return null
        return fenster.mapNotNull { (name, h) ->
            stunden.filter { it.first.hour in h }.mapNotNull { it.second.code }.maxOrNull()?.let { "$name ${WetterDienst.zeichen(it)}" }
        }.joinToString(" · ").ifBlank { null }
    }

    private fun anzeigen(ctx: Context, titel: String, kurz: String, text: String) {
        if (!Erinnerungen.darfBenachrichtigen(ctx)) return
        val oeffnen = PendingIntent.getActivity(
            ctx, ID, Intent(ctx, MainActivity::class.java).putExtra("ziel", "heute")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, KANAL)
            .setSmallIcon(R.drawable.ic_benachrichtigung)
            .setContentTitle(titel)
            .setContentText(kurz)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text).setSummaryText(kurz))
            .setAutoCancel(true)
            .setContentIntent(oeffnen)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(ID, n) } catch (x: SecurityException) { }
    }
}

/** Löst den Überblick aus und plant neu – auch wenn der Wecker umgestellt wird. */
class TagesueberblickEmpfaenger : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Speicher.init(context)
        val ergebnis = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (intent.action == Tagesueberblick.AKTION) {
                    val e = Speicher.aktuell.tagesueberblick
                    if (e.an && LocalDate.now().dayOfWeek.value in e.tage) Tagesueberblick.zeigen(context)
                }
                Tagesueberblick.planen(context)
            } catch (x: Exception) {
            } finally {
                ergebnis.finish()
            }
        }
    }
}
