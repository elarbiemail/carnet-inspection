@file:OptIn(ExperimentalMaterial3Api::class)

package ma.carnet.inspection.natif.ui

import android.graphics.BitmapFactory
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import ma.carnet.inspection.natif.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// ---------------- Aperçu HTML générique (listes, programme) ----------------
object HtmlHolder { var title = ""; var html = ""; var landscape = false }

@Composable
fun HtmlPreviewScreen() {
    PreviewWithExport(HtmlHolder.title, HtmlHolder.html, ma.carnet.inspection.natif.data.Export.safeName(HtmlHolder.title + " " + java.time.LocalDate.now()), HtmlHolder.landscape, null)
}

fun showHtml(title: String, html: String, landscape: Boolean = false) { HtmlHolder.title = title; HtmlHolder.html = html; HtmlHolder.landscape = landscape; Nav.push(Scr.HtmlPreview) }

// ---------------- Zones ----------------
@Composable
fun ZonesScreen() {
    val v by Store.version
    val zones = remember(v) { Store.list("zones").sortedBy { norm(it.s("nom")) } }
    var edit by remember { mutableStateOf<JSONObject?>(null) }
    var del by remember { mutableStateOf<JSONObject?>(null) }
    ScreenScaffold("Zones", { Nav.pop() }, fab = {
        FloatingActionButton(onClick = { edit = JSONObject() }, containerColor = Brand) { Icon(Icons.Default.Add, "Ajouter", tint = OnBrand) }
    }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 14.dp)) {
            item { Text("Les zones regroupent les établissements et permettent de filtrer tout le carnet.", color = InkFaint, fontSize = 12.5.sp, modifier = Modifier.padding(vertical = 8.dp)) }
            if (zones.isEmpty()) item { EmptyState("Aucune zone. Touchez + pour en créer une.") }
            items(zones, key = { it.s("id") }) { z ->
                val etabs = Store.list("etablissements").filter { it.s("zoneId") == z.s("id") }
                val nbEns = Store.list("enseignants").count { e -> etabs.any { it.s("id") == e.s("etablissementId") } }
                CardBox {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(z.s("nom"), fontWeight = FontWeight.SemiBold)
                            Text("${etabs.size} établissement(s) · $nbEns enseignant(e)s", color = InkFaint, fontSize = 12.5.sp)
                            if (z.s("description").isNotEmpty()) Text(z.s("description"), fontSize = 12.5.sp)
                        }
                        IconButton(onClick = { edit = JSONObject(z.toString()) }) { Icon(Icons.Default.Edit, "Modifier", tint = Brand) }
                        IconButton(onClick = { del = z }) { Icon(Icons.Default.Delete, "Supprimer", tint = Danger) }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
    edit?.let { z -> ZoneEditDialog(z) { edit = null } }
    del?.let { z ->
        ConfirmDialog("Supprimer « ${z.s("nom")} » ?", "Les établissements de cette zone seront conservés, sans zone.", onDismiss = { del = null }) {
            Store.list("etablissements").filter { it.s("zoneId") == z.s("id") }.forEach { e -> val c = JSONObject(e.toString()); c.put("zoneId", ""); Store.upsert("etablissements", c) }
            Store.delete("zones", z.s("id")); del = null
        }
    }
}

// ---------------- Notes d'inspection (liste globale / zone / établissement) ----------------
@Composable
fun NotesScreen() {
    val ctx = LocalContext.current
    val v by Store.version
    var etab by remember { mutableStateOf("") }
    val etabs = remember(v) { Store.etabs().sortedBy { norm(etabName(it.s("id"))) } }
    val ens = remember(v, etab) {
        Store.enseignants().filter { etab.isEmpty() || it.s("etablissementId") == etab }
            .sortedWith(compareBy({ norm(etabName(it.s("etablissementId"))) }, { norm(it.s("nom")) }))
    }
    var pick by remember { mutableStateOf(false) }
    ScreenScaffold("Notes d'inspection", { Nav.pop() }, actions = {
        IconButton(onClick = { showHtml("Notes d'inspection", notesHtml(ctx, ens, etab)) }) { Icon(Icons.Default.Share, "Imprimer") }
    }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 14.dp)) {
            item {
                ZoneFilterBar()
                PickField("Établissement", if (etab.isEmpty()) "Tous (zones sélectionnées)" else etabName(etab)) { pick = true }
                if (etab.isNotEmpty()) TextButton(onClick = { etab = "" }) { Text("Tous les établissements") }
                Button(onClick = { showHtml("Notes d'inspection", notesHtml(ctx, ens, etab)) }, modifier = Modifier.fillMaxWidth()) { Text("Fiche synthèse imprimable (${ens.size})") }
            }
            items(ens, key = { it.s("id") }) { e ->
                val last = ensInspections(e).firstOrNull()
                CardBox(onClick = { Nav.push(Scr.EnsDetail(e.s("id"))) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.s("nom"), fontWeight = FontWeight.SemiBold)
                            Text(listOf(e.s("ppr"), etabName(e.s("etablissementId"))).filter { it.isNotEmpty() }.joinToString(" · "), color = InkFaint, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(last?.let { noteTxt(it.note) }?.ifEmpty { "—" } ?: "—", fontWeight = FontWeight.Bold, color = Brand)
                            Text(last?.let { inspDateTxt(it.date) } ?: "", color = InkFaint, fontSize = 11.5.sp)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
    if (pick) PickerDialog("Établissement", etabs, { etabName(it.s("id")) }, { zoneName(it.s("zoneId")) }, onDismiss = { pick = false }) { etab = it?.s("id") ?: ""; pick = false }
}

fun notesHtml(ctx: android.content.Context, ens: List<JSONObject>, etab: String): String {
    val sb = StringBuilder(Docs.header(ctx, false))
    sb.append("<h1>Notes d'inspection</h1>")
    val scope = when {
        etab.isNotEmpty() -> "Établissement : " + etabName(etab)
        Store.zoneFilter.isNotEmpty() -> "Zone(s) : " + Store.zoneFilter.joinToString(", ") { if (it == "__none") "Sans zone" else zoneName(it) }
        else -> "Ensemble des établissements"
    }
    sb.append("<p style=\"text-align:center\">${Docs.h(scope)} — ${ens.size} enseignant(e)s</p>")
    val rows = ens.mapIndexed { i, e ->
        val l = ensInspections(e).firstOrNull()
        listOf((i + 1).toString(), e.s("nom"), e.s("ppr"), etabName(e.s("etablissementId")), l?.let { inspDateTxt(it.date) } ?: "", l?.let { noteTxt(it.note) } ?: "")
    }
    sb.append(Docs.table(listOf("N°", "Nom et prénom", "PPR", "Établissement", "Dernière inspection", "Note"), rows, listOf(5, 30, 12, 29, 14, 10)))
    val nums = ens.mapNotNull { e -> ensInspections(e).firstOrNull()?.let { noteNum(it.note) } }
    if (nums.isNotEmpty()) sb.append("<p><b>Moyenne :</b> ${"%.2f".format(nums.average())}/20 — ${nums.size} note(s) sur ${ens.size}</p>")
    return Docs.page(sb.toString(), false)
}

// ---------------- Programme annuel ----------------
private val MOIS = listOf("09", "10", "11", "12", "01", "02", "03", "04", "05", "06")
private val MOIS_L = listOf("Sept", "Oct", "Nov", "Déc", "Jan", "Fév", "Mar", "Avr", "Mai", "Juin")

private fun JSONArray.objs(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
private fun JSONArray.without(i: Int): JSONArray { val n = JSONArray(); for (j in 0 until length()) if (j != i) n.put(opt(j)); return n }

@Composable
fun ProgrammeScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pa = remember {
        Store.root.optJSONObject("programmeAnnuel")?.let { JSONObject(it.toString()) }
            ?: JSONObject().put("anneeScolaire", "").put("preambule", "").put("categories", JSONArray())
    }
    if (pa.optJSONArray("categories") == null) pa.put("categories", JSONArray())
    var tick by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(-1) }
    var busy by remember { mutableStateOf("") }
    var dirty by remember { mutableStateOf(false) }
    fun ch() { tick++; dirty = true }
    fun save() { pa.put("updatedAt", Store.now()); Store.root.put("programmeAnnuel", JSONObject(pa.toString())); Store.save(); dirty = false; toast(ctx, "Programme enregistré.") }
    fun ai(k: String, prompt: String, apply: (String) -> Unit) {
        if (!Ai.ready(ctx)) { toast(ctx, Ai.NOT_READY); return }
        busy = k
        scope.launch { try { apply(Ai.generate(ctx, prompt)); ch() } catch (e: Exception) { toast(ctx, e.message ?: "Erreur IA") }; busy = "" }
    }
    val p = Store.profil()
    val intro = "Tu es inspecteur(trice) pédagogique" + (if (p.s("matiere").isNotEmpty()) " de ${p.s("matiere")}" else "") + " au Maroc" +
        (if (p.s("province").isNotEmpty()) " (direction provinciale de ${p.s("province")})" else "") + ". "
    val cats = pa.getJSONArray("categories")
    ScreenScaffold("Programme annuel", { Nav.pop() }, actions = {
        IconButton(onClick = { showHtml("Programme annuel", programmeHtml(ctx, pa), true) }) { Icon(Icons.Default.Share, "Imprimer") }
        TextButton(onClick = { save() }, enabled = dirty) { Text("Enregistrer", color = OnBrand, fontWeight = FontWeight.Bold) }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            tick
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = tab == -1, onClick = { tab = -1 }, label = { Text("Préambule") })
                cats.objs().forEachIndexed { i, c -> FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(c.s("titre").ifEmpty { "Catégorie ${i + 1}" }, maxLines = 1) }) }
                AssistChip(onClick = { cats.put(JSONObject().put("id", UUID.randomUUID().toString()).put("titre", "Nouvelle catégorie").put("groups", JSONArray())); tab = cats.length() - 1; ch() }, label = { Text("+ Catégorie") })
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
                if (tab == -1 || tab >= cats.length()) {
                    TextFieldRow("Année scolaire", pa.s("anneeScolaire"), { pa.put("anneeScolaire", it); ch() })
                    TextFieldRow("Préambule", pa.s("preambule"), { pa.put("preambule", it); ch() }, multiline = true)
                    AiButton(if (pa.s("preambule").isBlank()) "Proposer un préambule" else "Enrichir le préambule", busy == "pre") {
                        val acts = cats.objs().joinToString("\n") { c -> "# " + c.s("titre") + "\n" + c.optJSONArray("groups")?.objs().orEmpty().joinToString("\n") { g -> "- " + g.s("typeActivite") + " : " + g.optJSONArray("rows")?.objs().orEmpty().joinToString(" ; ") { it.s("sujet") } } }
                        ai("pre", intro + "Rédige le préambule (8 à 12 lignes, en français, ton administratif) de ton programme annuel de travail " + pa.s("anneeScolaire") +
                            " : contexte, orientations, priorités et démarche d'encadrement, à partir des activités ci-dessous, sans inventer de chiffres. Retourne uniquement le texte." +
                            (if (pa.s("preambule").isNotBlank()) "\n\nBrouillon à améliorer :\n" + pa.s("preambule") else "") + "\n\nActivités :\n" + acts) { pa.put("preambule", it) }
                    }
                    Text("${cats.length()} catégorie(s). Touchez un onglet pour voir et modifier les activités.", color = InkFaint, fontSize = 12.5.sp)
                } else {
                    val ci = tab; val c = cats.getJSONObject(ci)
                    TextFieldRow("Titre de la catégorie", c.s("titre"), { c.put("titre", it); ch() })
                    TextFieldRow("Description de la catégorie", c.s("description"), { c.put("description", it); ch() }, multiline = true)
                    AiButton(if (c.s("description").isBlank()) "Proposer une description" else "Enrichir la description", busy == "c$ci") {
                        val acts = c.optJSONArray("groups")?.objs().orEmpty().joinToString("\n") { g -> "- " + g.s("typeActivite") + " : " + g.optJSONArray("rows")?.objs().orEmpty().joinToString(" ; ") { it.s("sujet") } }
                        ai("c$ci", intro + "Rédige en 4 à 6 lignes l'orientation de l'axe « ${c.s("titre")} » de ton programme annuel : finalités, priorités et démarche, à partir des activités prévues. Ton administratif, sans titre, sans inventer de chiffres. Rédige en français. Retourne uniquement le texte." +
                            (if (c.s("description").isNotBlank()) "\n\nBrouillon :\n" + c.s("description") else "") + "\n\nActivités :\n$acts") { c.put("description", it) }
                    }
                    val groups = c.optJSONArray("groups") ?: JSONArray().also { c.put("groups", it) }
                    groups.objs().forEachIndexed { gi, g ->
                        CardBox {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(value = g.s("typeActivite"), onValueChange = { g.put("typeActivite", it); ch() }, label = { Text("Type d'activité") }, singleLine = true, modifier = Modifier.weight(1f))
                                IconButton(onClick = { c.put("groups", groups.without(gi)); ch() }) { Icon(Icons.Default.Delete, "Supprimer", tint = Danger) }
                            }
                            val rows = g.optJSONArray("rows") ?: JSONArray().also { g.put("rows", it) }
                            rows.objs().forEachIndexed { ri, r ->
                                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(value = r.s("sujet"), onValueChange = { r.put("sujet", it); ch() }, label = { Text("Activité") }, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { g.put("rows", rows.without(ri)); ch() }) { Icon(Icons.Default.Delete, "Supprimer", tint = Danger) }
                                }
                                OutlinedTextField(value = r.s("description"), onValueChange = { r.put("description", it); ch() }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                                AiButton(if (r.s("description").isBlank()) "Proposer une description" else "Enrichir la description", busy == "r$ci-$gi-$ri") {
                                    ai("r$ci-$gi-$ri", intro + "Rédige une courte description (2 à 4 phrases) d'une activité de ton programme annuel de travail : objectif, public visé, modalités de mise en œuvre et résultat attendu. Ton administratif, sans titre ni puces, sans inventer de chiffres. Rédige dans la langue de l'intitulé de l'activité. Retourne uniquement la description.\n\nAxe : " +
                                        c.s("titre") + "\nType d'activité : " + g.s("typeActivite") + "\nActivité : " + r.s("sujet") + (if (r.s("description").isNotBlank()) "\nBrouillon : " + r.s("description") else "")) { r.put("description", it) }
                                }
                                val mois = r.optJSONObject("mois") ?: JSONObject().also { r.put("mois", it) }
                                Text("Nombre par mois", fontSize = 12.sp, color = InkFaint)
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    MOIS.forEachIndexed { mi, m ->
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(MOIS_L[mi], fontSize = 11.sp)
                                            OutlinedTextField(value = mois.optInt(m, 0).let { if (it == 0) "" else it.toString() }, onValueChange = { x -> mois.put(m, x.filter { it.isDigit() }.take(2).toIntOrNull() ?: 0); ch() },
                                                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(54.dp))
                                        }
                                    }
                                }
                            }
                            TextButton(onClick = { rows.put(JSONObject().put("id", UUID.randomUUID().toString()).put("sujet", "").put("description", "").put("mois", JSONObject().apply { MOIS.forEach { put(it, 0) } })); ch() }) { Icon(Icons.Default.Add, null); Text("Activité") }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { groups.put(JSONObject().put("id", UUID.randomUUID().toString()).put("typeActivite", "").put("rows", JSONArray())); ch() }) { Icon(Icons.Default.Add, null); Text("Type d'activité") }
                        TextButton(onClick = { pa.put("categories", cats.without(ci)); tab = -1; ch() }) { Text("Supprimer la catégorie", color = Danger) }
                    }
                }
                Spacer(Modifier.height(60.dp))
            }
        }
    }
}

