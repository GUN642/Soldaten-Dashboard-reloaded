package de.gun.dashboard.reloaded.logik

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

// =================== Zulu-Zeit und DTG ===================

object Dtg {
    private val MON = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
    private const val OST = "ABCDEFGHIKLM"   // J wird nicht verwendet
    private const val WEST = "NOPQRSTUVWXY"

    fun zone(versatzMin: Int): Char {
        val h = (versatzMin / 60.0).roundToInt()
        return when {
            h == 0 -> 'Z'
            h in 1..12 -> OST[h - 1]
            h in -12..-1 -> WEST[-h - 1]
            else -> 'Z'
        }
    }

    fun versatz(b: Char): Int? {
        val c = b.uppercaseChar()
        if (c == 'Z') return 0
        OST.indexOf(c).takeIf { it >= 0 }?.let { return (it + 1) * 60 }
        WEST.indexOf(c).takeIf { it >= 0 }?.let { return -(it + 1) * 60 }
        return null
    }

    fun bilden(z: ZonedDateTime, zulu: Boolean): String {
        val t = if (zulu) z.withZoneSameInstant(ZoneOffset.UTC) else z
        val b = if (zulu) 'Z' else zone(t.offset.totalSeconds / 60)
        return zwei(t.dayOfMonth) + zwei(t.hour) + zwei(t.minute) + b + MON[t.monthValue - 1] + (t.year % 100).toString().padStart(2, '0')
    }

    /** "101430ZAUG26" -> Zeitpunkt. */
    fun lesen(text: String): ZonedDateTime? {
        val t = text.uppercase().replace(Regex("\\s+"), "")
        val m = Regex("^(\\d{2})(\\d{2})(\\d{2})([A-Z])([A-Z]{3})(\\d{2}|\\d{4})$").matchEntire(t) ?: return null
        val (tag, std, min, zb, mon, jahrText) = m.destructured
        val v = versatz(zb[0]) ?: return null
        val monat = MON.indexOf(mon)
        if (monat < 0) return null
        var jahr = jahrText.toInt()
        if (jahr < 100) jahr += 2000
        return try {
            LocalDateTime.of(jahr, monat + 1, tag.toInt(), std.toInt(), min.toInt())
                .atOffset(ZoneOffset.ofTotalSeconds(v * 60)).atZoneSameInstant(ZoneId.systemDefault())
        } catch (e: Exception) {
            null
        }
    }
}

// =================== WGS84 <-> UTM <-> MGRS ===================

object Koordinaten {
    private const val A = 6378137.0
    private const val F = 1 / 298.257223563
    private val E2 = F * (2 - F)
    private const val K0 = 0.9996
    private const val BAENDER = "CDEFGHJKLMNPQRSTUVWX"
    private val SPALTEN = listOf("ABCDEFGH", "JKLMNPQR", "STUVWXYZ")
    private const val ZEILEN = "ABCDEFGHJKLMNPQRSTUV"

    data class Utm(val zone: Int, val band: Char?, val ost: Double, val nord: Double)
    data class Mgrs(val text: String)

    private fun rad(g: Double) = g * Math.PI / 180
    private fun grad(b: Double) = b * 180 / Math.PI

    fun band(breite: Double): Char? {
        if (breite < -80 || breite > 84) return null
        if (breite >= 72) return 'X'
        return BAENDER[floor((breite + 80) / 8).toInt()]
    }

    fun nachUtm(breite: Double, laenge: Double): Utm? {
        if (breite < -80 || breite > 84) return null
        var zone = floor((laenge + 180) / 6).toInt() + 1
        if (breite >= 56 && breite < 64 && laenge >= 3 && laenge < 12) zone = 32
        if (breite >= 72 && breite < 84) {
            zone = when {
                laenge >= 0 && laenge < 9 -> 31
                laenge >= 9 && laenge < 21 -> 33
                laenge >= 21 && laenge < 33 -> 35
                laenge >= 33 && laenge < 42 -> 37
                else -> zone
            }
        }
        val mm = (zone - 1) * 6 - 180 + 3
        val phi = rad(breite)
        val lam = rad(laenge - mm)
        val n = A / sqrt(1 - E2 * sin(phi) * sin(phi))
        val t = tan(phi) * tan(phi)
        val c = (E2 / (1 - E2)) * cos(phi) * cos(phi)
        val a = cos(phi) * lam
        val e2 = E2
        val m = A * ((1 - e2 / 4 - 3 * e2 * e2 / 64 - 5 * e2 * e2 * e2 / 256) * phi
            - (3 * e2 / 8 + 3 * e2 * e2 / 32 + 45 * e2 * e2 * e2 / 1024) * sin(2 * phi)
            + (15 * e2 * e2 / 256 + 45 * e2 * e2 * e2 / 1024) * sin(4 * phi)
            - (35 * e2 * e2 * e2 / 3072) * sin(6 * phi))
        val ep = e2 / (1 - e2)
        val ost = K0 * n * (a + (1 - t + c) * a.pow(3) / 6 + (5 - 18 * t + t * t + 72 * c - 58 * ep) * a.pow(5) / 120) + 500000.0
        var nord = K0 * (m + n * tan(phi) * (a * a / 2 + (5 - t + 9 * c + 4 * c * c) * a.pow(4) / 24
            + (61 - 58 * t + t * t + 600 * c - 330 * ep) * a.pow(6) / 720))
        if (breite < 0) nord += 10000000.0
        return Utm(zone, band(breite), ost, nord)
    }

