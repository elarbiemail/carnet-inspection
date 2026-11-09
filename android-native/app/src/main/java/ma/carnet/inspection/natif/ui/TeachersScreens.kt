@file:OptIn(ExperimentalMaterial3Api::class)

package ma.carnet.inspection.natif.ui

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
import ma.carnet.inspection.natif.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

fun genreColor(g: String): Color = if (g == "F") FemColor else if (g == "H") HomColor else Brand

@Composable
fun TeacherRow(e: JSONObject, sub: String = "", sel: Sel? = null) {
    val li = remember(e.s("updatedAt"), e.s("id")) { ensInspections(e).firstOrNull() }
    val body: @Composable ColumnScope.() -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(e.s("nom"), genreColor(e.s("genre")), tel = e.s("telephone"), photoId = e.s("id"))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(e.s("nom"), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 2.dp)) {
                    Tag(Ref.ENS_CYCLES[ensCycle(e)] ?: "", Earth)
                    if (e.s("statut") == "stagiaire") Tag("Stagiaire", Warn)
                    if (li != null && li.note.isNotEmpty()) Tag("Insp. " + li.note + (if (li.date.isNotEmpty()) " (" + li.date.take(4) + ")" else ""), Brand)
                }
                val s = sub.ifEmpty { listOf(e.s("matiere"), etabName(e.s("etablissementId"))).filter { it.isNotEmpty() }.joinToString(" · ") }
                if (s.isNotEmpty()) Text(s, color = InkFaint, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val ctx = LocalContext.current
            if (e.s("telephone").isNotEmpty()) {
                IconButton(onClick = { dial(ctx, e.s("telephone")) }) { Icon(Icons.Default.Call, "Appeler", tint = Brand) }
                IconButton(onClick = { whatsapp(ctx, e.s("telephone"), greeting(e.s("nom"))) }) { Icon(Icons.Default.Send, "WhatsApp", tint = Brand) }
            }
        }
    }
    if (sel != null) SelCard(e.s("id"), sel, { Nav.push(Scr.EnsDetail(e.s("id"))) }, body)
    else CardBox(onClick = { Nav.push(Scr.EnsDetail(e.s("id"))) }, content = body)
}

/** Créneau horaire : « qui est en classe à ce moment ? » */
fun slotMatches(sl: JSONObject, jour: String, heure: String): Boolean =
    sl.s("jour") == jour && sl.s("debut") <= heure && heure <= sl.s("fin")

@Composable
fun TeachersScreen() {
    val v by Store.version
    var q by remember { mutableStateOf("") }
    var cyc by remember { mutableStateOf("") }
    var gen by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("liste") }
    val all = remember(v) { Store.enseignants() }
    val sel = rememberSel()
    var askDel by remember { mutableStateOf(false) }
    var editSel by remember { mutableStateOf(false) }
    ScreenScaffold(selTitle(sel, "Enseignant(e)s"), if (sel.on) ({ sel.clear() }) else null, actions = {
        SelActions(sel, all.map { it.s("id") }, { editSel = true }, { askDel = true })
    }, fab = {
        FloatingActionButton(onClick = { Nav.push(Scr.EnsEdit(null)) }, containerColor = Brand, contentColor = OnBrand) { Icon(Icons.Default.Add, "Nouvel(le) enseignant(e)") }
    }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 14.dp)) {
            item {
                Spacer(Modifier.height(8.dp)); ZoneFilterBar()
                ChipRow(mapOf("liste" to "Liste", "horaire" to "Par horaire"), mode, { mode = it.ifEmpty { "liste" } }, null)
                FicheImportButton()
                FeuillesLotButton()
            }
            if (mode == "horaire") {
                item { HoraireSearch(all) }
            } else {
                val nq = norm(q)
                val list = all.filter {
                    (cyc.isEmpty() || ensCycle(it) == cyc) && (gen.isEmpty() || it.s("genre") == gen) &&
                    (nq.isEmpty() || norm(listOf(it.s("nom"), it.s("nomAr"), it.s("ppr"), it.s("matiere"), etabName(it.s("etablissementId"))).joinToString(" ")).contains(nq))
                }.sortedBy { norm(it.s("nom")) }
                item {
                    SearchField(q, { q = it }, "Nom, PPR, matière, établissement…")
                    Spacer(Modifier.height(6.dp))
                    ChipRow(Ref.ENS_CYCLES, cyc, { cyc = it }, "Tous cycles")
                    ChipRow(mapOf("F" to "Femmes", "H" to "Hommes"), gen, { gen = it }, "Tous")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${list.size} enseignant(e)(s)", color = InkFaint, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { Nav.push(Scr.Archives("enseignants")) }) { Text("Fiches supprimées") }
                    }
                }
                if (list.isEmpty()) item { EmptyState("Aucun(e) enseignant(e).") }
                items(list, key = { it.s("id") }) { TeacherRow(it, sel = sel) }
                if (sel.on) item { TextButton(onClick = { sel.selectAll(list.map { it.s("id") }) }) { Text("Sélectionner les ${list.size} de la liste (filtre actuel)") } }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
    if (askDel) BatchDeleteDialog(sel.ids.size, "enseignant(e)s", onDismiss = { askDel = false }) { Store.deleteMany("enseignants", sel.ids.toList()); sel.clear() }
    if (editSel) BatchEditDialog(sel.ids.size, ENS_BATCH_FIELDS, { editSel = false }) { f, vals -> applyBatch("enseignants", sel.ids.toList(), f, vals); sel.clear() }
}

