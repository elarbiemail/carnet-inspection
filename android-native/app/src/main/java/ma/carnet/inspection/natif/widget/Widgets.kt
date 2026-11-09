package ma.carnet.inspection.natif.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import ma.carnet.inspection.natif.MainActivity
import ma.carnet.inspection.natif.data.*
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

// ---------- couleurs et outils communs ----------
private val BRAND = Color(0xFF2F6F62)
private val INK = Color(0xFF1F2523)
private val FAINT = Color(0xFF6B7470)
private val BG = Color(0xFFF6F4EE)
private val CARD = Color(0xFFFFFFFF)
private val DANGER = Color(0xFFB3261E)
private fun c(x: Color) = ColorProvider(x)
private fun ts(size: Int, color: Color = INK, bold: Boolean = false, center: Boolean = false) =
    TextStyle(color = c(color), fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, textAlign = if (center) TextAlign.Center else TextAlign.Start)

private fun openApp(ctx: Context, sec: String = "", sub: String = "") =
    Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra("widgetSec", sec).putExtra("widgetSub", sub)
        .setData(Uri.parse("carnetnatif://widget/$sec/$sub")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun tasksOn(d: LocalDate): List<JSONObject> = Store.tasks().filter { parseDate(it.s("echeance")) == d }.sortedBy { taskTime(it) }
private fun taskLine(t: JSONObject): String = listOf(taskTime(t), t.s("titre").ifEmpty { Ref.TASK_TYPES[Ref.type(t)] ?: "Tâche" }).filter { it.isNotEmpty() }.joinToString(" · ")
private fun taskSub(t: JSONObject): String = listOf(taskEnsName(t), taskEtabName(t)).filter { it.isNotEmpty() }.joinToString(" — ")
private fun dayName(d: LocalDate, style: JTextStyle = JTextStyle.SHORT) = d.dayOfWeek.getDisplayName(style, Locale.FRENCH).replaceFirstChar { it.uppercase() }

@Composable
private fun Header(title: String, ctx: Context, sec: String, sub: String) {
    Row(GlanceModifier.fillMaxWidth().background(c(BRAND)).padding(horizontal = 12.dp, vertical = 8.dp).clickable(actionStartActivity(openApp(ctx, sec, sub))),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, GlanceModifier.defaultWeight(), style = ts(14, Color.White, bold = true), maxLines = 1)
        Text("Ouvrir ›", style = ts(12, Color.White))
    }
}

/** Met à jour tous les widgets (appelé quand les données changent). */
object Widgets {
    suspend fun refresh(ctx: Context) {
        try { TasksWidget().updateAll(ctx) } catch (_: Exception) {}
        try { TeachersWidget().updateAll(ctx) } catch (_: Exception) {}
        try { CalendarWidget().updateAll(ctx) } catch (_: Exception) {}
    }
}

