package de.gun.dashboard.reloaded.logik

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.util.Locale

val DE: Locale = Locale.GERMANY

private val DATUM_RE = Regex("""^(\d{1,2})\.(\d{1,2})\.(\d{4})$""")

/** "TT.MM.JJJJ" -> Datum, sonst null. */
fun parseDE(text: String?): LocalDate? {
    val m = DATUM_RE.matchEntire(text?.trim() ?: return null) ?: return null
    return try {
        LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())
    } catch (e: Exception) {
        null
    }
}

private val FORMAT_DE = DateTimeFormatter.ofPattern("dd.MM.yyyy")
fun LocalDate.alsDE(): String = format(FORMAT_DE)
fun heuteDE(): String = LocalDate.now().alsDE()

/** Leere Angabe als Gedankenstrich. */
fun fmt(s: String?): String = if (s.isNullOrBlank()) "—" else s.trim()

fun zwei(n: Int): String = n.toString().padStart(2, '0')

fun LocalTime.hhmm(): String = zwei(hour) + ":" + zwei(minute)
fun LocalDateTime.hhmm(): String = zwei(hour) + ":" + zwei(minute)

/** "Montag, 24. September 2026" */
fun LocalDate.lang(): String =
    dayOfWeek.getDisplayName(TextStyle.FULL, DE) + ", " + dayOfMonth + ". " +
        month.getDisplayName(TextStyle.FULL, DE) + " " + year

/** "Mo, 24.09." */
fun LocalDate.kurz(): String =
    dayOfWeek.getDisplayName(TextStyle.SHORT, DE).removeSuffix(".") + ", " + zwei(dayOfMonth) + "." + zwei(monthValue) + "."

val MONATE = listOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August",
    "September", "Oktober", "November", "Dezember")
val MONATE_KURZ = listOf("Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez")
val WOCHENTAGE = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

fun kalenderwoche(d: LocalDate): Int = d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

fun tageZwischen(von: LocalDate, bis: LocalDate): Long = ChronoUnit.DAYS.between(von, bis)

/** Uhrzeit tolerant einlesen: 1300, 13.00, 13 00, 9, 930 … -> "13:00". */
fun zeitNormieren(wert: String?): String? {
    var t = (wert ?: "").trim()
    if (t.isEmpty()) return null
    t = t.replace(Regex("(?i)uhr"), "").trim().replace(Regex("[.,;\\-\\s]+"), ":").trim(':')
    val st: Int
    val mi: Int
    if (t.contains(":")) {
        val teile = t.split(":").filter { it.isNotEmpty() }
        if (teile.isEmpty()) return null
        st = teile[0].toIntOrNull() ?: return null
        mi = if (teile.size > 1) teile[1].toIntOrNull() ?: return null else 0
    } else {
        if (!Regex("""^\d{1,4}$""").matches(t)) return null
        when (t.length) {
            1, 2 -> { st = t.toInt(); mi = 0 }
            3 -> { st = t.substring(0, 1).toInt(); mi = t.substring(1).toInt() }
            else -> { st = t.substring(0, 2).toInt(); mi = t.substring(2).toInt() }
        }
    }
    val s = if (st == 24 && mi == 0) 0 else st
    if (s !in 0..23 || mi !in 0..59) return null
    return zwei(s) + ":" + zwei(mi)
}

fun zeitAus(text: String?): LocalTime? = zeitNormieren(text)?.let {
    LocalTime.of(it.substring(0, 2).toInt(), it.substring(3, 5).toInt())
}

/** Datum + Dauer (T/M/J). */
fun datumPlus(vonText: String, wert: String, einheit: String): String? {
    val von = parseDE(vonText) ?: return null
    val n = wert.trim().toLongOrNull() ?: return null
    if (n == 0L) return null
    return when (einheit) {
        "T" -> von.plusDays(n)
        "M" -> von.plusMonths(n)
        else -> von.plusYears(n)
    }.alsDE()
}

/** Zahl ohne überflüssige Nachkommastellen, deutsches Komma. */
fun zahl(d: Double): String {
    if (d == Math.floor(d) && !d.isInfinite() && Math.abs(d) < 1e12) return d.toLong().toString()
    return String.format(DE, "%.2f", d).trimEnd('0').trimEnd(',')
}

fun mitVorzeichen(d: Double): String = (if (d > 0) "+" else "") + zahl(d)

// ---------------- Ablaufstatus ----------------

enum class Status(val label: String) {
    GUELTIG("Gültig"), WARNUNG("Läuft bald ab"), ABGELAUFEN("Abgelaufen"), UNBEKANNT("Unbekannt")
}

/** Gelb ab sechs Monate vor Ablauf. */
fun statusVon(gueltigBis: String?): Status {
    val bis = parseDE(gueltigBis) ?: return Status.UNBEKANNT
    val heute = LocalDate.now()
    if (bis.isBefore(heute)) return Status.ABGELAUFEN
    if (!heute.isBefore(bis.minusMonths(6))) return Status.WARNUNG
    return Status.GUELTIG
}

/** "in 12 Tagen", "seit 3 Tagen fällig" … */
fun restlaufzeit(gueltigBis: String?): String {
    val bis = parseDE(gueltigBis) ?: return ""
    val tage = tageZwischen(LocalDate.now(), bis)
    return when {
        tage < 0 -> "seit ${-tage} Tagen fällig"
        tage == 0L -> "heute fällig"
        tage < 62 -> "in $tage Tagen"
        else -> "in ${Math.round(tage / 30.4)} Monaten"
    }
}

fun istWochenende(d: LocalDate) = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY

/** Montag der Woche. */
fun wochenStart(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())
