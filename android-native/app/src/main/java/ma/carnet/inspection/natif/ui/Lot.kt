@file:OptIn(ExperimentalMaterial3Api::class)

package ma.carnet.inspection.natif.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ma.carnet.inspection.natif.data.*
import org.json.JSONObject

/**
 * Rattache chaque fichier à l'enseignant(e) dont le PPR figure dans son nom (ex. « 1408356_AGOUJIL_ZOUHIR.pdf »),
 * sinon dont tous les mots du nom y figurent (même règle que le carnet web).
 */
fun feuilleMatchEns(name: String): JSONObject? {
    val base = name.replace(Regex("\\.[A-Za-z0-9]+$"), "")
    val all = Store.list("enseignants")
    Regex("\\d{5,8}").findAll(base).forEach { m -> all.firstOrNull { it.s("ppr").trim() == m.value }?.let { return it } }
    fun words(t: String) = norm(t).replace(Regex("[^a-z0-9\\u0600-\\u06ff]+"), " ").trim().split(" ").filter { it.length > 1 }
    val fb = " " + words(base).joinToString(" ") + " "
    val c = all.filter { e -> listOf(e.s("nom"), e.s("nomFr"), e.s("nomAr")).any { n -> val w = words(n); w.size >= 2 && w.all { fb.contains(" $it ") } } }
    return if (c.size == 1) c[0] else null
}

/** « Joindre des feuilles personnelles (lot) » : liste Enseignants. */
@Composable
fun FeuillesLotButton() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var plan by remember { mutableStateOf<List<Triple<Uri, String, JSONObject?>>>(emptyList()) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) plan = uris.map { u -> val n = Attach.nameOf(ctx, u); Triple(u, n, feuilleMatchEns(n)) }
    }
    OutlinedButton(onClick = { pick.launch(arrayOf("image/*", "application/pdf")) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Joindre des feuilles personnelles (lot)")
    }
    if (plan.isNotEmpty()) {
        val ok = plan.filter { it.third != null }
        val ko = plan.filter { it.third == null }
        AlertDialog(onDismissRequest = { plan = emptyList() }, title = { Text("Feuilles personnelles (lot)") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (ok.isEmpty()) Text("Aucun fichier reconnu : mettez le PPR ou le nom de l'enseignant(e) dans le nom du fichier (ex. 1408356_AGOUJIL_ZOUHIR.pdf).", fontSize = 13.5.sp)
                else Text("Joindre ${ok.size} feuille(s) :", fontSize = 13.5.sp)
                ok.forEach { Text("• ${it.second} → ${it.third!!.s("nom")}", fontSize = 12.5.sp) }
                if (ko.isNotEmpty()) {
                    Text("Non reconnu(s), ignoré(s) :", fontSize = 13.5.sp, modifier = Modifier.padding(top = 8.dp))
                    ko.forEach { Text("• ${it.second}", fontSize = 12.5.sp, color = InkFaint) }
                }
            }
        }, confirmButton = {
            if (ok.isNotEmpty()) TextButton(onClick = {
                val todo = ok; plan = emptyList()
                scope.launch {
                    val n = withContext(Dispatchers.IO) { todo.groupBy { it.third!!.s("id") }.entries.sumOf { (id, l) -> Attach.add(ctx, "enseignants", id, l.map { it.first }, "feuille_personnelle") } }
                    toast(ctx, "$n feuille(s) personnelle(s) jointe(s)." + if (DriveSync.enabled(ctx)) " Envoi sur Drive à la prochaine synchronisation." else "")
                }
            }) { BtnText("Joindre", maxLines = 1, softWrap = false) }
        }, dismissButton = { TextButton(onClick = { plan = emptyList() }) { BtnText("Annuler", maxLines = 1, softWrap = false) } })
    }
}

/**
 * Brouillon automatique d'un formulaire : sauvegardé après chaque modification, proposé à la réouverture
 * si l'application a été fermée avant l'enregistrement, effacé quand on quitte l'écran normalement.
 */
@Composable
fun DraftGuard(key: String, state: MutableState<JSONObject>, tick: Int, onRestored: () -> Unit, stillOpen: () -> Boolean) {
    val ctx = LocalContext.current
    var offer by remember(key) { mutableStateOf(Drafts.load(ctx, key)) }
    LaunchedEffect(tick) { if (offer == null && tick > 0) { delay(600); Drafts.save(ctx, key, state.value) } }
    DisposableEffect(key) { onDispose { if (!stillOpen()) Drafts.clear(ctx, key) } }
    val o = offer
    if (o != null) AlertDialog(onDismissRequest = {}, title = { Text("Reprendre le brouillon ?") },
        text = { Text("Une saisie non enregistrée a été retrouvée (« " + listOf(o.s("nom"), o.s("titre"), o.s("beneficiaireNom")).firstOrNull { it.isNotEmpty() }.orEmpty().ifEmpty { "sans titre" } + " »). La reprendre ?") },
        confirmButton = { TextButton(onClick = { state.value = JSONObject(o.toString()); offer = null; onRestored() }) { BtnText("Reprendre", maxLines = 1, softWrap = false) } },
        dismissButton = { TextButton(onClick = { Drafts.clear(ctx, key); offer = null }) { BtnText("Ignorer", maxLines = 1, softWrap = false) } })
}
