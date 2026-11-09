@file:OptIn(ExperimentalMaterial3Api::class)

package ma.carnet.inspection.natif.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import ma.carnet.inspection.natif.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/** Données représentables dans le bilan (mêmes rubriques que le carnet web). */
data class BData(val k: String, val g: String, val l: String, val kind: String, val get: (List<JSONObject>, LocalDate, LocalDate) -> List<Pair<String, Int>>)

private fun count(items: List<JSONObject>, key: (JSONObject) -> String) = items.groupingBy(key).eachCount()
private fun top(m: Map<String, Int>, n: Int = 12) = m.entries.sortedByDescending { it.value }.take(n).map { it.key to it.value }
private fun ordered(keys: List<String>, m: Map<String, Int>) = keys.map { it to (m[it] ?: 0) }
private fun zoneOfEtab(id: String) = zoneName(Store.byId("etablissements", id)?.s("zoneId")).ifEmpty { "Sans zone" }

val BILAN_DATA = listOf(
    BData("activite", "Tâches de la période", "Activité par mois (réalisées / à venir)", "time") { l, from, to ->
        val months = generateSequence(from.withDayOfMonth(1)) { it.plusMonths(1) }.takeWhile { !it.isAfter(to) }.toList()
        months.map { m -> (m.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.FRENCH) + " " + (m.year % 100)) to l.count { parseDate(it.s("echeance"))?.let { d -> d.year == m.year && d.month == m.month } == true } }
    },
    BData("statut", "Tâches de la période", "Avancement (statut)", "ord") { l, _, _ -> Ref.WORKFLOW.map { (k, lab) -> lab to l.count { Ref.statut(it) == k } } },
    BData("type", "Tâches de la période", "Par type d'activité", "cat") { l, _, _ -> top(count(l) { Ref.TASK_TYPES[Ref.type(it)] ?: "Autre" }) },
    BData("etab", "Tâches de la période", "Par établissement", "cat") { l, _, _ -> top(count(l) { taskEtabName(it).ifEmpty { "Non renseigné" } }, 10) },
    BData("zone", "Tâches de la période", "Par zone", "cat") { l, _, _ -> top(count(l) { if (it.s("etablissementId").isEmpty()) "Sans établissement" else zoneOfEtab(it.s("etablissementId")) }) },
    BData("note", "Tâches de la période", "Appréciations des inspections", "ord") { l, _, _ -> ordered(Ref.NOTE_SCALE, count(l.filter { it.s("noteGlobale").isNotEmpty() }) { it.s("noteGlobale") }) },
    BData("jour", "Tâches de la période", "Jours de la semaine", "ord") { l, _, _ -> ordered(Ref.JOURS, count(l.filter { parseDate(it.s("echeance")) != null }) { Ref.JOURS.getOrElse(parseDate(it.s("echeance"))!!.dayOfWeek.value - 1) { "" } }) },
    BData("moment", "Tâches de la période", "Moment de la journée", "ord") { l, _, _ ->
        val L = listOf("Avant 10 h", "10 h – 12 h", "12 h – 14 h", "14 h – 16 h", "Après 16 h", "Heure non précisée")
        ordered(L, count(l) { val h = taskTime(it).take(2).toIntOrNull(); when { h == null -> L[5]; h < 10 -> L[0]; h < 12 -> L[1]; h < 14 -> L[2]; h < 16 -> L[3]; else -> L[4] } })
    },
    BData("priorite", "Tâches de la période", "Priorité", "ord") { l, _, _ -> ordered(Ref.PRIORITES.values.toList(), count(l) { Ref.PRIORITES[it.s("priorite").ifEmpty { "moyenne" }] ?: "" }) },
    BData("enseignant", "Tâches de la période", "Enseignant(e)s les plus suivi(e)s", "cat") { l, _, _ ->
        val m = mutableMapOf<String, Int>(); l.forEach { t -> (listOf(t.s("enseignantId")) + t.arr("invites").map { it.s("enseignantId") }).filter { it.isNotEmpty() }.toSet().forEach { id -> val n = ensName(id); if (n.isNotEmpty()) m[n] = (m[n] ?: 0) + 1 } }; top(m, 10)
    },
    BData("docs", "Tâches de la période", "Documents produits par type", "cat") { l, from, to ->
        val ids = l.map { it.s("id") }.toSet(); top(count(Store.list("documents").filter { it.s("taskId") in ids || (it.s("date") >= from.toString() && it.s("date") <= to.toString()) }) { Docs.label(it) }, 10)
    },
    BData("ens_cycle", "Enseignant(e)s", "Enseignant(e)s par cycle", "cat") { _, _, _ -> top(count(Store.enseignants()) { Ref.ENS_CYCLES[ensCycle(it)] ?: "Non précisé" }) },
    BData("ens_genre", "Enseignant(e)s", "Enseignant(e)s par genre", "cat") { _, _, _ -> top(count(Store.enseignants()) { when (it.s("genre")) { "F" -> "Femmes"; "H" -> "Hommes"; else -> "Non précisé" } }) },
    BData("ens_statut", "Enseignant(e)s", "Titulaires / stagiaires", "cat") { _, _, _ -> top(count(Store.enseignants()) { if (it.s("statut") == "stagiaire") "Stagiaires" else "Titulaires" }) },
    BData("ens_etab", "Enseignant(e)s", "Enseignant(e)s par établissement", "cat") { _, _, _ -> top(count(Store.enseignants()) { etabName(it.s("etablissementId")).ifEmpty { "Sans établissement" } }, 10) },
    BData("ens_zone", "Enseignant(e)s", "Enseignant(e)s par zone", "cat") { _, _, _ -> top(count(Store.enseignants()) { if (it.s("etablissementId").isEmpty()) "Sans établissement" else zoneOfEtab(it.s("etablissementId")) }, 10) },
    BData("ens_cadre", "Enseignant(e)s", "Enseignant(e)s par cadre", "cat") { _, _, _ -> top(count(Store.enseignants()) { it.s("cadre").take(30).ifEmpty { "Non précisé" } }, 8) },
    BData("ens_note", "Enseignant(e)s", "Dernières notes d'inspection (tranches)", "ord") { _, _, _ ->
        val L = listOf("Moins de 10", "10 – 12", "12 – 14", "14 – 16", "16 – 18", "18 et plus", "Sans note")
        ordered(L, count(Store.enseignants()) { e -> val n = ensInspections(e).firstOrNull()?.let { noteNum(it.note) }; when { n == null -> L[6]; n < 10 -> L[0]; n < 12 -> L[1]; n < 14 -> L[2]; n < 16 -> L[3]; n < 18 -> L[4]; else -> L[5] } })
    },
    BData("ens_anc", "Enseignant(e)s", "Ancienneté de la dernière inspection", "ord") { _, _, _ ->
        val L = listOf("0 – 2 ans", "3 – 4 ans", "5 – 9 ans", "10 ans et plus", "Jamais / inconnue"); val y0 = LocalDate.now().year
        ordered(L, count(Store.enseignants()) { e -> val y = ensInspections(e).firstOrNull()?.date?.take(4)?.toIntOrNull()?.let { y0 - it }; when { y == null -> L[4]; y <= 2 -> L[0]; y <= 4 -> L[1]; y <= 9 -> L[2]; else -> L[3] } })
    },
    BData("etab_cycle", "Établissements", "Établissements par cycle", "cat") { _, _, _ -> top(count(Store.etabs()) { Ref.ETAB_CYCLES[it.s("cycle")] ?: "Non précisé" }) },
    BData("etab_type", "Établissements", "Établissements publics / privés", "cat") { _, _, _ -> top(count(Store.etabs()) { if (it.s("type") == "prive") "Privés" else "Publics" }) },
    BData("etab_zone", "Établissements", "Établissements par zone", "cat") { _, _, _ -> top(count(Store.etabs()) { zoneOfEtab(it.s("id")) }, 10) }
)
val CHART_TYPES = linkedMapOf("hbars" to "Barres horizontales", "columns" to "Colonnes", "line" to "Courbe", "donut" to "Anneau", "pie" to "Secteurs", "stack100" to "Barre 100 %", "table" to "Tableau seul")
private val DEFAULT_CHARTS = listOf("statut" to "hbars", "type" to "hbars", "activite" to "columns", "etab" to "hbars", "ens_note" to "columns")
private val PALETTE = listOf("#2F6F62", "#2A78D6", "#B5762A", "#8A3FD8", "#C8365E", "#0F7B6C", "#E4A853", "#4A3FD1", "#6B7470", "#1BAF7A", "#EB6834", "#7A5BB5")

