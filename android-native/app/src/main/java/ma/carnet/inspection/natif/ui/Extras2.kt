@file:OptIn(ExperimentalMaterial3Api::class)

package ma.carnet.inspection.natif.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ma.carnet.inspection.natif.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// ---------------- Échange avec iNSPECTAPP ----------------
@Composable
fun InspectAppTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val v by Store.version
    var auto by remember { mutableStateOf(Exchange.auto(ctx)) }
    var pending by remember { mutableStateOf<JSONObject?>(null) }
    var preview by remember { mutableStateOf<Exchange.Report?>(null) }
    var msg by remember { mutableStateOf("") }
    fun whenTxt(iso: String) = if (iso.isEmpty()) "jamais" else fmtDate(iso) + " " + iso.drop(11).take(5)
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) { ctx.contentResolver.openOutputStream(uri)?.use { it.write(Exchange.payload().toString(2).toByteArray()) }; Exchange.markPush(ctx); msg = "Fichier d'échange enregistré : ouvrez-le dans InspectApp (Importer depuis le Carnet)." }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            val data = JSONObject((ctx.contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: "").removePrefix("﻿"))
            if (data.s("format").isEmpty() && data.has("enseignants") && data.has("exportedAt")) { val r = Store.importBackup(data); msg = "Sauvegarde fusionnée : ${r.added} ajout(s), ${r.updated} mise(s) à jour, ${r.removed} suppression(s)." }
            else { val r = Exchange.apply(data, true); if (r.total() == 0) msg = "Rien de nouveau à importer." else { pending = data; preview = r } }
        } catch (e: Throwable) { msg = e.message ?: "Fichier illisible." }
    }
    v
    SectionTitle("Échange de données avec iNSPECTAPP")
    Text("Établissements, enseignant(e)s, zones et tâches circulent dans les deux sens. Les éléments connus des deux côtés sont mis à jour, jamais dupliqués ; rien n'est supprimé.", fontSize = 12.5.sp, color = InkFaint)
    CardBox {
        Text("Par Google Drive", fontWeight = FontWeight.SemiBold)
        Text("Le carnet dépose « ${Exchange.FILE_OUT} » et lit « ${Exchange.FILE_IN} » dans le dossier « ${Exchange.FOLDER} » de son dossier Drive.", fontSize = 12.5.sp)
        Button(onClick = {
            SyncHolder.withToken("Échange avec InspectApp…") { tk ->
                val (r, found) = withContext(Dispatchers.IO) { Exchange.driveSync(ctx, tk, true) }
                msg = (if (!found) "Aucun fichier d'InspectApp dans « ${Exchange.FOLDER} » pour l'instant (ou non visible par cette application : utilisez alors l'import par fichier). " else "") +
                    "Données du carnet envoyées." + (r?.let { "\n" + it.text() } ?: "")
                SyncState.ok.value = true; SyncState.msg.value = "Échange avec InspectApp terminé."
            }
        }, modifier = Modifier.padding(top = 6.dp)) { BtnText("Échanger maintenant", maxLines = 1, softWrap = false) }
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(auto, { auto = it; Exchange.setAuto(ctx, it) }); Text("Échanger automatiquement à chaque synchronisation", Modifier.padding(start = 8.dp), fontSize = 13.sp) }
    }
    CardBox {
        Text("Par fichier", fontWeight = FontWeight.SemiBold)
        Text("Pour une clé USB, un courriel ou un partage : le même format, lu et produit par les deux applications.", fontSize = 12.5.sp)
        Row(Modifier.horizontalScrollRow(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { save.launch("Echange_Carnet_vers_InspectApp_${java.time.LocalDate.now()}.json") }) { BtnText("Exporter vers InspectApp", maxLines = 1, softWrap = false) }
            OutlinedButton(onClick = { open.launch(arrayOf("application/json", "*/*")) }) { BtnText("Importer depuis InspectApp", maxLines = 1, softWrap = false) }
            OutlinedButton(onClick = { scope.launch { val f = withContext(Dispatchers.IO) { File(Export.outDir(ctx), "Echange_Carnet_vers_InspectApp.json").also { it.writeText(Exchange.payload().toString(2)) } }; FileOps.share(ctx, f) } }) { BtnText("Partager", maxLines = 1, softWrap = false) }
        }
    }
    CardBox {
        InfoRow("Dernier envoi", whenTxt(Exchange.lastPush(ctx)))
        InfoRow("Dernière réception", whenTxt(Exchange.lastImport(ctx)))
        val (a, b, c) = Exchange.linked()
        InfoRow("Éléments reliés", "$a établissement(s), $b enseignant(e)(s), $c tâche(s)")
        Text("Rapprochement : code MASSAR ou nom pour les établissements, PPR ou nom pour les enseignant(e)s, type + date + enseignant(e) pour les tâches.", color = InkFaint, fontSize = 11.5.sp)
    }
    if (msg.isNotEmpty()) CardBox { Text(msg, fontSize = 13.sp, color = Brand) }
    preview?.let { r ->
        AlertDialog(onDismissRequest = { preview = null; pending = null }, title = { Text("Importer ces données ?") },
            text = { Text(r.text() + "\n\nAucune donnée locale ne sera supprimée.") },
            confirmButton = { TextButton(onClick = { val d = pending; if (d != null) { val rr = Exchange.apply(d, false); Exchange.markImport(ctx); msg = "Import terminé — ${rr.total()} élément(s) ajouté(s) ou mis à jour." }; preview = null; pending = null }) { BtnText("Importer", maxLines = 1, softWrap = false) } },
            dismissButton = { TextButton(onClick = { preview = null; pending = null }) { BtnText("Annuler", maxLines = 1, softWrap = false) } })
    }
}