@Composable
fun HoraireSearch(all: List<JSONObject>) {
    val nowDay = LocalDate.now().dayOfWeek.value
    var jour by remember { mutableStateOf(Ref.JOURS.getOrElse(nowDay - 1) { "Lundi" }) }
    var heure by remember { mutableStateOf(LocalTime.now().let { String.format("%02d:%02d", it.hour, it.minute) }) }
    Column {
        SectionTitle("Qui est en classe à quel moment ?")
        ChipRow(Ref.JOURS.associateWith { it.take(3) }, jour, { if (it.isNotEmpty()) jour = it }, null)
        TimeField("Heure", heure, { heure = it }, Modifier.fillMaxWidth())
        val m = all.filter { e -> e.arr("horaire").any { slotMatches(it, jour, heure) } }
            .sortedBy { norm(etabName(it.s("etablissementId")) + it.s("nom")) }
        val ctx = LocalContext.current
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${m.size} enseignant(e)(s) en séance", color = InkFaint, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(top = 4.dp))
            if (m.isNotEmpty()) TextButton(onClick = { showHtml("En séance le $jour à $heure", horaireSearchHtml(ctx, jour, heure, m)) }) { Text("Exporter la liste") }
        }
        if (m.isEmpty()) EmptyState("Personne en séance à ce créneau.")
        m.forEach { e ->
            val sl = e.arr("horaire").first { slotMatches(it, jour, heure) }
            TeacherRow(e, listOf(etabName(e.s("etablissementId")), sl.s("classe"), sl.s("salle").let { if (it.isEmpty()) "" else "salle $it" }, sl.s("debut") + "–" + sl.s("fin")).filter { it.isNotEmpty() }.joinToString(" · "))
        }
    }
}