fun bilanCharts(): List<JSONObject> {
    val a = Store.root.optJSONObject("templates")?.optJSONArray("bilanCharts")
    if (a == null) return DEFAULT_CHARTS.map { (d, t) -> JSONObject().put("id", "bc_$d").put("data", d).put("type", t).put("titre", "").put("export", true) }
    return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
}
fun setBilanCharts(list: List<JSONObject>) {
    val tpl = Store.root.optJSONObject("templates") ?: JSONObject().also { Store.root.put("templates", it) }
    tpl.put("bilanCharts", JSONArray().apply { list.forEach { put(it) } }); tpl.put("updatedAt", Store.now()); Store.save()
}

// ---------------- graphiques SVG (affichés dans l'application et dans le bilan exporté) ----------------
object Svg {
    private fun e(s: String) = Docs.h(s)
    fun chart(type: String, data: List<Pair<String, Int>>, w: Int = 640): String {
        val d = data.filter { it.second > 0 || type in listOf("line", "columns") }
        if (d.isEmpty() || d.all { it.second == 0 }) return "<p style=\"color:#777\">Aucune donnée.</p>"
        val max = d.maxOf { it.second }.coerceAtLeast(1); val tot = d.sumOf { it.second }.coerceAtLeast(1)
        val c = { i: Int -> PALETTE[i % PALETTE.size] }
        val sb = StringBuilder()
        when (type) {
            "columns", "line" -> {
                val h = 230; val left = 34; val bottom = 54; val n = d.size; val bw = (w - left - 10) / n
                sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $w $h\" width=\"100%\" style=\"max-width:${w}px\" font-family=\"sans-serif\" font-size=\"11\">")
                for (g in 0..4) { val y = 10 + (h - bottom - 10) * g / 4; sb.append("<line x1=\"$left\" x2=\"$w\" y1=\"$y\" y2=\"$y\" stroke=\"#ddd\"/><text x=\"${left - 4}\" y=\"${y + 4}\" text-anchor=\"end\" fill=\"#777\">${max - max * g / 4}</text>") }
                val pts = mutableListOf<String>()
                d.forEachIndexed { i, (l, v) ->
                    val x = left + i * bw; val bh = (h - bottom - 10) * v / max; val y = h - bottom - bh
                    if (type == "columns") sb.append("<rect x=\"${x + bw * 0.15}\" y=\"$y\" width=\"${bw * 0.7}\" height=\"$bh\" rx=\"3\" fill=\"${c(0)}\"/><text x=\"${x + bw / 2}\" y=\"${y - 3}\" text-anchor=\"middle\" fill=\"#333\">$v</text>")
                    else { pts.add("${x + bw / 2},$y"); sb.append("<circle cx=\"${x + bw / 2}\" cy=\"$y\" r=\"3.5\" fill=\"${c(1)}\"/><text x=\"${x + bw / 2}\" y=\"${y - 6}\" text-anchor=\"middle\" fill=\"#333\">$v</text>") }
                    sb.append("<text transform=\"translate(${x + bw / 2},${h - bottom + 12}) rotate(30)\" fill=\"#444\">${e(l.take(16))}</text>")
                }
                if (type == "line" && pts.isNotEmpty()) sb.append("<polyline points=\"${pts.joinToString(" ")}\" fill=\"none\" stroke=\"${c(1)}\" stroke-width=\"2.5\"/>")
                sb.append("</svg>")
            }
            "donut", "pie" -> {
                val r = 90.0; val cx = 110.0; val cy = 110.0; var a0 = -Math.PI / 2
                sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $w 230\" width=\"100%\" style=\"max-width:${w}px\" font-family=\"sans-serif\" font-size=\"12\">")
                d.forEachIndexed { i, (_, v) ->
                    val a1 = a0 + 2 * Math.PI * v / tot
                    if (d.size == 1) sb.append("<circle cx=\"$cx\" cy=\"$cy\" r=\"$r\" fill=\"${c(i)}\"/>")
                    else { val large = if (a1 - a0 > Math.PI) 1 else 0
                        sb.append("<path d=\"M$cx,$cy L${cx + r * Math.cos(a0)},${cy + r * Math.sin(a0)} A$r,$r 0 $large,1 ${cx + r * Math.cos(a1)},${cy + r * Math.sin(a1)} Z\" fill=\"${c(i)}\" stroke=\"#fff\" stroke-width=\"1.5\"/>") }
                    a0 = a1
                }
                if (type == "donut") sb.append("<circle cx=\"$cx\" cy=\"$cy\" r=\"${r * 0.55}\" fill=\"#fff\"/><text x=\"$cx\" y=\"${cy + 6}\" text-anchor=\"middle\" font-size=\"20\" font-weight=\"bold\">$tot</text>")
                d.take(12).forEachIndexed { i, (l, v) -> val y = 22 + i * 17; sb.append("<rect x=\"240\" y=\"${y - 10}\" width=\"12\" height=\"12\" rx=\"2\" fill=\"${c(i)}\"/><text x=\"258\" y=\"$y\">${e(l.take(40))} — $v (${v * 100 / tot} %)</text>") }
                sb.append("</svg>")
            }
            "stack100" -> {
                sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $w ${50 + 17 * d.size}\" width=\"100%\" style=\"max-width:${w}px\" font-family=\"sans-serif\" font-size=\"12\">")
                var x = 0.0
                d.forEachIndexed { i, (_, v) -> val ww = w * v.toDouble() / tot; sb.append("<rect x=\"$x\" y=\"4\" width=\"$ww\" height=\"30\" fill=\"${c(i)}\"/>"); x += ww }
                d.forEachIndexed { i, (l, v) -> val y = 54 + i * 17; sb.append("<rect x=\"0\" y=\"${y - 10}\" width=\"12\" height=\"12\" fill=\"${c(i)}\"/><text x=\"18\" y=\"$y\">${e(l.take(50))} — $v (${v * 100 / tot} %)</text>") }
                sb.append("</svg>")
            }
            "table" -> {}
            else -> {
                val rowH = 24; val lw = 210
                sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $w ${d.size * rowH + 6}\" width=\"100%\" style=\"max-width:${w}px\" font-family=\"sans-serif\" font-size=\"12\">")
                d.forEachIndexed { i, (l, v) ->
                    val y = i * rowH + 4; val bw = ((w - lw - 70) * v / max).coerceAtLeast(if (v > 0) 2 else 0)
                    sb.append("<text x=\"${lw - 6}\" y=\"${y + 15}\" text-anchor=\"end\" fill=\"#333\">${e(l.take(32))}</text><rect x=\"$lw\" y=\"${y + 3}\" width=\"$bw\" height=\"16\" rx=\"3\" fill=\"${c(i)}\"/><text x=\"${lw + bw + 6}\" y=\"${y + 15}\" fill=\"#333\">$v (${v * 100 / tot} %)</text>")
                }
                sb.append("</svg>")
            }
        }
        return sb.toString()
    }
}

