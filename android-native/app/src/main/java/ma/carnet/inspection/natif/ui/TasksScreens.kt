@file:OptIn(ExperimentalMaterial3Api::class)

package ma.carnet.inspection.natif.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.carnet.inspection.natif.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

fun statutColor(st: String): Color = when (st) {
    "a_planifier" -> Color(0xFF8A8F98); "planifiee" -> Color(0xFF2A6FB0); "realisee" -> Brand
    "rapport" -> Color(0xFF7A5BB5); else -> Color(0xFF4F6B3A)
}

fun isLate(t: JSONObject): Boolean {
    val d = parseDate(t.s("echeance")) ?: return false
    return d.isBefore(LocalDate.now()) && Ref.statut(t) !in Ref.DONE
}

@Composable
fun TaskCard(t: JSONObject, sel: Sel? = null) {
    val st = Ref.statut(t)
    val body: @Composable ColumnScope.() -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.s("titre").ifEmpty { autoTaskTitle(t) }, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 4.dp)) {
                    Tag(Ref.TASK_TYPES[Ref.type(t)] ?: "", Brand)
                    Tag(Ref.WORKFLOW[st] ?: "", statutColor(st))
                    if (isLate(t)) Tag("En retard", Danger)
                }
                val sub = listOf(
                    t.s("echeance").let { if (it.isEmpty()) "" else fmtDate(it) + taskTime(t).let { h -> if (h.isEmpty()) "" else " à $h" } },
                    taskEnsName(t), taskEtabName(t).ifEmpty { t.s("lieu") }
                ).filter { it.isNotEmpty() }.joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, color = InkFaint, fontSize = 12.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.KeyboardArrowRight, null, tint = InkFaint)
        }
    }
    if (sel != null) SelCard(t.s("id"), sel, { Nav.push(Scr.TaskDetail(t.s("id"))) }, body)
    else CardBox(onClick = { Nav.push(Scr.TaskDetail(t.s("id"))) }, content = body)
}

