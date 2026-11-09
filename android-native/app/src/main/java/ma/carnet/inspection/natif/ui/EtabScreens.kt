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
import android.net.Uri
import ma.carnet.inspection.natif.data.*
import org.json.JSONObject

val PriveColor = Color(0xFF8A5A00)

@Composable
fun EtabRow(e: JSONObject, nEns: Int, sel: Sel? = null) {
    val ctx = LocalContext.current
    val body: @Composable ColumnScope.() -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(e.s("nom"), if (e.s("type") == "prive") PriveColor else Brand, tel = e.s("directeurTel").ifEmpty { e.s("telephone") }, contactName = e.s("directeur").ifEmpty { e.s("nom") }, photoId = e.s("id"))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                MarqueeText(e.s("nom"), fontWeight = FontWeight.SemiBold)
                Row(Modifier.padding(top = 2.dp)) {
                    Tag(if (e.s("type") == "prive") "Privé" else "Public", if (e.s("type") == "prive") PriveColor else Brand)
                    Tag(Ref.ETAB_CYCLES[e.s("cycle")] ?: "", Earth)
                }
                MarqueeText(listOf("$nEns enseignant(s)", e.s("directeur").let { if (it.isEmpty()) "" else "Dir. $it" }).filter { it.isNotEmpty() }.joinToString(" · "),
                    color = InkFaint, fontSize = 12.5.sp)
            }
            val tel = e.s("directeurTel").ifEmpty { e.s("telephone") }
            if (tel.isNotEmpty()) IconButton(onClick = { dial(ctx, tel) }) { Icon(Icons.Default.Call, "Appeler", tint = Brand) }
            if (e.s("directeurTel").isNotEmpty()) IconButton(onClick = { whatsapp(ctx, e.s("directeurTel"), greeting(e.s("directeur"))) }) { Icon(Icons.Default.Send, "WhatsApp", tint = Brand) }
        }
    }
    if (sel != null) SelCard(e.s("id"), sel, { Nav.push(Scr.EtabDetail(e.s("id"))) }, body)
    else CardBox(onClick = { Nav.push(Scr.EtabDetail(e.s("id"))) }, content = body)
}

@Composable
fun EtabsScreen() {
    val v by Store.version
    var q by remember { mutableStateOf("") }
    var ty by remember { mutableStateOf("") }
    val etabs = remember(v) { Store.etabs() }
    val ens = remember(v) { Store.enseignants() }
    val zones = remember(v) { Store.list("zones").sortedBy { norm(it.s("nom")) } }
    val nq = norm(q)
    val list = etabs.filter { (ty.isEmpty() || (if (ty == "prive") it.s("type") == "prive" else it.s("type") != "prive")) &&
        (nq.isEmpty() || norm(listOf(it.s("nom"), it.s("nomAr"), it.s("nomFr"), it.s("directeur")).joinToString(" ")).contains(nq)) }
    val sel = rememberSel()
    var askDel by remember { mutableStateOf(false) }
    var editSel by remember { mutableStateOf(false) }
    ScreenScaffold(selTitle(sel, "Établissements"), if (sel.on) ({ sel.clear() }) else null, actions = {
        SelActions(sel, list.map { it.s("id") }, { editSel = true }, { askDel = true })
    }, fab = {
        FloatingActionButton(onClick = { Nav.push(Scr.EtabEdit(null)) }, containerColor = Brand, contentColor = OnBrand) { Icon(Icons.Default.Add, "Nouvel établissement") }
    }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 14.dp)) {
            item {
                Spacer(Modifier.height(8.dp)); ZoneFilterBar()
                SearchField(q, { q = it }, "Nom, directeur…")
                Spacer(Modifier.height(6.dp))
                ChipRow(Ref.ETAB_TYPES, ty, { ty = it }, "Tous")
            }
            val groups = zones.map { z -> Triple(z.s("id"), z.s("nom"), list.filter { it.s("zoneId") == z.s("id") }) } +
                listOf(Triple("__none", "Sans zone", list.filter { e -> zones.none { it.s("id") == e.s("zoneId") } }))
            groups.forEach { (zid, zn, l) ->
                if (l.isNotEmpty()) {
                    item(key = "zone_$zid") { SectionTitle("$zn (${l.size})") }
                    items(l.sortedBy { norm(it.s("nom")) }, key = { it.s("id") }) { e -> EtabRow(e, ens.count { it.s("etablissementId") == e.s("id") }, sel) }
                }
            }
            if (list.isEmpty()) item { EmptyState("Aucun établissement.") }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
    if (askDel) BatchDeleteDialog(sel.ids.size, "établissements", " Les enseignant(e)s restent.", { askDel = false }) { Store.deleteMany("etablissements", sel.ids.toList()); sel.clear() }
    if (editSel) BatchEditDialog(sel.ids.size, etabBatchFields(), { editSel = false }) { f, vals -> applyBatch("etablissements", sel.ids.toList(), f, vals); sel.clear() }
}

