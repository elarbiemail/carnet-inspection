@file:OptIn(ExperimentalMaterial3Api::class)

package ma.carnet.inspection.natif.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ma.carnet.inspection.natif.data.*
import org.json.JSONArray
import org.json.JSONObject

// ---------------- Validation des tableaux de service ----------------
val TS_STATUTS = linkedMapOf("validee" to "Validée", "non_validee" to "Non validée", "en_attente" to "En attente")
val TS_STATUTS_AR = mapOf("validee" to "مصادق عليه", "non_validee" to "غير مصادق عليه", "en_attente" to "في الانتظار")
val REMARQUES_TS = listOf(
    "Règles générales" to "Volume horaire hebdomadaire de l'enseignant(e) incomplet (masse horaire non respectée)",
    "Règles générales" to "Cours d'une même matière non répartis de façon équilibrée sur la semaine (concentrés en début ou fin de semaine)",
    "Règles générales" to "Séances non suffisamment espacées entre elles au cours de la semaine",
    "Règles générales" to "Séances d'une classe à examen programmées après 16h",
    "Règles générales" to "Heures d'encadrement (soutien/accompagnement) du professeur(e) principal(e) dépassant 4h par semaine",
    "Règles générales" to "Professeur(e) stagiaire chargé(e) d'une classe à examen",
    "Règles générales" to "Plus de six heures de cours programmées en une seule journée pour l'enseignant(e) (secondaire qualifiant)",
    "Langue française" to "Non-respect de l'ordre de succession des composantes de la matière (Lecture, Langue, Oral, Écrit)",
    "Langue française" to "Deux heures consécutives programmées en une seule séance pour une classe dont le volume hebdomadaire ne dépasse pas quatre heures",
    "Langue française" to "Séances de la matière non espacées convenablement durant la semaine",
    "Langue française" to "Volume horaire hebdomadaire non conforme au barème réglementaire du niveau/de la filière"
)

@Composable
fun RemarquesPicker(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Remarques usuelles") }, text = {
        LazyColumn(Modifier.heightIn(max = 460.dp)) {
            var last = ""
            REMARQUES_TS.forEach { (cat, t) ->
                if (cat != last) { val c = cat; item { Text(c, fontWeight = FontWeight.Bold, color = Brand, modifier = Modifier.padding(top = 8.dp)) }; last = cat }
                item { Text("• $t", fontSize = 13.5.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onPick(t) }) }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { BtnText("Fermer", maxLines = 1, softWrap = false) } })
}