// ---------------- Polices des documents ----------------
@Composable
fun FontsSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("") }
    val inst = remember(tick) { Fonts.installed(ctx) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try { val f = Fonts.install(ctx, uri, Attach.nameOf(ctx, uri)); tick++; msg = "Police « ${Fonts.familyOf(f)} » installée." } catch (e: Throwable) { msg = "Installation impossible." }
    }
    SectionTitle("Polices des documents")
    CardBox {
        Text("Utilisées dans les documents (aperçu, PDF, Word). Dans Word, la police doit aussi exister sur l'ordinateur qui ouvre le fichier.", fontSize = 12.5.sp, color = InkFaint)
        val opts = linkedMapOf("" to "Police du modèle (par défaut)") + inst.associate { Fonts.familyOf(it) to Fonts.familyOf(it) + " (installée)" } + Fonts.COMMON.associateWith { it }
        Fonts.SLOTS.forEach { (k, l) ->
            tick
            SelectField(l, opts, Fonts.prefs().s(k), { Fonts.set(k, it); tick++ })
        }
        SectionTitle("Polices installées")
        if (inst.isEmpty()) Text("Aucune police installée.", color = InkFaint, fontSize = 12.5.sp)
        inst.forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Fonts.familyOf(f) + " — أبجد هوز", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontFamily = try { FontFamily(androidx.compose.ui.text.font.Font(f)) } catch (_: Throwable) { FontFamily.Default })
                Text("${f.length() / 1024} Ko", color = InkFaint, fontSize = 11.5.sp)
                IconButton(onClick = { f.delete(); tick++ }) { Icon(Icons.Default.Delete, "Supprimer", tint = Danger) }
            }
        }
        OutlinedButton(onClick = { pick.launch(arrayOf("font/*", "application/x-font-ttf", "application/octet-stream", "*/*")) }) { BtnText("Installer une police (.ttf, .otf, .woff2)", maxLines = 1, softWrap = false) }
        Text("Polices libres à télécharger :", fontSize = 12.5.sp, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.horizontalScrollRow(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Fonts.DOWNLOADS.forEach { (fam, url) ->
                AssistChip(onClick = {
                    msg = "Téléchargement de $fam…"
                    scope.launch { msg = try { val b = withContext(Dispatchers.IO) { java.net.URL(url).readBytes() }; Fonts.installBytes(ctx, fam, b); tick++; "Police « $fam » installée." } catch (e: Throwable) { "Téléchargement impossible : " + (e.message ?: "") } }
                }, label = { Text(fam) })
            }
        }
        TextButton(onClick = { openUri(ctx, "https://sourceforge.net/projects/maghribifonts/") }) { BtnText("Police maghribi « Rabat » : où la trouver ?", maxLines = 1, softWrap = false) }
        if (msg.isNotEmpty()) Text(msg, color = Brand, fontSize = 12.5.sp)
    }
}