    fun ausUtm(zone: Int, nordHalb: Boolean, ost: Double, nord: Double): Pair<Double, Double> {
        val e2 = E2
        val e1 = (1 - sqrt(1 - e2)) / (1 + sqrt(1 - e2))
        val x = ost - 500000.0
        val y = if (nordHalb) nord else nord - 10000000.0
        val m = y / K0
        val mu = m / (A * (1 - e2 / 4 - 3 * e2 * e2 / 64 - 5 * e2 * e2 * e2 / 256))
        val phi1 = mu + (3 * e1 / 2 - 27 * e1.pow(3) / 32) * sin(2 * mu) +
            (21 * e1 * e1 / 16 - 55 * e1.pow(4) / 32) * sin(4 * mu) +
            (151 * e1.pow(3) / 96) * sin(6 * mu) + (1097 * e1.pow(4) / 512) * sin(8 * mu)
        val ep = e2 / (1 - e2)
        val c1 = ep * cos(phi1) * cos(phi1)
        val t1 = tan(phi1) * tan(phi1)
        val n1 = A / sqrt(1 - e2 * sin(phi1) * sin(phi1))
        val r1 = A * (1 - e2) / (1 - e2 * sin(phi1) * sin(phi1)).pow(1.5)
        val d = x / (n1 * K0)
        val breite = phi1 - (n1 * tan(phi1) / r1) * (d * d / 2 - (5 + 3 * t1 + 10 * c1 - 4 * c1 * c1 - 9 * ep) * d.pow(4) / 24
            + (61 + 90 * t1 + 298 * c1 + 45 * t1 * t1 - 252 * ep - 3 * c1 * c1) * d.pow(6) / 720)
        val laenge = (d - (1 + 2 * t1 + c1) * d.pow(3) / 6 +
            (5 - 2 * c1 + 28 * t1 - 3 * c1 * c1 + 8 * ep + 24 * t1 * t1) * d.pow(5) / 120) / cos(phi1)
        val mm = (zone - 1) * 6 - 180 + 3
        return grad(breite) to (mm + grad(laenge))
    }

    private fun quadrat(zone: Int, ost: Double, nord: Double): String {
        val satz = (zone - 1) % 3
        val spalte = SPALTEN[satz][floor(ost / 100000).toInt() - 1]
        var zi = (floor(nord).toLong() % 2000000 / 100000).toInt()
        if (zone % 2 == 0) zi = (zi + 5) % 20
        return "$spalte${ZEILEN[zi]}"
    }

    fun nachMgrs(breite: Double, laenge: Double, stellen: Int = 5): String? {
        val u = nachUtm(breite, laenge) ?: return null
        val b = u.band ?: return null
        val q = try { quadrat(u.zone, u.ost, u.nord) } catch (e: Exception) { return null }
        val teiler = 10.0.pow(5 - stellen)
        val o = floor((u.ost % 100000) / teiler).toLong().toString().padStart(stellen, '0')
        val n = floor((u.nord % 100000) / teiler).toLong().toString().padStart(stellen, '0')
        return "${u.zone}$b $q $o $n"
    }