@Composable
fun TeacherDetailScreen(id: String) {
    val v by Store.version
    val e = remember(v, id) { Store.byId("enseignants", id) }
    var askDelete by remember { mutableStateOf(false) }
    var addNote by remember { mutableStateOf(false) }
    if (e == null) { ScreenScaffold("Enseignant(e)", { Nav.pop() }) { EmptyState("Fiche introuvable.") }; return }
    ScreenScaffold(e.s("nom"), { Nav.pop() }, actions = {
        IconButton(onClick = { Nav.push(Scr.EnsEdit(id)) }) { Icon(Icons.Default.Edit, "Modifier") }
        IconButton(onClick = { askDelete = true }) { Icon(Icons.Default.Delete, "Supprimer") }
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(14.dp)) {
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(e.s("nom"), genreColor(e.s("genre")), 56, tel = e.s("telephone"), photoId = id)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(e.s("nom"), fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (e.s("nomAr").isNotEmpty()) Text(e.s("nomAr"), color = InkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row { Tag(Ref.ENS_CYCLES[ensCycle(e)] ?: ""); Tag(Ref.STATUTS[e.s("statut")] ?: "", if (e.s("statut") == "stagiaire") Warn else Brand); Tag(Ref.GENRES[e.s("genre")] ?: "", genreColor(e.s("genre"))) }
                    }
                }
                ContactButtons(e.s("telephone"), e.s("nom"), e.s("email"))
                PhotoButton(id, e.s("telephone"))
            }
            CardBox {
                InfoRow("PPR", e.s("ppr")); InfoRow("Cadre", e.s("cadre")); InfoRow("Matière", e.s("matiere")); InfoRow("Niveaux", e.s("cycle"))
                InfoRow("Téléphone", e.s("telephone")); InfoRow("Email", e.s("email"))
                val et = Store.byId("etablissements", e.s("etablissementId"))
                if (et != null) TextButton(onClick = { Nav.push(Scr.EtabDetail(et.s("id"))) }) { Icon(Icons.Default.LocationOn, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(et.s("nom")) }
            }
            // Notes d'inspection
            val ins = ensInspections(e)
            SectionTitle("Notes d'inspection (${ins.size})")
            CardBox {
                if (ins.isEmpty()) Text("Aucune note enregistrée.", color = InkFaint, fontSize = 13.sp)
                ins.forEach { i ->
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(i.note.ifEmpty { "—" }, fontWeight = FontWeight.Bold, color = Brand, modifier = Modifier.width(70.dp))
                        Column { Text(inspDateTxt(i.date).ifEmpty { "Date inconnue" }, fontSize = 13.5.sp); Text(listOf(if (i.par.isNotEmpty()) "Par ${i.par}" else "", i.source).filter { it.isNotEmpty() }.joinToString(" · "), color = InkFaint, fontSize = 11.5.sp) }
                    }
                }
                TextButton(onClick = { addNote = true }) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Text("Ajouter une note") }
            }
            // Emploi du temps
            val hor = e.arr("horaire").sortedWith(compareBy({ Ref.JOURS.indexOf(it.s("jour")) }, { it.s("debut") }))
            SectionTitle("Emploi du temps (${hor.size} séance(s))")
            CardBox {
                if (hor.isEmpty()) Text("Aucun horaire enregistré.", color = InkFaint, fontSize = 13.sp)
                Ref.JOURS.forEach { j ->
                    val dj = hor.filter { it.s("jour") == j }
                    if (dj.isNotEmpty()) {
                        Text(j, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, modifier = Modifier.padding(top = 6.dp))
                        dj.forEach { sl -> Text("  " + sl.s("debut") + "–" + sl.s("fin") + "  " + listOf(sl.s("classe"), sl.s("salle").let { if (it.isEmpty()) "" else "salle $it" }).filter { it.isNotEmpty() }.joinToString(" · "), fontSize = 13.sp) }
                    }
                }
            }
            // Tâches liées
            val tasks = Store.list("tasks").filter { it.s("enseignantId") == id || it.arr("invites").any { i -> i.s("enseignantId") == id } }.sortedByDescending { it.s("echeance") }
            SectionTitle("Historique des visites et tâches (${tasks.size})")
            tasks.forEach { TaskCard(it) }
            OutlinedButton(onClick = { Nav.push(Scr.TaskEdit(null, ensId = id)) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Icon(Icons.Default.Add, null); Text("Nouvelle tâche pour cet(te) enseignant(e)") }
            if (e.s("notes").isNotEmpty()) { SectionTitle("Notes"); CardBox { Text(e.s("notes"), fontSize = 13.5.sp) } }
            val ts = Store.list("tasks").filter { Ref.type(it) == "validation_ts" }.flatMap { t -> t.arr("feuilles").filter { it.s("enseignantId") == id }.map { t to it } }.sortedByDescending { it.first.s("echeance") }
            if (ts.isNotEmpty()) {
                SectionTitle("Tableaux de service (${ts.size})")
                CardBox { ts.forEach { (t, f) -> Text(fmtDate(t.s("echeance")) + " — " + (TS_STATUTS[f.s("statut")] ?: "En attente") + if (f.s("observation").isNotEmpty()) " : " + f.s("observation") else "", fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) } }
            }
            AttachmentsSection("enseignants", id)
            HistorySection(id)
            Spacer(Modifier.height(40.dp))
        }
    }
    if (askDelete) ConfirmDialog("Supprimer la fiche ?", "La fiche de ${e.s("nom")} sera supprimée.", onDismiss = { askDelete = false }) { Store.delete("enseignants", id); Nav.pop() }
    if (addNote) AddNoteDialog(onDismiss = { addNote = false }) { note, date, par ->
        val n = JSONObject(e.toString())
        val arr = n.optJSONArray("inspections") ?: JSONArray()
        arr.put(JSONObject().put("id", java.util.UUID.randomUUID().toString()).put("note", note.replace(Regex("\\s*/\\s*20$"), "")).put("date", date).put("par", par))
        n.put("inspections", arr); Store.upsert("enseignants", n)
    }
}

