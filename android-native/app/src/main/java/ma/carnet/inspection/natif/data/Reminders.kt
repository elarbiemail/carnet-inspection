package ma.carnet.inspection.natif.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Rappels : tâches imminentes, approche des vacances, tâches prévues pendant des vacances (comme le carnet web). */
object Reminders {
    const val CHANNEL = "rappels"
    private fun p(ctx: Context) = ctx.getSharedPreferences("carnet", Context.MODE_PRIVATE)
    fun enabled(ctx: Context) = p(ctx).getBoolean("notifOn", true)
    fun setEnabled(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("notifOn", v).apply(); schedule(ctx) }
    fun leadMinutes(ctx: Context) = p(ctx).getInt("notifLeadMinutes", 60)
    fun setLeadMinutes(ctx: Context, v: Int) { p(ctx).edit().putInt("notifLeadMinutes", v.coerceIn(5, 1440)).apply(); schedule(ctx) }
    fun vacLeadDays(ctx: Context) = p(ctx).getInt("vacanceLeadDays", 7)
    fun setVacLeadDays(ctx: Context, v: Int) { p(ctx).edit().putInt("vacanceLeadDays", v.coerceIn(1, 60)).apply(); schedule(ctx) }

    fun permitted(ctx: Context) = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun channel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Rappels du carnet", NotificationManager.IMPORTANCE_HIGH).apply { description = "Tâches imminentes et vacances" })
    }

    fun show(ctx: Context, id: Int, title: String, text: String) {
        if (!permitted(ctx)) return
        channel(ctx)
        val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        val pi = PendingIntent.getActivity(ctx, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setAutoCancel(true).setContentIntent(pi).setPriority(NotificationCompat.PRIORITY_HIGH).build()
        try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (_: SecurityException) {}
    }

    data class Alarm(val key: String, val at: LocalDateTime, val title: String, val text: String)

    fun vacanceFor(date: String): org.json.JSONObject? {
        val d = parseDate(date) ?: return null
        return Store.list("vacances").firstOrNull { v -> val a = parseDate(v.s("dateDebut")); val b = parseDate(v.s("dateFin")) ?: a; a != null && b != null && !d.isBefore(a) && !d.isAfter(b) }
    }

    fun upcoming(ctx: Context): List<Alarm> {
        val now = LocalDateTime.now(); val out = mutableListOf<Alarm>()
        val lead = leadMinutes(ctx).toLong()
        Store.list("tasks").filter { Ref.statut(it) !in Ref.DONE }.forEach { t ->
            val d = parseDate(t.s("echeance")) ?: return@forEach
            if (d.isBefore(LocalDate.now()) || d.isAfter(LocalDate.now().plusDays(30))) return@forEach
            val tm = taskTime(t).let { try { LocalTime.parse(it) } catch (_: Exception) { null } } ?: LocalTime.of(8, 0)
            val start = LocalDateTime.of(d, tm)
            val at = start.minusMinutes(lead)
            if (at.isAfter(now)) out.add(Alarm("t_" + t.s("id"), at, "Bientôt : " + t.s("titre").ifEmpty { autoTaskTitle(t) },
                listOf(fmtDate(d.toString()) + " à " + tm.toString(), t.s("lieu").ifEmpty { taskEtabName(t) }).filter { it.isNotBlank() }.joinToString(" · ")))
            vacanceFor(t.s("echeance"))?.let { v ->
                val c = LocalDateTime.of(LocalDate.now(), LocalTime.of(9, 0)).let { if (it.isBefore(now)) now.plusMinutes(1) else it }
                if (d.isAfter(LocalDate.now())) out.add(Alarm("c_" + t.s("id"), c, "Conflit avec les vacances", (t.s("titre").ifEmpty { "Cette tâche" }) + " est prévue le " + fmtDate(d.toString()) + ", pendant " + v.s("nom").ifEmpty { "une période de vacances" } + "."))
            }
        }
        Store.list("vacances").forEach { v ->
            val a = parseDate(v.s("dateDebut")) ?: return@forEach
            val at = LocalDateTime.of(a.minusDays(vacLeadDays(ctx).toLong()), LocalTime.of(9, 0))
            if (at.isAfter(now) && at.isBefore(now.plusDays(90))) out.add(Alarm("v_" + v.s("id"), at, "Vacances à l'approche", v.s("nom").ifEmpty { "Vacances" } + " : du " + fmtDate(v.s("dateDebut")) + " au " + fmtDate(v.s("dateFin"))))
        }
        return out.sortedBy { it.at }.take(60)
    }

    private fun pi(ctx: Context, a: Alarm?, idx: Int): PendingIntent? {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction("rappel_$idx")
        if (a != null) i.putExtra("title", a.title).putExtra("text", a.text).putExtra("key", a.key + "@" + a.at)
        return PendingIntent.getBroadcast(ctx, 7000 + idx, i, PendingIntent.FLAG_IMMUTABLE or (if (a == null) PendingIntent.FLAG_NO_CREATE else PendingIntent.FLAG_UPDATE_CURRENT))
    }

    /** Reprogramme tous les rappels (appelé à l'ouverture et après chaque modification). */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val prev = p(ctx).getInt("alarmCount", 0)
        for (i in 0 until prev) pi(ctx, null, i)?.let { am.cancel(it) }
        if (!enabled(ctx)) { p(ctx).edit().putInt("alarmCount", 0).apply(); return }
        val seen = p(ctx).getStringSet("notifSeen", emptySet()) ?: emptySet()
        val list = upcoming(ctx).filter { it.key + "@" + it.at !in seen }
        list.forEachIndexed { i, a ->
            val ms = a.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            pi(ctx, a, i)?.let { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, it) }
        }
        p(ctx).edit().putInt("alarmCount", list.size).apply()
    }

    fun markSeen(ctx: Context, key: String) {
        val s = (p(ctx).getStringSet("notifSeen", emptySet()) ?: emptySet()).toMutableSet()
        s.add(key); p(ctx).edit().putStringSet("notifSeen", s.toList().takeLast(300).toSet()).apply()
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: return
        val key = intent.getStringExtra("key") ?: title
        Reminders.show(ctx, key.hashCode(), title, intent.getStringExtra("text") ?: "")
        Reminders.markSeen(ctx, key)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        try { Store.init(ctx.applicationContext); Reminders.schedule(ctx.applicationContext) } catch (_: Exception) {}
    }
}
