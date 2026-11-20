package ma.carnet.inspection.natif.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ma.carnet.inspection.natif.data.Weather
import ma.carnet.inspection.natif.data.Weather.Sky
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

fun skyIcon(s: Sky, day: Boolean = true): ImageVector = when (s) {
    Sky.CLEAR -> if (day) Icons.Outlined.WbSunny else Icons.Outlined.NightsStay
    Sky.PARTLY -> Icons.Outlined.WbCloudy; Sky.CLOUDY -> Icons.Outlined.Cloud; Sky.FOG -> Icons.Outlined.Dehaze
    Sky.DRIZZLE -> Icons.Outlined.Grain; Sky.RAIN -> Icons.Outlined.Umbrella; Sky.SNOW -> Icons.Outlined.AcUnit; Sky.STORM -> Icons.Outlined.FlashOn
}
private fun skyColors(s: Sky, day: Boolean): List<Color> = if (!day) when (s) {
    Sky.CLEAR, Sky.PARTLY -> listOf(Color(0xFF0B1A3A), Color(0xFF243B6B))
    Sky.STORM -> listOf(Color(0xFF111318), Color(0xFF2B2F3A))
    else -> listOf(Color(0xFF1C2230), Color(0xFF3A4252))
} else when (s) {
    Sky.CLEAR -> listOf(Color(0xFF2E8DE6), Color(0xFF8CCBFF))
    Sky.PARTLY -> listOf(Color(0xFF4A90D0), Color(0xFFA9CDEB))
    Sky.CLOUDY -> listOf(Color(0xFF7D8A99), Color(0xFFB9C3CE))
    Sky.FOG -> listOf(Color(0xFF9AA3A8), Color(0xFFD3D7D9))
    Sky.DRIZZLE, Sky.RAIN -> listOf(Color(0xFF4F5D6E), Color(0xFF8796A6))
    Sky.SNOW -> listOf(Color(0xFF8FA6BD), Color(0xFFE3ECF3))
    Sky.STORM -> listOf(Color(0xFF2E3440), Color(0xFF5A6273))
}
private fun fmt(t: Double) = if (t.isNaN()) "–" else "${t.roundToInt()}°"

private fun DrawScope.cloud(cx: Float, cy: Float, r: Float, c: Color) {
    drawCircle(c, r, Offset(cx, cy)); drawCircle(c, r * 0.8f, Offset(cx - r * 1.0f, cy + r * 0.25f)); drawCircle(c, r * 0.85f, Offset(cx + r * 1.05f, cy + r * 0.2f))
    drawRoundRect(c, Offset(cx - r * 1.8f, cy + r * 0.1f), Size(r * 3.7f, r * 0.95f), androidx.compose.ui.geometry.CornerRadius(r * 0.5f))
}