@Composable
fun EtabDetailScreen(id: String) {
    val v by Store.version
    val e = remember(v, id) { Store.byId("etablissements", id) }
    val ctx = LocalContext.current
    var askDelete by remember { mutableStateOf(false) }
    if (e == null) { ScreenScaffold("Établissement", { Nav.pop() }) { EmptyState("Fiche introuvable.") }; return }
    ScreenScaffold(e.s("nom"), { Nav.pop() }, actions = {
        IconButton(onClick = { Nav.push(Scr.EtabEdit(id)) }) { Icon(Icons.Default.Edit, "Modifier") }
        IconButton(onClick = { askDelete = true }) { Icon(Icons.Default.Delete, "Supprimer") }
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(14.dp)) {
            CardBox {
                Text(e.s("nom"), fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (e.s("nomAr").isNotEmpty() && e.s("nomAr") != e.s("nom")) Text(e.s("nomAr"), color = InkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 4.dp)) {
                    Tag(if (e.s("type") == "prive") "Privé" else "Public", if (e.s("type") == "prive") PriveColor else Brand)
                    Tag(Ref.ETAB_CYCLES[e.s("cycle")] ?: "", Earth)
                    if (e.optBoolean("pionnier")) Tag("Collège pionnier", Color(0xFF7A5BB5))
                }
                InfoRow("Zone", zoneName(e.s("zoneId"))); InfoRow("Code MASSAR", e.s("codeMassar")); InfoRow("Adresse", e.s("adresse")); InfoRow("Téléphone", e.s("telephone"))
                if (e.s("telephone").isNotEmpty()) TextButton(onClick = { dial(ctx, e.s("telephone")) }) { Icon(Icons.Default.Call, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Appeler l'établissement", maxLines = 1, softWrap = false) }
                if (e.s("latitude").isNotEmpty() && e.s("longitude").isNotEmpty())
                    TextButton(onClick = { openUri(ctx, "geo:${e.s("latitude")},${e.s("longitude")}?q=${e.s("latitude")},${e.s("longitude")}(" + Uri.encode(e.s("nom")) + ")") }) {
                        Icon(Icons.Default.LocationOn, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Voir sur la carte")
                    }
            }
            if (e.s("directeur").isNotEmpty() || e.s("directeurTel").isNotEmpty()) {
                SectionTitle("Directeur(trice)")
                CardBox {
                    Text(e.s("directeur").ifEmpty { "—" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (e.s("directeurTel").isNotEmpty()) Text(e.s("directeurTel"), color = InkFaint, fontSize = 13.sp)
                    ContactButtons(e.s("directeurTel"), e.s("directeur"))
                    PhotoButton(id, e.s("directeurTel"), e.s("directeur").ifEmpty { e.s("nom") })
                }
            }
            val ens = Store.list("enseignants").filter { it.s("etablissementId") == id }.sortedBy { norm(it.s("nom")) }
            SectionTitle("Enseignant(e)s affecté(e)s (${ens.size}) · ${ens.count { it.s("genre") == "F" }} F · ${ens.count { it.s("genre") == "H" }} H")
            ens.forEach { TeacherRow(it) }
            OutlinedButton(onClick = { Nav.push(Scr.EnsEdit(null, id)) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Icon(Icons.Default.Add, null); Text("Ajouter un(e) enseignant(e)") }
            val tasks = Store.list("tasks").filter { it.s("etablissementId") == id }.sortedByDescending { it.s("echeance") }
            SectionTitle("Tâches (${tasks.size})")
            tasks.take(30).forEach { TaskCard(it) }
            OutlinedButton(onClick = { Nav.push(Scr.TaskEdit(null, etabId = id)) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Icon(Icons.Default.Add, null); Text("Nouvelle tâche dans cet établissement") }
            if (e.s("notes").isNotEmpty()) { SectionTitle("Notes"); CardBox { Text(e.s("notes"), fontSize = 13.5.sp) } }
            SectionTitle("Documents de l'établissement")
            val ctx2 = LocalContext.current
            Row(Modifier.horizontalScrollRow(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showHtml("Emploi du temps - " + e.s("nom"), etabTimetableHtml(ctx2, id), true) }) { Text("Emploi du temps hebdomadaire", maxLines = 1, softWrap = false) }
                OutlinedButton(onClick = { showHtml("Notes d'inspection - " + e.s("nom"), notesHtml(ctx2, Store.list("enseignants").filter { it.s("etablissementId") == id }.sortedBy { norm(it.s("nom")) }, id)) }) { Text("Fiche synthèse des notes", maxLines = 1, softWrap = false) }
                OutlinedButton(onClick = { Nav.push(Scr.TaskEdit(null, etabId = id)) }) { Text("Validation des tableaux de service", maxLines = 1, softWrap = false) }
            }
            val tsT = Store.list("tasks").filter { Ref.type(it) == "validation_ts" && it.s("etablissementId") == id }.maxByOrNull { it.s("echeance") }
            if (tsT != null) CardBox(onClick = { Nav.push(Scr.TaskDetail(tsT.s("id"))) }) {
                val fs = tsT.arr("feuilles")
                Text("Dernière validation des tableaux de service · " + fmtDate(tsT.s("echeance")), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${fs.count { it.s("statut") == "validee" }} validée(s) · ${fs.count { it.s("statut") == "non_validee" }} non validée(s) · ${fs.count { it.s("statut").ifEmpty { "en_attente" } == "en_attente" }} en attente", color = InkFaint, fontSize = 12.5.sp)
            }
            AttachmentsSection("etablissements", id)
            HistorySection(id)
            Spacer(Modifier.height(40.dp))
        }
    }
    if (askDelete) ConfirmDialog("Supprimer l'établissement ?", "La fiche « ${e.s("nom")} » sera supprimée (les enseignant(e)s restent).", onDismiss = { askDelete = false }) { Store.delete("etablissements", id); Nav.pop() }
}

@Composable
fun EtabEditScreen(id: String?) {
    val ctx = LocalContext.current
    val orig = remember(id) { id?.let { Store.byId("etablissements", it) } }
    val e = remember(id) { mutableStateOf(if (orig != null) JSONObject(orig.toString()) else JSONObject().put("type", "public").put("cycle", "college")) }
    var tick by remember { mutableIntStateOf(0) }
    fun set(k: String, value: Any?) { e.value.put(k, value ?: JSONObject.NULL); tick++ }
    fun g(k: String): String { tick; return e.value.s(k) }
    val zones = Store.list("zones").sortedBy { norm(it.s("nom")) }
    DraftGuard("etab:" + (id ?: "new"), e, tick, { tick++ }) { Nav.stack.any { it is Scr.EtabEdit && it.id == id } }
    ScreenScaffold(if (orig == null) "Nouvel établissement" else "Modifier", { Nav.pop() }, actions = {
        TextButton(onClick = {
            if (e.value.s("nom").isBlank()) { toast(ctx, "Indiquez le nom."); return@TextButton }
            Store.upsert("etablissements", e.value); toast(ctx, "Établissement enregistré.")
            Nav.replace(Scr.EtabDetail(e.value.s("id")))
        }) { Text("Enregistrer", color = OnBrand, fontWeight = FontWeight.Bold) }
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(14.dp)) {
            TextFieldRow("Nom", g("nom"), { set("nom", it) })
            TextFieldRow("Nom en arabe", g("nomAr"), { set("nomAr", it) })
            TextFieldRow("Nom en français", g("nomFr"), { set("nomFr", it) })
            TextFieldRow("Code MASSAR / GRESA", g("codeMassar"), { set("codeMassar", it) })
            SelectField("Zone", zones.associate { it.s("id") to it.s("nom") }, g("zoneId"), { set("zoneId", it.ifEmpty { null }) }, allowEmpty = true)
            SelectField("Type", Ref.ETAB_TYPES, g("type").ifEmpty { "public" }, { set("type", it) })
            SelectField("Cycle", Ref.ETAB_CYCLES, g("cycle"), { set("cycle", it) })
            TextFieldRow("Directeur(trice)", g("directeur"), { set("directeur", it) })
            TextFieldRow("Téléphone du/de la directeur(trice)", g("directeurTel"), { set("directeurTel", it) })
            TextFieldRow("Téléphone de l'établissement", g("telephone"), { set("telephone", it) })
            TextFieldRow("Adresse", g("adresse"), { set("adresse", it) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextFieldRow("Latitude", g("latitude"), { set("latitude", it) }, modifier = Modifier.weight(1f))
                TextFieldRow("Longitude", g("longitude"), { set("longitude", it) }, modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(g("pionnier") == "true", { e.value.put("pionnier", it); tick++ }); Text("Collège pionnier", Modifier.padding(start = 8.dp)) }
            TextFieldRow("Notes", g("notes"), { set("notes", it) }, multiline = true)
            if (orig != null) EtabMergeCard(id!!)
            Spacer(Modifier.height(40.dp))
        }
    }
}
