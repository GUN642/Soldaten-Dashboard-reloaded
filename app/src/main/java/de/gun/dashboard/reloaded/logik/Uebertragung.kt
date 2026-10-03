package de.gun.dashboard.reloaded.logik

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Dauer einer RFC-5545-DURATION wie "P3600S", "PT1H30M", "P1D", "P2W" in Millisekunden. */
fun dauerMs(text: String?): Long? {
    val t = text?.trim()?.uppercase()?.removePrefix("+") ?: return null
    val m = Regex("^P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$").find(t)
        ?: Regex("^P(\\d+)S$").find(t)?.let { return it.groupValues[1].toLong() * 1000 }
        ?: return null
    fun g(i: Int) = m.groupValues[i].toLongOrNull() ?: 0L
    val s = g(1) * 7 * 86400 + g(2) * 86400 + g(3) * 3600 + g(4) * 60 + g(5)
    return if (s > 0 || t == "P0D" || t == "PT0S") s * 1000 else null
}

/** DURATION-Text für eine Serie; ganztägig in ganzen Tagen. */
fun dauerText(ms: Long, ganztags: Boolean): String {
    val s = maxOf(0L, ms / 1000)
    return if (ganztags) "P${maxOf(1L, s / 86400)}D" else "P${s}S"
}

private val UTC_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
private val LOKAL_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

/**
 * Ein Vorkommen (Beginn in ms) zur EXDATE-Liste einer Serie hinzufügen.
 * Hat die Liste ein TZID-Präfix ("Europe/Berlin;2026…"), wird in dieser Zone ohne "Z" ergänzt.
 */
fun exdateAnhaengen(exdate: String?, beginnMs: Long): String {
    val alt = exdate?.trim().orEmpty()
    if (alt.isEmpty()) return UTC_FORMAT.format(Instant.ofEpochMilli(beginnMs))
    val semi = alt.indexOf(';')
    if (semi > 0) {
        val zone = runCatching { ZoneId.of(alt.substring(0, semi)) }.getOrNull()
        if (zone != null) return alt + "," + LOKAL_FORMAT.format(Instant.ofEpochMilli(beginnMs).atZone(zone))
    }
    return alt + "," + UTC_FORMAT.format(Instant.ofEpochMilli(beginnMs))
}

/** Schlüssel zum Erkennen bereits vorhandener Termine: gleicher Titel und gleicher Beginn. */
fun terminSchluessel(titel: String?, beginnMs: Long): String = (titel ?: "").trim().lowercase() + "|" + beginnMs