/** Fond animé selon l'état du ciel (soleil, lune et étoiles, nuages, pluie, neige, orage, brouillard). */
@Composable
fun SkyBackground(sky: Sky, day: Boolean, modifier: Modifier) {
    val tr = rememberInfiniteTransition(label = "ciel")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(if (sky == Sky.SNOW) 9000 else 1400, easing = LinearEasing)), label = "t")
    val drift by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse), label = "d")
    val cols = skyColors(sky, day)
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(cols))
        val w = size.width; val h = size.height
        val sunC = Offset(w * 0.82f, h * 0.24f)
        if (sky == Sky.CLEAR || sky == Sky.PARTLY) {
            if (day) {
                drawCircle(Brush.radialGradient(listOf(Color(0x66FFF3B0), Color.Transparent), sunC, h * 0.55f), h * 0.55f, sunC)
                drawCircle(Color(0xFFFFD54F), h * 0.13f, sunC)
            } else {
                for (i in 0 until 26) { val x = ((i * 137) % 100) / 100f * w; val y = ((i * 71) % 60) / 100f * h; drawCircle(Color.White.copy(alpha = 0.35f + 0.5f * (((i * 13) % 10) / 10f)), 1.6f + (i % 3), Offset(x, y)) }
                drawCircle(Color(0xFFF5F3CE), h * 0.11f, sunC); drawCircle(cols[0], h * 0.10f, Offset(sunC.x + h * 0.05f, sunC.y - h * 0.03f))
            }
        }
        val cloudC = when (sky) { Sky.STORM -> Color(0xFF3C4250); Sky.RAIN, Sky.DRIZZLE -> Color(0xFFB0B9C4); else -> Color.White.copy(alpha = if (day) 0.85f else 0.25f) }
        when (sky) {
            Sky.PARTLY -> cloud(w * (0.62f + 0.06f * drift), h * 0.36f, h * 0.11f, cloudC)
            Sky.CLOUDY, Sky.RAIN, Sky.DRIZZLE, Sky.SNOW, Sky.STORM -> {
                cloud(w * (0.20f + 0.05f * drift), h * 0.20f, h * 0.10f, cloudC.copy(alpha = cloudC.alpha * 0.8f))
                cloud(w * (0.72f - 0.05f * drift), h * 0.26f, h * 0.13f, cloudC)
                cloud(w * (0.48f + 0.04f * drift), h * 0.10f, h * 0.08f, cloudC.copy(alpha = cloudC.alpha * 0.7f))
            }
            Sky.FOG -> for (i in 0 until 6) { val y = h * (0.18f + i * 0.13f); drawLine(Color.White.copy(alpha = 0.35f), Offset(w * (0.05f + 0.05f * drift * (i % 2)), y), Offset(w * (0.75f + 0.2f * drift), y), h * 0.035f, StrokeCap.Round) }
            else -> {}
        }
        if (sky == Sky.RAIN || sky == Sky.DRIZZLE || sky == Sky.STORM) {
            val n = if (sky == Sky.DRIZZLE) 22 else 40
            for (i in 0 until n) {
                val x = ((i * 53) % 100) / 100f * w; val base = ((i * 37) % 100) / 100f
                val y = ((base + t) % 1f) * h; val len = if (sky == Sky.DRIZZLE) h * 0.03f else h * 0.07f
                drawLine(Color(0xAADDE8F5), Offset(x, y), Offset(x - len * 0.25f, y + len), if (sky == Sky.DRIZZLE) 1.6f else 2.2f, StrokeCap.Round)
            }
        }
        if (sky == Sky.SNOW) for (i in 0 until 34) {
            val x = ((i * 53) % 100) / 100f * w + 8f * kotlin.math.sin((t * 6.28f) + i); val y = ((((i * 37) % 100) / 100f + t) % 1f) * h
            drawCircle(Color.White.copy(alpha = 0.9f), 2.5f + (i % 3), Offset(x, y))
        }
        if (sky == Sky.STORM && t in 0.05f..0.12f) {
            val p = Path().apply { moveTo(w * 0.62f, h * 0.30f); lineTo(w * 0.56f, h * 0.52f); lineTo(w * 0.61f, h * 0.52f); lineTo(w * 0.54f, h * 0.78f) }
            drawPath(p, Color(0xFFFFF59D), style = Stroke(4f, cap = StrokeCap.Round))
            drawRect(Color.White.copy(alpha = 0.12f))
        }
    }
}

