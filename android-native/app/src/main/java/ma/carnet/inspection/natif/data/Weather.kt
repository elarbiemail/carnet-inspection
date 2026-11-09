package ma.carnet.inspection.natif.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Météo de l'accueil. Services : Open-Meteo (gratuit, sans clé — par défaut) ou OpenWeatherMap (clé personnelle).
 * Position : ville cherchée par son nom, coordonnées saisies, ou position du téléphone.
 */
object Weather {
    val version = mutableIntStateOf(0)
    val PROVIDERS = linkedMapOf("openmeteo" to "Open-Meteo (gratuit, sans clé)", "owm" to "OpenWeatherMap (clé API)")

    /** Catégories d'état du ciel (servent au fond animé et aux pictogrammes). */
    enum class Sky(val label: String) { CLEAR("Ensoleillé"), PARTLY("Éclaircies"), CLOUDY("Nuageux"), FOG("Brouillard"), DRIZZLE("Bruine"), RAIN("Pluie"), SNOW("Neige"), STORM("Orage") }

    data class Day(val date: LocalDate, val sky: Sky, val tmin: Double, val tmax: Double, val pop: Int, val sunrise: String, val sunset: String, val wind: Double)
    data class Data(
        val place: String, val temp: Double, val feels: Double, val humidity: Int, val wind: Double, val windDir: Int, val pressure: Int,
        val clouds: Int, val precip: Double, val isDay: Boolean, val sky: Sky, val desc: String, val days: List<Day>, val at: String, val provider: String
    )

    private fun p(ctx: Context) = ctx.getSharedPreferences("carnet", Context.MODE_PRIVATE)
    fun enabled(ctx: Context) = p(ctx).getBoolean("meteoOn", true)
    fun setEnabled(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("meteoOn", v).apply(); version.intValue++ }
    fun provider(ctx: Context) = p(ctx).getString("meteoProvider", "openmeteo") ?: "openmeteo"
    fun setProvider(ctx: Context, v: String) { p(ctx).edit().putString("meteoProvider", v).remove("meteoCache").apply(); version.intValue++ }
    fun apiKey(ctx: Context) = p(ctx).getString("meteoKey", "") ?: ""
    fun setApiKey(ctx: Context, v: String) { p(ctx).edit().putString("meteoKey", v.trim()).remove("meteoCache").apply(); version.intValue++ }
    /** "ville" (nom cherché), "gps" (position du téléphone) ou "coords" (saisie). */
    fun mode(ctx: Context) = p(ctx).getString("meteoMode", "ville") ?: "ville"
    fun place(ctx: Context) = p(ctx).getString("meteoPlace", "Sefrou") ?: "Sefrou"
    fun lat(ctx: Context) = p(ctx).getFloat("meteoLat", 33.83f).toDouble()
    fun lon(ctx: Context) = p(ctx).getFloat("meteoLon", -4.83f).toDouble()
    fun setPlace(ctx: Context, mode: String, name: String, lat: Double, lon: Double) {
        p(ctx).edit().putString("meteoMode", mode).putString("meteoPlace", name).putFloat("meteoLat", lat.toFloat()).putFloat("meteoLon", lon.toFloat()).remove("meteoCache").apply()
        version.intValue++
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 20000
        val code = c.responseCode
        val t = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
        if (code !in 200..299) throw Exception(when (code) { 401 -> "Clé météo refusée."; 429 -> "Quota du service météo atteint."; else -> "Service météo ($code)" })
        return t
    }

    /** Recherche d'une ville (géocodage Open-Meteo, gratuit). */
    suspend fun searchPlace(q: String): List<Triple<String, Double, Double>> = withContext(Dispatchers.IO) {
        val j = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=8&language=fr&name=" + URLEncoder.encode(q, "UTF-8")))
        val a = j.optJSONArray("results") ?: JSONArray()
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { r ->
            Triple(listOf(r.optString("name"), r.optString("admin1"), r.optString("country")).filter { it.isNotEmpty() }.distinct().joinToString(", "), r.optDouble("latitude"), r.optDouble("longitude"))
        }
    }

    /** Dernière position connue du téléphone (autorisation de localisation requise). */
    @SuppressLint("MissingPermission")
    fun lastLocation(ctx: Context): Pair<Double, Double>? = try {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { try { lm.getLastKnownLocation(it) } catch (_: Exception) { null } }.maxByOrNull { it.time }?.let { it.latitude to it.longitude }
    } catch (_: Exception) { null }