@Composable
fun TsSection(t: JSONObject) {
    val id = t.s("id")
    var tick by remember { mutableIntStateOf(0) }
    var remarkFor by remember { mutableStateOf(-1) }
    var pickOne by remember { mutableStateOf(false) }
    fun save(m: (JSONObject) -> Unit) { val c = JSONObject((Store.byId("tasks", id) ?: t).toString()); m(c); Store.upsert("tasks", c); tick++ }
    val cur = remember(tick, Store.version.intValue) { Store.byId("tasks", id) ?: t }
    val fs = cur.arr("feuilles")
    val n = { st: String -> fs.count { it.s("statut") == st } }
    val missing = Store.list("enseignants").filter { e -> e.s("etablissementId") == cur.s("etablissementId") && cur.s("etablissementId").isNotEmpty() && fs.none { it.s("enseignantId") == e.s("id") } }
    SectionTitle("Feuilles personnelles — tableaux de service (${fs.size})")
    CardBox {
        Text("${n("validee")} validée(s) · ${n("non_validee")} non validée(s) · ${n("en_attente")} en attente", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        if (cur.s("etablissementId").isEmpty()) Text("Choisissez d'abord l'établissement (Modifier la tâche).", color = Warn, fontSize = 12.5.sp)
        fs.sortedBy { norm(it.s("nom")) }.forEach { f ->
            val idx = fs.indexOf(f)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text(f.s("nom") + if (f.s("ppr").isNotEmpty()) " · PPR ${f.s("ppr")}" else "", fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ChipRow(TS_STATUTS, f.s("statut").ifEmpty { "en_attente" }, { st -> if (st.isNotEmpty()) save { c -> c.optJSONArray("feuilles")?.optJSONObject(idx)?.put("statut", st) } }, null)
            if (f.s("statut") == "non_validee") {
                var obs by remember(f.s("enseignantId"), tick) { mutableStateOf(f.s("observation")) }
                OutlinedTextField(obs, { obs = it }, Modifier.fillMaxWidth(), label = { Text("Remarques (motif de la non-validation)") })
                Row {
                    TextButton(onClick = { save { c -> c.optJSONArray("feuilles")?.optJSONObject(idx)?.put("observation", obs) } }) { BtnText("Enregistrer", maxLines = 1, softWrap = false) }
                    TextButton(onClick = { save { c -> c.optJSONArray("feuilles")?.optJSONObject(idx)?.put("observation", obs) }; remarkFor = idx }) { BtnText("+ Remarque usuelle", maxLines = 1, softWrap = false) }
                }
            }
            TextButton(onClick = { save { c -> val a = c.optJSONArray("feuilles") ?: return@save; val nn = JSONArray(); for (j in 0 until a.length()) if (j != idx) nn.put(a.opt(j)); c.put("feuilles", nn) } }) { BtnText("Retirer", color = Danger) }
        }
        Row(Modifier.horizontalScrollRow(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (missing.isNotEmpty()) OutlinedButton(onClick = { save { c -> val a = c.optJSONArray("feuilles") ?: JSONArray().also { c.put("feuilles", it) }; missing.sortedBy { norm(it.s("nom")) }.forEach { e -> a.put(JSONObject().put("enseignantId", e.s("id")).put("nom", e.s("nom")).put("ppr", e.s("ppr")).put("statut", "en_attente").put("observation", "")) } } }) { BtnText("Ajouter les enseignant(e)s (${missing.size})", maxLines = 1, softWrap = false) }
            OutlinedButton(onClick = { pickOne = true }) { BtnText("+ Enseignant(e)", maxLines = 1, softWrap = false) }
            if (n("en_attente") > 0) OutlinedButton(onClick = { save { c -> c.optJSONArray("feuilles")?.let { a -> for (j in 0 until a.length()) a.optJSONObject(j)?.let { if (it.s("statut").ifEmpty { "en_attente" } == "en_attente") it.put("statut", "validee") } } } }) { BtnText("Valider celles en attente", maxLines = 1, softWrap = false) }
        }
    }
    if (remarkFor >= 0) RemarquesPicker({ remarkFor = -1 }) { r ->
        val i = remarkFor
        save { c -> c.optJSONArray("feuilles")?.optJSONObject(i)?.let { f -> f.put("observation", listOf(f.s("observation"), r).filter { it.isNotBlank() }.joinToString("\n")) } }
        remarkFor = -1
    }
    if (pickOne) MultiTeacherPicker(emptySet(), cur.s("etablissementId"), { pickOne = false }) { picked ->
        save { c -> val a = c.optJSONArray("feuilles") ?: JSONArray().also { c.put("feuilles", it) }; picked.filter { p -> fs.none { it.s("enseignantId") == p.s("id") } }.forEach { e -> a.put(JSONObject().put("enseignantId", e.s("id")).put("nom", e.s("nom")).put("ppr", e.s("ppr")).put("statut", "en_attente").put("observation", "")) } }
        pickOne = false
    }
}

// ---------------- Horaire hebdomadaire (fiche enseignant) ----------------
@Composable
fun HoraireEditor(e: JSONObject, onChange: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val arr = e.optJSONArray("horaire") ?: JSONArray().also { e.put("horaire", it) }
    SectionTitle("Horaire hebdomadaire (${arr.length()} séance(s))")
    tick
    for (i in 0 until arr.length()) {
        val sl = arr.optJSONObject(i) ?: continue
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SelectField("Jour", Ref.JOURS.associateWith { it }, sl.s("jour").ifEmpty { "Lundi" }, { sl.put("jour", it); tick++; onChange() }) }
                IconButton(onClick = { val n = JSONArray(); for (j in 0 until arr.length()) if (j != i) n.put(arr.opt(j)); e.put("horaire", n); tick++; onChange() }) { Icon(Icons.Default.Delete, "Retirer", tint = Danger) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeField("Début", sl.s("debut"), { sl.put("debut", it); tick++; onChange() }, Modifier.weight(1f))
                TimeField("Fin", sl.s("fin"), { sl.put("fin", it); tick++; onChange() }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextFieldRow("Classe", sl.s("classe"), { sl.put("classe", it); tick++; onChange() }, modifier = Modifier.weight(1f))
                TextFieldRow("Salle", sl.s("salle"), { sl.put("salle", it); tick++; onChange() }, modifier = Modifier.weight(1f))
            }
            TextFieldRow("Matière", sl.s("matiere"), { sl.put("matiere", it); tick++; onChange() })
        }
    }
    OutlinedButton(onClick = {
        val last = (if (arr.length() > 0) arr.optJSONObject(arr.length() - 1) else null)
        arr.put(JSONObject().put("jour", last?.s("jour") ?: "Lundi").put("debut", last?.s("fin")?.ifEmpty { null } ?: "08:00").put("fin", "").put("classe", "").put("salle", "").put("matiere", e.s("matiere"))); tick++; onChange()
    }) { Icon(Icons.Default.Add, null); Text("Ajouter une séance") }
}

// ---------------- Emploi du temps d'un établissement / recherche par horaire ----------------
fun etabTimetableHtml(ctx: Context, etabId: String): String {
    val ens = Store.list("enseignants").filter { it.s("etablissementId") == etabId }.sortedBy { norm(it.s("nom")) }
    val slots = ens.flatMap { e -> e.arr("horaire").map { e to it } }
    val times = slots.map { it.second.s("debut") + "–" + it.second.s("fin") }.toSortedSet().toList()
    val sb = StringBuilder(Docs.header(ctx, false))
    sb.append("<h1>Emploi du temps hebdomadaire</h1><p style=\"text-align:center\"><b>${Docs.h(etabName(etabId))}</b> — ${ens.size} enseignant(e)s</p>")
    if (slots.isEmpty()) sb.append("<p>Aucun horaire n'est enregistré pour les enseignant(e)s de cet établissement (fiche enseignant → Horaire hebdomadaire).</p>")
    else {
        val rows = times.map { tm -> listOf(tm) + Ref.JOURS.map { j -> slots.filter { (_, s) -> s.s("jour") == j && s.s("debut") + "–" + s.s("fin") == tm }.joinToString("\n") { (e, s) -> e.s("nom") + listOf(s.s("classe"), s.s("salle").let { if (it.isEmpty()) "" else "salle $it" }).filter { it.isNotEmpty() }.joinToString(" · ").let { if (it.isEmpty()) "" else " ($it)" } } } }
        sb.append(Docs.table(listOf("Horaire") + Ref.JOURS, rows, listOf(10) + Ref.JOURS.map { 90 / Ref.JOURS.size }))
        sb.append("<h2>Par enseignant(e)</h2>")
        sb.append(Docs.table(listOf("Enseignant(e)", "PPR", "Séances", "Heures / semaine"), ens.map { e ->
            val mins = e.arr("horaire").sumOf { s -> val a = s.s("debut").split(":"); val b = s.s("fin").split(":"); ((b.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (b.getOrNull(1)?.toIntOrNull() ?: 0) - (a.getOrNull(0)?.toIntOrNull() ?: 0) * 60 - (a.getOrNull(1)?.toIntOrNull() ?: 0)).coerceAtLeast(0) }
            listOf(e.s("nom"), e.s("ppr"), e.arr("horaire").size.toString(), "%.1f".format(mins / 60.0))
        }, listOf(45, 20, 15, 20)))
    }
    return Docs.page(sb.toString(), false, landscape = true)
}

fun horaireSearchHtml(ctx: Context, jour: String, heure: String, list: List<JSONObject>): String {
    val sb = StringBuilder(Docs.header(ctx, false))
    sb.append("<h1>Enseignant(e)s en séance</h1><p style=\"text-align:center\">$jour à $heure — ${list.size} enseignant(e)s</p>")
    sb.append(Docs.table(listOf("N°", "Enseignant(e)", "PPR", "Établissement", "Classe", "Salle", "Horaire"), list.mapIndexed { i, e ->
        val sl = e.arr("horaire").firstOrNull { slotMatches(it, jour, heure) } ?: JSONObject()
        listOf("${i + 1}", e.s("nom"), e.s("ppr"), etabName(e.s("etablissementId")), sl.s("classe"), sl.s("salle"), sl.s("debut") + "–" + sl.s("fin"))
    }, listOf(5, 25, 11, 25, 11, 9, 14)))
    return Docs.page(sb.toString(), false)
}

// ---------------- Recherche globale ----------------
@Composable
fun SearchScreen() {
    var q by remember { mutableStateOf("") }
    val nq = norm(q)
    ScreenScaffold("Rechercher", { Nav.pop() }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 14.dp)) {
            item { Spacer(Modifier.height(8.dp)); SearchField(q, { q = it }, "Tâche, enseignant(e), établissement, document…") }
            if (nq.length >= 2) {
                val ts = Store.list("tasks").filter { norm(it.s("titre") + " " + it.s("lieu") + " " + taskEtabName(it) + " " + taskEnsName(it) + " " + it.s("description")).contains(nq) }.take(30)
                val es = Store.list("enseignants").filter { norm(it.s("nom") + " " + it.s("nomAr") + " " + it.s("ppr") + " " + it.s("matiere")).contains(nq) }.take(30)
                val ets = Store.list("etablissements").filter { norm(it.s("nom") + " " + it.s("nomAr") + " " + it.s("directeur") + " " + it.s("codeMassar")).contains(nq) }.take(30)
                val ds = Store.list("documents").filter { norm(Docs.label(it) + " " + it.s("titre") + " " + it.s("objet") + " " + it.s("enseignantNom") + " " + it.s("beneficiaireNom")).contains(nq) }.take(30)
                if (ts.isEmpty() && es.isEmpty() && ets.isEmpty() && ds.isEmpty()) item { EmptyState("Aucun résultat.") }
                if (ts.isNotEmpty()) item { SectionTitle("Tâches (${ts.size})") }
                items(ts, key = { "t" + it.s("id") }) { t -> CardBox(onClick = { Nav.push(Scr.TaskDetail(t.s("id"))) }) { Text(t.s("titre").ifEmpty { autoTaskTitle(t) }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(fmtDate(t.s("echeance")) + " · " + taskEtabName(t), color = InkFaint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                if (es.isNotEmpty()) item { SectionTitle("Enseignant(e)s (${es.size})") }
                items(es, key = { "e" + it.s("id") }) { TeacherRow(it) }
                if (ets.isNotEmpty()) item { SectionTitle("Établissements (${ets.size})") }
                items(ets, key = { "s" + it.s("id") }) { e -> EtabRow(e, Store.list("enseignants").count { it.s("etablissementId") == e.s("id") }) }
                if (ds.isNotEmpty()) item { SectionTitle("Documents (${ds.size})") }
                items(ds, key = { "d" + it.s("id") }) { d -> CardBox(onClick = { Nav.push(Scr.DocPreview(d.s("id"))) }) { Text(Docs.label(d), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(fmtDate(d.s("date")), color = InkFaint, fontSize = 12.sp) } }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

// ---------------- Import d'une fiche personnelle par photo (IA) ----------------
@Composable
fun FicheImportButton() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    suspend fun analyse(bytes: ByteArray, mime: String) {
        busy = true
        try {
            val etabs = Store.list("etablissements").joinToString("; ") { it.s("nom") }
            val prompt = "Voici la fiche personnelle d'un(e) enseignant(e) marocain(e). Extrais les informations et réponds UNIQUEMENT par un objet JSON avec les clés : " +
                "nom (prénom et nom en lettres latines), nomAr (en arabe si présent), ppr, cin, matiere, cadre, genre (F ou H), statut (titulaire ou stagiaire), telephone, email, etablissement (nom de l'établissement d'affectation), " +
                "dateNaissance, dateRecrutement, derniereNote (nombre), dateDerniereNote (AAAA-MM-JJ ou AAAA), horaire (liste de {jour, debut HH:MM, fin HH:MM, classe, salle}). Laisse vide ce qui est absent. Établissements connus : $etabs"
            val raw = Ai.generate(ctx, prompt, bytes, mime)
            val j = JSONObject(raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1))
            val et = Store.list("etablissements").firstOrNull { e -> val n = norm(j.s("etablissement")); n.isNotEmpty() && (norm(e.s("nom")).contains(n) || n.contains(norm(e.s("nom")))) }
            val ex = Store.list("enseignants").firstOrNull { (j.s("ppr").isNotEmpty() && it.s("ppr") == j.s("ppr")) || norm(it.s("nom")) == norm(j.s("nom")) }
            val e = if (ex != null) JSONObject(ex.toString()) else JSONObject().put("statut", "titulaire").put("horaire", JSONArray())
            listOf("nom", "nomAr", "ppr", "cin", "matiere", "cadre", "genre", "statut", "telephone", "email", "dateNaissance", "dateRecrutement").forEach { k -> if (j.s(k).isNotEmpty() && e.s(k).isEmpty()) e.put(k, j.s(k)) }
            if (et != null && e.s("etablissementId").isEmpty()) e.put("etablissementId", et.s("id"))
            j.optJSONArray("horaire")?.let { h -> if (h.length() > 0 && e.arr("horaire").isEmpty()) e.put("horaire", h) }
            if (j.s("derniereNote").isNotEmpty()) {
                val ins = e.optJSONArray("inspections") ?: JSONArray().also { e.put("inspections", it) }
                if (ins.length() == 0) ins.put(JSONObject().put("date", j.s("dateDerniereNote")).put("note", j.s("derniereNote")).put("par", "").put("source", "Fiche personnelle"))
            }
            Store.upsert("enseignants", e)
            toast(ctx, if (ex != null) "Fiche existante complétée." else "Nouvelle fiche créée : vérifiez-la.")
            Nav.push(Scr.EnsEdit(e.s("id")))
        } catch (t: Throwable) { toast(ctx, "Lecture impossible : " + (t.message ?: "")) }
        busy = false
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val mime = ctx.contentResolver.getType(uri) ?: "image/jpeg"
            val raw = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } } ?: return@launch
            val bytes = if (mime.startsWith("image/")) withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(raw, 0, raw.size)?.let { ProfileImages.toJpeg(it, false, 1800) } ?: raw } else raw
            analyse(bytes, if (mime.startsWith("image/")) "image/jpeg" else mime)
        }
    }
    val cam = rememberCamera { b -> if (b != null) scope.launch { analyse(ProfileImages.toJpeg(b, false, 1800), "image/jpeg") } }
    Row(Modifier.horizontalScrollRow(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        AssistChip(onClick = { if (Ai.provider(ctx) == "device") toast(ctx, "Choisissez Gemini ou une API en ligne pour lire les photos.") else cam() }, enabled = !busy, label = { Text(if (busy) "Lecture par l'IA…" else "Photo d'une fiche (IA)") }, leadingIcon = { Icon(Icons.Default.Face, null, Modifier.size(16.dp)) })
        AssistChip(onClick = { pick.launch("*/*") }, enabled = !busy, label = { Text("Image / PDF d'une fiche") }, leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) })
        val ff = Store.root.optJSONObject("ficheForm")
        if (ff != null && ff.s("formUrl").isNotEmpty()) AssistChip(onClick = {
            val i = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, "Bonjour, merci de remplir votre fiche personnelle : " + ff.s("formUrl"))
            ctx.startActivity(android.content.Intent.createChooser(i, "Envoyer le lien").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }, label = { Text("Envoyer le lien de la fiche") }, leadingIcon = { Icon(Icons.Default.Share, null, Modifier.size(16.dp)) })
        if (ff != null && ff.s("sheetFileId").isNotEmpty()) AssistChip(onClick = {
            SyncHolder.withToken("Lecture des réponses…") { tk ->
                val csv = withContext(Dispatchers.IO) { String(DriveSync.export(tk, ff.s("sheetFileId"), "text/csv"), Charsets.UTF_8) }
                val n = CsvImport.teachers(csv)
                SyncState.ok.value = true; SyncState.msg.value = "$n réponse(s) importée(s) dans les fiches enseignant(e)s."; toast(ctx, SyncState.msg.value)
            }
        }, label = { Text("Importer les réponses reçues") }, leadingIcon = { Icon(Icons.Default.Refresh, null, Modifier.size(16.dp)) })
    }
}

// ---------------- Import CSV (enseignant(e)s, établissements) ----------------
object CsvImport {
    private fun split(line: String, sep: Char): List<String> {
        val out = mutableListOf<String>(); val sb = StringBuilder(); var q = false
        line.forEach { c -> when { c == '"' -> q = !q; c == sep && !q -> { out.add(sb.toString().trim()); sb.clear() }; else -> sb.append(c) } }
        out.add(sb.toString().trim()); return out
    }
    fun rows(txt: String): List<Map<String, String>> {
        val lines = txt.removePrefix("﻿").lines().filter { it.isNotBlank() }
        if (lines.size < 2) return emptyList()
        val sep = listOf(';', ',', '\t').maxBy { s -> lines[0].count { it == s } }
        val head = split(lines[0], sep).map { norm(it).replace(" ", "") }
        return lines.drop(1).map { l -> val c = split(l, sep); head.mapIndexed { i, h -> h to c.getOrElse(i) { "" } }.toMap() }
    }
    private fun pick(r: Map<String, String>, vararg keys: String) = keys.firstNotNullOfOrNull { k -> r.entries.firstOrNull { it.key == k || it.key.startsWith(k) }?.value?.ifBlank { null } } ?: ""
    fun teachers(txt: String): Int {
        var n = 0
        rows(txt).forEach { r ->
            val nom = pick(r, "nom", "nometprenom", "name"); if (nom.isBlank()) return@forEach
            val ppr = pick(r, "ppr", "som")
            val ex = Store.list("enseignants").firstOrNull { (ppr.isNotEmpty() && it.s("ppr") == ppr) || norm(it.s("nom")) == norm(nom) }
            val e = if (ex != null) JSONObject(ex.toString()) else JSONObject().put("statut", "titulaire").put("horaire", JSONArray())
            e.put("nom", nom); if (ppr.isNotEmpty()) e.put("ppr", ppr)
            mapOf("nomAr" to arrayOf("nomar", "nomarabe", "الاسم"), "matiere" to arrayOf("matiere"), "cadre" to arrayOf("cadre", "grade"), "telephone" to arrayOf("telephone", "tel", "gsm"), "email" to arrayOf("email", "mail"), "cycle" to arrayOf("niveau", "cycle"))
                .forEach { (k, ks) -> pick(r, *ks).let { if (it.isNotEmpty()) e.put(k, it) } }
            pick(r, "genre", "sexe").let { g -> if (g.isNotEmpty()) e.put("genre", if (norm(g).startsWith("f")) "F" else "H") }
            pick(r, "etablissement", "ecole", "college", "lycee").let { en -> Store.list("etablissements").firstOrNull { norm(it.s("nom")) == norm(en) || norm(it.s("nomAr")) == norm(en) }?.let { e.put("etablissementId", it.s("id")) } }
            Store.upsert("enseignants", e); n++
        }
        return n
    }
    fun etabs(txt: String): Int {
        var n = 0
        rows(txt).forEach { r ->
            val nom = pick(r, "nom", "etablissement", "name"); if (nom.isBlank()) return@forEach
            val ex = Store.list("etablissements").firstOrNull { norm(it.s("nom")) == norm(nom) }
            val e = if (ex != null) JSONObject(ex.toString()) else JSONObject().put("type", "public")
            e.put("nom", nom)
            mapOf("nomAr" to arrayOf("nomar", "nomarabe"), "codeMassar" to arrayOf("code", "massar", "gresa"), "directeur" to arrayOf("directeur", "dir"), "directeurTel" to arrayOf("teldirecteur", "telephonedirecteur", "gsmdirecteur"),
                "telephone" to arrayOf("telephone", "tel"), "adresse" to arrayOf("adresse"), "cycle" to arrayOf("cycle")).forEach { (k, ks) -> pick(r, *ks).let { if (it.isNotEmpty()) e.put(k, it) } }
            pick(r, "zone").let { z -> Store.list("zones").firstOrNull { norm(it.s("nom")) == norm(z) }?.let { e.put("zoneId", it.s("id")) } }
            Store.upsert("etablissements", e); n++
        }
        return n
    }
}

@Composable
fun CsvImportCard() {
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf("") }
    val ens = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) msg = "${CsvImport.teachers(ctx.contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: "")} enseignant(e)(s) importé(e)(s) ou mis à jour." }
    val ets = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) msg = "${CsvImport.etabs(ctx.contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: "")} établissement(s) importé(s) ou mis à jour." }
    SectionTitle("Importer un fichier CSV")
    CardBox {
        Text("Enregistrez votre tableau Excel au format CSV. Colonnes reconnues — enseignant(e)s : nom, nomAr, ppr, matiere, cadre, genre, telephone, email, etablissement, niveau ; établissements : nom, nomAr, code, zone, cycle, directeur, telDirecteur, telephone, adresse.", fontSize = 12.5.sp, color = InkFaint)
        Row(Modifier.horizontalScrollRow(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { ens.launch(arrayOf("text/*", "*/*")) }) { BtnText("Enseignant(e)s (CSV)", maxLines = 1, softWrap = false) }
            OutlinedButton(onClick = { ets.launch(arrayOf("text/*", "*/*")) }) { BtnText("Établissements (CSV)", maxLines = 1, softWrap = false) }
        }
        if (msg.isNotEmpty()) Text(msg, color = Brand, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

// ---------------- Zone : formulaire complet ----------------
val ZONE_CYCLES = linkedMapOf("" to "—", "primaire" to "Primaire", "college" to "Collège", "lycee" to "Lycée", "mixte" to "Mixte")

@Composable
fun ZoneEditDialog(z: JSONObject, onDismiss: () -> Unit) {
    var nom by remember(z) { mutableStateOf(z.s("nom")) }
    var aref by remember(z) { mutableStateOf(z.s("academieRegionale")) }
    var dp by remember(z) { mutableStateOf(z.s("directionProvinciale")) }
    var commune by remember(z) { mutableStateOf(z.s("commune")) }
    var cycle by remember(z) { mutableStateOf(z.s("cycle")) }
    var disc by remember(z) { mutableStateOf(z.s("discipline")) }
    var notes by remember(z) { mutableStateOf(z.s("notes").ifEmpty { z.s("description") }) }
    val zid = z.s("id")
    val sel = remember(z) { mutableStateListOf<String>().apply { addAll(Store.list("etablissements").filter { zid.isNotEmpty() && it.s("zoneId") == zid }.map { it.s("id") }) } }
    val regs = regions()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (zid.isEmpty()) "Nouvelle zone" else "Modifier la zone") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            TextFieldRow("Nom de la zone d'inspection", nom, { nom = it })
            SelectField("Académie régionale (AREF)", regs.associate { it.first to it.first }, aref, { aref = it; dp = "" }, allowEmpty = true)
            SelectField("Direction provinciale", (regs.firstOrNull { it.first == aref }?.second ?: regs.flatMap { it.second }).associateWith { it }, dp, { dp = it }, allowEmpty = true)
            TextFieldRow("Commune", commune, { commune = it })
            SelectField("Cycle", ZONE_CYCLES, cycle, { cycle = it })
            TextFieldRow("Discipline", disc, { disc = it })
            TextFieldRow("Notes", notes, { notes = it }, multiline = true)
            Text("Établissements rattachés", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            Store.list("etablissements").sortedBy { norm(it.s("nom")) }.forEach { e ->
                val other = e.s("zoneId").let { if (it.isNotEmpty() && it != zid) zoneName(it) else "" }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(e.s("id") in sel, { c -> if (c) sel.add(e.s("id")) else sel.remove(e.s("id")) })
                    Column { Text(e.s("nom"), fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); if (other.isNotEmpty()) Text("actuellement dans « $other »", color = InkFaint, fontSize = 11.sp) }
                }
            }
        }
    }, confirmButton = {
        TextButton(enabled = nom.isNotBlank(), onClick = {
            z.put("nom", nom.trim()).put("academieRegionale", aref).put("directionProvinciale", dp).put("commune", commune.trim()).put("cycle", cycle).put("discipline", disc.trim()).put("notes", notes.trim())
            Store.upsert("zones", z)
            Store.list("etablissements").forEach { e ->
                val inSel = e.s("id") in sel; val was = e.s("zoneId") == z.s("id")
                if (inSel != was) { val c = JSONObject(e.toString()); c.put("zoneId", if (inSel) z.s("id") else ""); Store.upsert("etablissements", c) }
            }
            onDismiss()
        }) { BtnText("Enregistrer", maxLines = 1, softWrap = false) }
    }, dismissButton = { TextButton(onClick = onDismiss) { BtnText("Annuler", maxLines = 1, softWrap = false) } })
}

