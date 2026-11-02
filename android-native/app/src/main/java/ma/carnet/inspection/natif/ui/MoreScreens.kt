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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.carnet.inspection.natif.data.*
import org.json.JSONObject
import java.time.LocalDate

@Composable
fun MoreScreen() {
    val v by Store.version
    val gv by GoogleAccount.version
    val p = remember(v) { Store.profil() }
    val ctx = LocalContext.current
    var logout by remember { mutableStateOf(false) }
    ScreenScaffold("Plus", null, actions = { IconButton(onClick = { Nav.push(Scr.Search) }) { Icon(Icons.Default.Search, "Rechercher") }; HelpButton("demarrer") }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(14.dp)) {
            CardBox(onClick = { Nav.push(Scr.Settings("profil")) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(64)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(p.s("nomSignature").ifEmpty { p.s("nom") }.ifEmpty { GoogleAccount.name(ctx) }.ifEmpty { "Profil non renseigné" }, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOf(p.s("grade"), p.s("matiere")).filter { it.isNotEmpty() }.joinToString(" · "), color = InkFaint, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOf(p.s("province"), p.s("academieRegionale")).filter { it.isNotEmpty() }.joinToString(" · "), color = InkFaint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        gv
                        Text(if (GoogleAccount.signedIn(ctx)) "Google : " + GoogleAccount.email(ctx) else "Non connecté à Google", color = if (GoogleAccount.signedIn(ctx)) Brand else InkFaint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            MenuItem(Icons.Default.Create, "Documents", "Tous les documents produits : aperçu, PDF, Word, Excel") { Nav.push(Scr.DocsAll) }
            MenuItem(Icons.Default.Search, "Liseuse de documents", "Ouvrir un PDF, un Word, un Excel, une image…") { ViewerHolder.file = null; ViewerHolder.title = ""; Nav.push(Scr.Viewer) }
            MenuItem(Icons.Default.DateRange, "Programme annuel", "Préambule, catégories, activités, descriptions par l'IA") { Nav.push(Scr.Programme) }
            MenuItem(Icons.Default.Check, "Notes d'inspection", "Liste globale, par zone ou par établissement") { Nav.push(Scr.Notes) }
            MenuItem(Icons.Default.Star, "Bilan", "Graphiques au choix, bilan PDF / Word / Excel") { Nav.push(Scr.Bilan) }
            MenuItem(Icons.Default.LocationOn, "Zones", "Gérer les zones d'établissements") { Nav.push(Scr.Zones) }
            MenuItem(Icons.Default.Settings, "Paramètres", "Profil, compte Google, IA, thèmes, modèles, vacances…") { Nav.push(Scr.Settings()) }
            MenuItem(Icons.Default.Refresh, "Données et synchronisation", "Google Drive, import / export, CSV") { Nav.push(Scr.Data) }
            MenuItem(Icons.Default.Info, "Aide et contact", "Aide illustrée par les captures de vos écrans") { Nav.push(Scr.Help("")) }
            MenuItem(Icons.Default.Share, "Ouvrir le carnet web", "La version web du carnet, dans le navigateur") { openUri(ctx, WEB_URL) }
            MenuItem(Icons.Default.ExitToApp, "Déconnexion et fermeture", "Synchroniser, se déconnecter de Google et fermer") { logout = true }
        }
    }
    if (logout) LogoutDialog { logout = false }
}

@Composable
fun MenuItem(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    CardBox(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(BrandSoft), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Brand) }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, color = InkFaint, fontSize = 12.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.KeyboardArrowRight, null, tint = InkFaint)
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
                Box(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(5.dp)).background(Track)) {
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
            GoogleAccountCard()
            DriveCard(SyncHolder.fn)
            CsvImportCard()
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