// ---------- 1. Tâches de la semaine ----------
class TasksWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.ensure(context)
        val mon = LocalDate.now().with(DayOfWeek.MONDAY)
        val rows = (0 until 7).map { mon.plusDays(it.toLong()) }.flatMap { d -> tasksOn(d).map { d to it } }
        val late = Store.tasks().count { t -> parseDate(t.s("echeance"))?.let { it.isBefore(LocalDate.now()) && Ref.statut(t) !in Ref.DONE } == true }
        provideContent {
            Column(GlanceModifier.fillMaxSize().background(c(BG)).cornerRadius(16.dp)) {
                Header("Tâches de la semaine", context, "taches", "avenir")
                if (late > 0) Text("⚠ $late tâche(s) en retard", GlanceModifier.padding(horizontal = 12.dp, vertical = 4.dp).clickable(actionStartActivity(openApp(context, "taches", "retard"))), style = ts(12, DANGER, bold = true))
                if (rows.isEmpty()) Text("Aucune tâche cette semaine.", GlanceModifier.padding(12.dp), style = ts(13, FAINT))
                else LazyColumn(GlanceModifier.fillMaxSize().padding(horizontal = 8.dp)) {
                    items(rows, itemId = { (it.second.s("id") + it.first).hashCode().toLong() }) { (d, t) ->
                        val today = d == LocalDate.now()
                        Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).clickable(actionStartActivity(openApp(context, "task", t.s("id")))), verticalAlignment = Alignment.CenterVertically) {
                            Column(GlanceModifier.width(46.dp).background(c(if (today) BRAND else CARD)).cornerRadius(10.dp).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(dayName(d), style = ts(11, if (today) Color.White else FAINT, center = true))
                                Text(d.dayOfMonth.toString(), style = ts(15, if (today) Color.White else INK, bold = true, center = true))
                            }
                            Column(GlanceModifier.defaultWeight().padding(start = 8.dp)) {
                                Text(taskLine(t), style = ts(13, if (Ref.statut(t) in Ref.DONE) FAINT else INK, bold = true), maxLines = 1)
                                val s = taskSub(t); if (s.isNotEmpty()) Text(s, style = ts(11, FAINT), maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
class TasksWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TasksWidget() }

// ---------- 2. Enseignant(e)s : appel, WhatsApp, SMS ----------
class TeachersWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.ensure(context)
        val list = Store.enseignants().sortedBy { norm(it.s("nom")) }
        provideContent {
            Column(GlanceModifier.fillMaxSize().background(c(BG)).cornerRadius(16.dp)) {
                Header("Enseignant(e)s (${list.size})", context, "ens", "liste")
                if (list.isEmpty()) Text("Aucun(e) enseignant(e).", GlanceModifier.padding(12.dp), style = ts(13, FAINT))
                else LazyColumn(GlanceModifier.fillMaxSize().padding(horizontal = 8.dp)) {
                    items(list, itemId = { it.s("id").hashCode().toLong() }) { e ->
                        val tel = e.s("telephone").replace(" ", "")
                        Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).background(c(CARD)).cornerRadius(10.dp).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(openApp(context, "ens", e.s("id"))))) {
                                Text(e.s("nom"), style = ts(13, INK, bold = true), maxLines = 1)
                                Text(listOf(etabName(e.s("etablissementId")), e.s("telephone")).filter { it.isNotEmpty() }.joinToString(" · "), style = ts(11, FAINT), maxLines = 1)
                            }
                            if (tel.isNotEmpty()) {
                                Pill("📞", Color(0xFFE3F0EC), actionStartActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$tel")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))
                                Spacer(GlanceModifier.width(5.dp))
                                Pill("💬", Color(0xFFDCF8C6), actionStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + waNumber(tel))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))
                                Spacer(GlanceModifier.width(5.dp))
                                Pill("✉", Color(0xFFE6ECF7), actionStartActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$tel")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))
                            }
                        }
                    }
                }
            }
        }
    }
}
class TeachersWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TeachersWidget() }

@Composable
private fun Pill(label: String, bg: Color, action: androidx.glance.action.Action) {
    Box(GlanceModifier.size(34.dp).background(c(bg)).cornerRadius(17.dp).clickable(action), contentAlignment = Alignment.Center) { Text(label, style = ts(15, INK, center = true)) }
}

// ---------- 3. Calendrier : vues mois, semaine, jour ----------
val MODE = stringPreferencesKey("mode")
val OFFSET = intPreferencesKey("offset")
val P_MODE = ActionParameters.Key<String>("mode")
val P_STEP = ActionParameters.Key<Int>("step")

class CalModeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { p ->
            parameters[P_MODE]?.let { p[MODE] = it; p[OFFSET] = 0 }
            parameters[P_STEP]?.let { s -> p[OFFSET] = if (s == 0) 0 else (p[OFFSET] ?: 0) + s }
        }
        CalendarWidget().update(context, glanceId)
    }
}

class CalendarWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.ensure(context)
        provideContent {
            val prefs = currentState<Preferences>()
            val mode = prefs[MODE] ?: "mois"
            val off = prefs[OFFSET] ?: 0
            val today = LocalDate.now()
            Column(GlanceModifier.fillMaxSize().background(c(BG)).cornerRadius(16.dp)) {
                val title = when (mode) {
                    "semaine" -> today.plusWeeks(off.toLong()).with(DayOfWeek.MONDAY).let { "Semaine du " + it.dayOfMonth + "/" + it.monthValue }
                    "jour" -> today.plusDays(off.toLong()).let { dayName(it, JTextStyle.FULL) + " " + it.dayOfMonth + "/" + it.monthValue }
                    else -> YearMonth.from(today).plusMonths(off.toLong()).let { it.month.getDisplayName(JTextStyle.FULL, Locale.FRENCH).replaceFirstChar { c -> c.uppercase() } + " " + it.year }
                }
                Row(GlanceModifier.fillMaxWidth().background(c(BRAND)).padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("‹", GlanceModifier.padding(horizontal = 8.dp).clickable(actionRunCallback<CalModeAction>(actionParametersOf(P_STEP to -1))), style = ts(18, Color.White, bold = true))
                    Text(title, GlanceModifier.defaultWeight().clickable(actionRunCallback<CalModeAction>(actionParametersOf(P_STEP to 0))), style = ts(13, Color.White, bold = true, center = true), maxLines = 1)
                    Text("›", GlanceModifier.padding(horizontal = 8.dp).clickable(actionRunCallback<CalModeAction>(actionParametersOf(P_STEP to 1))), style = ts(18, Color.White, bold = true))
                }
                Row(GlanceModifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
                    listOf("mois" to "Mois", "semaine" to "Semaine", "jour" to "Jour").forEach { (k, l) ->
                        Box(GlanceModifier.defaultWeight().padding(horizontal = 2.dp).clickable(actionRunCallback<CalModeAction>(actionParametersOf(P_MODE to k)))) {
                            Text(l, GlanceModifier.fillMaxWidth().background(c(if (k == mode) BRAND else CARD)).cornerRadius(10.dp).padding(vertical = 4.dp), style = ts(12, if (k == mode) Color.White else INK, bold = k == mode, center = true))
                        }
                    }
                }
                when (mode) {
                    "semaine" -> WeekView(context, today.plusWeeks(off.toLong()).with(DayOfWeek.MONDAY), today)
                    "jour" -> DayView(context, today.plusDays(off.toLong()))
                    else -> MonthView(context, YearMonth.from(today).plusMonths(off.toLong()), today)
                }
            }
        }
    }
}
class CalendarWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = CalendarWidget() }