    fun wmo(code: Int): Sky = when (code) {
        0 -> Sky.CLEAR; 1, 2 -> Sky.PARTLY; 3 -> Sky.CLOUDY; 45, 48 -> Sky.FOG; in 51..57 -> Sky.DRIZZLE
        in 61..67, in 80..82 -> Sky.RAIN; in 71..77, 85, 86 -> Sky.SNOW; in 95..99 -> Sky.STORM; else -> Sky.CLOUDY
    }
    private val WMO_DESC = mapOf(0 to "Ciel dégagé", 1 to "Plutôt dégagé", 2 to "Partiellement nuageux", 3 to "Couvert", 45 to "Brouillard", 48 to "Brouillard givrant",
        51 to "Bruine légère", 53 to "Bruine", 55 to "Bruine dense", 61 to "Pluie faible", 63 to "Pluie", 65 to "Forte pluie", 66 to "Pluie verglaçante", 67 to "Pluie verglaçante forte",
        71 to "Neige faible", 73 to "Neige", 75 to "Forte neige", 77 to "Grains de neige", 80 to "Averses", 81 to "Averses", 82 to "Violentes averses", 85 to "Averses de neige", 86 to "Fortes averses de neige",
        95 to "Orage", 96 to "Orage avec grêle", 99 to "Orage violent avec grêle")
    private fun owm(id: Int): Sky = when (id) { in 200..299 -> Sky.STORM; in 300..399 -> Sky.DRIZZLE; in 500..599 -> Sky.RAIN; in 600..699 -> Sky.SNOW; in 700..799 -> Sky.FOG; 800 -> Sky.CLEAR; 801, 802 -> Sky.PARTLY; else -> Sky.CLOUDY }

    fun cached(ctx: Context): Data? = try { p(ctx).getString("meteoCache", null)?.let { parse(JSONObject(it)) } } catch (_: Exception) { null }

    /** Météo à jour (cache de 30 minutes). */
    suspend fun load(ctx: Context, force: Boolean = false): Data = withContext(Dispatchers.IO) {
        val pr = p(ctx)
        if (!force) pr.getString("meteoCache", null)?.let { c -> val j = JSONObject(c); if (System.currentTimeMillis() - j.optLong("ts") < 30 * 60_000) return@withContext parse(j) }
        var la = lat(ctx); var lo = lon(ctx); var name = place(ctx)
        if (mode(ctx) == "gps") lastLocation(ctx)?.let { (a, b) -> la = a; lo = b; name = "Ma position" }
        val j = if (provider(ctx) == "owm") fetchOwm(ctx, la, lo, name) else fetchOpenMeteo(la, lo, name)
        j.put("ts", System.currentTimeMillis())
        pr.edit().putString("meteoCache", j.toString()).apply()
        parse(j)
    }

