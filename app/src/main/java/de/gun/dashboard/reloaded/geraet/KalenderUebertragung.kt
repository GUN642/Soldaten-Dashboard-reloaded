package de.gun.dashboard.reloaded.geraet

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import de.gun.dashboard.reloaded.daten.Speicher
import de.gun.dashboard.reloaded.logik.dauerMs
import de.gun.dashboard.reloaded.logik.dauerText
import de.gun.dashboard.reloaded.logik.exdateAnhaengen
import de.gun.dashboard.reloaded.logik.terminSchluessel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ein Termin des Quellkalenders, wie er übertragen wird. */
data class QuellTermin(
    val id: Long,
    val syncId: String?,
    val titel: String,
    val ort: String,
    val notiz: String,
    val beginn: Long,
    val ende: Long?,
    val dauer: String?,
    val ganztags: Boolean,
    val zone: String?,
    val zoneEnde: String?,
    val rrule: String?,
    val rdate: String?,
    val exdate: String?,
    val originalId: Long?,
    val originalSyncId: String?,
    val originalBeginn: Long?,
    val abgesagt: Boolean,
    val verfuegbarkeit: Int?,
    val letztesDatum: Long?,
) {
    val istSerie get() = !rrule.isNullOrBlank() || !rdate.isNullOrBlank()
    val istAusnahme get() = originalId != null || !originalSyncId.isNullOrBlank()
}

/** Was übertragen würde. */
data class UebertragungsPlan(
    val termine: List<QuellTermin>,
    val serien: Int,
    val ausnahmen: Int,
    val schonVorhanden: Int,
    /** Ausnahmen je Serie: Vorkommen, die in der Kopie per EXDATE ausgelassen werden. */
    val auslassen: Map<Long, List<Long>>,
)

data class UebertragungsErgebnis(val kopiert: Int, val fehler: Int, val neuVerknuepft: Int)

/** Kopiert Termine direkt im Gerätekalender von einem Kalender in einen anderen (z. B. Outlook → Google). */
object KalenderUebertragung {

    private fun Cursor.s(k: String) = getColumnIndex(k).let { if (it >= 0 && !isNull(it)) getString(it) else null }
    private fun Cursor.l(k: String) = getColumnIndex(k).let { if (it >= 0 && !isNull(it)) getLong(it) else null }
    private fun Cursor.i(k: String) = getColumnIndex(k).let { if (it >= 0 && !isNull(it)) getInt(it) else null }

