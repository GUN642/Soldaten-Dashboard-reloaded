package de.gun.dashboard.reloaded.logik

import de.gun.dashboard.reloaded.daten.EigenerTermin
import de.gun.dashboard.reloaded.daten.QuellenTermin
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Wiederholungsregeln, wie sie die Eingabemaske anbietet. */
val WDH_REGELN = linkedMapOf(
    "" to "",
    "daily" to "FREQ=DAILY;INTERVAL=1",
    "weekly" to "FREQ=WEEKLY;INTERVAL=1",
    "monthly" to "FREQ=MONTHLY;INTERVAL=1",
    "quarterly" to "FREQ=MONTHLY;INTERVAL=3",
    "yearly" to "FREQ=YEARLY;INTERVAL=1",
)
val WDH_NAMEN = linkedMapOf(
    "" to "keine", "daily" to "täglich", "weekly" to "wöchentlich",
    "monthly" to "monatlich", "quarterly" to "quartalsweise", "yearly" to "jährlich",
)

fun wdhZuRegel(wert: String?): String = WDH_REGELN[wert ?: ""] ?: ""

/** Umgekehrt; null, wenn die Regel feiner ist als die fünf Standardfälle. */
fun regelZuWdh(regel: String?): String? {
    if (regel.isNullOrBlank()) return ""
    val r = rruleZerlegen(regel.removePrefix("RRULE:"))
    val freq = r["FREQ"]?.uppercase() ?: ""
    val abstand = r["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    if (r.keys.any { it != "FREQ" && it != "INTERVAL" && it != "WKST" }) return null
    return when {
        freq == "DAILY" && abstand == 1 -> "daily"
        freq == "WEEKLY" && abstand == 1 -> "weekly"
        freq == "MONTHLY" && abstand == 1 -> "monthly"
        freq == "MONTHLY" && abstand == 3 -> "quarterly"
        freq == "YEARLY" && abstand == 1 -> "yearly"
        else -> null
    }
}

fun rruleZerlegen(r: String): Map<String, String> =
    r.split(";").mapNotNull {
        val teile = it.split("=", limit = 2)
        if (teile.size == 2 && teile[0].isNotBlank()) teile[0].uppercase().trim() to teile[1].trim() else null
    }.toMap()

private val BYDAY = mapOf(
    "MO" to DayOfWeek.MONDAY, "TU" to DayOfWeek.TUESDAY, "WE" to DayOfWeek.WEDNESDAY,
    "TH" to DayOfWeek.THURSDAY, "FR" to DayOfWeek.FRIDAY, "SA" to DayOfWeek.SATURDAY, "SU" to DayOfWeek.SUNDAY
)

data class Vorkommen(val start: LocalDateTime, val ende: LocalDateTime)

/**
 * Löst einen (ggf. wiederkehrenden) Termin im Fenster auf. Unterstützt
 * FREQ=DAILY/WEEKLY(+BYDAY)/MONTHLY/YEARLY mit INTERVAL, COUNT und UNTIL.
 * Monats- und Jahresschritte werden immer vom Ursprungsdatum aus gerechnet;
 * fällt ein Vorkommen auf einen Tag, den der Monat nicht hat (31., 29.02.),
 * entfällt es – wie im Kalenderstandard vorgesehen.
 */
fun expandiere(
    start: LocalDateTime,
    ende: LocalDateTime?,
    ganztags: Boolean,
    rrule: String?,
    fensterVon: LocalDateTime,
    fensterBis: LocalDateTime,
): List<Vorkommen> {
    val dauer = if (ende != null && ende.isAfter(start)) java.time.Duration.between(start, ende)
    else if (ganztags) java.time.Duration.ofDays(1) else java.time.Duration.ofHours(1)

    if (rrule.isNullOrBlank()) {
        val e = start.plus(dauer)
        if (start.isAfter(fensterBis) || e.isBefore(fensterVon)) return emptyList()
        return listOf(Vorkommen(start, e))
    }
    val r = rruleZerlegen(rrule.removePrefix("RRULE:"))
    val freq = r["FREQ"]?.uppercase() ?: return listOf(Vorkommen(start, start.plus(dauer)))
        .filter { !it.start.isAfter(fensterBis) && !it.ende.isBefore(fensterVon) }
    val intervall = (r["INTERVAL"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
    val anzahl = r["COUNT"]?.toIntOrNull()
    val bis = r["UNTIL"]?.let { untilLesen(it) }
    val byday = r["BYDAY"]?.split(",")?.mapNotNull { BYDAY[it.trim().takeLast(2).uppercase()] }?.toSet()
        ?.takeIf { it.isNotEmpty() }

    val treffer = mutableListOf<Vorkommen>()
    var gezaehlt = 0
    var cursor = start
    var schritt = 0L
    val startWoche = wochenStart(start.toLocalDate())
    var n = 0
    while (n < 5000) {
        n++
        if (bis != null && cursor.isAfter(bis)) break
        if (anzahl != null && gezaehlt >= anzahl) break
        if (cursor.isAfter(fensterBis)) break

        var gueltig = true
        when (freq) {
            "MONTHLY" -> gueltig = cursor.dayOfMonth == start.dayOfMonth
            "YEARLY" -> gueltig = cursor.dayOfMonth == start.dayOfMonth && cursor.month == start.month
            "WEEKLY" -> if (byday != null) {
                val wochen = ChronoUnit.WEEKS.between(startWoche, wochenStart(cursor.toLocalDate()))
                gueltig = cursor.dayOfWeek in byday && wochen % intervall == 0L && !cursor.isBefore(start)
            }
        }
        if (gueltig) {
            gezaehlt++
            val e = cursor.plus(dauer)
            if (!e.isBefore(fensterVon)) treffer.add(Vorkommen(cursor, e))
        }

        cursor = when (freq) {
            "DAILY" -> cursor.plusDays(intervall.toLong())
            "WEEKLY" -> if (byday != null) cursor.plusDays(1) else cursor.plusWeeks(intervall.toLong())
            "MONTHLY" -> { schritt += intervall; start.plusMonths(schritt) }
            "YEARLY" -> { schritt += intervall; start.plusYears(schritt) }
            else -> break
        }
    }
    return treffer
}

// plusMonths/plusYears kürzen den 31. bzw. 29.02. – solche Vorkommen
// fallen oben über den Tagesvergleich heraus.

private fun untilLesen(u: String): LocalDateTime? {
    val t = u.trim()
    return try {
        when {
            Regex("""^\d{8}$""").matches(t) -> LocalDate.parse(t, DateTimeFormatter.BASIC_ISO_DATE).atTime(23, 59, 59)
            t.endsWith("Z") -> LocalDateTime.parse(t.dropLast(1), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                .atZone(ZoneOffset.UTC).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
            else -> LocalDateTime.parse(t, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
        }
    } catch (e: Exception) {
        null
    }
}

// ---------------- ICS lesen ----------------

private fun icsEntfalten(text: String) =
    text.replace("\r\n", "\n").replace("\r", "\n").replace(Regex("\n[ \t]"), "")

private fun icsUnescape(v: String) =
    v.replace(Regex("""\\[nN]"""), "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

private data class IcsZeit(val lokal: LocalDateTime, val ganztags: Boolean)

private fun icsZeitLesen(wert: String, params: String): IcsZeit? {
    val w = wert.trim()
    val nurDatum = Regex("VALUE=DATE(?!-TIME)", RegexOption.IGNORE_CASE).containsMatchIn(params) || Regex("""^\d{8}$""").matches(w)
    return try {
        if (nurDatum) {
            IcsZeit(LocalDate.parse(w.take(8), DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay(), true)
        } else {
            val basis = LocalDateTime.parse(w.removeSuffix("Z").take(15), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
            val tzid = Regex("TZID=([^;:]+)", RegexOption.IGNORE_CASE).find(params)?.groupValues?.get(1)?.trim('"')
            val lokal = when {
                w.endsWith("Z") -> basis.atZone(ZoneOffset.UTC).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                tzid != null -> runCatching {
                    basis.atZone(ZoneId.of(tzid)).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                }.getOrDefault(basis)
                else -> basis
            }
            IcsZeit(lokal, false)
        }
    } catch (e: Exception) {
        null
    }
}

fun LocalDateTime.alsIso(): String = atZone(ZoneId.systemDefault()).toInstant().toString()

fun isoZuLokal(iso: String?): LocalDateTime? = try {
    if (iso.isNullOrBlank()) null
    else Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDateTime()
} catch (e: Exception) {
    try { LocalDateTime.parse(iso) } catch (e2: Exception) { null }
}

/** Liest eine ICS-Datei in das gespeicherte Format der Kalenderquellen. */
fun icsLesen(text: String): List<QuellenTermin> {
    val liste = mutableListOf<QuellenTermin>()
    var aktuell: MutableMap<String, Any?>? = null
    for (zeile in icsEntfalten(text).split("\n")) {
        val z = zeile.trim()
        if (z == "BEGIN:VEVENT") { aktuell = mutableMapOf(); continue }
        if (z == "END:VEVENT") {
            val a = aktuell
            aktuell = null
            if (a != null && a["start"] != null && (a["status"] as? String)?.uppercase() != "CANCELLED") {
                val s = a["start"] as IcsZeit
                val e = a["ende"] as IcsZeit?
                liste.add(
                    QuellenTermin(
                        uid = (a["uid"] as? String) ?: java.util.UUID.randomUUID().toString(),
                        titel = (a["titel"] as? String) ?: "",
                        start = s.lokal.alsIso(),
                        end = e?.lokal?.alsIso(),
                        allDay = s.ganztags,
                        rrule = a["rrule"] as? String,
                        ort = (a["ort"] as? String) ?: "",
                        beschreibung = (a["beschreibung"] as? String) ?: "",
                    )
                )
            }
            continue
        }
        val a = aktuell ?: continue
        val idx = z.indexOf(':')
        if (idx < 0) continue
        val links = z.substring(0, idx)
        val wert = z.substring(idx + 1)
        val semi = links.indexOf(';')
        val name = (if (semi < 0) links else links.substring(0, semi)).uppercase()
        val params = if (semi < 0) "" else links.substring(semi + 1)
        when (name) {
            "UID" -> a["uid"] = wert
            "SUMMARY" -> a["titel"] = icsUnescape(wert)
            "DESCRIPTION" -> a["beschreibung"] = icsUnescape(wert)
            "LOCATION" -> a["ort"] = icsUnescape(wert)
            "STATUS" -> a["status"] = wert
            "RRULE" -> a["rrule"] = wert
            "DTSTART" -> icsZeitLesen(wert, params)?.let { a["start"] = it }
            "DTEND" -> icsZeitLesen(wert, params)?.let { a["ende"] = it }
        }
    }
    return liste
}

// ---------------- ICS schreiben ----------------

private fun icsEscape(v: String) =
    v.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(Regex("\r?\n"), "\\n")

private fun icsFalten(zeile: String): String {
    if (zeile.length <= 75) return zeile
    val sb = StringBuilder(zeile.substring(0, 75))
    var rest = zeile.substring(75)
    while (rest.length > 74) {
        sb.append("\r\n ").append(rest.substring(0, 74))
        rest = rest.substring(74)
    }
    if (rest.isNotEmpty()) sb.append("\r\n ").append(rest)
    return sb.toString()
}

private val ICS_DATUM = DateTimeFormatter.BASIC_ISO_DATE
private val ICS_ZEIT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm'00'")

/** Eigene Termine als .ics (für Import in andere Kalender). */
fun icsSchreiben(termine: List<EigenerTermin>): String {
    val z = mutableListOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Soldaten Dashboard Reloaded//DE",
        "CALSCALE:GREGORIAN", "METHOD:PUBLISH")
    val zone = ZoneId.systemDefault()
    val stempel = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
    for (t in termine) {
        val s = parseDE(t.von) ?: continue
        val e = parseDE(t.bis) ?: s
        z += "BEGIN:VEVENT"
        z += "UID:" + t.uid
        z += "DTSTAMP:$stempel"
        z += "SEQUENCE:" + (t.sequence ?: 0)
        if (t.ganztags) {
            z += "DTSTART;VALUE=DATE:" + s.format(ICS_DATUM)
            z += "DTEND;VALUE=DATE:" + e.plusDays(1).format(ICS_DATUM)
        } else {
            val a = s.atTime(zeitAus(t.zeitVon) ?: LocalTime.of(8, 0))
            var b = e.atTime(zeitAus(t.zeitBis) ?: LocalTime.of(16, 0))
            if (!b.isAfter(a)) b = a.plusHours(1)
            z += "DTSTART;TZID=${zone.id}:" + a.format(ICS_ZEIT)
            z += "DTEND;TZID=${zone.id}:" + b.format(ICS_ZEIT)
        }
        wdhZuRegel(t.wiederholung).takeIf { it.isNotEmpty() }?.let { z += "RRULE:$it" }
        z += "SUMMARY:" + icsEscape(t.titel)
        if (t.ort.isNotBlank()) z += "LOCATION:" + icsEscape(t.ort)
        if (t.notiz.isNotBlank()) z += "DESCRIPTION:" + icsEscape(t.notiz)
        z += "END:VEVENT"
    }
    z += "END:VCALENDAR"
    return z.joinToString("\r\n") { icsFalten(it) } + "\r\n"
}