// ---------------- Déconnexion et fermeture ----------------
@Composable
fun LogoutDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val act = ctx.findActivity()
    val scope = rememberCoroutineScope()
    var wipe by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Déconnexion et fermeture") }, text = {
        Column {
            Text(if (DriveSync.enabled(ctx)) "Vos dernières modifications vont être synchronisées avec Google Drive, puis le compte Google sera déconnecté et l'application fermée." else "Google Drive n'est pas connecté : vos données restent enregistrées sur ce téléphone.", fontSize = 13.5.sp)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) { Checkbox(wipe, { wipe = it }); Text("Effacer aussi les données de ce téléphone (appareil partagé)", fontSize = 13.sp) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
        }
    }, confirmButton = {
        TextButton(enabled = !busy, onClick = {
            busy = true
            fun finish() {
                scope.launch {
                    if (act != null) GoogleAccount.signOut(act)
                    if (wipe) { ctx.filesDir.listFiles()?.forEach { if (it.name != "ia") it.deleteRecursively() }; Store.init(ctx) }
                    act?.finishAndRemoveTask()
                }
            }
            if (DriveSync.enabled(ctx)) SyncHolder.withToken("Dernière synchronisation…") { tk -> try { DriveSync.sync(ctx, tk) } catch (_: Throwable) {}; finish() } else finish()
        }) { BtnText("Se déconnecter et fermer", color = Danger) }
    }, dismissButton = { TextButton(onClick = onDismiss) { BtnText("Annuler", maxLines = 1, softWrap = false) } })
}