    fun ausMgrs(text: String): Pair<Double, Double>? {
        val t = text.uppercase().replace(Regex("\\s+"), "")
        val m = Regex("^(\\d{1,2})([C-HJ-NP-X])([A-HJ-NP-Z])([A-HJ-NP-V])(\\d*)$").matchEntire(t) ?: return null
        val zone = m.groupValues[1].toInt()
        val band = m.groupValues[2][0]
        val spalte = m.groupValues[3][0]
        val zeile = m.groupValues[4][0]
        val ziffern = m.groupValues[5]
        if (zone !in 1..60 || ziffern.length % 2 != 0) return null
        val stellen = ziffern.length / 2
        var ostRest = 0.0
        var nordRest = 0.0
        if (stellen > 0) {
            val teiler = 10.0.pow(5 - stellen)
            ostRest = ziffern.substring(0, stellen).toDouble() * teiler
            nordRest = ziffern.substring(stellen).toDouble() * teiler
        }
        val si = SPALTEN[(zone - 1) % 3].indexOf(spalte)
        if (si < 0) return null
        val ost = (si + 1) * 100000 + ostRest
        var zi = ZEILEN.indexOf(zeile)
        if (zi < 0) return null
        if (zone % 2 == 0) zi = (zi - 5 + 20) % 20
        val bandUnten = -80 + BAENDER.indexOf(band) * 8
        val nordHalb = bandUnten >= 0
        val basis = nachUtm(bandUnten.toDouble().coerceIn(-80.0, 84.0), ((zone - 1) * 6 - 180 + 3).toDouble())?.nord ?: 0.0
        var nord = zi * 100000 + nordRest
        while (nord < basis - 100000) nord += 2000000
        var g = ausUtm(zone, nordHalb, ost, nord)
        var versuche = 0
        while (band(g.first) != band && versuche < 3) {
            nord += 2000000
            g = ausUtm(zone, nordHalb, ost, nord)
            versuche++
        }
        return g
    }

    /** "52.5163", "52 30 58.6 N", "N 52 30.98" … */
    fun gradLesen(text: String, istBreite: Boolean): Double? {
        var t = text.trim().uppercase()
        if (t.isEmpty()) return null
        var vz = 1.0
        if (Regex("[SW]").containsMatchIn(t)) vz = -1.0
        t = t.replace(Regex("[NSEWO]"), " ")
        if (t.contains("-")) vz = -1.0
        t = t.replace(Regex("[°'\"´`]"), " ").replace(",", ".").replace("-", " ")
        val z = t.split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.toDoubleOrNull() ?: return null }
        if (z.isEmpty()) return null
        val w = when (z.size) {
            1 -> z[0]
            2 -> z[0] + z[1] / 60
            else -> z[0] + z[1] / 60 + z[2] / 3600
        }
        val wert = abs(w) * vz
        val grenze = if (istBreite) 90.0 else 180.0
        return if (wert < -grenze || wert > grenze) null else wert
    }

    fun gradSchreiben(wert: Double, istBreite: Boolean): String {
        val r = if (istBreite) (if (wert >= 0) "N" else "S") else (if (wert >= 0) "E" else "W")
        val a = abs(wert)
        val g = floor(a).toInt()
        val mr = (a - g) * 60
        val m = floor(mr).toInt()
        val s = (mr - m) * 60
        return "$g° ${zwei(m)}' ${String.format(java.util.Locale.US, "%05.2f", s)}\" $r"
    }
}

// =================== Einheiten ===================

data class EinheitenPaar(val titel: String, val a: String, val b: String, val faktor: Double = 1.0, val temperatur: Boolean = false)

val EINHEITEN = listOf(
    EinheitenPaar("Länge", "Fuß (ft)", "Meter (m)", 0.3048),
    EinheitenPaar("Höhe", "Fuß (ft)", "Meter (m)", 0.3048),
    EinheitenPaar("Gewicht", "Pfund (lbs)", "Kilogramm (kg)", 0.45359237),
    EinheitenPaar("Strecke", "Seemeilen (NM)", "Kilometer (km)", 1.852),
    EinheitenPaar("Geschwindigkeit", "Knoten (kt)", "km/h", 1.852),
    EinheitenPaar("Temperatur", "Fahrenheit (°F)", "Celsius (°C)", temperatur = true),
    EinheitenPaar("Druck", "inHg", "hPa", 33.8639),
    EinheitenPaar("Volumen", "Gallonen (US)", "Liter", 3.785411784),
)

fun einheitRechnen(p: EinheitenPaar, vonA: Boolean, wert: String): String {
    val z = wert.replace(",", ".").trim().toDoubleOrNull() ?: return ""
    val e = if (p.temperatur) (if (vonA) (z - 32) * 5 / 9 else z * 9 / 5 + 32)
    else (if (vonA) z * p.faktor else z / p.faktor)
    val s = when {
        abs(e) >= 100 -> String.format(java.util.Locale.US, "%.1f", e)
        abs(e) >= 1 -> String.format(java.util.Locale.US, "%.2f", e)
        else -> String.format(java.util.Locale.US, "%.4f", e)
    }
    return s.toDouble().let { if (it == floor(it)) it.toLong().toString() else it.toString() }.replace(".", ",")
}

// =================== BFT-Bewertung ===================

object Bft {
    fun laufSekunden(text: String?): Int? {
        val t = (text ?: "").trim().replace(",", ".")
        if (t.isEmpty()) return null
        Regex("^(\\d{1,2})[:.](\\d{1,2})$").matchEntire(t)?.let { if (it.groupValues[2].toInt() < 60) return it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }
        Regex("^(\\d{1,2})(\\d{2})$").matchEntire(t)?.let { if (it.groupValues[2].toInt() < 60) return it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }
        return t.toIntOrNull()
    }