@Composable
fun AddNoteDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var par by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Note d'inspection") }, text = {
        Column {
            TextFieldRow("Note /20", note, { note = it })
            TextFieldRow("Date (JJ/MM/AAAA ou année)", date, { date = it })
            TextFieldRow("Par", par, { par = it })
        }
    }, confirmButton = {
        TextButton(onClick = {
            val n = noteNum(note)
            if (n == null || n > 20) { toast(ctx, "Saisissez une note sur 20."); return@TextButton }
            val m = Regex("^(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})$").find(date.trim())
            val iso = if (m != null) "%s-%02d-%02d".format(m.groupValues[3], m.groupValues[2].toInt(), m.groupValues[1].toInt()) else date.trim()
            onSave(note.trim(), iso, par.trim()); onDismiss()
        }) { Text("Ajouter") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } })
}

@Composable
fun TeacherEditScreen(id: String?, etabId: String?) {
    val ctx = LocalContext.current
    val orig = remember(id) { id?.let { Store.byId("enseignants", it) } }
    val e = remember(id) {
        mutableStateOf(if (orig != null) JSONObject(orig.toString()) else JSONObject().apply {
            put("statut", "titulaire"); put("horaire", JSONArray()); put("attachments", JSONArray())
            if (!etabId.isNullOrEmpty()) put("etablissementId", etabId)
        })
    }
    var tick by remember { mutableIntStateOf(0) }
    fun set(k: String, value: Any?) { e.value.put(k, value ?: JSONObject.NULL); tick++ }
    fun g(k: String): String { tick; return e.value.s(k) }
    var pickEtab by remember { mutableStateOf(false) }
    DraftGuard("ens:" + (id ?: "new"), e, tick, { tick++ }) { Nav.stack.any { it is Scr.EnsEdit && it.id == id } }
    ScreenScaffold(if (orig == null) "Nouvel(le) enseignant(e)" else "Modifier la fiche", { Nav.pop() }, actions = {
        TextButton(onClick = {
            if (e.value.s("nom").isBlank()) { toast(ctx, "Indiquez le nom."); return@TextButton }
            Store.upsert("enseignants", e.value); toast(ctx, "Fiche enregistrée.")
            Nav.replace(Scr.EnsDetail(e.value.s("id")))
        }) { Text("Enregistrer", color = OnBrand, fontWeight = FontWeight.Bold) }
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(14.dp)) {
            TextFieldRow("Nom et prénom", g("nom"), { set("nom", it) })
            TextFieldRow("Nom en arabe", g("nomAr"), { set("nomAr", it) })
            TextFieldRow("PPR", g("ppr"), { set("ppr", it) })
            PickField("Établissement", etabName(g("etablissementId"))) { pickEtab = true }
            TextFieldRow("Matière", g("matiere"), { set("matiere", it) })
            TextFieldRow("Niveaux (ex. TC, 1BAC)", g("cycle"), { set("cycle", it) })
            TextFieldRow("Cadre / échelle", g("cadre"), { set("cadre", it) })
            SelectField("Genre", Ref.GENRES, g("genre"), { set("genre", it) }, allowEmpty = true)
            SelectField("Statut", Ref.STATUTS, g("statut").ifEmpty { "titulaire" }, { set("statut", it) })
            TextFieldRow("Téléphone", g("telephone"), { set("telephone", it) })
            TextFieldRow("Email", g("email"), { set("email", it) })
            SelectField("Cycle d'enseignement", linkedMapOf("" to "Automatique") + Ref.ENS_CYCLES, g("cycleEns"), { set("cycleEns", it) })
            SelectField("Session de titularisation visée", linkedMapOf("" to "—", "s1" to "Session 1 (avril–mai)", "s2" to "Session 2 (octobre–novembre)"), g("sessionTitularisation"), { set("sessionTitularisation", it) })
            TextFieldRow("Notes", g("notes"), { set("notes", it) }, multiline = true)
            HoraireEditor(e.value) { tick++ }
            Spacer(Modifier.height(40.dp))
        }
    }
    if (pickEtab) PickerDialog("Établissement", Store.etabs(), { it.s("nom") }, { zoneName(it.s("zoneId")) }, { pickEtab = false }) { x ->
        set("etablissementId", x?.s("id")); pickEtab = false
    }
}