// ---------------- Accueil ----------------
@Composable
fun HomeScreen() {
    val v by Store.version
    val p = remember(v) { Store.profil() }
    val tasks = remember(v) { Store.tasks() }
    val ens = remember(v) { Store.enseignants() }
    val etabs = remember(v) { Store.etabs() }
    val today = LocalDate.now()
    val upcoming = tasks.filter { val d = parseDate(it.s("echeance")); d != null && !d.isBefore(today) && !d.isAfter(today.plusDays(14)) && Ref.statut(it) !in Ref.DONE }
        .sortedBy { it.s("echeance") + taskTime(it) }
    val late = tasks.filter { isLate(it) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().background(Brand).statusBarsPadding().padding(horizontal = 18.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Carnet d'Inspection", color = OnBrand.copy(alpha = 0.8f), fontSize = 13.sp)
                Text("Bonjour" + p.s("nomSignature").ifEmpty { p.s("nom") }.ifEmpty { GoogleAccount.name(LocalContext.current) }.let { if (it.isEmpty()) "" else ", $it" },
                    color = OnBrand, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(fmtLong(today.toString()), color = OnBrand.copy(alpha = 0.85f), fontSize = 13.sp)
                Row {
                    IconButton(onClick = { Nav.push(Scr.Search) }) { Icon(Icons.Default.Search, "Rechercher", tint = OnBrand) }
                    IconButton(onClick = { Nav.push(Scr.Help("accueil")) }) { Icon(Icons.Default.Info, "Aide", tint = OnBrand) }
                    IconButton(onClick = { SyncHolder.fn(true) }) { Icon(Icons.Default.Refresh, "Synchroniser", tint = OnBrand) }
                }
            }
            ProfileAvatar(84) { Nav.push(Scr.Settings("profil")) }
        }
        Column(Modifier.padding(horizontal = 14.dp)) {
            Row(Modifier.padding(top = 10.dp)) { ZoneFilterBar() }
            SyncStatusLine(SyncHolder.fn)
            if (Store.isEmpty()) {
                CardBox {
                    Text("Bienvenue !", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Importez votre sauvegarde du carnet web (fichier sauvegarde-donnees.json dans votre Google Drive) pour retrouver toutes vos données.", fontSize = 13.5.sp, modifier = Modifier.padding(vertical = 6.dp))
                    Button(onClick = { Nav.push(Scr.Data) }) { Text("Importer mes données") }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Kpi("Tâches", tasks.size.toString(), Modifier.weight(1f)) { Nav.tab(1) }
                Kpi("En retard", late.size.toString(), Modifier.weight(1f), if (late.isNotEmpty()) Danger else Brand) { Nav.tab(1) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Kpi("Enseignants", ens.size.toString(), Modifier.weight(1f)) { Nav.tab(2) }
                Kpi("Établissements", etabs.size.toString(), Modifier.weight(1f)) { Nav.tab(3) }
            }
            SectionTitle("À venir (14 jours)")
            if (upcoming.isEmpty()) EmptyState("Aucune tâche prévue dans les 14 prochains jours.")
            upcoming.take(8).forEach { TaskCard(it) }
            if (late.isNotEmpty()) { SectionTitle("En retard"); late.sortedBy { it.s("echeance") }.take(5).forEach { TaskCard(it) } }
            if (etabs.isNotEmpty()) {
                SectionTitle("Établissements")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(etabs.sortedBy { norm(it.s("nom")) }, key = { it.s("id") }) { e ->
                        val n = ens.count { it.s("etablissementId") == e.s("id") }
                        MiniCard(e.s("nom"), "$n enseignant(s)") { Nav.push(Scr.EtabDetail(e.s("id"))) }
                    }
                }
            }
            if (ens.isNotEmpty()) {
                SectionTitle("Enseignant(e)s")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(ens.sortedBy { norm(it.s("nom")) }, key = { it.s("id") }) { e ->
                        MiniCard(e.s("nom"), etabName(e.s("etablissementId")), if (e.s("genre") == "F") FemColor else if (e.s("genre") == "H") HomColor else Brand) { Nav.push(Scr.EnsDetail(e.s("id"))) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun Kpi(label: String, value: String, modifier: Modifier, color: Color = Brand, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = Surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, color = InkFaint, fontSize = 12.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun MiniCard(title: String, sub: String, color: Color = Brand, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.width(150.dp), colors = CardDefaults.cardColors(containerColor = Surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(title, color, 46)
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            Text(sub, color = InkFaint, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------- Liste des tâches ----------------
@Composable
fun TasksScreen() {
    val v by Store.version
    var q by remember { mutableStateOf("") }
    var st by remember { mutableStateOf("") }
    var ty by remember { mutableStateOf("") }
    val all = remember(v) { Store.tasks() }
    val nq = norm(q)
    val list = all.filter { t ->
        (st.isEmpty() || (st == "late" && isLate(t)) || Ref.statut(t) == st) &&
        (ty.isEmpty() || Ref.type(t) == ty) &&
        (nq.isEmpty() || norm(listOf(t.s("titre"), taskEnsName(t), taskEtabName(t), t.s("lieu")).joinToString(" ")).contains(nq))
    }.sortedWith(compareByDescending<JSONObject> { it.s("echeance") })
    val sel = rememberSel()
    var askDel by remember { mutableStateOf(false) }
    var editSel by remember { mutableStateOf(false) }
    ScreenScaffold(selTitle(sel, "Tâches"), if (sel.on) ({ sel.clear() }) else null, actions = {
        SelActions(sel, list.map { it.s("id") }, { editSel = true }, { askDel = true })
    }, fab = {
        FloatingActionButton(onClick = { Nav.push(Scr.TaskEdit(null)) }, containerColor = Brand, contentColor = OnBrand) { Icon(Icons.Default.Add, "Nouvelle tâche") }
    }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 14.dp)) {
            item {
                Spacer(Modifier.height(8.dp)); ZoneFilterBar()
                SearchField(q, { q = it }, "Rechercher une tâche…")
                Spacer(Modifier.height(6.dp))
                ChipRow(Ref.WORKFLOW + mapOf("late" to "En retard"), st, { st = it }, "Toutes")
                ChipRow(Ref.TASK_TYPES, ty, { ty = it }, "Tous types")
                Text("${list.size} tâche(s)", color = InkFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
            if (list.isEmpty()) item { EmptyState("Aucune tâche.") }
            items(list, key = { it.s("id") }) { TaskCard(it, sel) }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
    if (askDel) BatchDeleteDialog(sel.ids.size, "tâches", onDismiss = { askDel = false }) { Store.deleteMany("tasks", sel.ids.toList()); sel.clear() }
    if (editSel) BatchEditDialog(sel.ids.size, taskBatchFields(), { editSel = false }) { f, vals -> applyBatch("tasks", sel.ids.toList(), f, vals); sel.clear() }
}

// ---------------- Fiche d'une tâche ----------------
@Composable
fun TaskDetailScreen(id: String) {
    val v by Store.version
    val t = remember(v, id) { Store.byId("tasks", id) }
    var askDelete by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    if (t == null) { ScreenScaffold("Tâche", { Nav.pop() }) { EmptyState("Tâche introuvable.") }; return }
    val st = Ref.statut(t)
    ScreenScaffold(t.s("titre").ifEmpty { autoTaskTitle(t) }, { Nav.pop() }, actions = {
        IconButton(onClick = { Nav.push(Scr.TaskEdit(id)) }) { Icon(Icons.Default.Edit, "Modifier") }
        IconButton(onClick = { askDelete = true }) { Icon(Icons.Default.Delete, "Supprimer") }
        HelpButton("tache")
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(14.dp)) {
            Row { Tag(Ref.TASK_TYPES[Ref.type(t)] ?: "", Brand); Tag(Ref.WORKFLOW[st] ?: "", statutColor(st)); if (isLate(t)) Tag("En retard", Danger) }
            CardBox {
                InfoRow("Date", if (t.s("echeance").isEmpty()) "" else fmtLong(t.s("echeance")))
                InfoRow("Horaire", listOf(taskTime(t), t.s("heureFin")).filter { it.isNotEmpty() }.joinToString(" – "))
                InfoRow("Lieu", t.s("lieu"))
                InfoRow("Établissement", taskEtabName(t))
                InfoRow("Enseignant(e)", taskEnsName(t))
                InfoRow("Priorité", Ref.PRIORITES[t.s("priorite")] ?: "")
                if (t.s("noteGlobale").isNotEmpty()) InfoRow("Appréciation", t.s("noteGlobale"))
                if (t.s("lienConnexion").isNotEmpty()) TextButton(onClick = { openUri(ctx, t.s("lienConnexion")) }) { Text("Rejoindre la réunion") }
            }
            val ens = Store.byId("enseignants", t.s("enseignantId"))
            if (ens != null) {
                CardBox(onClick = { Nav.push(Scr.EnsDetail(ens.s("id"))) }) {
                    Text(ens.s("nom"), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOf(ens.s("cadre"), ens.s("ppr").let { if (it.isEmpty()) "" else "PPR $it" }).filter { it.isNotEmpty() }.joinToString(" · "), color = InkFaint, fontSize = 12.5.sp)
                    ContactButtons(ens.s("telephone"), ens.s("nom"), ens.s("email"))
                }
            }
            val et = Store.byId("etablissements", t.s("etablissementId"))
            if (et != null && (et.s("directeurTel").isNotEmpty() || et.s("telephone").isNotEmpty())) {
                CardBox(onClick = { Nav.push(Scr.EtabDetail(et.s("id"))) }) {
                    Text(et.s("nom"), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (et.s("directeur").isNotEmpty()) Text("Directeur(trice) : " + et.s("directeur"), color = InkFaint, fontSize = 12.5.sp)
                    ContactButtons(et.s("directeurTel").ifEmpty { et.s("telephone") }, et.s("directeur"))
                }
            }
            SectionTitle("Suivi")
            Row(Modifier.fillMaxWidth().horizontalScrollRow(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Ref.WORKFLOW.forEach { (k, l) ->
                    FilterChip(selected = st == k, onClick = {
                        val n = JSONObject(t.toString()); n.put("statut", k); Store.upsert("tasks", n)
                    }, label = { Text(l) })
                }
            }
            if (t.s("description").isNotEmpty()) { SectionTitle("Notes"); CardBox { Text(t.s("description"), fontSize = 14.sp) } }
            val inv = t.arr("invites")
            if (inv.isNotEmpty()) {
                SectionTitle("Invité(e)s (${inv.size})")
                CardBox { inv.forEach { i -> Text("• " + i.s("nom") + i.s("ppr").let { if (it.isEmpty()) "" else "  (PPR $it)" }, fontSize = 13.5.sp, modifier = Modifier.padding(vertical = 2.dp)) } }
            }
            if (Ref.type(t) == "validation_ts") TsSection(t)
            TaskDocsSection(t)
            AttachmentsSection("tasks", id)
            HistorySection(id)
            Spacer(Modifier.height(40.dp))
        }
    }
    if (askDelete) ConfirmDialog("Supprimer la tâche ?", "Cette tâche sera supprimée sur cet appareil (et sur le carnet web au prochain échange de données).",
        onDismiss = { askDelete = false }) { Store.delete("tasks", id); Nav.pop() }
}

const val WEB_URL = "https://elarbiemail.github.io/carnet-inspection/"

@Composable
fun Modifier.horizontalScrollRow(): Modifier = this.then(Modifier.horizontalScroll(rememberScrollState()))

// ---------------- Formulaire de tâche ----------------
@Composable
fun TaskEditScreen(id: String?, etabId: String?, ensId: String?) {
    val ctx = LocalContext.current
    val orig = remember(id) { id?.let { Store.byId("tasks", it) } }
    val t = remember(id) {
        val o = if (orig != null) JSONObject(orig.toString()) else JSONObject().apply {
            put("statut", "a_planifier"); put("priorite", "moyenne"); put("taskType", "inspection")
            put("echeance", LocalDate.now().toString()); put("invites", JSONArray()); put("attachments", JSONArray())
            if (!etabId.isNullOrEmpty()) { put("etablissementId", etabId); put("etablissementNom", etabName(etabId)) }
            if (!ensId.isNullOrEmpty()) {
                put("enseignantId", ensId); put("enseignantNom", ensName(ensId))
                val e = Store.byId("enseignants", ensId); if (e != null && e.s("etablissementId").isNotEmpty()) { put("etablissementId", e.s("etablissementId")); put("etablissementNom", etabName(e.s("etablissementId"))) }
            }
        }
        mutableStateOf(o)
    }
    var tick by remember { mutableIntStateOf(0) }
    fun set(k: String, value: Any?) { t.value.put(k, value ?: JSONObject.NULL); tick++ }
    fun g(k: String): String { tick; return t.value.s(k) }
    var pickEtab by remember { mutableStateOf(false) }
    var pickEns by remember { mutableStateOf(false) }
    var pickInv by remember { mutableStateOf(false) }
    DraftGuard("task:" + (id ?: "new"), t, tick, { tick++ }) { Nav.stack.any { it is Scr.TaskEdit && it.id == id } }

    ScreenScaffold(if (orig == null) "Nouvelle tâche" else "Modifier la tâche", { Nav.pop() }, actions = {
        TextButton(onClick = {
            val o = t.value
            if (o.s("titre").isBlank()) o.put("titre", autoTaskTitle(o))
            Store.upsert("tasks", o)
            toast(ctx, "Tâche enregistrée.")
            Nav.replace(Scr.TaskDetail(o.s("id")))
        }) { Text("Enregistrer", color = OnBrand, fontWeight = FontWeight.Bold) }
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(14.dp)) {
            SelectField("Type de tâche", Ref.TASK_TYPES, g("taskType").ifEmpty { "inspection" }, { set("taskType", it) })
            TextFieldRow("Titre (vide = automatique : ${autoTaskTitle(t.value)})", g("titre"), { set("titre", it) })
            DateField("Date", g("echeance"), { set("echeance", it) })
            Reminders.vacanceFor(g("echeance"))?.let { vac -> Text("Attention : cette date tombe pendant « ${vac.s("nom")} ».", color = Warn, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeField("Début", g("heureDebut").ifEmpty { g("heure") }, { set("heureDebut", it); set("heure", it) }, Modifier.weight(1f))
                TimeField("Fin", g("heureFin"), { set("heureFin", it) }, Modifier.weight(1f))
            }
            TextFieldRow("Lieu", g("lieu"), { set("lieu", it) })
            PickField("Établissement", g("etablissementNom").ifEmpty { etabName(g("etablissementId")) }) { pickEtab = true }
            PickField("Enseignant(e)", g("enseignantNom").ifEmpty { ensName(g("enseignantId")) }) { pickEns = true }
            SelectField("Statut", Ref.WORKFLOW, Ref.statut(t.value).also { tick }, { set("statut", it) })
            SelectField("Priorité", Ref.PRIORITES, g("priorite").ifEmpty { "moyenne" }, { set("priorite", it) })
            if (g("taskType") == "inspection") SelectField("Appréciation globale", Ref.NOTE_SCALE.associateWith { it }, g("noteGlobale"), { set("noteGlobale", it) }, allowEmpty = true)
            if (g("taskType") == "reunion_distance") TextFieldRow("Lien de connexion", g("lienConnexion"), { set("lienConnexion", it) })
            TextFieldRow("Notes", g("description"), { set("description", it) }, multiline = true)
            if (g("taskType") == "formation") {
                SectionTitle("Session de formation")
                val fo = t.value.optJSONObject("formation") ?: JSONObject().also { t.value.put("formation", it) }
                listOf("sujet" to "الموضوع — Thème", "organisateur" to "الجهة المنظمة — Organisateur", "formateurs" to "المكون(ون) — Formateur(s)",
                    "cible" to "الفئة المستفيدة — Public", "nombre" to "العدد — Nombre").forEach { (k, l) -> TextFieldRow(l, fo.s(k).also { tick }, { fo.put(k, it); tick++ }) }
                listOf("objectifs" to "الأهداف — Objectifs (notes)", "axes" to "المحاور — Axes / programme", "deroulement" to "Déroulement / activités réalisées")
                    .forEach { (k, l) -> TextFieldRow(l, fo.s(k).also { tick }, { fo.put(k, it); tick++ }, multiline = true) }
            }
            SectionTitle("Invité(e)s (${t.value.arr("invites").size.also { tick }})")
            CardBox {
                t.value.arr("invites").forEachIndexed { i, iv ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(iv.s("nom"), fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOf(iv.s("ppr"), iv.s("etablissementNom")).filter { it.isNotEmpty() }.joinToString(" · "), color = InkFaint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = {
                            val a = t.value.optJSONArray("invites") ?: JSONArray(); val n = JSONArray(); for (j in 0 until a.length()) if (j != i) n.put(a.opt(j)); t.value.put("invites", n); tick++
                        }) { Icon(Icons.Default.Delete, "Retirer", tint = Danger) }
                    }
                }
                OutlinedButton(onClick = { pickInv = true }) { Icon(Icons.Default.Person, null); Spacer(Modifier.width(6.dp)); Text("Choisir les invité(e)s") }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
    if (pickEtab) PickerDialog("Établissement", Store.etabs(), { it.s("nom") }, { zoneName(it.s("zoneId")) }, { pickEtab = false }) { e ->
        set("etablissementId", e?.s("id")); set("etablissementNom", e?.s("nom") ?: "")
        val ens = Store.byId("enseignants", g("enseignantId"))
        if (e != null && ens != null && ens.s("etablissementId") != e.s("id")) { set("enseignantId", null); set("enseignantNom", "") }
        pickEtab = false
    }
    if (pickInv) {
        val cur = t.value.arr("invites").map { it.s("enseignantId") }.filter { it.isNotEmpty() }.toSet()
        MultiTeacherPicker(initial = cur, etabId = g("etablissementId"), onDismiss = { pickInv = false }) { picked ->
            val keep = t.value.arr("invites").filter { it.s("enseignantId").isEmpty() }
            val n = JSONArray(); keep.forEach { n.put(it) }
            picked.forEach { e -> n.put(JSONObject().put("enseignantId", e.s("id")).put("nom", e.s("nom")).put("ppr", e.s("ppr")).put("etablissementNom", etabName(e.s("etablissementId")))) }
            t.value.put("invites", n); tick++; pickInv = false
        }
    }
    if (pickEns) {
        val et = g("etablissementId")
        val items = Store.enseignants().filter { et.isEmpty() || it.s("etablissementId") == et }
        PickerDialog("Enseignant(e)", items, { it.s("nom") }, { listOf(it.s("ppr"), etabName(it.s("etablissementId"))).filter { s -> s.isNotEmpty() }.joinToString(" · ") }, { pickEns = false }) { e ->
            set("enseignantId", e?.s("id")); set("enseignantNom", e?.s("nom") ?: "")
            if (e != null && e.s("etablissementId").isNotEmpty()) { set("etablissementId", e.s("etablissementId")); set("etablissementNom", etabName(e.s("etablissementId"))) }
            pickEns = false
        }
    }
}