    fun sekundenText(s: Int) = "${s / 60}:${zwei(s % 60)}"

    /** 100 Punkte bei Mindestleistung, 400 bei Bestleistung, linear. */
    fun basisPunkte(wert: Double, mindest: Double, best: Double): Int? {
        val spanne = best - mindest
        if (spanne == 0.0) return null
        return maxOf(0, (100 + (wert - mindest) / spanne * 300).roundToInt())
    }

    /** Zuschläge: weiblich (Sprint/Lauf 15 %, Klimmhang 40 %), ab 36 Jahren 0,5 % je Jahr. */
    fun mitZuschlag(p: Int?, klimm: Boolean, weiblich: Boolean, alter: Int?): Int? {
        if (p == null) return null
        var f = 1.0
        if (weiblich) f += if (klimm) 0.40 else 0.15
        if (alter != null && alter >= 36) f += (alter - 35) * 0.005
        return (p * f).roundToInt()
    }

    fun note(p: Int): Double = when {
        p >= 400 -> maxOf(1.00, 1.49 - (p - 400) / 100.0 * 0.49)
        p >= 300 -> 2.49 - (p - 300) / 100.0 * 0.99
        p >= 200 -> 3.49 - (p - 200) / 100.0 * 0.99
        else -> 4.49 - (p - 100) / 100.0 * 0.99
    }

    fun noteWort(n: Double) = when {
        n <= 1.49 -> "sehr gute Leistung"
        n <= 2.49 -> "gute Leistung"
        n <= 3.49 -> "zufriedenstellende Leistung"
        else -> "ausreichende Leistung"
    }

    fun noteText(n: Double) = String.format(java.util.Locale.GERMANY, "%.2f", n)
}

// =================== DUZ (§ 3 EZulV) ===================

object DuzRechner {
    val LABEL = linkedMapOf(
        "sonntag" to "Sonn-/Feiertag", "fest" to "Ostersa./Pfingstsa./24.,31.12.",
        "samstag" to "Samstag ab 13 Uhr", "nacht" to "Nacht (20–6 Uhr)"
    )

    fun kategorie(tag: LocalDate, minute: Int, land: String): String? {
        if (tag.dayOfWeek == java.time.DayOfWeek.SUNDAY || Feiertage.ist(tag, land)) return "sonntag"
        val o = Feiertage.ostersonntag(tag.year)
        val besonders = tag == o.minusDays(1) || tag == o.plusDays(48) ||
            (tag.monthValue == 12 && (tag.dayOfMonth == 24 || tag.dayOfMonth == 31))
        if (besonders && minute >= 12 * 60) return "fest"
        if (tag.dayOfWeek == java.time.DayOfWeek.SATURDAY && minute >= 13 * 60) return "samstag"
        if (minute >= 20 * 60 || minute < 6 * 60) return "nacht"
        return null
    }

    /** Minuten je Kategorie; Schlüssel wie in [LABEL] plus "gesamt". */
    fun zerlegen(datum: String, von: String, bis: String, land: String): Map<String, Int>? {
        val d = parseDE(datum) ?: return null
        val a = zeitAus(von) ?: return null
        val b = zeitAus(bis) ?: return null
        val beginn = d.atTime(a)
        var ende = d.atTime(b)
        if (!ende.isAfter(beginn)) ende = ende.plusDays(1)
        val erg = mutableMapOf("sonntag" to 0, "samstag" to 0, "fest" to 0, "nacht" to 0, "gesamt" to 0)
        val grenzen = listOf(0, 6 * 60, 12 * 60, 13 * 60, 20 * 60, 24 * 60)
        var zeiger = beginn
        while (zeiger.isBefore(ende)) {
            val tag = zeiger.toLocalDate()
            val minute = zeiger.hour * 60 + zeiger.minute
            val naechste = grenzen.first { it > minute }
            val grenzZeit = tag.atStartOfDay().plusMinutes(naechste.toLong())
            val abschnittEnde = if (grenzZeit.isBefore(ende)) grenzZeit else ende
            val dauer = java.time.Duration.between(zeiger, abschnittEnde).toMinutes().toInt()
            if (dauer > 0) {
                kategorie(tag, minute, land)?.let { erg[it] = erg.getValue(it) + dauer }
                erg["gesamt"] = erg.getValue("gesamt") + dauer
            }
            zeiger = abschnittEnde
        }
        return erg
    }

    fun stunden(min: Int) = "${min / 60}:${zwei(min % 60)} Std."
}
