@file:OptIn(ExperimentalMaterial3Api::class)

package ma.carnet.inspection.natif.ui

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/** Boutons « + document » d'une tâche et liste de ses documents. */
@Composable
fun TaskDocsSection(task: JSONObject) {
    val v by Store.version
    val id = task.s("id")
    val docs = remember(v, id) { Store.list("documents").filter { it.s("taskId") == id }.sortedBy { it.s("createdAt") } }
    SectionTitle("Documents (${docs.size})")
    CardBox {
        docs.forEach { d ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Create, null, tint = Brand, modifier = Modifier.size(18.dp))
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(Docs.label(d) + (if (d.s("beneficiaireNom").isNotEmpty() && d.s("type") == "attestation") " — " + d.s("beneficiaireNom") else ""), fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(fmtDate(d.s("date")), color = InkFaint, fontSize = 11.5.sp)
                }
                IconButton(onClick = { Nav.push(Scr.DocPreview(d.s("id"))) }) { Icon(Icons.Default.Search, "Aperçu", tint = Brand) }
                IconButton(onClick = { Nav.push(Scr.DocEdit(d.s("id"), id, d.s("type"), null)) }) { Icon(Icons.Default.Edit, "Modifier", tint = Brand) }
            }
        }
        if (docs.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text("Nouveau document :", color = InkFaint, fontSize = 12.sp)
        val types = Docs.FOR_TYPE[Ref.type(task)] ?: listOf("rapport", "bordereau")
        FlowRowCompat {
            types.forEach { t -> AssistChip(onClick = { Nav.push(Scr.DocEdit(null, id, t, null)) }, label = { Text(Docs.LABELS[t] ?: t, fontSize = 12.sp) }, leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) }) }
            CustomTpl.forTask(task).forEach { ct -> AssistChip(onClick = { Nav.push(Scr.DocEdit(null, id, "custom", ct.s("id"))) }, label = { Text(ct.s("nom"), fontSize = 12.sp) }, leadingIcon = { Icon(Icons.Default.Star, null, Modifier.size(16.dp)) }) }
            if (task.arr("invites").isNotEmpty() && "attestation" in types)
                AssistChip(onClick = { createAttestations(task) }, label = { Text("Attestations des invité(e)s (${task.arr("invites").size})", fontSize = 12.sp) }, leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) })
        }
    }
}

fun createAttestations(task: JSONObject) {
    task.arr("invites").forEach { iv ->
        val d = Docs.defaultDoc(task, "attestation"); d.put("beneficiaireNom", iv.s("nom")); Store.upsert("documents", d)
    }
}

/** Disposition en lignes qui passent à la ligne (FlowRow simplifié). */
@Composable
fun FlowRowCompat(content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content = content) { measurables, constraints ->
        val gap = 6.dp.roundToPx()
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        var x = 0; var y = 0; var rowH = 0
        val pos = placeables.map { p ->
            if (x > 0 && x + p.width > constraints.maxWidth) { x = 0; y += rowH + gap; rowH = 0 }
            val r = Pair(x, y); x += p.width + gap; rowH = maxOf(rowH, p.height); r
        }
        layout(constraints.maxWidth, y + rowH) { placeables.forEachIndexed { i, p -> p.placeRelative(pos[i].first, pos[i].second) } }
    }
}

@Composable
fun AiButton(label: String = "Rédiger avec l'IA", busy: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = !busy) {
        if (busy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Star, null, Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp)); Text(label, fontSize = 12.5.sp)
    }
}

