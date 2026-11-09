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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.carnet.inspection.natif.data.*
import org.json.JSONObject

val ARCHIVE_COLLS = linkedMapOf("enseignants" to "Enseignant(e)s", "etablissements" to "Établissements", "tasks" to "Tâches", "documents" to "Documents", "zones" to "Zones")
private val OP_COLOR = mapOf("création" to Brand, "modification" to Earth, "suppression" to Danger, "restauration" to Brand, "synchronisation" to InkFaint)

private fun stamp(iso: String): String = if (iso.length >= 16) fmtDate(iso) + " " + iso.substring(11, 16) else iso

/** Historique permanent d'une fiche (création, modifications, suppression, restauration). */
@Composable
fun HistorySection(id: String) {
    val v by Store.version
    val h = remember(v, id) { Store.historyOf(id) }
    var open by remember { mutableStateOf(false) }
    if (h.isEmpty()) return
    SectionTitle("Historique (${h.size})")
    CardBox(onClick = { open = !open }) {
        Text(if (open) "Masquer l'historique" else "Voir l'historique de cette fiche", color = Brand, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
        if (open) h.forEach { e ->
            Column(Modifier.padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tag(e.optString("op"), OP_COLOR[e.optString("op")] ?: Brand)
                    Text(stamp(e.optString("t")), color = InkFaint, fontSize = 12.sp)
                }
                if (e.optString("detail").isNotEmpty()) Text(e.optString("detail"), fontSize = 12.5.sp)
            }
        }
    }
}

private fun openRecord(coll: String, id: String) {
    when {
        Store.byId(coll, id) != null -> when (coll) {
            "enseignants" -> Nav.push(Scr.EnsDetail(id)); "etablissements" -> Nav.push(Scr.EtabDetail(id))
            "tasks" -> Nav.push(Scr.TaskDetail(id)); "documents" -> Nav.push(Scr.DocPreview(id)); else -> {}
        }
        Store.archivedById(coll, id) != null -> Nav.push(Scr.ArchiveView(coll, id))
    }
}

