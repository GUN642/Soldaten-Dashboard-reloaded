package de.gun.dashboard.reloaded.netz

import de.gun.dashboard.reloaded.BuildConfig
import de.gun.dashboard.reloaded.daten.FerienAbschnitt
import de.gun.dashboard.reloaded.daten.JsonFormat
import de.gun.dashboard.reloaded.daten.Ort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Netz {
    suspend fun text(url: String, kopf: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        var ziel = url
        var umleitungen = 0
        while (true) {
            val c = URL(ziel).openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "SoldatenDashboardReloaded/" + BuildConfig.VERSION_NAME)
            kopf.forEach { (k, v) -> c.setRequestProperty(k, v) }
            val code = c.responseCode
            if (code in 300..399 && umleitungen < 5) {
                val neu = c.getHeaderField("Location") ?: break
                ziel = URL(URL(ziel), neu).toString()
                umleitungen++
                c.disconnect()
                continue
            }
            if (code !in 200..299) {
                val fehler = c.errorStream?.bufferedReader()?.use { it.readText() }?.take(120) ?: ""
                c.disconnect()
                throw IllegalStateException("HTTP $code $fehler".trim())
            }
            return@withContext c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
        throw IllegalStateException("Zu viele Weiterleitungen.")
    }

    suspend fun json(url: String, kopf: Map<String, String> = emptyMap()): JsonElement =
        JsonFormat.parseToJsonElement(text(url, kopf))

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}

fun JsonElement?.obj(): JsonObject? = this as? JsonObject
fun JsonElement?.arr(): JsonArray? = this as? JsonArray
fun JsonElement?.d(): Double? = (this as? JsonPrimitive)?.doubleOrNull
fun JsonElement?.s(): String? = (this as? JsonPrimitive)?.contentOrNull
fun JsonElement?.i(): Int? = (this as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }

// ---------------- Wetter (Open-Meteo) ----------------

data class WetterStunde(val zeit: String, val grad: Double?, val code: Int?, val regenWkt: Int?, val regenMm: Double?)
data class WetterTag(
    val datum: String, val code: Int?, val max: Double?, val min: Double?,
    val regenWkt: Int?, val regenMm: Double?, val aufgang: String?, val untergang: String?,
)
data class Wetter(
    val ort: String,
    val grad: Double?, val gefuehlt: Double?, val code: Int?, val wind: Double?, val feuchte: Double?,
    val stunden: List<WetterStunde>, val tage: List<WetterTag>, val abgerufen: Long,
)

object WetterDienst {
    private var zwischen: Wetter? = null

    suspend fun laden(ort: Ort, neu: Boolean): Wetter {
        val z = zwischen
        if (!neu && z != null && z.ort == ort.name && System.currentTimeMillis() - z.abgerufen < 15 * 60_000) return z
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${ort.breite}&longitude=${ort.laenge}" +
            "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,relative_humidity_2m" +
            "&hourly=temperature_2m,weather_code,precipitation_probability,precipitation" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,sunrise,sunset" +
            "&timezone=auto&models=best_match&forecast_days=7"
        val j = Netz.json(url).jsonObject
        val cur = j["current"].obj()
        val h = j["hourly"].obj()
        val d = j["daily"].obj()
        fun liste(o: JsonObject?, k: String) = o?.get(k).arr() ?: JsonArray(emptyList())
        val hz = liste(h, "time")
        val stunden = hz.indices.map { i ->
            WetterStunde(hz[i].s() ?: "", liste(h, "temperature_2m").getOrNull(i).d(), liste(h, "weather_code").getOrNull(i).i(),
                liste(h, "precipitation_probability").getOrNull(i).i(), liste(h, "precipitation").getOrNull(i).d())
        }
        val dz = liste(d, "time")
        val tage = dz.indices.map { i ->
            WetterTag(dz[i].s() ?: "", liste(d, "weather_code").getOrNull(i).i(), liste(d, "temperature_2m_max").getOrNull(i).d(),
                liste(d, "temperature_2m_min").getOrNull(i).d(), liste(d, "precipitation_probability_max").getOrNull(i).i(),
                liste(d, "precipitation_sum").getOrNull(i).d(), liste(d, "sunrise").getOrNull(i).s(), liste(d, "sunset").getOrNull(i).s())
        }
        val w = Wetter(
            ort.name, cur?.get("temperature_2m").d(), cur?.get("apparent_temperature").d(), cur?.get("weather_code").i(),
            cur?.get("wind_speed_10m").d(), cur?.get("relative_humidity_2m").d(), stunden, tage, System.currentTimeMillis()
        )
        zwischen = w
        return w
    }