// ---------------- Fusion de deux établissements en double ----------------
@Composable
fun EtabMergeCard(targetId: String) {
    val ctx = LocalContext.current
    var pick by remember { mutableStateOf(false) }
    var src by remember { mutableStateOf<JSONObject?>(null) }
    SectionTitle("Fusionner avec un doublon")
    CardBox {
        Text("Choisissez l'établissement en double : ses enseignant(e)s, tâches, documents et pièces jointes sont rattachés à celui-ci, les informations manquantes sont reprises, puis le doublon est supprimé.", fontSize = 12.5.sp, color = InkFaint)
        OutlinedButton(onClick = { pick = true }) { BtnText("Choisir le doublon…", maxLines = 1, softWrap = false) }
    }
    if (pick) PickerDialog("Établissement en double", Store.list("etablissements").filter { it.s("id") != targetId }.sortedBy { norm(it.s("nom")) },
        { it.s("nom") }, { listOf(zoneName(it.s("zoneId")), it.s("codeMassar")).filter { s -> s.isNotEmpty() }.joinToString(" · ") }, { pick = false }) { src = it; pick = false }
    src?.let { s ->
        val nE = Store.list("enseignants").count { it.s("etablissementId") == s.s("id") }
        val nT = Store.list("tasks").count { it.s("etablissementId") == s.s("id") }
        ConfirmDialog("Fusionner « ${s.s("nom")} » ?", "$nE enseignant(e)(s) et $nT tâche(s) seront rattaché(e)s à « ${etabName(targetId)} », puis « ${s.s("nom")} » sera supprimé.", confirm = "Fusionner", onDismiss = { src = null }) {
            mergeEtabs(s.s("id"), targetId); toast(ctx, "Établissements fusionnés.")
        }
    }
}

fun mergeEtabs(srcId: String, dstId: String) {
    val src = Store.byId("etablissements", srcId) ?: return
    val dst = JSONObject((Store.byId("etablissements", dstId) ?: return).toString())
    src.keys().forEach { k -> if (k !in listOf("id", "createdAt", "updatedAt", "attachments") && dst.s(k).isEmpty() && src.s(k).isNotEmpty()) dst.put(k, src.get(k)) }
    val att = dst.optJSONArray("attachments") ?: JSONArray().also { dst.put("attachments", it) }
    src.arr("attachments").forEach { att.put(it) }
    if (src.s("notes").isNotEmpty() && !dst.s("notes").contains(src.s("notes"))) dst.put("notes", listOf(dst.s("notes"), src.s("notes")).filter { it.isNotEmpty() }.joinToString("\n"))
    Store.upsert("etablissements", dst)
    Store.list("enseignants").filter { it.s("etablissementId") == srcId }.forEach { Store.upsert("enseignants", JSONObject(it.toString()).put("etablissementId", dstId)) }
    Store.list("tasks").filter { it.s("etablissementId") == srcId }.forEach { Store.upsert("tasks", JSONObject(it.toString()).put("etablissementId", dstId).put("etablissementNom", dst.s("nom"))) }
    Store.delete("etablissements", srcId)
}

