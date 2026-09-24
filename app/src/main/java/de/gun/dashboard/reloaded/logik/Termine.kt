package de.gun.dashboard.reloaded.logik

import de.gun.dashboard.reloaded.daten.AppDaten
import de.gun.dashboard.reloaded.geraet.GeraetKalender
import de.gun.dashboard.reloaded.geraet.GeraetTermin
import de.gun.dashboard.reloaded.geraet.KontaktAnlass
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Ein Termin, gleich aus welcher Quelle, wie ihn Kalender, Heute und Widget zeigen. */
data class Termin(
    val quelleId: String,
    val quelleName: String,
    val farbe: Int,
    val titel: String,
    val uid: String,
    val ort: String = "",
    val notiz: String = "",
    val start: LocalDateTime,
    /** Exklusives Ende; bei ganztägig der Folgetag 00:00. */
    val ende: LocalDateTime?,
    val ganztags: Boolean,
    val rrule: String? = null,
    val eventId: Long? = null,
    val rohBeginn: Long? = null,
    val eigenerId: String? = null,
    val todoId: String? = null,
    val istKontakt: Boolean = false,
    val schreibbar: Boolean = false,
) {
    val ersterTag: LocalDate get() = start.toLocalDate()

    /** Letzter belegter Tag (Ende exklusiv; Mitternacht zählt nicht mehr). */
    val letzterTag: LocalDate
        get() {
            val e = ende ?: return start.toLocalDate()
            if (!e.isAfter(start)) return start.toLocalDate()
            return if (e.toLocalTime() == LocalTime.MIDNIGHT) e.toLocalDate().minusDays(1) else e.toLocalDate()
        }

    fun liegtAuf(tag: LocalDate) = !ersterTag.isAfter(tag) && !letzterTag.isBefore(tag)

    val istTodo get() = todoId != null
    val istSerie get() = !rrule.isNullOrBlank()
}