/** Bloc météo de l'accueil : météo du jour sur fond du ciel + prévisions de la semaine. */
@Composable
fun WeatherBlock() {
    val ctx = LocalContext.current
    val wv by Weather.version
    if (!Weather.enabled(ctx)) return
    var data by remember { mutableStateOf(Weather.cached(ctx)) }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var force by remember { mutableIntStateOf(0) }
    LaunchedEffect(wv, force) {
        busy = true; err = ""
        try { data = Weather.load(ctx, force > 0) } catch (e: Exception) { err = e.message ?: "Météo indisponible (connexion ?)" }
        busy = false
    }
    SectionTitle("Météo")
    val d = data
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { force++ }, shape = RoundedCornerShape(18.dp)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 190.dp)) {
            SkyBackground(d?.sky ?: Sky.PARTLY, d?.isDay ?: true, Modifier.matchParentSize())
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Place, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(d?.place ?: Weather.place(ctx), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp).weight(1f))
                    if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    else IconButton(onClick = { force++ }, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Refresh, "Actualiser la météo", tint = Color.White) }
                }
                if (d == null) {
                    Text(if (err.isNotEmpty()) err else "Chargement de la météo…", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 30.dp))
                    if (err.isNotEmpty()) TextButton(onClick = { Nav.root("reglages", "meteo") }) { Text("Régler la météo", color = Color.White) }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Text(fmt(d.temp), color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Light)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(d.desc, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            d.days.firstOrNull()?.let { Text("Max ${fmt(it.tmax)} · Min ${fmt(it.tmin)}", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp) }
                        }
                    }
                    val today = d.days.firstOrNull()
                    // détails : grille régulière de tuiles (4 par ligne)
                    val tiles = buildList {
                        add(Triple(Icons.Outlined.Accessibility, "Ressenti", fmt(d.feels)))
                        add(Triple(Icons.Outlined.Opacity, "Humidité", "${d.humidity} %"))
                        add(Triple(Icons.Outlined.Navigation, "Vent", "${d.wind.roundToInt()} km/h"))
                        add(Triple(Icons.Outlined.Explore, "Direction", Weather.windDir(d.windDir)))
                        add(Triple(Icons.Outlined.Umbrella, "Pluie", if (today != null) "${today.pop} %" else "${d.precip} mm"))
                        add(Triple(Icons.Outlined.Cloud, "Nuages", "${d.clouds} %"))
                        if (!today?.sunrise.isNullOrEmpty()) add(Triple(Icons.Outlined.WbSunny, "Lever", today!!.sunrise))
                        if (!today?.sunset.isNullOrEmpty()) add(Triple(Icons.Outlined.NightsStay, "Coucher", today!!.sunset))
                    }
                    Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        tiles.chunked(4).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { (ic, l, v) -> Detail(ic, l, v, Modifier.weight(1f), rot = if (l == "Vent") d.windDir + 180f else 0f) }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    Text("${d.provider} · mis à jour à ${d.at}" + if (err.isNotEmpty()) " · hors ligne" else "", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
    if (d != null && d.days.size > 1) WeekChart(d.days)
}

