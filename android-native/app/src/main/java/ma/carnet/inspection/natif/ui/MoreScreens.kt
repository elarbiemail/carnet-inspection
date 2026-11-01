@file:OptIn(ExperimentalMaterial3Api::class)

package ma.carnet.inspection.natif.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.carnet.inspection.natif.data.*
import org.json.JSONObject
import java.time.LocalDate

@Composable
fun MoreScreen() {
    val v by Store.version
    val p = remember(v) { Store.profil() }
    val ctx = LocalContext.current
    ScreenScaffold("Plus", null) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(14.dp)) {
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(p.s("nomSignature").ifEmpty { p.s("nom") }.ifEmpty { "Profil" }, Brand, 54)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(p.s("nomSignature").ifEmpty { p.s("nom") }.ifEmpty { "Profil non renseigné" }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(listOf(p.s("grade"), p.s("matiere")).filter { it.isNotEmpty() }.joinToString(" · "), color = InkFaint, fontSize = 12.5.sp)
                        Text(listOf(p.s("province"), p.s("academieRegionale")).filter { it.isNotEmpty() }.joinToString(" · "), color = InkFaint, fontSize = 12.sp)
                    }
                }
            }
            MenuItem(Icons.Default.Star, "Bilan", "Statistiques de la période, par type et par statut") { Nav.push(Scr.Bilan) }
            MenuItem(Icons.Default.Refresh, "Données et synchronisation", "Importer la sauvegarde du carnet web, exporter vos modifications") { Nav.push(Scr.Data) }
            MenuItem(Icons.Default.Share, "Ouvrir le carnet web", "Documents Word/PDF, modèles, IA, programme annuel") { openUri(ctx, WEB_URL) }
            MenuItem(Icons.Default.Info, "Aide et contact", "Fonctionnement, contact") { Nav.push(Scr.About) }
        }
    }
}

@Composable
fun MenuItem(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    CardBox(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(BrandSoft), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Brand) }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(sub, color = InkFaint, fontSize = 12.5.sp)
            }
            Icon(Icons.Default.KeyboardArrowRight, null, tint = InkFaint)
        }
    }
}

// ---------------- Bilan ----------------
@Composable
fun BilanScreen() {
    val v by Store.version
    var period by remember { mutableStateOf("annee") }
    val today = LocalDate.now()
    val from = when (period) {
        "mois" -> today.withDayOfMonth(1)
        "trimestre" -> today.minusMonths(((today.monthValue - 1) % 3).toLong()).withDayOfMonth(1)
        else -> if (today.monthValue >= 9) LocalDate.of(today.year, 9, 1) else LocalDate.of(today.year - 1, 9, 1)
    }
    val to = when (period) {
        "mois" -> from.plusMonths(1).minusDays(1)
        "trimestre" -> from.plusMonths(3).minusDays(1)
        else -> LocalDate.of(from.year + 1, 8, 31)
    }
    val list = remember(v, period) { Store.tasks().filter { val d = parseDate(it.s("echeance")); d != null && !d.isBefore(from) && !d.isAfter(to) } }
    val done = list.count { Ref.statut(it) in Ref.DONE }
    val visites = list.filter { Ref.type(it) == "inspection" || Ref.type(it) == "visite_conseil" }
    val ensVis = visites.map { it.s("enseignantId") }.filter { it.isNotEmpty() }.toSet()
    val ensTotal = Store.enseignants().size
    ScreenScaffold("Bilan", { Nav.pop() }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(14.dp)) {
            ZoneFilterBar()
            ChipRow(mapOf("mois" to "Ce mois", "trimestre" to "Trimestre", "annee" to "Année scolaire"), period, { if (it.isNotEmpty()) period = it }, null)
            Text("Du ${fmtDate(from.toString())} au ${fmtDate(to.toString())}", color = InkFaint, fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Kpi("Tâches", list.size.toString(), Modifier.weight(1f)) {}
                Kpi("Réalisées", if (list.isEmpty()) "0 %" else "${done * 100 / list.size} %", Modifier.weight(1f)) {}
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Kpi("Inspections et visites", visites.size.toString(), Modifier.weight(1f)) {}
                Kpi("Enseignants vus", "${ensVis.size} / $ensTotal", Modifier.weight(1f)) {}
            }
            Bars("Avancement", Ref.WORKFLOW.map { (k, l) -> l to list.count { Ref.statut(it) == k } }, statutColorsFor())
            Bars("Par type d'activité", Ref.TASK_TYPES.map { (k, l) -> l to list.count { Ref.type(it) == k } }.filter { it.second > 0 }.sortedByDescending { it.second })
            val byEtab = list.groupBy { taskEtabName(it).ifEmpty { "Non renseigné" } }.map { it.key to it.value.size }.sortedByDescending { it.second }.take(10)
            Bars("Par établissement", byEtab)
            val ens = Store.enseignants()
            val tranches = listOf("Moins de 12", "12 – 14", "14 – 16", "16 – 18", "18 et plus", "Sans note")
            val notes = ens.map { e -> ensInspections(e).firstOrNull()?.let { noteNum(it.note) } }
            Bars("Dernières notes d'inspection", tranches.mapIndexed { i, l ->
                l to notes.count { n -> when (i) { 0 -> n != null && n < 12; 1 -> n != null && n >= 12 && n < 14; 2 -> n != null && n >= 14 && n < 16; 3 -> n != null && n >= 16 && n < 18; 4 -> n != null && n >= 18; else -> n == null } }
            })
            Spacer(Modifier.height(40.dp))
        }
    }
}