    // format interne commun (JSON mis en cache)
    private fun fetchOpenMeteo(la: Double, lo: Double, name: String): JSONObject {
        val r = JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=$la&longitude=$lo&timezone=auto&forecast_days=7" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,wind_speed_10m_max"))
        val c = r.getJSONObject("current"); val d = r.getJSONObject("daily")
        val days = JSONArray()
        val n = d.getJSONArray("time").length()
        for (i in 0 until n) days.put(JSONObject().put("date", d.getJSONArray("time").getString(i)).put("code", d.getJSONArray("weather_code").optInt(i))
            .put("tmin", d.getJSONArray("temperature_2m_min").optDouble(i)).put("tmax", d.getJSONArray("temperature_2m_max").optDouble(i))
            .put("pop", d.optJSONArray("precipitation_probability_max")?.optInt(i) ?: 0).put("sunrise", d.getJSONArray("sunrise").optString(i).takeLast(5))
            .put("sunset", d.getJSONArray("sunset").optString(i).takeLast(5)).put("wind", d.optJSONArray("wind_speed_10m_max")?.optDouble(i) ?: 0.0).put("src", "wmo"))
        val code = c.optInt("weather_code")
        return JSONObject().put("place", name).put("temp", c.optDouble("temperature_2m")).put("feels", c.optDouble("apparent_temperature")).put("hum", c.optInt("relative_humidity_2m"))
            .put("wind", c.optDouble("wind_speed_10m")).put("windDir", c.optInt("wind_direction_10m")).put("pressure", c.optDouble("pressure_msl").toInt()).put("clouds", c.optInt("cloud_cover"))
            .put("precip", c.optDouble("precipitation")).put("day", c.optInt("is_day", 1) == 1).put("code", code).put("src", "wmo").put("desc", WMO_DESC[code] ?: "")
            .put("days", days).put("provider", "Open-Meteo")
    }

    private fun fetchOwm(ctx: Context, la: Double, lo: Double, name: String): JSONObject {
        val k = apiKey(ctx); if (k.isBlank()) throw Exception("Saisissez la clé OpenWeatherMap dans Réglages → Météo.")
        val c = JSONObject(get("https://api.openweathermap.org/data/2.5/weather?lat=$la&lon=$lo&units=metric&lang=fr&appid=$k"))
        val f = JSONObject(get("https://api.openweathermap.org/data/2.5/forecast?lat=$la&lon=$lo&units=metric&lang=fr&appid=$k"))
        val tz = f.optJSONObject("city")?.optInt("timezone") ?: 0
        val byDay = linkedMapOf<String, MutableList<JSONObject>>()
        val l = f.optJSONArray("list") ?: JSONArray()
        for (i in 0 until l.length()) { val o = l.getJSONObject(i); val d = LocalDateTime.ofEpochSecond(o.getLong("dt") + tz, 0, ZoneOffset.UTC).toLocalDate().toString(); byDay.getOrPut(d) { mutableListOf() }.add(o) }
        val sr = c.optJSONObject("sys")?.optLong("sunrise") ?: 0; val ss = c.optJSONObject("sys")?.optLong("sunset") ?: 0
        fun hm(t: Long) = if (t == 0L) "" else LocalDateTime.ofEpochSecond(t + tz, 0, ZoneOffset.UTC).toLocalTime().toString().take(5)
        val days = JSONArray()
        byDay.entries.take(6).forEach { (d, xs) ->
            val mid = xs.minByOrNull { kotlin.math.abs(LocalDateTime.ofEpochSecond(it.getLong("dt") + tz, 0, ZoneOffset.UTC).hour - 13) } ?: xs[0]
            days.put(JSONObject().put("date", d).put("code", mid.getJSONArray("weather").getJSONObject(0).optInt("id")).put("src", "owm")
                .put("tmin", xs.minOf { it.getJSONObject("main").optDouble("temp_min") }).put("tmax", xs.maxOf { it.getJSONObject("main").optDouble("temp_max") })
                .put("pop", (xs.maxOf { it.optDouble("pop", 0.0) } * 100).toInt()).put("sunrise", if (d == byDay.keys.first()) hm(sr) else "").put("sunset", if (d == byDay.keys.first()) hm(ss) else "")
                .put("wind", xs.maxOf { it.optJSONObject("wind")?.optDouble("speed") ?: 0.0 } * 3.6))
        }
        val w = c.getJSONArray("weather").getJSONObject(0); val m = c.getJSONObject("main")
        val now = System.currentTimeMillis() / 1000
        return JSONObject().put("place", if (name == "Ma position") c.optString("name").ifEmpty { name } else name).put("temp", m.optDouble("temp")).put("feels", m.optDouble("feels_like"))
            .put("hum", m.optInt("humidity")).put("wind", (c.optJSONObject("wind")?.optDouble("speed") ?: 0.0) * 3.6).put("windDir", c.optJSONObject("wind")?.optInt("deg") ?: 0)
            .put("pressure", m.optInt("pressure")).put("clouds", c.optJSONObject("clouds")?.optInt("all") ?: 0).put("precip", c.optJSONObject("rain")?.optDouble("1h") ?: 0.0)
            .put("day", sr == 0L || now in sr..ss).put("code", w.optInt("id")).put("src", "owm").put("desc", w.optString("description").replaceFirstChar { it.uppercase() })
            .put("days", days).put("provider", "OpenWeatherMap")
    }

    private fun parse(j: JSONObject): Data {
        fun sky(o: JSONObject) = if (o.optString("src") == "owm") owm(o.optInt("code")) else wmo(o.optInt("code"))
        val a = j.optJSONArray("days") ?: JSONArray()
        val days = (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { d ->
            Day(LocalDate.parse(d.getString("date")), sky(d), d.optDouble("tmin"), d.optDouble("tmax"), d.optInt("pop"), d.optString("sunrise"), d.optString("sunset"), d.optDouble("wind"))
        }
        return Data(j.optString("place"), j.optDouble("temp"), j.optDouble("feels"), j.optInt("hum"), j.optDouble("wind"), j.optInt("windDir"), j.optInt("pressure"),
            j.optInt("clouds"), j.optDouble("precip"), j.optBoolean("day", true), sky(j), j.optString("desc").ifEmpty { sky(j).label }, days,
            java.time.Instant.ofEpochMilli(j.optLong("ts")).atZone(java.time.ZoneId.systemDefault()).toLocalTime().toString().take(5), j.optString("provider"))
    }

    fun windDir(deg: Int) = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")[((deg % 360 + 22) / 45) % 8]
}