data class Period(val from: LocalDate, val to: LocalDate, val label: String)
fun period(k: String): Period {
    val today = LocalDate.now()
    return when (k) {
        "mois" -> today.withDayOfMonth(1).let { Period(it, it.plusMonths(1).minusDays(1), "Mois de " + it.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.FRENCH) + " " + it.year) }
        "trimestre" -> today.minusMonths(((today.monthValue - 1) % 3).toLong()).withDayOfMonth(1).let { Period(it, it.plusMonths(3).minusDays(1), "Trimestre") }
        else -> (if (today.monthValue >= 9) LocalDate.of(today.year, 9, 1) else LocalDate.of(today.year - 1, 9, 1)).let { Period(it, LocalDate.of(it.year + 1, 8, 31), "Année scolaire ${it.year}/${it.year + 1}") }
    }
}

fun bilanHtml(ctx: Context, p: Period, onlyExport: Boolean, synthese: String): String {
    val list = Store.tasks().filter { val d = parseDate(it.s("echeance")); d != null && !d.isBefore(p.from) && !d.isAfter(p.to) }
    val done = list.count { Ref.statut(it) in Ref.DONE }
    val sb = StringBuilder(Docs.header(ctx, false))
    val pr = Store.profil()
    sb.append("<h1>Bilan d'activité</h1><p style=\"text-align:center\">${Docs.h(p.label)} — du ${fmtDate(p.from.toString())} au ${fmtDate(p.to.toString())}</p>")
    if (pr.s("nom").isNotEmpty()) sb.append("<p style=\"text-align:center\">${Docs.h(pr.s("nomSignature").ifEmpty { pr.s("nom") })}, ${Docs.h(pr.s("grade"))} ${Docs.h(pr.s("matiere"))}</p>")
    sb.append(Docs.table(listOf("Tâches", "Réalisées", "Taux de réalisation", "Enseignant(e)s", "Établissements"),
        listOf(listOf(list.size.toString(), done.toString(), if (list.isEmpty()) "—" else "${done * 100 / list.size} %", Store.enseignants().size.toString(), Store.etabs().size.toString()))))
    if (synthese.isNotBlank()) sb.append("<h2>Synthèse</h2>").append(Docs.para(synthese))
    bilanCharts().filter { !onlyExport || it.optBoolean("export", true) }.forEach { cf ->
        val D = BILAN_DATA.firstOrNull { it.k == cf.s("data") } ?: return@forEach
        val data = D.get(list, p.from, p.to)
        sb.append("<h2>").append(Docs.h(cf.s("titre").ifEmpty { D.l })).append("</h2>")
        if (cf.s("type") != "table") sb.append("<div class=\"chart\">").append(Svg.chart(cf.s("type"), data)).append("</div>")
        val tot = data.sumOf { it.second }.coerceAtLeast(1)
        sb.append(Docs.table(listOf(D.l, "Nombre", "%"), data.map { listOf(it.first, it.second.toString(), "${it.second * 100 / tot} %") }, listOf(60, 20, 20)))
    }
    sb.append("<h2>Liste des tâches de la période</h2>")
    sb.append(Docs.table(listOf("Date", "Tâche", "Type", "Établissement", "Statut"), list.sortedBy { it.s("echeance") }.map { listOf(fmtDate(it.s("echeance")), it.s("titre").ifEmpty { autoTaskTitle(it) }, Ref.TASK_TYPES[Ref.type(it)] ?: "", taskEtabName(it), Ref.WORKFLOW[Ref.statut(it)] ?: "") }, listOf(13, 37, 17, 20, 13)))
    return Docs.page(sb.toString(), false)
}

