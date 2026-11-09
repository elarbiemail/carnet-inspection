@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package ma.carnet.inspection.natif.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import ma.carnet.inspection.natif.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Sélection multiple dans les listes : appui long sur une fiche pour démarrer, puis cases à cocher.
 * Menu ⋮ de la barre du haut : tout sélectionner, modifier en lot, supprimer.
 */
class Sel {
    val ids = mutableStateListOf<String>()
    var on by mutableStateOf(false)
        private set
    fun has(id: String) = id in ids
    fun start(id: String) { on = true; if (id !in ids) ids.add(id) }
    fun toggle(id: String) { if (id in ids) ids.remove(id) else ids.add(id); if (ids.isEmpty()) on = false }
    fun clear() { ids.clear(); on = false }
    fun selectAll(all: List<String>) { ids.clear(); ids.addAll(all); on = all.isNotEmpty() }
}

@Composable
fun rememberSel(): Sel {
    val s = remember { Sel() }
    BackHandler(enabled = s.on) { s.clear() }
    return s
}

/** Titre de la barre du haut selon le mode. */
fun selTitle(sel: Sel, normal: String) = if (sel.on) "${sel.ids.size} sélectionné(s)" else normal

/** Fiche de liste sélectionnable (clic = ouvrir, appui long = sélectionner). */
@Composable
fun SelCard(id: String, sel: Sel, onOpen: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val picked = sel.has(id)
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).combinedClickable(
            onClick = { if (sel.on) sel.toggle(id) else onOpen() },
            onLongClick = { if (sel.on) sel.toggle(id) else sel.start(id) }),
        colors = CardDefaults.cardColors(containerColor = if (picked) Brand.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface),
        border = if (picked) BorderStroke(2.dp, Brand) else null,
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (sel.on) { Checkbox(checked = picked, onCheckedChange = { sel.toggle(id) }); Spacer(Modifier.width(6.dp)) }
            Column(Modifier.weight(1f), content = content)
        }
    }
}

/** Boutons de la barre du haut en mode sélection : Modifier (si disponible), menu ⋮ (tout sélectionner, supprimer). */
@Composable
fun RowScope.SelActions(sel: Sel, allIds: List<String>, onEdit: (() -> Unit)?, onDelete: () -> Unit) {
    if (!sel.on) return
    var menu by remember { mutableStateOf(false) }
    if (onEdit != null) IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Modifier la sélection") }
    IconButton(onClick = { onDelete() }) { Icon(Icons.Default.Delete, "Supprimer la sélection") }
    Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Plus") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Tout sélectionner (${allIds.size})") }, onClick = { sel.selectAll(allIds); menu = false })
            DropdownMenuItem(text = { Text("Annuler la sélection") }, onClick = { sel.clear(); menu = false })
        }
    }
}

/** Confirmation de suppression en lot. */
@Composable
fun BatchDeleteDialog(count: Int, what: String, extra: String = "", onDismiss: () -> Unit, onConfirm: () -> Unit) {
    ConfirmDialog("Supprimer $count $what ?", "Les $count éléments sélectionnés seront supprimés sur cet appareil (et du carnet web au prochain échange de données)." + extra,
        "Supprimer", onDismiss, onConfirm)
}

// ---------------- modification en lot ----------------
/** kind : select | text | date | etab | note (ajout d'une note d'inspection). */
class BField(val key: String, val label: String, val kind: String = "select", val options: Map<String, String> = emptyMap(), val canClear: Boolean = false)