@Composable
private fun MonthView(ctx: Context, ym: YearMonth, today: LocalDate) {
    val counts = Store.tasks().mapNotNull { parseDate(it.s("echeance")) }.filter { YearMonth.from(it) == ym }.groupingBy { it.dayOfMonth }.eachCount()
    val first = ym.atDay(1); val lead = first.dayOfWeek.value - 1
    val cells = (0 until 42).map { i -> val n = i - lead + 1; if (n in 1..ym.lengthOfMonth()) n else 0 }
    val weeks = cells.chunked(7).filter { w -> w.any { it > 0 } }
    Column(GlanceModifier.fillMaxSize().padding(horizontal = 4.dp)) {
        Row(GlanceModifier.fillMaxWidth()) { listOf("L", "M", "M", "J", "V", "S", "D").forEach { Text(it, GlanceModifier.defaultWeight(), style = ts(11, FAINT, bold = true, center = true)) } }
        weeks.forEach { w ->
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                w.forEach { n ->
                    if (n == 0) Spacer(GlanceModifier.defaultWeight())
                    else {
                        val d = ym.atDay(n); val k = counts[n] ?: 0
                        Column(GlanceModifier.defaultWeight().fillMaxHeight().padding(1.dp).background(c(if (d == today) BRAND else if (k > 0) Color(0xFFE3F0EC) else CARD)).cornerRadius(8.dp)
                            .clickable(actionStartActivity(openApp(ctx, "jour", d.toString()))), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                            Text(n.toString(), style = ts(12, if (d == today) Color.White else INK, bold = k > 0, center = true))
                            if (k > 0) Text(if (k > 3) "•••+" else "•".repeat(k), style = ts(9, if (d == today) Color.White else BRAND, bold = true, center = true))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekView(ctx: Context, mon: LocalDate, today: LocalDate) {
    val days = (0 until 7).map { mon.plusDays(it.toLong()) }
    LazyColumn(GlanceModifier.fillMaxSize().padding(horizontal = 8.dp)) {
        items(days, itemId = { it.toEpochDay() }) { d ->
            val l = tasksOn(d)
            Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp).background(c(if (d == today) Color(0xFFE3F0EC) else CARD)).cornerRadius(10.dp).padding(6.dp)
                .clickable(actionStartActivity(openApp(ctx, "jour", d.toString())))) {
                Column(GlanceModifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(dayName(d), style = ts(11, FAINT, center = true)); Text(d.dayOfMonth.toString(), style = ts(15, if (d == today) BRAND else INK, bold = true, center = true))
                }
                Column(GlanceModifier.defaultWeight().padding(start = 6.dp)) {
                    if (l.isEmpty()) Text("—", style = ts(12, FAINT))
                    l.take(4).forEach { Text(taskLine(it), style = ts(12, INK), maxLines = 1) }
                    if (l.size > 4) Text("+ ${l.size - 4} autre(s)", style = ts(11, FAINT))
                }
            }
        }
    }
}

@Composable
private fun DayView(ctx: Context, d: LocalDate) {
    val l = tasksOn(d)
    if (l.isEmpty()) { Text("Aucune tâche ce jour.", GlanceModifier.padding(12.dp), style = ts(13, FAINT)); return }
    LazyColumn(GlanceModifier.fillMaxSize().padding(horizontal = 8.dp)) {
        items(l, itemId = { it.s("id").hashCode().toLong() }) { t ->
            Column(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).background(c(CARD)).cornerRadius(10.dp).padding(8.dp).clickable(actionStartActivity(openApp(ctx, "task", t.s("id"))))) {
                Text(taskLine(t), style = ts(13, INK, bold = true), maxLines = 2)
                val s = taskSub(t); if (s.isNotEmpty()) Text(s, style = ts(11, FAINT), maxLines = 1)
                Text(Ref.WORKFLOW[Ref.statut(t)] ?: "", style = ts(11, BRAND))
            }
        }
    }
}