    private fun lesen(ctx: Context, kalenderId: String): List<QuellTermin> {
        val felder = arrayOf(
            CalendarContract.Events._ID, CalendarContract.Events._SYNC_ID, CalendarContract.Events.TITLE,
            CalendarContract.Events.EVENT_LOCATION, CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND, CalendarContract.Events.DURATION,
            CalendarContract.Events.ALL_DAY, CalendarContract.Events.EVENT_TIMEZONE, CalendarContract.Events.EVENT_END_TIMEZONE,
            CalendarContract.Events.RRULE, CalendarContract.Events.RDATE, CalendarContract.Events.EXDATE,
            CalendarContract.Events.ORIGINAL_ID, CalendarContract.Events.ORIGINAL_SYNC_ID, CalendarContract.Events.ORIGINAL_INSTANCE_TIME,
            CalendarContract.Events.STATUS, CalendarContract.Events.AVAILABILITY, CalendarContract.Events.LAST_DATE,
        )
        val liste = mutableListOf<QuellTermin>()
        ctx.contentResolver.query(
            CalendarContract.Events.CONTENT_URI, felder,
            "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DELETED} = 0", arrayOf(kalenderId), null
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.l(CalendarContract.Events._ID) ?: continue
                val beginn = c.l(CalendarContract.Events.DTSTART) ?: continue
                liste += QuellTermin(
                    id = id, syncId = c.s(CalendarContract.Events._SYNC_ID),
                    titel = c.s(CalendarContract.Events.TITLE) ?: "", ort = c.s(CalendarContract.Events.EVENT_LOCATION) ?: "",
                    notiz = c.s(CalendarContract.Events.DESCRIPTION) ?: "",
                    beginn = beginn, ende = c.l(CalendarContract.Events.DTEND), dauer = c.s(CalendarContract.Events.DURATION),
                    ganztags = (c.i(CalendarContract.Events.ALL_DAY) ?: 0) == 1,
                    zone = c.s(CalendarContract.Events.EVENT_TIMEZONE), zoneEnde = c.s(CalendarContract.Events.EVENT_END_TIMEZONE),
                    rrule = c.s(CalendarContract.Events.RRULE), rdate = c.s(CalendarContract.Events.RDATE), exdate = c.s(CalendarContract.Events.EXDATE),
                    originalId = c.l(CalendarContract.Events.ORIGINAL_ID), originalSyncId = c.s(CalendarContract.Events.ORIGINAL_SYNC_ID),
                    originalBeginn = c.l(CalendarContract.Events.ORIGINAL_INSTANCE_TIME),
                    abgesagt = c.i(CalendarContract.Events.STATUS) == CalendarContract.Events.STATUS_CANCELED,
                    verfuegbarkeit = c.i(CalendarContract.Events.AVAILABILITY), letztesDatum = c.l(CalendarContract.Events.LAST_DATE),
                )
            }
        }
        return liste
    }

    /** Vorschau: was würde ab [abMs] (null = alles) kopiert, was ist im Ziel schon vorhanden? */
    suspend fun planen(ctx: Context, quelle: String, ziel: String, abMs: Long?): UebertragungsPlan = withContext(Dispatchers.IO) {
        val alle = lesen(ctx, quelle)
        val vorhanden = lesen(ctx, ziel).mapTo(HashSet()) { terminSchluessel(it.titel, it.beginn) }
        val nachSync = alle.filter { !it.syncId.isNullOrBlank() }.associateBy { it.syncId }
        fun serieVon(a: QuellTermin): QuellTermin? = a.originalId?.let { o -> alle.firstOrNull { it.id == o } } ?: a.originalSyncId?.let { nachSync[it] }

        // Ausnahmen einer Serie: das ursprüngliche Vorkommen in der Kopie auslassen
        val auslassen = mutableMapOf<Long, MutableList<Long>>()
        for (a in alle.filter { it.istAusnahme }) {
            val s = serieVon(a) ?: continue
            val o = a.originalBeginn ?: continue
            auslassen.getOrPut(s.id) { mutableListOf() } += o
        }
        val imZeitraum = alle.filter { t ->
            if (t.istAusnahme && t.abgesagt) return@filter false // abgesagte Vorkommen: nur auslassen
            if (abMs == null) return@filter true
            if (t.istSerie) (t.letztesDatum ?: Long.MAX_VALUE) >= abMs else (t.ende ?: t.beginn) >= abMs
        }
        val neu = imZeitraum.filter { terminSchluessel(it.titel, it.beginn) !in vorhanden }
        UebertragungsPlan(
            termine = neu,
            serien = neu.count { it.istSerie },
            ausnahmen = neu.count { it.istAusnahme },
            schonVorhanden = imZeitraum.size - neu.size,
            auslassen = auslassen,
        )
    }

    private fun werte(t: QuellTermin, ziel: String, auslassen: List<Long>): ContentValues {
        val v = ContentValues()
        v.put(CalendarContract.Events.CALENDAR_ID, ziel.toLong())
        v.put(CalendarContract.Events.TITLE, t.titel)
        v.put(CalendarContract.Events.EVENT_LOCATION, t.ort)
        v.put(CalendarContract.Events.DESCRIPTION, t.notiz)
        v.put(CalendarContract.Events.DTSTART, t.beginn)
        v.put(CalendarContract.Events.ALL_DAY, if (t.ganztags) 1 else 0)
        v.put(CalendarContract.Events.EVENT_TIMEZONE, if (t.ganztags) "UTC" else t.zone ?: java.util.TimeZone.getDefault().id)
        if (!t.ganztags && !t.zoneEnde.isNullOrBlank()) v.put(CalendarContract.Events.EVENT_END_TIMEZONE, t.zoneEnde)
        t.verfuegbarkeit?.let { v.put(CalendarContract.Events.AVAILABILITY, it) }
        val dauer = dauerMs(t.dauer) ?: t.ende?.let { it - t.beginn } ?: if (t.ganztags) 86_400_000L else 3_600_000L
        if (t.istSerie && !t.istAusnahme) {
            if (!t.rrule.isNullOrBlank()) v.put(CalendarContract.Events.RRULE, t.rrule)
            if (!t.rdate.isNullOrBlank()) v.put(CalendarContract.Events.RDATE, t.rdate)
            var ex = t.exdate
            auslassen.forEach { ex = exdateAnhaengen(ex, it) }
            if (!ex.isNullOrBlank()) v.put(CalendarContract.Events.EXDATE, ex)
            v.put(CalendarContract.Events.DURATION, t.dauer?.takeIf { dauerMs(it) != null } ?: dauerText(dauer, t.ganztags))
            v.putNull(CalendarContract.Events.DTEND)
        } else {
            // Einzeltermin bzw. geänderte Ausnahme als eigenständiger Termin
            v.put(CalendarContract.Events.DTEND, t.ende ?: (t.beginn + dauer))
        }
        return v
    }

    private fun erinnerungenKopieren(ctx: Context, von: Long, nach: Long) {
        val r = ctx.contentResolver
        r.query(
            CalendarContract.Reminders.CONTENT_URI,
            arrayOf(CalendarContract.Reminders.MINUTES, CalendarContract.Reminders.METHOD),
            "${CalendarContract.Reminders.EVENT_ID} = ?", arrayOf(von.toString()), null
        )?.use { c ->
            while (c.moveToNext()) {
                val v = ContentValues()
                v.put(CalendarContract.Reminders.EVENT_ID, nach)
                v.put(CalendarContract.Reminders.MINUTES, c.getInt(0))
                v.put(CalendarContract.Reminders.METHOD, c.getInt(1))
                runCatching { r.insert(CalendarContract.Reminders.CONTENT_URI, v) }
            }
        }
    }

    /** Überträgt den Plan; eigene Termine der App werden auf die neuen Einträge umgehängt. */
    suspend fun ausfuehren(ctx: Context, plan: UebertragungsPlan, ziel: String, fortschritt: (Int, Int) -> Unit): UebertragungsErgebnis =
        withContext(Dispatchers.IO) {
            val neuIds = mutableMapOf<Long, Long>()
            var fehler = 0
            plan.termine.forEachIndexed { i, t ->
                try {
                    val uri = ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, werte(t, ziel, plan.auslassen[t.id].orEmpty()))
                    if (uri == null) fehler++ else {
                        val neu = ContentUris.parseId(uri)
                        neuIds[t.id] = neu
                        erinnerungenKopieren(ctx, t.id, neu)
                    }
                } catch (e: Exception) { fehler++ }
                fortschritt(i + 1, plan.termine.size)
            }
            // Verknüpfungen eigener Termine (Notiz, Anhänge, Anrechnung) auf die Kopien umstellen
            var verknuepft = 0
            Speicher.aendern { a ->
                a.copy(kalender = a.kalender.copy(eigene = a.kalender.eigene.map { e ->
                    val alt = e.nativId.toLongOrNull()
                    val neu = alt?.let { neuIds[it] }
                    if (neu != null) { verknuepft++; e.copy(nativId = neu.toString(), kalenderId = ziel, nativ = true) } else e
                }))
            }
            UebertragungsErgebnis(neuIds.size, fehler, verknuepft)
        }
}