// ---------------- Écran de bienvenue (connexion Google) ----------------
@Composable
fun WelcomeScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val act = ctx.findActivity()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    var showCid by remember { mutableStateOf(DriveSync.clientId(ctx).isBlank()) }
    var cid by remember { mutableStateOf(DriveSync.clientId(ctx)) }
    Column(Modifier.fillMaxSize().background(Brand).statusBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(30.dp))
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(ma.carnet.inspection.natif.R.mipmap.ic_launcher), null, Modifier.size(110.dp))
        Text("Carnet d'Inspection", color = OnBrand, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
        Text("Tâches, enseignant(e)s, établissements, documents et bilans de l'inspecteur(trice) pédagogique.", color = OnBrand.copy(alpha = 0.85f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(30.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text("Connexion", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Connectez-vous avec votre compte Google pour enregistrer vos données sur votre Google Drive et les retrouver dans le carnet web.", fontSize = 13.sp, color = InkFaint, modifier = Modifier.padding(vertical = 6.dp))
                if (showCid) {
                    OutlinedTextField(cid, { cid = it; DriveSync.setClientId(ctx, it) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Identifiant client Google (Google ID)") }, placeholder = { Text("123…-xxx.apps.googleusercontent.com") })
                    Text("Celui du carnet web (Paramètres → Synchronisation). Voir l'aide dans Paramètres → Compte Google.", fontSize = 11.5.sp, color = InkFaint)
                } else TextButton(onClick = { showCid = true }) { BtnText("Modifier l'identifiant client Google", maxLines = 1, softWrap = false) }
                GoogleSignInButton(busy) {
                    if (act == null) return@GoogleSignInButton
                    busy = true; err = ""
                    scope.launch {
                        try {
                            if (DriveSync.clientId(ctx).isNotBlank()) GoogleAccount.signIn(act)
                            GoogleAccount.setWelcomeDone(ctx); onDone(); SyncHolder.fn(true)
                        } catch (e: Throwable) {
                            if (e.javaClass.simpleName.contains("Cancellation")) err = signInError(e)
                            else { // repli : autorisation Google Drive (choix du compte) — le nom et la photo sont lus ensuite sur Drive
                                toast(ctx, "Connexion directe indisponible : passage par l'autorisation Google Drive.")
                                GoogleAccount.setWelcomeDone(ctx); onDone(); SyncHolder.fn(true)
                            }
                        }
                        busy = false
                    }
                }
                if (err.isNotEmpty()) Text(err, color = Danger, fontSize = 12.5.sp, modifier = Modifier.padding(top = 6.dp))
                OutlinedButton(onClick = { if (DriveSync.clientId(ctx).isBlank()) { err = "Saisissez d'abord l'identifiant client Google."; return@OutlinedButton }; DriveSync.setAuthMode(ctx, "web"); DriveSync.forceChoose = true; GoogleAccount.setWelcomeDone(ctx); onDone(); SyncHolder.fn(true) },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { BtnText("Se connecter par le navigateur (sans Google Cloud)", maxLines = 1, softWrap = false) }
                TextButton(onClick = { GoogleAccount.setWelcomeDone(ctx); onDone() }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { MarqueeText("Continuer sans compte (données sur ce téléphone)", center = true) }
            }
        }
    }
}