// ---------------- Édition d'un document ----------------
@Composable
fun DocEditScreen(docId: String?, taskId: String, type: String, tplId: String?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val task = remember(taskId) { Store.byId("tasks", taskId) ?: JSONObject().put("id", taskId) }
    val orig = remember(docId) { docId?.let { Store.byId("documents", it) } }
    val d = remember(docId) {
        mutableStateOf(if (orig != null) JSONObject(orig.toString()) else {
            val tpl = tplId?.let { id -> CustomTpl.templates().firstOrNull { it.s("id") == id } }
            Docs.defaultDoc(task, type, tpl).also { nd -> if (tpl != null) { nd.put("aiZones", JSONArray(tpl.optJSONArray("aiZones")?.toString() ?: "[]")); nd.put("aiValues", JSONObject()) } }
        })
    }
    var tick by remember { mutableIntStateOf(0) }
    fun set(k: String, value: Any?) { d.value.put(k, value ?: JSONObject.NULL); tick++ }
    fun g(k: String): String { tick; return d.value.s(k) }
    var aiBusy by remember { mutableStateOf("") }
    var askDelete by remember { mutableStateOf(false) }
    var pickTeachers by remember { mutableStateOf<String?>(null) }
    val rtl = Docs.isRtl(d.value)
    fun save(): String { Store.upsert("documents", d.value); return d.value.s("id") }
    fun runAi(key: String, prompt: String, apply: (String) -> Unit) {
        if (!Ai.ready(ctx)) { toast(ctx, "Ajoutez votre clé Gemini dans Plus → Paramètres."); return }
        aiBusy = key
        scope.launch {
            try { apply(Ai.generate(ctx, prompt)) } catch (e: Exception) { toast(ctx, e.message ?: "Erreur IA") }
            aiBusy = ""
        }
    }
    ScreenScaffold(Docs.label(d.value), { Nav.pop() }, actions = {
        IconButton(onClick = { val id = save(); Nav.replace(Scr.DocPreview(id)) }) { Icon(Icons.Default.Search, "Aperçu") }
        if (orig != null) IconButton(onClick = { askDelete = true }) { Icon(Icons.Default.Delete, "Supprimer") }
        TextButton(onClick = { save(); toast(ctx, "Document enregistré."); Nav.pop() }) { Text("Enregistrer", color = Color.White, fontWeight = FontWeight.Bold) }
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(14.dp)) {
            if (rtl) Text("Document rédigé en arabe.", color = InkFaint, fontSize = 12.sp)
            Docs.fields(type).forEach { f ->
                when (f.kind) {
                    Docs.Kind.DATE -> DateField(f.label, g(f.key), { set(f.key, it) })
                    Docs.Kind.TIME -> TimeField(f.label, g(f.key), { set(f.key, it) }, Modifier.fillMaxWidth())
                    Docs.Kind.SELECT -> SelectField(f.label, f.options.associateWith { it }, g(f.key), { set(f.key, it) }, allowEmpty = true)
                    Docs.Kind.AREA -> {
                        TextFieldRow(f.label, g(f.key), { set(f.key, it) }, multiline = true)
                        if (f.ai) AiButton(if (g(f.key).isBlank()) "Rédiger avec l'IA" else "Enrichir avec l'IA", aiBusy == f.key) {
                            runAi(f.key, Ai.fieldPrompt(d.value, f.label, f.key, task, rtl)) { set(f.key, it) }
                        }
                    }
                    else -> TextFieldRow(f.label, g(f.key), { set(f.key, it) })
                }
            }
            // grille d'observation : critères
            if (type == "observation") {
                SectionTitle("Critères")
                tick
                val crit = d.value.optJSONArray("criteres") ?: JSONArray().also { d.value.put("criteres", it) }
                for (i in 0 until crit.length()) {
                    val c = crit.optJSONObject(i) ?: continue
                    CardBox {
                        Text(c.s("nom"), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                        ChipRow(Ref.NOTE_SCALE.associateWith { it }, c.s("note"), { c.put("note", it); tick++ }, null)
                        OutlinedTextField(value = c.s("commentaire"), onValueChange = { c.put("commentaire", it); tick++ }, label = { Text("Commentaire") }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            // listes (participants, pièces, actions)
            Docs.lists(type).forEach { L ->
                SectionTitle(L.label)
                tick
                val arr = d.value.optJSONArray(L.key) ?: JSONArray().also { d.value.put(L.key, it) }
                for (i in 0 until arr.length()) {
                    val r = arr.optJSONObject(i) ?: continue
                    CardBox {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", color = InkFaint, modifier = Modifier.width(22.dp))
                            Column(Modifier.weight(1f)) {
                                L.cols.forEach { c ->
                                    OutlinedTextField(value = r.s(c.key), onValueChange = { r.put(c.key, it); tick++ }, label = { Text(c.label) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
                                }
                            }
                            IconButton(onClick = {
                                val n = JSONArray(); for (j in 0 until arr.length()) if (j != i) n.put(arr.opt(j)); d.value.put(L.key, n); tick++
                            }) { Icon(Icons.Default.Delete, "Retirer", tint = Danger) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { arr.put(JSONObject().apply { L.cols.forEach { c -> put(c.key, "") } }); tick++ }) { Icon(Icons.Default.Add, null); Text("Ajouter une ligne") }
                    if (L.fromTeachers) OutlinedButton(onClick = { pickTeachers = L.key }) { Icon(Icons.Default.Person, null); Text("Des enseignant(e)s") }
                }
            }
            // zones IA d'un modèle personnalisé
            val zones = d.value.arr("aiZones")
            if (zones.isNotEmpty()) {
                SectionTitle("Zones rédigées par l'IA")
                val vals = d.value.optJSONObject("aiValues") ?: JSONObject().also { d.value.put("aiValues", it) }
                zones.forEach { z ->
                    tick
                    TextFieldRow(z.s("label").ifEmpty { z.s("id") }, vals.s(z.s("id")), { vals.put(z.s("id"), it); tick++ }, multiline = true)
                    Text("Consigne : " + z.s("prompt"), color = InkFaint, fontSize = 11.sp)
                    AiButton(busy = aiBusy == "z_" + z.s("id")) {
                        val prompt = "Tu rédiges pour un(e) inspecteur(trice) pédagogique au Maroc une partie du document « ${d.value.s("titre")} ». Consigne : " +
                            CustomTpl.apply(z.s("prompt").ifEmpty { z.s("label") }, CustomTpl.values(d.value, task)) + "\n\nRédige " + (if (rtl) "en arabe standard moderne" else "en français") +
                            ", ton administratif sobre, sans inventer de noms ni de chiffres. Retourne uniquement le texte.\n\nContexte :\n" + Ai.taskContext(task)
                        runAi("z_" + z.s("id"), prompt) { vals.put(z.s("id"), it); tick++ }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = { val id = save(); Nav.replace(Scr.DocPreview(id)) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Search, null); Spacer(Modifier.width(6.dp)); Text("Enregistrer et voir l'aperçu") }
            Spacer(Modifier.height(40.dp))
        }
    }
    if (askDelete) ConfirmDialog("Supprimer le document ?", "Le document sera supprimé.", onDismiss = { askDelete = false }) { Store.delete("documents", d.value.s("id")); Nav.pop() }
    pickTeachers?.let { key ->
        MultiTeacherPicker(initial = emptySet(), etabId = task.s("etablissementId"), onDismiss = { pickTeachers = null }) { picked ->
            val arr = d.value.optJSONArray(key) ?: JSONArray().also { d.value.put(key, it) }
            picked.forEach { e -> arr.put(Docs.presenceRow(e, null)) }
            tick++; pickTeachers = null
        }
    }
}

// ---------------- Aperçu, impression et PDF ----------------
@Composable
fun DocPreviewScreen(docId: String) {
    val ctx = LocalContext.current
    val v by Store.version
    val d = remember(v, docId) { Store.byId("documents", docId) }
    var web by remember { mutableStateOf<WebView?>(null) }
    if (d == null) { ScreenScaffold("Document", { Nav.pop() }) { EmptyState("Document introuvable.") }; return }
    val html = remember(v, docId) { Docs.html(ctx, d) }
    val task = Store.byId("tasks", d.s("taskId"))
    val name = (Docs.label(d) + "_" + (task?.s("titre") ?: d.s("titre"))).replace(Regex("[\\\\/:*?\"<>|]+"), "_").take(80)
    ScreenScaffold(Docs.label(d), { Nav.pop() }, actions = {
        IconButton(onClick = { Nav.push(Scr.DocEdit(docId, d.s("taskId"), d.s("type"), null)) }) { Icon(Icons.Default.Edit, "Modifier") }
        IconButton(onClick = { web?.let { printWeb(ctx, it, name) } }) { Icon(Icons.Default.Share, "Imprimer / PDF") }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Imprimez ou enregistrez en PDF (choisissez « Enregistrer au format PDF »).", fontSize = 12.sp, color = InkFaint, modifier = Modifier.weight(1f))
                Button(onClick = { web?.let { printWeb(ctx, it, name) } }) { Text("PDF / Imprimer") }
            }
            AndroidView(factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = false
                    settings.loadWithOverviewMode = true; settings.useWideViewPort = true; settings.builtInZoomControls = true; settings.displayZoomControls = false
                    webViewClient = WebViewClient()
                    web = this
                }
            }, update = { it.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null) }, modifier = Modifier.fillMaxSize())
        }
    }
}

fun printWeb(ctx: Context, web: WebView, name: String) {
    val pm = ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager
    pm.print(name, web.createPrintDocumentAdapter(name), PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
}

// ---------------- Tous les documents ----------------
@Composable
fun DocsAllScreen() {
    val v by Store.version
    var q by remember { mutableStateOf("") }
    var ty by remember { mutableStateOf("") }
    val all = remember(v) { Store.list("documents").sortedByDescending { it.s("updatedAt").ifEmpty { it.s("date") } } }
    val nq = norm(q)
    val list = all.filter { d -> (ty.isEmpty() || d.s("type") == ty) && (nq.isEmpty() || norm(Docs.label(d) + " " + (Store.byId("tasks", d.s("taskId"))?.s("titre") ?: "")).contains(nq)) }
    ScreenScaffold("Documents", { Nav.pop() }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 14.dp)) {
            item { Spacer(Modifier.height(8.dp)); SearchField(q, { q = it }, "Rechercher un document…"); ChipRow(Docs.LABELS, ty, { ty = it }, "Tous") }
            if (list.isEmpty()) item { EmptyState("Aucun document.") }
            items(list, key = { it.s("id") }) { d ->
                val t = Store.byId("tasks", d.s("taskId"))
                CardBox(onClick = { Nav.push(Scr.DocPreview(d.s("id"))) }) {
                    Text(Docs.label(d), fontWeight = FontWeight.SemiBold)
                    Text(listOfNotNull(t?.s("titre"), fmtDate(d.s("date"))).filter { it.isNotEmpty() }.joinToString(" · "), color = InkFaint, fontSize = 12.5.sp)
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

// ---------------- Sélection multiple d'enseignant(e)s ----------------
@Composable
fun MultiTeacherPicker(initial: Set<String>, etabId: String, onDismiss: () -> Unit, onDone: (List<JSONObject>) -> Unit) {
    var q by remember { mutableStateOf("") }
    var onlyEtab by remember { mutableStateOf(etabId.isNotEmpty()) }
    val sel = remember { mutableStateListOf<String>().apply { addAll(initial) } }
    val all = remember { Store.enseignants() }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Fermer") }
                    Text("Choisir des enseignant(e)s (${sel.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onDone(all.filter { it.s("id") in sel }) }) { Text("Valider") }
                }
                SearchField(q, { q = it }, "Rechercher…")
                if (etabId.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = onlyEtab, onCheckedChange = { onlyEtab = it }); Text("Seulement : " + etabName(etabId), fontSize = 13.sp)
                }
                val nq = norm(q)
                val list = all.filter { (!onlyEtab || it.s("etablissementId") == etabId) && (nq.isEmpty() || norm(it.s("nom") + " " + it.s("ppr") + " " + etabName(it.s("etablissementId"))).contains(nq)) }.sortedBy { norm(it.s("nom")) }
                TextButton(onClick = { list.forEach { if (it.s("id") !in sel) sel.add(it.s("id")) } }) { Text("Tout cocher (${list.size})") }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.s("id") }) { e ->
                        val id = e.s("id")
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = id in sel, onCheckedChange = { if (it) sel.add(id) else sel.remove(id) })
                            Column(Modifier.weight(1f)) {
                                Text(e.s("nom"), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOf(e.s("ppr"), etabName(e.s("etablissementId"))).filter { it.isNotEmpty() }.joinToString(" · "), color = InkFaint, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