// ---------------- Tableaux Google Sheets ----------------
@Composable
fun SheetsCard() {
    val ctx = LocalContext.current
    val prefs = ctx.getSharedPreferences("carnet", android.content.Context.MODE_PRIVATE)
    var msg by remember { mutableStateOf("") }
    fun csv(rows: List<List<String>>) = rows.joinToString("\n") { r -> r.joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" } }
    val tables = linkedMapOf<String, () -> List<List<String>>>(
        "Tâches" to { listOf(listOf("Date", "Heure", "Fin", "Titre", "Type", "Statut", "Priorité", "Établissement", "Enseignant(e)", "Lieu", "Invité(e)s", "Appréciation", "Notes")) +
            Store.list("tasks").sortedBy { it.s("echeance") }.map { t -> listOf(t.s("echeance"), taskTime(t), t.s("heureFin"), t.s("titre"), Ref.TASK_TYPES[Ref.type(t)] ?: "", Ref.WORKFLOW[Ref.statut(t)] ?: "",
                Ref.PRIORITES[t.s("priorite")] ?: "", taskEtabName(t), taskEnsName(t), t.s("lieu"), t.arr("invites").joinToString("; ") { it.s("nom") }, t.s("noteGlobale"), t.s("description")) } },
        "Enseignants" to { listOf(listOf("Nom", "Nom (arabe)", "PPR", "Établissement", "Zone", "Matière", "Cadre", "Genre", "Statut", "Téléphone", "Email", "Dernière note", "Date")) +
            Store.list("enseignants").sortedBy { norm(it.s("nom")) }.map { e -> val li = ensInspections(e).firstOrNull(); val et = Store.byId("etablissements", e.s("etablissementId"))
                listOf(e.s("nom"), e.s("nomAr"), e.s("ppr"), et?.s("nom") ?: "", zoneName(et?.s("zoneId")), e.s("matiere"), e.s("cadre"), e.s("genre"), e.s("statut"), e.s("telephone"), e.s("email"), li?.note ?: "", li?.let { inspDateTxt(it.date) } ?: "") } },
        "Etablissements" to { listOf(listOf("Nom", "Nom (arabe)", "Code MASSAR", "Zone", "Type", "Cycle", "Directeur(trice)", "Tél. directeur", "Téléphone", "Adresse", "Enseignant(e)s")) +
            Store.list("etablissements").sortedBy { norm(it.s("nom")) }.map { e -> listOf(e.s("nom"), e.s("nomAr"), e.s("codeMassar"), zoneName(e.s("zoneId")), e.s("type"), Ref.ETAB_CYCLES[e.s("cycle")] ?: "",
                e.s("directeur"), e.s("directeurTel"), e.s("telephone"), e.s("adresse"), Store.list("enseignants").count { it.s("etablissementId") == e.s("id") }.toString()) } }
    )
    SectionTitle("Tableaux Google Sheets")
    CardBox {
        Text("Crée ou met à jour, dans votre Drive (dossier « Tableaux »), une feuille Google Sheets par liste. La synchronisation des données elle-même passe par la sauvegarde Drive, partagée avec le carnet web.", fontSize = 12.5.sp, color = InkFaint)
        Button(onClick = {
            SyncHolder.withToken("Mise à jour des feuilles Google Sheets…") { tk ->
                val done = withContext(Dispatchers.IO) {
                    val folder = DriveSync.subFolder(tk, "Tableaux")
                    tables.map { (name, rows) ->
                        val id = DriveSync.sheetFromCsv(tk, folder, "Carnet d'Inspection — $name", csv(rows()), prefs.getString("sheet_$name", null))
                        prefs.edit().putString("sheet_$name", id).apply(); name
                    }
                }
                msg = "Feuilles mises à jour : " + done.joinToString(", "); SyncState.ok.value = true; SyncState.msg.value = msg
            }
        }) { BtnText("Mettre à jour les feuilles", maxLines = 1, softWrap = false) }
        Row(Modifier.horizontalScrollRow(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tables.keys.forEach { name -> prefs.getString("sheet_$name", null)?.let { id -> AssistChip(onClick = { openUri(ctx, "https://docs.google.com/spreadsheets/d/$id/edit") }, label = { Text("Ouvrir : $name") }) } }
        }
        if (msg.isNotEmpty()) Text(msg, color = Brand, fontSize = 12.5.sp)
    }
}
