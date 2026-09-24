package de.gun.dashboard.reloaded.erinnerung

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.gun.dashboard.reloaded.MainActivity
import de.gun.dashboard.reloaded.R
import de.gun.dashboard.reloaded.daten.Speicher
import de.gun.dashboard.reloaded.logik.fmt
import de.gun.dashboard.reloaded.logik.parseDE
import de.gun.dashboard.reloaded.logik.zeitAus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Geplante Erinnerung. */
data class Erinnerung(val schluessel: String, val titel: String, val text: String, val zeit: LocalDateTime, val ziel: String)

object Erinnerungen {
    const val KANAL = "erinnerungen"
    private const val PREFS = "erinnerungen"
    private const val MAX = 60

    fun kanalAnlegen(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(KANAL, "Erinnerungen", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Termine, Aufgaben, ablaufende Lehrgänge, Dokumente und Fristen"
            })
        }
    }

    fun darfBenachrichtigen(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Stabile Ganzzahl je Eintrag. */
    fun kennung(text: String): Int {
        var h = 0
        for (c in text) h = 31 * h + c.code
        return (Math.abs(h % 2_000_000_000)) + 1
    }

    /** Stellt alle anstehenden Erinnerungen zusammen (wie im alten Dashboard). */
    fun sammeln(): List<Erinnerung> {
        val d = Speicher.aktuell
        val jetzt = LocalDateTime.now()
        val liste = mutableListOf<Erinnerung>()
        val ganztagsZeit = zeitAus(d.ben.ganztagsZeit) ?: LocalTime.of(21, 0)

        // 1) Eigene Termine
        for (t in d.kalender.eigene) {
            val s = parseDE(t.von) ?: continue
            val zeit = if (t.ganztags) s.minusDays(1).atTime(ganztagsZeit)
            else s.atTime(zeitAus(t.zeitVon) ?: LocalTime.of(8, 0)).minusMinutes(d.ben.vorlaufMin.toLong())
            if (zeit.isAfter(jetzt)) liste += Erinnerung(
                "termin:" + t.id, t.titel,
                if (t.ganztags) "Morgen ganztägig" else "Beginnt um " + t.zeitVon + if (t.ort.isNotBlank()) " · " + t.ort else "",
                zeit, "kalender"
            )
        }

        // 2) Lehrgänge und Dokumente: 6 Monate, 3 Monate, 2 Wochen vorher
        fun ablauf(prefix: String, id: String, titel: String, bisText: String, ziel: String) {
            val bis = parseDE(bisText) ?: return
            listOf(bis.minusMonths(6), bis.minusMonths(3), bis.minusDays(14)).forEachIndexed { i, tag ->
                val z = tag.atTime(9, 0)
                if (z.isAfter(jetzt)) liste += Erinnerung("$prefix:$id:$i", titel, "Gültig bis " + fmt(bisText), z, ziel)
            }
        }
        d.ablaufregister.forEach { ablauf("lehrgang", it.id, "Lehrgang läuft ab: " + it.art, it.gueltigBis, "lehrgaenge") }
        d.dokumente.forEach {
            ablauf("dokument", it.id, "Dokument läuft ab: " + it.art + if (it.inhaber.isNotBlank()) " (${it.inhaber})" else "", it.gueltigBis, "dokumente")
        }

        // 3) Akte: 3 Monate, 1 Monat, 1 Woche vorher
        listOf("IGF" to d.medizin.igf, "ICCS" to d.medizin.iccs, "Untersuchung" to d.medizin.avu, "Impfung" to d.medizin.impfungen)
            .forEach { (bez, l) ->
                l.forEach { u ->
                    val bis = parseDE(u.gueltigBis) ?: return@forEach
                    listOf(bis.minusMonths(3), bis.minusMonths(1), bis.minusDays(7)).forEachIndexed { i, tag ->
                        val z = tag.atTime(9, 0)
                        if (z.isAfter(jetzt)) liste += Erinnerung("med:$bez:${u.id}:$i", "$bez läuft ab: ${u.art}", "Gültig bis " + fmt(u.gueltigBis), z, "akte")
                    }
                }
            }

        // 4) Eigene Sicherung: 30 Tage nach der letzten, sonst in einer Woche
        run {
            val letzte = parseDE(d.sicherung.letzte)
            var faellig = (letzte?.plusDays(30) ?: LocalDate.now().plusDays(7)).atTime(9, 0)
            if (!faellig.isAfter(jetzt)) faellig = LocalDate.now().plusDays(1).atTime(9, 0)
            liste += Erinnerung(
                "sicherung", "Sicherung erstellen",
                if (letzte != null) "Die letzte eigene Sicherung ist vom ${d.sicherung.letzte}. Im Menü unter „Sicherung“ exportieren."
                else "Es wurde noch keine eigene Sicherung erstellt. Im Menü unter „Sicherung“ exportieren.",
                faellig, "menue"
            )
        }

        // 5) Fällige Aufgaben
        for (t in d.todos.eintraege) {
            if (t.erledigt) continue
            val tag = parseDE(t.faellig) ?: continue
            val z = tag.atTime(zeitAus(t.uhrzeit) ?: LocalTime.of(9, 0))
            if (z.isAfter(jetzt)) liste += Erinnerung("todo:" + t.id, "Aufgabe fällig",
                t.text + if (t.notiz.isNotBlank()) " — " + t.notiz.take(80) else "", z, "todo")
        }
        return liste.sortedBy { it.zeit }.take(MAX)
    }

    fun planen(ctx: Context): Int {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return 0
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Bisherige Planung zurücknehmen
        prefs.getStringSet("ids", emptySet())?.forEach { id ->
            am.cancel(absicht(ctx, id.toInt(), "", "", "", PendingIntent.FLAG_UPDATE_CURRENT))
        }
        if (!Speicher.aktuell.ben.an) {
            prefs.edit().putStringSet("ids", emptySet()).apply()
            return 0
        }
        val liste = sammeln()
        val ids = mutableSetOf<String>()
        for (e in liste) {
            val id = kennung(e.schluessel)
            val ms = e.zeit.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, absicht(ctx, id, e.titel, e.text, e.ziel, PendingIntent.FLAG_UPDATE_CURRENT))
            ids += id.toString()
        }
        prefs.edit().putStringSet("ids", ids).apply()
        return liste.size
    }

    private fun absicht(ctx: Context, id: Int, titel: String, text: String, ziel: String, flags: Int): PendingIntent {
        val i = Intent(ctx, ErinnerungEmpfaenger::class.java)
            .putExtra("id", id).putExtra("titel", titel).putExtra("text", text).putExtra("ziel", ziel)
        return PendingIntent.getBroadcast(ctx, id, i, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    fun zeigen(ctx: Context, id: Int, titel: String, text: String, ziel: String) {
        if (!darfBenachrichtigen(ctx)) return
        val oeffnen = PendingIntent.getActivity(
            ctx, id, Intent(ctx, MainActivity::class.java).putExtra("ziel", ziel)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, KANAL)
            .setSmallIcon(R.drawable.ic_benachrichtigung)
            .setContentTitle(titel)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(oeffnen)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (e: SecurityException) { }
    }
}

class ErinnerungEmpfaenger : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Speicher.init(context)
        Erinnerungen.zeigen(
            context, intent.getIntExtra("id", 1), intent.getStringExtra("titel") ?: "Erinnerung",
            intent.getStringExtra("text") ?: "", intent.getStringExtra("ziel") ?: ""
        )
    }
}

/** Nach Neustart oder Update die Erinnerungen neu einplanen. */
class NeustartEmpfaenger : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Speicher.init(context)
        try { Erinnerungen.planen(context) } catch (e: Exception) { }
    }
}