    suspend fun ortSuchen(begriff: String): List<Ort> {
        val j = Netz.json("https://geocoding-api.open-meteo.com/v1/search?name=" + Netz.enc(begriff) + "&count=8&language=de&format=json")
        return (j.obj()?.get("results").arr() ?: return emptyList()).mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val zusatz = listOfNotNull(o["admin1"].s(), o["country"].s()).joinToString(", ")
            Ort(
                name = (o["name"].s() ?: return@mapNotNull null) + if (zusatz.isNotBlank()) " · $zusatz" else "",
                breite = o["latitude"].d() ?: return@mapNotNull null,
                laenge = o["longitude"].d() ?: return@mapNotNull null,
            )
        }
    }

    suspend fun ortsname(breite: Double, laenge: Double): String? = try {
        val j = Netz.json("https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$breite&longitude=$laenge&localityLanguage=de").obj()
        j?.get("city").s()?.takeIf { it.isNotBlank() } ?: j?.get("locality").s()?.takeIf { it.isNotBlank() }
            ?: j?.get("principalSubdivision").s()
    } catch (e: Exception) {
        null
    }

    private val CODES = mapOf(
        0 to ("☀️" to "klar"), 1 to ("🌤️" to "überwiegend klar"), 2 to ("⛅" to "teils bewölkt"), 3 to ("☁️" to "bedeckt"),
        45 to ("☁️" to "Nebel"), 48 to ("☁️" to "Reifnebel"),
        51 to ("🌦️" to "leichter Sprühregen"), 53 to ("🌦️" to "Sprühregen"), 55 to ("🌦️" to "starker Sprühregen"),
        56 to ("🌧️" to "gefrierender Sprühregen"), 57 to ("🌧️" to "gefrierender Sprühregen"),
        61 to ("🌦️" to "leichter Regen"), 63 to ("🌧️" to "Regen"), 65 to ("🌧️" to "starker Regen"),
        66 to ("🌧️" to "gefrierender Regen"), 67 to ("🌧️" to "gefrierender Regen"),
        71 to ("🌨️" to "leichter Schnee"), 73 to ("🌨️" to "Schnee"), 75 to ("❄️" to "starker Schnee"), 77 to ("🌨️" to "Schneegriesel"),
        80 to ("🌦️" to "leichte Schauer"), 81 to ("🌧️" to "Schauer"), 82 to ("⛈️" to "starke Schauer"),
        85 to ("🌨️" to "Schneeschauer"), 86 to ("🌨️" to "starke Schneeschauer"),
        95 to ("⛈️" to "Gewitter"), 96 to ("⛈️" to "Gewitter mit Hagel"), 99 to ("⛈️" to "schweres Gewitter"),
    )

    fun zeichen(code: Int?) = CODES[code]?.first ?: "·"
    fun text(code: Int?) = CODES[code]?.second ?: "unbekannt"
}

// ---------------- Schulferien (OpenHolidays) ----------------

object FerienDienst {
    suspend fun laden(land: String, jahr: Int): List<FerienAbschnitt> {
        val url = "https://openholidaysapi.org/SchoolHolidays?countryIsoCode=DE&subdivisionCode=DE-$land" +
            "&languageIsoCode=DE&validFrom=$jahr-01-01&validTo=$jahr-12-31"
        val arr = Netz.json(url, mapOf("Accept" to "application/json")).arr() ?: return emptyList()
        return arr.mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val name = o["name"].arr()?.firstOrNull().obj()?.get("text").s() ?: "Ferien"
            val von = o["startDate"].s() ?: return@mapNotNull null
            val bis = o["endDate"].s() ?: return@mapNotNull null
            FerienAbschnitt(name, von, bis)
        }
    }
}

// ---------------- Update-Prüfung (GitHub-Releases) ----------------

data class UpdateInfo(val version: String, val groesse: Long, val notizen: String, val seite: String)

object UpdateDienst {
    /** > 0, wenn a neuer ist als b. */
    fun vergleichen(a: String, b: String): Int {
        val x = Regex("\\d+").findAll(a).map { it.value.toInt() }.toList()
        val y = Regex("\\d+").findAll(b).map { it.value.toInt() }.toList()
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrElse(i) { 0 }
            val q = y.getOrElse(i) { 0 }
            if (p != q) return p - q
        }
        return 0
    }

    /** Liefert ein Update, falls eine neuere Version veröffentlicht ist; sonst null. */
    suspend fun pruefen(repo: String): UpdateInfo? {
        val j = Netz.json("https://api.github.com/repos/$repo/releases/latest", mapOf("Accept" to "application/vnd.github+json")).jsonObject
        val version = (j["tag_name"].s() ?: "").removePrefix("v").removePrefix("V")
        val apk = j["assets"].arr()?.mapNotNull { it.obj() }?.firstOrNull { (it["name"].s() ?: "").endsWith(".apk", true) }
            ?: throw IllegalStateException("Die neueste Veröffentlichung enthält keine APK-Datei.")
        if (vergleichen(version, BuildConfig.VERSION_NAME) <= 0) return null
        return UpdateInfo(
            version, (apk["size"] as? JsonPrimitive)?.longOrNull ?: 0L,
            (j["body"].s() ?: "").take(800), "https://github.com/$repo/releases"
        )
    }
}