@Composable
private fun Detail(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier, rot: Float = 0f) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.20f)).padding(vertical = 7.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(17.dp).rotate(rot))
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Réglages → Météo : affichage, service utilisé, clé API et position. */
@Composable
fun MeteoTab() {
    val ctx = LocalContext.current
    val wv by Weather.version
    val scope = rememberCoroutineScope()
    var on by remember(wv) { mutableStateOf(Weather.enabled(ctx)) }
    var prov by remember(wv) { mutableStateOf(Weather.provider(ctx)) }
    var key by remember { mutableStateOf(Weather.apiKey(ctx)) }
    var q by remember { mutableStateOf("") }
    var res by remember { mutableStateOf(listOf<Triple<String, Double, Double>>()) }
    var msg by remember { mutableStateOf("") }
    var la by remember(wv) { mutableStateOf(Weather.lat(ctx).toString()) }
    var lo by remember(wv) { mutableStateOf(Weather.lon(ctx).toString()) }
    val useGps = {
        val p = Weather.lastLocation(ctx)
        if (p == null) msg = "Position indisponible : activez la localisation du téléphone puis réessayez."
        else { Weather.setPlace(ctx, "gps", "Ma position", p.first, p.second); msg = "Position du téléphone utilisée." }
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) useGps() else msg = "Autorisation de localisation refusée." }

    SectionTitle("Affichage")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Afficher la météo sur l'accueil", Modifier.weight(1f))
        Switch(on, { on = it; Weather.setEnabled(ctx, it) })
    }
    SectionTitle("Service météo")
    Weather.PROVIDERS.forEach { (k, l) ->
        Row(Modifier.fillMaxWidth().clickable { prov = k; Weather.setProvider(ctx, k) }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(prov == k, { prov = k; Weather.setProvider(ctx, k) }); Text(l)
        }
    }
    if (prov == "owm") {
        OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("Clé API OpenWeatherMap") }, singleLine = true)
        Row {
            Button(onClick = { Weather.setApiKey(ctx, key); msg = "Clé enregistrée." }) { BtnText("Enregistrer la clé") }
            TextButton(onClick = { openUri(ctx, "https://home.openweathermap.org/api_keys") }) { Text("Obtenir une clé gratuite", maxLines = 1) }
        }
    } else Text("Open-Meteo est gratuit et ne demande aucune clé.", color = InkFaint, fontSize = 13.sp)

    SectionTitle("Position")
    Text("Actuelle : " + (if (Weather.mode(ctx) == "gps") "position du téléphone" else Weather.place(ctx) + "  (" + "%.3f".format(Weather.lat(ctx)) + ", " + "%.3f".format(Weather.lon(ctx)) + ")"), fontSize = 14.sp)
    OutlinedButton(onClick = { perm.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }, modifier = Modifier.padding(top = 6.dp)) {
        Icon(Icons.Outlined.MyLocation, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Utiliser la position du téléphone", maxLines = 1)
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        OutlinedTextField(q, { q = it }, Modifier.weight(1f), label = { Text("Ville ou commune") }, singleLine = true)
        IconButton(onClick = { scope.launch { msg = ""; res = try { Weather.searchPlace(q.trim()) } catch (e: Exception) { msg = e.message ?: "Recherche impossible."; emptyList() }; if (res.isEmpty() && msg.isEmpty()) msg = "Aucun lieu trouvé." } }) { Icon(Icons.Outlined.Search, "Rechercher le lieu") }
    }
    res.forEach { (n, a, b) ->
        Text(n, Modifier.fillMaxWidth().clickable { Weather.setPlace(ctx, "ville", n.substringBefore(","), a, b); res = emptyList(); msg = "Lieu choisi : $n" }.padding(vertical = 10.dp, horizontal = 6.dp), fontSize = 14.sp)
        HorizontalDivider()
    }
    Text("Ou coordonnées (latitude, longitude) :", color = InkFaint, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(la, { la = it }, Modifier.weight(1f), label = { Text("Latitude") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OutlinedTextField(lo, { lo = it }, Modifier.weight(1f), label = { Text("Longitude") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    }
    OutlinedButton(onClick = {
        val a = la.replace(',', '.').toDoubleOrNull(); val b = lo.replace(',', '.').toDoubleOrNull()
        if (a == null || b == null || a !in -90.0..90.0 || b !in -180.0..180.0) msg = "Coordonnées invalides."
        else { Weather.setPlace(ctx, "coords", "%.3f, %.3f".format(a, b), a, b); msg = "Coordonnées enregistrées." }
    }) { Text("Utiliser ces coordonnées", maxLines = 1) }
    if (msg.isNotEmpty()) Text(msg, color = Brand, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
    SectionTitle("Aperçu")
    WeatherBlock()
}

/** Prévisions de la semaine en graphique : courbes des maximales et minimales, plage colorée, risque de pluie en barres. */
@Composable
fun WeekChart(days: List<Weather.Day>) {
    val tm = rememberTextMeasurer()
    val hot = Color(0xFFE8833A); val cold = Color(0xFF3D7CC9); val ink = Ink; val faint = InkFaint
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 8.dp)) {
            Row(Modifier.padding(start = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Prévisions sur ${days.size} jours", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(hot)); Text(" max", fontSize = 11.sp, color = faint)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(cold)); Text(" min", fontSize = 11.sp, color = faint)
            }
            // jours + pictogrammes
            Row(Modifier.fillMaxWidth()) {
                days.forEachIndexed { i, day ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (i == 0) "Auj." else day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.FRENCH).replaceFirstChar { it.uppercase() }.trimEnd('.'),
                            fontWeight = if (i == 0) FontWeight.Bold else FontWeight.SemiBold, fontSize = 12.sp, color = if (i == 0) Brand else ink, maxLines = 1)
                        Text("${day.date.dayOfMonth}", fontSize = 10.5.sp, color = faint)
                        Icon(skyIcon(day.sky), day.sky.label, tint = when (day.sky) { Sky.CLEAR -> Color(0xFFF2A900); Sky.STORM -> Color(0xFF6A5ACD); Sky.RAIN, Sky.DRIZZLE -> cold; Sky.SNOW -> Color(0xFF7FA7C9); else -> faint },
                            modifier = Modifier.padding(top = 4.dp).size(24.dp))
                    }
                }
            }
            // courbes de température
            val maxs = days.map { it.tmax }; val mins = days.map { it.tmin }
            val hi = (maxs.filter { !it.isNaN() }.maxOrNull() ?: 30.0); val lo = (mins.filter { !it.isNaN() }.minOrNull() ?: 0.0)
            Canvas(Modifier.fillMaxWidth().height(130.dp).padding(top = 4.dp)) {
                val n = days.size; val cw = size.width / n
                val top = 22.dp.toPx(); val bot = size.height - 22.dp.toPx()
                fun y(t: Double) = if (hi == lo) (top + bot) / 2 else (bot - ((t - lo) / (hi - lo)).toFloat() * (bot - top))
                fun x(i: Int) = cw * i + cw / 2
                fun curve(v: List<Double>): Path = Path().apply {
                    v.forEachIndexed { i, t -> val px = x(i); val py = y(t)
                        if (i == 0) moveTo(px, py) else { val ppx = x(i - 1); val ppy = y(v[i - 1]); val mx = (ppx + px) / 2; cubicTo(mx, ppy, mx, py, px, py) } }
                }
                // plage entre min et max
                val band = Path().apply {
                    addPath(curve(maxs))
                    for (i in n - 1 downTo 0) { val px = x(i); val py = y(mins[i]); if (i == n - 1) lineTo(px, py) else { val nx = x(i + 1); val ny = y(mins[i + 1]); val mx = (nx + px) / 2; cubicTo(mx, ny, mx, py, px, py) } }
                    close()
                }
                drawPath(band, Brush.verticalGradient(listOf(hot.copy(alpha = 0.22f), cold.copy(alpha = 0.18f)), top, bot))
                // repères verticaux discrets
                for (i in 0 until n) drawLine(faint.copy(alpha = 0.12f), Offset(x(i), top - 6f), Offset(x(i), bot + 6f), 1f)
                drawPath(curve(maxs), hot, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                drawPath(curve(mins), cold, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                days.forEachIndexed { i, day ->
                    listOf(day.tmax to hot, day.tmin to cold).forEachIndexed { k, (t, c) ->
                        val p = Offset(x(i), y(t))
                        drawCircle(Color.White, 4.5.dp.toPx(), p); drawCircle(c, 3.dp.toPx(), p)
                        val lay = tm.measure(fmt(t), androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = c))
                        drawText(lay, topLeft = Offset(p.x - lay.size.width / 2f, if (k == 0) p.y - lay.size.height - 5.dp.toPx() else p.y + 5.dp.toPx()))
                    }
                }
            }
            // risque de pluie
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.Bottom) {
                days.forEach { day ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.width(14.dp).height(28.dp).clip(RoundedCornerShape(4.dp)).background(cold.copy(alpha = 0.10f)), contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.fillMaxWidth().fillMaxHeight((day.pop.coerceIn(0, 100) / 100f).coerceAtLeast(0.04f)).background(cold.copy(alpha = 0.75f)))
                        }
                        Text("${day.pop} %", fontSize = 10.5.sp, color = cold, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
            Row(Modifier.padding(start = 6.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Umbrella, null, tint = faint, modifier = Modifier.size(13.dp)); Text(" Risque de pluie", fontSize = 11.sp, color = faint)
            }
        }
    }
}