@Composable
fun BatchEditDialog(count: Int, fields: List<BField>, onDismiss: () -> Unit, onApply: (BField, Map<String, String>) -> Unit) {
    var fk by remember { mutableStateOf(fields.first().key) }
    val f = fields.first { it.key == fk }
    var v by remember(fk) { mutableStateOf("") }
    var d by remember(fk) { mutableStateOf("") }
    var p by remember(fk) { mutableStateOf("") }
    var etabLabel by remember(fk) { mutableStateOf("") }
    var pickEtab by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val ok = when (f.kind) {
        "note" -> v.isNotBlank()
        "etab" -> etabLabel.isNotEmpty()
        else -> f.canClear || v.isNotEmpty()
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Modifier $count élément(s)") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SelectField("Champ à modifier", fields.associate { it.key to it.label }, fk, { if (it.isNotEmpty()) fk = it })
            when (f.kind) {
                "select" -> SelectField("Nouvelle valeur", f.options, v, { v = it }, allowEmpty = f.canClear)
                "text" -> TextFieldRow("Nouvelle valeur", v, { v = it })
                "date" -> DateField("Nouvelle date", v, { v = it })
                "etab" -> PickField("Établissement", etabLabel, { pickEtab = true })
                "note" -> {
                    TextFieldRow("Note /20", v, { v = it })
                    TextFieldRow("Date (JJ/MM/AAAA ou année)", d, { d = it })
                    TextFieldRow("Par", p, { p = it })
                }
            }
            Text("S'applique à tous les éléments sélectionnés.", color = InkFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }, confirmButton = {
        TextButton(enabled = ok, onClick = {
            if (f.kind == "note") {
                val n = noteNum(v)
                if (n == null || n > 20) { toast(ctx, "Saisissez une note sur 20."); return@TextButton }
                val m = Regex("^(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})$").find(d.trim())
                val iso = if (m != null) "%s-%02d-%02d".format(m.groupValues[3], m.groupValues[2].toInt(), m.groupValues[1].toInt()) else d.trim()
                onApply(f, mapOf("v" to v.trim(), "date" to iso, "par" to p.trim()))
            } else onApply(f, mapOf("v" to v.trim()))
            onDismiss()
        }) { Text("Appliquer", maxLines = 1, softWrap = false) }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler", maxLines = 1, softWrap = false) } })
    if (pickEtab) PickerDialog("Établissement", Store.etabs(), { it.s("nom") }, { zoneName(it.s("zoneId")) }, { pickEtab = false }) { x ->
        v = x?.s("id") ?: ""; etabLabel = x?.s("nom") ?: "Aucun"; pickEtab = false
    }
}

/** Applique une modification en lot (champ simple, ou ajout d'une note d'inspection). */
fun applyBatch(coll: String, ids: Collection<String>, f: BField, vals: Map<String, String>) {
    val value = vals["v"] ?: ""
    Store.updateMany(coll, ids) { r ->
        when (f.kind) {
            "note" -> {
                val arr = r.optJSONArray("inspections") ?: JSONArray().also { r.put("inspections", it) }
                arr.put(JSONObject().put("id", UUID.randomUUID().toString()).put("note", value.replace(Regex("\\s*/\\s*20$"), ""))
                    .put("date", vals["date"] ?: "").put("par", vals["par"] ?: ""))
            }
            "etab" -> { if (value.isEmpty()) { r.remove("etablissementId"); r.remove("etablissementNom") } else { r.put("etablissementId", value); r.remove("etablissementNom") } }
            else -> if (value.isEmpty()) r.remove(f.key) else r.put(f.key, value)
        }
    }
}

// ---------------- champs modifiables par liste ----------------
val ENS_BATCH_FIELDS = listOf(
    BField("genre", "Genre", "select", Ref.GENRES),
    BField("__note", "Note d'inspection (ajouter)", "note"),
    BField("statut", "Statut", "select", Ref.STATUTS),
    BField("cycleEns", "Cycle d'enseignement", "select", Ref.ENS_CYCLES, canClear = true),
    BField("etablissementId", "Établissement", "etab"),
    BField("matiere", "Matière", "text", canClear = true)
)

fun etabBatchFields() = listOf(
    BField("type", "Type (public / privé)", "select", Ref.ETAB_TYPES),
    BField("cycle", "Cycle", "select", Ref.ETAB_CYCLES),
    BField("zoneId", "Zone", "select", Store.list("zones").associate { it.s("id") to it.s("nom") }, canClear = true)
)

fun taskBatchFields() = listOf(
    BField("statut", "Statut", "select", Ref.WORKFLOW),
    BField("noteGlobale", "Appréciation globale", "select", Ref.NOTE_SCALE.associateWith { it }, canClear = true),
    BField("priorite", "Priorité", "select", Ref.PRIORITES),
    BField("taskType", "Type de tâche", "select", Ref.TASK_TYPES),
    BField("echeance", "Date", "date")
)

// ---------------- fichiers générés (PDF / Word / Excel…) ----------------
/** Fichiers produits par l'application (exports), conservés dans le cache de l'appareil. */
@Composable
fun GeneratedFilesCard() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val files = remember(tick) { (Export.outDir(ctx).listFiles()?.filter { it.isFile } ?: emptyList()).sortedByDescending { it.lastModified() } }
    val picked = remember { mutableStateListOf<String>() }
    var askAll by remember { mutableStateOf(false) }
    var askSel by remember { mutableStateOf(false) }
    fun size(fs: List<File>): String { val b = fs.sumOf { it.length() }; return if (b > 1_000_000) "%.1f Mo".format(b / 1_000_000.0) else "${b / 1000} Ko" }
    CardBox {
        Text("Fichiers générés", fontWeight = FontWeight.Bold)
        Text("PDF, Word et Excel produits par l'application : ${files.size} fichier(s), ${size(files)}. Supprimer ces fichiers ne touche ni aux documents ni aux fiches.", fontSize = 13.sp, color = InkFaint)
        if (files.isEmpty()) Text("Aucun fichier généré.", fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        files.forEach { f ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = f.name in picked, onCheckedChange = { if (it) picked.add(f.name) else picked.remove(f.name) })
                Column(Modifier.weight(1f)) {
                    Text(f.name, fontSize = 13.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(size(listOf(f)) + " · " + fmtDate(java.time.Instant.ofEpochMilli(f.lastModified()).toString()), color = InkFaint, fontSize = 11.5.sp)
                }
                TextButton(onClick = { FileOps.openWith(ctx, f) }) { Text("Ouvrir", maxLines = 1, softWrap = false) }
            }
        }
        if (files.isNotEmpty()) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = picked.isNotEmpty(), onClick = { askSel = true }) { Text("Supprimer (${picked.size})", maxLines = 1, softWrap = false) }
            OutlinedButton(onClick = { askAll = true }) { Text("Tout supprimer", maxLines = 1, softWrap = false) }
            if (picked.size < files.size) TextButton(onClick = { picked.clear(); picked.addAll(files.map { it.name }) }) { Text("Tout cocher", maxLines = 1, softWrap = false) }
        }
    }
    if (askSel) ConfirmDialog("Supprimer ${picked.size} fichier(s) ?", "Les fichiers cochés seront effacés de cet appareil.", "Supprimer", { askSel = false }) {
        picked.toList().forEach { n -> File(Export.outDir(ctx), n).delete() }; picked.clear(); tick++
    }
    if (askAll) ConfirmDialog("Supprimer tous les fichiers générés ?", "${files.size} fichier(s) seront effacés de cet appareil.", "Tout supprimer", { askAll = false }) {
        Export.outDir(ctx).listFiles()?.forEach { if (it.isFile) it.delete() }; picked.clear(); tick++
    }
}