fun farbeAusHex(hex: String?, ersatz: Int = 0xFF8B96A5.toInt()): Int {
    if (hex.isNullOrBlank()) return ersatz
    var t = hex.trim().lowercase()
    if (t.matches(Regex("-?\\d+"))) return (t.toLong().toInt()) or 0xFF000000.toInt()
    if (t.startsWith("rgb")) {
        val m = Regex("(\\d+)\\D+(\\d+)\\D+(\\d+)").find(t) ?: return ersatz
        val (r, g, b) = m.destructured
        return (0xFF shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
    }
    t = t.removePrefix("#")
    val h = when (t.length) {
        3 -> t.map { "$it$it" }.joinToString("")
        6 -> t
        8 -> t.substring(2)
        else -> return ersatz
    }
    return (h.toLongOrNull(16)?.toInt() ?: return ersatz) or 0xFF000000.toInt()
}

fun farbeAlsHex(farbe: Int): String = "#" + String.format("%06x", farbe and 0xFFFFFF)

const val FARBE_EIGENE = 0xFF35D488.toInt()
const val FARBE_KONTAKTE = 0xFFFF8FC7.toInt()
const val FARBE_TODO = 0xFF4DD0C4.toInt()
const val FARBE_TODO_HOCH = 0xFFFF8A3D.toInt()
const val FARBE_TODO_ERLEDIGT = 0xFF5B6472.toInt()
const val FARBE_UEBERFAELLIG = 0xFFFF5C5C.toInt()

/** Eingaben für die Terminberechnung, zusammengefasst. */
data class TerminBestand(
    val daten: AppDaten,
    val geraetKalender: List<GeraetKalender>,
    val geraetTermine: List<GeraetTermin>,
    val kontakte: List<KontaktAnlass>,
)

/** Farbe eines Gerätekalenders: selbst gewählte vor der des Geräts. */
fun kalenderFarbe(b: TerminBestand, id: String, geraet: Int?): Int =
    b.daten.farbenEigen[id]?.let { farbeAusHex(it) } ?: geraet ?: 0xFF5FB4FF.toInt()

/** Sichtbare Gerätekalender (ohne ausgeblendete und entfernte). */
fun sichtbareKalender(b: TerminBestand): List<GeraetKalender> =
    b.geraetKalender.filter { it.id !in b.daten.nativ.versteckt && it.id !in b.daten.nativ.entfernt }

/**
 * Alle Termine im Fenster [von, bis] (einschließlich), aus allen Quellen:
 * Gerätekalender, ICS-Abos, eigene (nicht im Gerät liegende) Termine,
 * Anlässe aus den Kontakten und – falls eingeschaltet – Aufgaben.
 */
fun terminFenster(b: TerminBestand, von: LocalDate, bis: LocalDate, mitAufgaben: Boolean = true): List<Termin> {
    val d = b.daten
    val vonZ = von.atStartOfDay()
    val bisZ = bis.plusDays(1).atStartOfDay().minusNanos(1)
    val liste = mutableListOf<Termin>()

    // 1) Gerätekalender
    val sichtbar = sichtbareKalender(b).associateBy { it.id }
    for (e in b.geraetTermine) {
        val k = sichtbar[e.kalenderId] ?: continue
        val ende = e.ende ?: e.start
        if (e.start.isAfter(bisZ) || ende.isBefore(vonZ)) continue
        liste += Termin(
            quelleId = k.id, quelleName = k.titel + (if (k.dienst.isNotBlank()) " · " + k.dienst else ""),
            farbe = kalenderFarbe(b, k.id, k.farbe), titel = e.titel, uid = "g:" + e.eventId + ":" + e.rohBeginn,
            ort = e.ort, notiz = e.notiz, start = e.start, ende = e.ende, ganztags = e.ganztags,
            rrule = e.rrule, eventId = e.eventId, rohBeginn = e.rohBeginn, schreibbar = k.schreibbar,
        ).let { t ->
            // Zusatzangaben eines selbst angelegten Termins (Notiz, Anhänge)
            val eigen = d.kalender.eigene.firstOrNull { it.nativId.isNotBlank() && it.nativId == e.eventId.toString() }
            if (eigen != null) t.copy(eigenerId = eigen.id, notiz = t.notiz.ifBlank { eigen.notiz }) else t
        }
    }

    // 2) Eingebundene ICS-Quellen
    for (q in d.kalender.quellen) {
        if (!q.aktiv) continue
        val farbe = d.farbenEigen[q.id]?.let { farbeAusHex(it) } ?: farbeAusHex(q.farbe)
        for (ev in q.events) {
            val s = isoZuLokal(ev.start) ?: continue
            var e = isoZuLokal(ev.end)
            val start = if (ev.allDay) s.toLocalDate().atStartOfDay() else s
            if (ev.allDay) e = (e ?: start.plusDays(1)).toLocalDate().atStartOfDay().let { if (!it.isAfter(start)) start.plusDays(1) else it }
            for (vk in expandiere(start, e, ev.allDay, ev.rrule, vonZ, bisZ)) {
                liste += Termin(
                    quelleId = q.id, quelleName = q.name, farbe = farbe, titel = ev.titel.ifBlank { "(ohne Titel)" },
                    uid = "q:" + q.id + ":" + ev.uid + ":" + vk.start, ort = ev.ort, notiz = ev.beschreibung,
                    start = vk.start, ende = vk.ende, ganztags = ev.allDay, rrule = ev.rrule,
                )
            }
        }
    }

    // 3) Eigene Termine, die nicht im Gerätekalender liegen
    for (t in d.kalender.eigene) {
        if (t.nativId.isNotBlank()) continue
        val s = parseDE(t.von) ?: continue
        val e = parseDE(t.bis) ?: s
        val (a, z) = if (t.ganztags) s.atStartOfDay() to e.plusDays(1).atStartOfDay()
        else {
            val a = s.atTime(zeitAus(t.zeitVon) ?: LocalTime.of(8, 0))
            var z = e.atTime(zeitAus(t.zeitBis) ?: LocalTime.of(16, 0))
            if (!z.isAfter(a)) z = a.plusHours(1)
            a to z
        }
        val regel = wdhZuRegel(t.wiederholung).ifBlank { null }
        for (vk in expandiere(a, z, t.ganztags, regel, vonZ, bisZ)) {
            liste += Termin(
                quelleId = "eigene", quelleName = "Eigener Termin", farbe = FARBE_EIGENE, titel = t.titel,
                uid = "e:" + t.id + ":" + vk.start, ort = t.ort, notiz = t.notiz, start = vk.start, ende = vk.ende,
                ganztags = t.ganztags, rrule = regel, eigenerId = t.id,
            )
        }
    }

    // 4) Anlässe aus den Kontakten
    if (d.nativ.kontaktdaten && "kontakte" !in d.nativ.versteckt && "kontakte" !in d.nativ.entfernt) {
        val farbe = d.farbenEigen["kontakte"]?.let { farbeAusHex(it) } ?: FARBE_KONTAKTE
        for (k in b.kontakte) {
            if (!d.nativ.geburtstage && k.bezeichnung == "Geburtstag") continue
            for (jahr in von.year..bis.year) {
                val tag = runCatching { LocalDate.of(jahr, k.monat, k.tag) }.getOrNull() ?: continue
                if (tag.isBefore(von) || tag.isAfter(bis)) continue
                val jahre = k.jahr?.let { jahr - it }?.takeIf { it > 0 }
                val titel = if (k.bezeichnung == "Geburtstag") "🎂 " + k.name + (jahre?.let { " ($it)" } ?: "")
                else "🎉 " + k.name + " · " + k.bezeichnung + (jahre?.let { " ($it)" } ?: "")
                liste += Termin(
                    quelleId = "kontakte", quelleName = "Geburtstage & Anlässe", farbe = farbe, titel = titel,
                    uid = "k:" + k.kontaktId + ":" + k.bezeichnung + ":" + jahr, start = tag.atStartOfDay(),
                    ende = tag.plusDays(1).atStartOfDay(), ganztags = true, istKontakt = true,
                )
            }
        }
    }

    // 5) Aufgaben mit Fälligkeitsdatum
    if (mitAufgaben && d.kalender.todosImKalender) {
        for (t in d.todos.eintraege) {
            if (t.erledigt && !d.kalender.erledigteImKalender) continue
            val tag = parseDE(t.faellig) ?: continue
            if (tag.isBefore(von) || tag.isAfter(bis)) continue
            val zeit = zeitAus(t.uhrzeit)
            val s = if (zeit != null) tag.atTime(zeit) else tag.atStartOfDay()
            val e = if (zeit != null) s.plusHours(1) else tag.plusDays(1).atStartOfDay()
            liste += Termin(
                quelleId = "todo", quelleName = "Aufgabe",
                farbe = if (t.erledigt) FARBE_TODO_ERLEDIGT else if (t.prio == "hoch") FARBE_TODO_HOCH else FARBE_TODO,
                titel = (if (t.erledigt) "✓ " else "☐ ") + t.text.ifBlank { "(ohne Titel)" }, uid = "todo:" + t.id,
                start = s, ende = e, ganztags = zeit == null, todoId = t.id, notiz = t.notiz,
            )
        }
    }
    return liste.sortedWith(compareBy({ it.start }, { !it.ganztags }))
}

/** Termine, die an einem bestimmten Tag liegen. */
fun termineAm(b: TerminBestand, tag: LocalDate, mitAufgaben: Boolean): List<Termin> =
    terminFenster(b, tag, tag, mitAufgaben).filter { it.liegtAuf(tag) }
        .sortedWith(compareBy({ !it.ganztags }, { it.start }))
