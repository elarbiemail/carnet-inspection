package ma.carnet.inspection.natif.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.carnet.inspection.natif.data.*
import java.time.DayOfWeek
import java.time.LocalDate

/** Blocs de la page d'accueil : choix et ordre enregistrés dans les préférences. */
object HomeBlocks {
    val ALL = linkedMapOf(
        "meteo" to "Météo du jour et de la semaine", "kpi" to "Chiffres clés", "semaine" to "Tâches de la semaine",
        "avenir" to "À venir (14 jours)", "retard" to "En retard", "etabs" to "Établissements",
        "ens" to "Enseignant(e)s", "contacts" to "Contacts rapides (appel, WhatsApp, SMS)"
    )
    private val DEFAULT = listOf("meteo", "kpi", "avenir", "retard", "etabs", "ens")
    val version = mutableIntStateOf(0)
    private fun p(ctx: Context) = ctx.getSharedPreferences("carnet", Context.MODE_PRIVATE)
    fun list(ctx: Context): List<String> = p(ctx).getString("homeBlocks", null)?.split(",")?.filter { it in ALL } ?: DEFAULT
    fun save(ctx: Context, l: List<String>) { p(ctx).edit().putString("homeBlocks", l.joinToString(",")).apply(); version.intValue++ }
    fun reset(ctx: Context) { p(ctx).edit().remove("homeBlocks").apply(); version.intValue++ }
}

/** Accueil → Personnaliser : ajouter, retirer et ordonner les blocs. */
@Composable
fun HomeEditScreen() {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(HomeBlocks.list(ctx)) }
    val order = remember(on) { on + HomeBlocks.ALL.keys.filter { it !in on } }
    fun set(l: List<String>) { on = l; HomeBlocks.save(ctx, l) }
    ScreenScaffold("Personnaliser l'accueil", null) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 14.dp)) {
            item { Text("Cochez les blocs à afficher sur l'accueil et réglez leur ordre avec les flèches. Pour ajouter un widget sur l'écran du téléphone : appui long sur l'écran d'accueil → Widgets → Carnet d'Inspection.", color = InkFaint, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp)) }
            itemsIndexed(order, key = { _, k -> k }) { _, k ->
                val i = on.indexOf(k)
                CardBox {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(i >= 0, { c -> set(if (c) on + k else on - k) })
                        Picto.of(HomeBlocks.ALL[k] ?: "")?.let { Icon(it, null, tint = Brand, modifier = Modifier.size(20.dp)) }
                        Text(HomeBlocks.ALL[k] ?: k, Modifier.weight(1f).padding(start = 8.dp), fontSize = 14.5.sp, fontWeight = if (i >= 0) FontWeight.SemiBold else FontWeight.Normal)
                        if (i >= 0) {
                            IconButton(onClick = { if (i > 0) set(on.toMutableList().also { it.add(i - 1, it.removeAt(i)) }) }, enabled = i > 0) { Icon(Icons.Outlined.KeyboardArrowUp, "Monter") }
                            IconButton(onClick = { if (i < on.size - 1) set(on.toMutableList().also { it.add(i + 1, it.removeAt(i)) }) }, enabled = i < on.size - 1) { Icon(Icons.Outlined.KeyboardArrowDown, "Descendre") }
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { HomeBlocks.reset(ctx); on = HomeBlocks.list(ctx) }, modifier = Modifier.padding(vertical = 12.dp)) { BtnText("Rétablir l'accueil par défaut") }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

/** Tâches de la semaine en cours (lundi → dimanche), regroupées par jour. */
@Composable
fun WeekTasksBlock(tasks: List<org.json.JSONObject>) {
    val mon = LocalDate.now().with(DayOfWeek.MONDAY)
    val week = tasks.mapNotNull { t -> parseDate(t.s("echeance"))?.let { d -> if (!d.isBefore(mon) && d.isBefore(mon.plusDays(7))) d to t else null } }
        .sortedBy { it.first.toString() + taskTime(it.second) }
    SectionTitle("Tâches de la semaine")
    if (week.isEmpty()) { EmptyState("Aucune tâche cette semaine."); return }
    week.groupBy { it.first }.forEach { (d, l) ->
        Text(fmtLong(d.toString()).replaceFirstChar { it.uppercase() }, color = if (d == LocalDate.now()) Brand else InkFaint, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
        l.forEach { TaskCard(it.second) }
    }
}

/** Enseignant(e)s avec boutons appel, WhatsApp et SMS. */
@Composable
fun ContactsBlock(ens: List<org.json.JSONObject>) {
    val ctx = LocalContext.current
    val l = ens.filter { it.s("telephone").isNotBlank() || it.s("tel").isNotBlank() }.sortedBy { norm(it.s("nom")) }
    SectionTitle("Contacts rapides")
    if (l.isEmpty()) { EmptyState("Aucun numéro de téléphone enregistré."); return }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(l, key = { it.s("id") }) { e ->
            val tel = e.s("telephone").ifEmpty { e.s("tel") }
            Card(onClick = { Nav.push(Scr.EnsDetail(e.s("id"))) }, modifier = Modifier.width(170.dp).height(150.dp), colors = CardDefaults.cardColors(containerColor = Surface), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                    MarqueeText(e.s("nom"), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, center = true)
                    MarqueeText(etabName(e.s("etablissementId")), color = InkFaint, fontSize = 11.5.sp, center = true)
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        FilledTonalIconButton(onClick = { dial(ctx, tel) }) { Icon(Icons.Outlined.Phone, "Appeler") }
                        FilledTonalIconButton(onClick = { whatsapp(ctx, tel, greeting(e.s("nom"))) }) { Icon(Icons.Outlined.Chat, "WhatsApp") }
                        FilledTonalIconButton(onClick = { sms(ctx, tel, greeting(e.s("nom"))) }) { Icon(Icons.Outlined.Sms, "SMS") }
                    }
                }
            }
        }
    }
}

fun sms(ctx: Context, tel: String, msg: String = "") {
    if (tel.isBlank()) { toast(ctx, "Aucun numéro de téléphone."); return }
    try { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:" + tel.replace(" ", ""))).putExtra("sms_body", msg).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (e: Exception) { toast(ctx, "Aucune application de SMS.") }
}