@Composable
fun ArchivesScreen(initialColl: String, initialTab: String = "supprimes") {
    val v by Store.version
    val ctx = LocalContext.current
    var tab by remember(initialTab) { mutableStateOf(initialTab) }
    var coll by remember { mutableStateOf(initialColl.ifEmpty { "enseignants" }) }
    var q by remember { mutableStateOf("") }
    var op by remember { mutableStateOf("") }
    ScreenScaffold("Archives et historique", { Nav.pop() }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 14.dp)) {
            item {
                Spacer(Modifier.height(8.dp))
                ChipRow(mapOf("supprimes" to "Fiches supprimées", "journal" to "Journal des opérations"), tab, { if (it.isNotEmpty()) tab = it }, null)
                Spacer(Modifier.height(6.dp))
                SearchField(q, { q = it }, "Rechercher…")
                Spacer(Modifier.height(6.dp))
                ChipRow(ARCHIVE_COLLS, coll, { coll = it }, if (tab == "journal") "Tout" else null)
                if (tab == "journal") ChipRow(OP_COLOR.keys.associateWith { it }, op, { op = it }, "Toutes opérations")
            }
            val nq = norm(q)
            if (tab == "supprimes") {
                v; val list = Store.archived(coll).filter { nq.isEmpty() || norm(Store.labelOf(coll, it) + " " + it.s("ppr") + " " + it.s("matiere")).contains(nq) }
                item { Text("${list.size} fiche(s) supprimée(s) conservée(s)", color = InkFaint, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp)) }
                if (list.isEmpty()) item { EmptyState("Aucune fiche supprimée.") }
                items(list, key = { it.s("id") }) { r ->
                    CardBox(onClick = { Nav.push(Scr.ArchiveView(coll, r.s("id"))) }) {
                        Text(Store.labelOf(coll, r), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(listOf("Supprimée le " + stamp(r.s("deletedAt")), r.s("ppr"), r.s("matiere")).filter { it.isNotEmpty() }.joinToString(" · "), color = InkFaint, fontSize = 12.5.sp)
                    }
                }
            } else {
                v; val all = Store.journal().sortedByDescending { it.optString("t") }
                val list = all.filter { (coll.isEmpty() || it.optString("coll") == coll || it.optString("coll").isEmpty()) && (op.isEmpty() || it.optString("op") == op) &&
                    (nq.isEmpty() || norm(it.optString("label") + " " + it.optString("detail")).contains(nq)) }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${list.size} opération(s)", color = InkFaint, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { exportJournalCsv(ctx, list) }) { BtnText("Exporter en CSV", maxLines = 1, softWrap = false) }
                    }
                }
                if (list.isEmpty()) item { EmptyState("Aucune opération.") }
                items(list.take(400), key = { it.optString("id") }) { e ->
                    val c = e.optString("coll"); val rid = e.optString("recId")
                    CardBox(onClick = if (rid.isNotEmpty()) ({ openRecord(c, rid) }) else null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Tag(e.optString("op"), OP_COLOR[e.optString("op")] ?: Brand)
                            Tag(ARCHIVE_COLLS[c] ?: "", Earth)
                            Text(stamp(e.optString("t")), color = InkFaint, fontSize = 11.5.sp)
                        }
                        if (e.optString("label").isNotEmpty()) Text(e.optString("label"), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (e.optString("detail").isNotEmpty()) Text(e.optString("detail"), fontSize = 12.5.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (list.size > 400) item { Text("Affichage des 400 plus récentes : affinez avec la recherche ou exportez en CSV.", color = InkFaint, fontSize = 12.sp, modifier = Modifier.padding(8.dp)) }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

private fun exportJournalCsv(ctx: android.content.Context, list: List<JSONObject>) {
    fun q(s: String) = "\"" + s.replace("\"", "\"\"").replace("\n", " | ") + "\""
    val sb = StringBuilder("﻿date;heure;opération;type;fiche;détail\n")
    list.forEach { e -> val t = e.optString("t"); sb.append(listOf(fmtDate(t), if (t.length >= 16) t.substring(11, 16) else "", e.optString("op"), ARCHIVE_COLLS[e.optString("coll")] ?: "", e.optString("label"), e.optString("detail")).joinToString(";") { q(it) }).append('\n') }
    val f = java.io.File(Export.outDir(ctx), "Journal_operations_" + java.time.LocalDate.now() + ".csv")
    f.writeText(sb.toString()); FileOps.share(ctx, f)
}

private val FIELD_LABELS = mapOf("nom" to "Nom", "nomAr" to "Nom en arabe", "nomFr" to "Nom (français)", "ppr" to "PPR", "cin" to "CIN", "matiere" to "Matière", "cadre" to "Cadre",
    "cycle" to "Niveaux / cycle", "telephone" to "Téléphone", "email" to "Email", "dateNaissance" to "Naissance", "dateRecrutement" to "Recrutement",
    "directeur" to "Directeur(trice)", "directeurTel" to "Tél. directeur", "adresse" to "Adresse", "codeMassar" to "Code MASSAR", "titre" to "Titre", "echeance" to "Date",
    "heureDebut" to "Heure", "lieu" to "Lieu", "description" to "Description", "noteGlobale" to "Appréciation", "date" to "Date", "notes" to "Notes", "deletedAt" to "Supprimée le",
    "enseignantNom" to "Enseignant(e)", "etablissementNom" to "Établissement", "classe" to "Classe", "note" to "Note", "constats" to "Constats", "recommandations" to "Recommandations",
    "texte" to "Texte", "beneficiaireNom" to "Bénéficiaire")
private val HIDDEN = setOf("id", "createdAt", "updatedAt", "attachments", "horaire", "inspections", "feuilles", "invites", "criteres", "pieces", "participants", "aiZones", "aiValues", "etablissementId", "enseignantId", "zoneId", "taskId")

private fun nameAny(coll: String, id: String): String = Store.byId(coll, id)?.s("nom") ?: Store.archivedById(coll, id)?.s("nom") ?: ""

/** Fiche supprimée, en lecture seule, avec son historique ; restauration possible. */
@Composable
fun ArchiveViewScreen(coll: String, id: String) {
    val v by Store.version
    val r = remember(v, id) { Store.archivedById(coll, id) }
    val ctx = LocalContext.current
    var askRestore by remember { mutableStateOf(false) }
    val live = remember(v, id) { Store.byId(coll, id) }
    if (r == null) {
        ScreenScaffold("Fiche", { Nav.pop() }) { pad ->
            Column(Modifier.padding(pad)) {
                EmptyState(if (live != null) "Cette fiche a été restaurée." else "Fiche introuvable dans les archives.")
                if (live != null) TextButton(onClick = { Nav.replace(when (coll) { "enseignants" -> Scr.EnsDetail(id); "etablissements" -> Scr.EtabDetail(id); "tasks" -> Scr.TaskDetail(id); else -> Scr.DocPreview(id) }) }) { BtnText("Ouvrir la fiche", maxLines = 1, softWrap = false) }
            }
        }
        return
    }
    ScreenScaffold(Store.labelOf(coll, r), { Nav.pop() }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(14.dp)) {
            CardBox {
                Text("Fiche supprimée le " + stamp(r.s("deletedAt")) + " — consultation seule", color = Danger, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Button(onClick = { askRestore = true }, modifier = Modifier.padding(top = 8.dp)) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Restaurer cette fiche", maxLines = 1, softWrap = false) }
            }
            CardBox {
                InfoRow("Établissement", r.s("etablissementNom").ifEmpty { nameAny("etablissements", r.s("etablissementId")) })
                InfoRow("Enseignant(e)", r.s("enseignantNom").ifEmpty { nameAny("enseignants", r.s("enseignantId")) })
                InfoRow("Genre", Ref.GENRES[r.s("genre")] ?: ""); InfoRow("Statut", Ref.STATUTS[r.s("statut")] ?: Ref.WORKFLOW[r.s("statut")] ?: "")
                InfoRow("Type", Ref.ETAB_TYPES[r.s("type")] ?: Docs.LABELS[r.s("type")] ?: "")
                FIELD_LABELS.forEach { (k, l) -> if (k != "deletedAt" && r.has(k) && !r.isNull(k) && r.opt(k) is String) InfoRow(l, if (k in setOf("echeance", "date", "dateNaissance", "dateRecrutement")) fmtDate(r.s(k)) else r.s(k)) }
            }
            val ins = ensInspections(r)
            if (coll == "enseignants" && ins.isNotEmpty()) { SectionTitle("Notes d'inspection (${ins.size})"); CardBox { ins.forEach { Text(inspDateTxt(it.date) + " — " + it.note + (if (it.par.isNotEmpty()) " (" + it.par + ")" else ""), fontSize = 13.5.sp) } } }
            val hor = r.arr("horaire")
            if (hor.isNotEmpty()) {
                SectionTitle("Emploi du temps")
                CardBox { Ref.JOURS.forEach { j -> val dj = hor.filter { it.s("jour") == j }; if (dj.isNotEmpty()) { Text(j, fontWeight = FontWeight.SemiBold, fontSize = 13.sp); dj.forEach { Text("  " + it.s("debut") + "–" + it.s("fin") + "  " + it.s("classe") + " " + it.s("matiere"), fontSize = 12.5.sp) } } } }
            }
            val inv = r.arr("invites")
            if (inv.isNotEmpty()) { SectionTitle("Invité(e)s (${inv.size})"); CardBox { Text(inv.joinToString(", ") { it.s("nom") }, fontSize = 13.sp) } }
            HistorySection(id)
            Spacer(Modifier.height(40.dp))
        }
    }
    if (askRestore) ConfirmDialog("Restaurer cette fiche ?", "« ${Store.labelOf(coll, r)} » sera remise dans le carnet, avec ses données telles qu'au moment de la suppression.", "Restaurer", { askRestore = false }) {
        Store.restore(coll, id); toast(ctx, "Fiche restaurée.")
    }
}