@Composable
fun BilanScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val v by Store.version
    var pk by remember { mutableStateOf("annee") }
    val p = period(pk)
    val list = remember(v, pk) { Store.tasks().filter { val d = parseDate(it.s("echeance")); d != null && !d.isBefore(p.from) && !d.isAfter(p.to) } }
    var charts by remember(v) { mutableStateOf(bilanCharts()) }
    var edit by remember { mutableStateOf<JSONObject?>(null) }
    var synth by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val done = list.count { Ref.statut(it) in Ref.DONE }
    ScreenScaffold("Bilan", { Nav.pop() }, actions = {
        IconButton(onClick = { showHtml("Bilan " + p.label, bilanHtml(ctx, p, true, synth)) }) { Icon(Icons.Default.Share, "Produire le bilan") }
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(14.dp)) {
            ZoneFilterBar()
            ChipRow(mapOf("mois" to "Ce mois", "trimestre" to "Trimestre", "annee" to "Année scolaire"), pk, { if (it.isNotEmpty()) pk = it }, null)
            Text("Du ${fmtDate(p.from.toString())} au ${fmtDate(p.to.toString())}", color = InkFaint, fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Kpi("Tâches", list.size.toString(), Modifier.weight(1f)) {}
                Kpi("Réalisées", if (list.isEmpty()) "0 %" else "${done * 100 / list.size} %", Modifier.weight(1f)) {}
            }
            charts.forEachIndexed { i, cf ->
                val D = BILAN_DATA.firstOrNull { it.k == cf.s("data") } ?: return@forEachIndexed
                val data = remember(v, pk, cf.toString()) { D.get(list, p.from, p.to) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(cf.s("titre").ifEmpty { D.l }, Modifier.weight(1f).padding(top = 12.dp), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { edit = JSONObject(cf.toString()) }) { Icon(Icons.Default.Edit, "Modifier", tint = Brand) }
                    IconButton(onClick = { charts = charts.filterIndexed { j, _ -> j != i }; setBilanCharts(charts) }) { Icon(Icons.Default.Delete, "Supprimer", tint = Danger) }
                }
                if (!cf.optBoolean("export", true)) Text("Non inclus dans le bilan produit", color = InkFaint, fontSize = 11.5.sp)
                CardBox {
                    if (cf.s("type") == "table" || data.isEmpty()) data.forEach { (l, n) -> Row { Text(l, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("$n", fontWeight = FontWeight.SemiBold) } }
                    else {
                        val svg = Svg.chart(cf.s("type"), data, 640)
                        AndroidView(factory = { c -> android.webkit.WebView(c).apply { setBackgroundColor(android.graphics.Color.WHITE); isVerticalScrollBarEnabled = false } },
                            update = { if (it.tag != svg) { it.tag = svg; it.loadDataWithBaseURL(null, "<html><body style=\"margin:0;background:#fff\">$svg</body></html>", "text/html", "utf-8", null) } },
                            modifier = Modifier.fillMaxWidth().height(if (cf.s("type") in listOf("hbars")) (data.size * 24 + 30).coerceAtMost(420).dp else if (cf.s("type") == "stack100") (60 + 17 * data.size).dp else 240.dp))
                    }
                }
            }
            OutlinedButton(onClick = { edit = JSONObject().put("id", "bc_" + UUID.randomUUID().toString().take(6)).put("data", "type").put("type", "hbars").put("titre", "").put("export", true) }, modifier = Modifier.padding(top = 10.dp)) {
                Icon(Icons.Default.Add, null); Text("Ajouter un graphique")
            }
            SectionTitle("Synthèse du bilan (facultatif)")
            TextFieldRow("Synthèse", synth, { synth = it }, multiline = true)
            AiButton(if (synth.isBlank()) "Rédiger la synthèse avec l'IA" else "Enrichir avec l'IA", busy) {
                if (!Ai.ready(ctx)) { toast(ctx, Ai.NOT_READY); return@AiButton }
                busy = true
                scope.launchCatching(ctx, { busy = false }) {
                    val stats = charts.mapNotNull { cf -> BILAN_DATA.firstOrNull { it.k == cf.s("data") }?.let { D -> D.l + " : " + D.get(list, p.from, p.to).joinToString(", ") { "${it.first} ${it.second}" } } }.joinToString("\n")
                    synth = Ai.generate(ctx, "Tu es inspecteur(trice) pédagogique au Maroc. Rédige en français la synthèse (10 à 15 lignes, ton administratif) de ton bilan d'activité « ${p.label} », à partir de ces chiffres, sans en inventer d'autres. Retourne uniquement le texte.\n\nTâches : ${list.size}, réalisées : $done.\n$stats" + (if (synth.isNotBlank()) "\n\nBrouillon : $synth" else ""))
                }
            }
            Button(onClick = { showHtml("Bilan " + p.label, bilanHtml(ctx, p, true, synth)) }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { MarqueeText("Produire le bilan (PDF, Word, Excel)", center = true) }
            Spacer(Modifier.height(40.dp))
        }
    }
    edit?.let { cf ->
        var data by remember(cf) { mutableStateOf(cf.s("data")) }
        var type by remember(cf) { mutableStateOf(cf.s("type")) }
        var titre by remember(cf) { mutableStateOf(cf.s("titre")) }
        var exp by remember(cf) { mutableStateOf(cf.optBoolean("export", true)) }
        AlertDialog(onDismissRequest = { edit = null }, title = { Text("Graphique") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SelectField("Données", BILAN_DATA.associate { it.k to (it.g + " — " + it.l) }, data, { data = it })
                SelectField("Type de graphique", CHART_TYPES, type, { type = it })
                TextFieldRow("Titre (facultatif)", titre, { titre = it })
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(exp, { exp = it }); Text("Inclure dans le bilan produit", fontSize = 13.5.sp) }
            }
        }, confirmButton = {
            TextButton(onClick = {
                cf.put("data", data).put("type", type).put("titre", titre.trim()).put("export", exp)
                val l = charts.toMutableList(); val i = l.indexOfFirst { it.s("id") == cf.s("id") }; if (i >= 0) l[i] = cf else l.add(cf)
                charts = l; setBilanCharts(l); edit = null
            }) { Text("Enregistrer", maxLines = 1, softWrap = false) }
        }, dismissButton = { TextButton(onClick = { edit = null }) { Text("Annuler", maxLines = 1, softWrap = false) } })
    }
}

/** Lance une tâche asynchrone en affichant l'erreur éventuelle. */
fun kotlinx.coroutines.CoroutineScope.launchCatching(ctx: Context, onDone: () -> Unit = {}, block: suspend () -> Unit) =
    launch(kotlinx.coroutines.Dispatchers.Main) { try { block() } catch (e: Throwable) { toast(ctx, e.message ?: "Erreur") } finally { onDone() } }