fun programmeHtml(ctx: android.content.Context, pa: JSONObject): String {
    val sb = StringBuilder(Docs.header(ctx, false))
    sb.append("<h1>Programme annuel de travail ${Docs.h(pa.s("anneeScolaire"))}</h1>")
    if (pa.s("preambule").isNotBlank()) sb.append("<h2>Préambule</h2>").append(Docs.para(pa.s("preambule")))
    pa.optJSONArray("categories")?.objs().orEmpty().forEach { c ->
        sb.append("<h2>${Docs.h(c.s("titre"))}</h2>")
        if (c.s("description").isNotBlank()) sb.append(Docs.para(c.s("description")))
        val rows = mutableListOf<List<String>>()
        c.optJSONArray("groups")?.objs().orEmpty().forEach { g ->
            g.optJSONArray("rows")?.objs().orEmpty().forEach { r ->
                val m = r.optJSONObject("mois") ?: JSONObject()
                rows.add(listOf(g.s("typeActivite"), r.s("sujet") + (if (r.s("description").isNotBlank()) "\n" + r.s("description") else "")) + MOIS.map { val n = m.optInt(it, 0); if (n == 0) "" else n.toString() } + listOf(MOIS.sumOf { m.optInt(it, 0) }.toString()))
            }
        }
        sb.append(Docs.table(listOf("Type", "Activité") + MOIS_L + listOf("Total"), rows, listOf(12, 40) + MOIS.map { 4 } + listOf(6)))
    }
    return Docs.page(sb.toString(), false, landscape = true)
}