private fun statutColorsFor(): List<Color> = Ref.WORKFLOW.keys.map { statutColor(it) }

@Composable
fun Bars(title: String, data: List<Pair<String, Int>>, colors: List<Color>? = null) {
    if (data.isEmpty() || data.all { it.second == 0 }) return
    SectionTitle(title)
    val max = (data.maxOf { it.second }).coerceAtLeast(1)
    val tot = data.sumOf { it.second }.coerceAtLeast(1)
    CardBox {
        data.forEachIndexed { i, (l, n) ->
            Column(Modifier.padding(vertical = 4.dp)) {
                Row { Text(l, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text("$n (${n * 100 / tot} %)", fontSize = 12.5.sp, color = InkFaint) }
                Box(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFEDEAE1))) {
                    if (n > 0) Box(Modifier.fillMaxWidth(n.toFloat() / max).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(colors?.getOrNull(i) ?: Brand))
                }
            }
        }
    }
}

// ---------------- Données ----------------
@Composable
fun DataScreen() {
    val v by Store.version
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf("") }
    val prefs = remember { ctx.getSharedPreferences("carnet", android.content.Context.MODE_PRIVATE) }
    var lastImport by remember { mutableStateOf(prefs.getString("lastImport", "") ?: "") }
    var lastExport by remember { mutableStateOf(prefs.getString("lastExport", "") ?: "") }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val txt = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            val json = JSONObject(txt.trimStart('﻿'))
            if (!json.has("enseignants") && !json.has("etablissements") && !json.has("tasks")) { msg = "Ce fichier n'est pas une sauvegarde du carnet."; return@rememberLauncherForActivityResult }
            val r = Store.importBackup(json)
            lastImport = Store.now(); prefs.edit().putString("lastImport", lastImport).apply()
            msg = "Import terminé : ${r.added} ajout(s), ${r.updated} mise(s) à jour, ${r.removed} suppression(s)."
        } catch (e: Exception) { msg = "Lecture impossible : " + (e.message ?: "fichier invalide") }
    }
    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(Store.exportJson().toByteArray(Charsets.UTF_8)) }
            lastExport = Store.now(); prefs.edit().putString("lastExport", lastExport).apply()
            msg = "Sauvegarde enregistrée. Dans le carnet web : Paramètres → InspectApp → Importer ce fichier."
        } catch (e: Exception) { msg = "Enregistrement impossible : " + (e.message ?: "") }
    }
    ScreenScaffold("Données et synchronisation", { Nav.pop() }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(14.dp)) {
            v.let { }
            CardBox {
                Text("Sur cet appareil", fontWeight = FontWeight.Bold)
                listOf("enseignants" to "enseignant(e)s", "etablissements" to "établissements", "tasks" to "tâches", "zones" to "zones", "documents" to "documents")
                    .forEach { (k, l) -> Text("• ${Store.list(k).size} $l", fontSize = 13.5.sp) }
                if (lastImport.isNotEmpty()) Text("Dernier import : " + fmtDate(lastImport) + " " + lastImport.substring(11, 16), color = InkFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                if (lastExport.isNotEmpty()) Text("Dernier export : " + fmtDate(lastExport) + " " + lastExport.substring(11, 16), color = InkFaint, fontSize = 12.sp)
            }
            DriveCard(SyncHolder.fn)
            SectionTitle("Ou par fichier : recevoir les données du carnet web")
            CardBox {
                Text("Choisissez le fichier « sauvegarde-donnees.json » dans votre Google Drive (dossier de l'application → Sauvegardes). Les fiches les plus récentes remplacent les anciennes ; rien n'est perdu.", fontSize = 13.5.sp)
                Button(onClick = { openDoc.launch(arrayOf("application/json", "text/plain", "*/*")) }, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Importer une sauvegarde")
                }
            }
            SectionTitle("Envoyer mes modifications au carnet web")
            CardBox {
                Text("Enregistrez une sauvegarde complète (par exemple dans votre Google Drive), puis importez-la dans le carnet web : Paramètres → InspectApp → Importer. La fusion garde, fiche par fiche, la version la plus récente.", fontSize = 13.5.sp)
                Button(onClick = { createDoc.launch("carnet-natif-" + LocalDate.now() + ".json") }, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Exporter une sauvegarde")
                }
            }
            if (msg.isNotEmpty()) CardBox { Text(msg, color = Brand, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun AboutScreen() {
    val ctx = LocalContext.current
    ScreenScaffold("Aide et contact", { Nav.pop() }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(14.dp)) {
            CardBox {
                Text("Carnet d'Inspection — application native", fontWeight = FontWeight.Bold)
                Text("Version Android native : tâches, enseignant(e)s, établissements, directeurs, notes d'inspection, emplois du temps, recherche par horaire et bilan, utilisables hors connexion.", fontSize = 13.5.sp, modifier = Modifier.padding(top = 6.dp))
                Text("Les données s'échangent avec le carnet web par fichier de sauvegarde (menu Données). La génération des documents officiels, les modèles, l'assistant IA et le programme annuel restent dans le carnet web.", fontSize = 13.5.sp, modifier = Modifier.padding(top = 6.dp))
            }
            SectionTitle("Contact")
            CardBox {
                Text("El Arbi EL ADLOUNI", fontWeight = FontWeight.SemiBold)
                ContactButtons("0700024598", "El Arbi", "elarbi.eladlouni@fm6education.net")
                TextButton(onClick = { mail(ctx, "elarbi.eladlouni@taalim.ma", "Carnet d'Inspection") }) { Text("elarbi.eladlouni@taalim.ma") }
            }
        }
    }
}
