@file:OptIn(ExperimentalMaterial3Api::class)

package ma.carnet.inspection.natif.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch
import ma.carnet.inspection.natif.data.DriveSync
import ma.carnet.inspection.natif.data.GoogleAccount
import ma.carnet.inspection.natif.data.fmtDate

object SyncHolder {
    var fn: (Boolean) -> Unit = {}
    /** Exécute une action Drive avec un jeton (autorisation demandée au besoin). */
    var withToken: (String, suspend (String) -> Unit) -> Unit = { _, _ -> }
}

object SyncState {
    val busy = mutableStateOf(false)
    val msg = mutableStateOf("")
    val ok = mutableStateOf(true)
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

fun syncErrorText(e: Throwable): String = when {
    e is ApiException && e.statusCode == 10 ->
        "Google refuse la connexion : l'identifiant OAuth « Android » n'est pas encore créé (ou pas dans le bon projet). Voir Données → Google Drive."
    e is ApiException && e.statusCode == 16 -> "Connexion annulée par Google. Si vous n'avez rien annulé vous-même, l'identifiant OAuth « Android » manque dans Google Cloud (voir Paramètres → Compte Google)."
    e is ApiException && e.statusCode == 7 -> "Pas de connexion Internet."
    else -> e.message ?: e.toString()
}

/** Renvoie une fonction qui lance la synchronisation Drive (avec demande d'autorisation si besoin). */
@Composable
fun rememberDriveSyncer(): (Boolean) -> Unit {
    val ctx = LocalContext.current
    val act = ctx.findActivity()
    val scope = rememberCoroutineScope()
    val pending = remember { arrayOfNulls<suspend (String) -> Unit>(1) }
    suspend fun syncWith(token: String) {
        SyncState.msg.value = "Synchronisation avec Google Drive…"
        try { GoogleAccount.refresh(ctx, token) } catch (_: Throwable) {}
        val r = DriveSync.sync(ctx, token)
        DriveSync.setEnabled(ctx, true)
        if (ma.carnet.inspection.natif.data.Exchange.auto(ctx)) try { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ma.carnet.inspection.natif.data.Exchange.driveSync(ctx, token, true) } } catch (_: Throwable) {}
        SyncState.ok.value = true
        SyncState.msg.value = (if (r.created) "Sauvegarde créée sur Google Drive." else "Synchronisé avec Google Drive.") +
            (r.imported?.let { if (it.added + it.updated + it.removed > 0) " Reçu : ${it.added} ajout(s), ${it.updated} mise(s) à jour, ${it.removed} suppression(s)." else "" } ?: "")
    }
    suspend fun exec(token: String, action: suspend (String) -> Unit) {
        try { SyncState.busy.value = true; action(token) }
        catch (e: Throwable) { SyncState.ok.value = false; SyncState.msg.value = syncErrorText(e) }
        finally { SyncState.busy.value = false }
    }
    fun openWebAuth(action: suspend (String) -> Unit) {
        SyncState.msg.value = "Connexion à Google dans le navigateur…"
        ma.carnet.inspection.natif.data.WebAuth.pending = { tk, err ->
            if (tk != null) scope.launch { exec(tk, action) }
            else { SyncState.busy.value = false; SyncState.ok.value = false; SyncState.msg.value = "Connexion Google refusée (" + (err ?: "annulée") + ")." }
        }
        val u = android.net.Uri.parse(DriveSync.webAuthUrl(ctx, DriveSync.forceChoose).also { DriveSync.forceChoose = false })
        // Chrome de préférence : la fenêtre de choix du compte Google y fonctionne de façon fiable
        try { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, u).setPackage("com.android.chrome").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Throwable) { try { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, u).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Throwable) { SyncState.busy.value = false; SyncState.ok.value = false; SyncState.msg.value = "Aucun navigateur pour la connexion Google." } }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val action = pending[0]; pending[0] = null
        if (res.resultCode == Activity.RESULT_OK && act != null && action != null) {
            scope.launch {
                try { val tk = DriveSync.tokenFromIntent(act, res.data); exec(tk, action) }
                catch (e: Throwable) { SyncState.ok.value = false; SyncState.msg.value = syncErrorText(e); SyncState.busy.value = false }
            }
        } else if (action != null && DriveSync.clientId(ctx).isNotBlank()) { DriveSync.setAuthMode(ctx, "web"); openWebAuth(action) }
        else { SyncState.busy.value = false; SyncState.ok.value = false; SyncState.msg.value = "Autorisation Google Drive annulée." }
    }
    fun start(label: String, interactive: Boolean, action: suspend (String) -> Unit) {
        if (act == null || SyncState.busy.value) return
        scope.launch {
            try {
                SyncState.busy.value = true; SyncState.msg.value = label
                when (val a = DriveSync.authorize(act)) {
                    is DriveSync.Auth.Ok -> exec(a.token, action)
                    is DriveSync.Auth.NeedsWeb -> {
                        if (!interactive) { SyncState.busy.value = false; SyncState.ok.value = false; SyncState.msg.value = "Touchez « Synchroniser » pour vous reconnecter à Google (par le navigateur)."; return@launch }
                        openWebAuth(action)
                    }
                    is DriveSync.Auth.NeedsConsent -> {
                        val pi = a.result.pendingIntent
                        if (interactive && pi != null) { pending[0] = action; launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build()) }
                        else { SyncState.busy.value = false; SyncState.ok.value = false; SyncState.msg.value = "Touchez « Synchroniser » pour autoriser Google Drive." }
                    }
                }
            } catch (e: Throwable) { SyncState.busy.value = false; SyncState.ok.value = false; SyncState.msg.value = syncErrorText(e) }
        }
    }
    SyncHolder.withToken = { label, action -> start(label, true, action) }
    // jeton reçu du navigateur alors que l'écran avait été recréé : on lance la synchronisation
    LaunchedEffect(ma.carnet.inspection.natif.data.WebAuth.orphan.intValue) {
        if (ma.carnet.inspection.natif.data.WebAuth.orphan.intValue > 0) { ma.carnet.inspection.natif.data.WebAuth.orphan.intValue = 0; start("Synchronisation…", false) { syncWith(it) } }
    }
    return { interactive -> start("Connexion à Google…", interactive) { syncWith(it) } }
}

/** Bandeau d'état de synchronisation (accueil). */
@Composable
fun SyncStatusLine(sync: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val busy by SyncState.busy
    val msg by SyncState.msg
    if (!DriveSync.enabled(ctx) && msg.isEmpty()) return
    CardBox {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Refresh, null, tint = if (SyncState.ok.value) Brand else Danger)
            Text(msg.ifEmpty { "Google Drive : dernière synchronisation " + DriveSync.lastSync(ctx).let { if (it.isEmpty()) "jamais" else fmtDate(it) + " " + it.substring(11, 16) } },
                fontSize = 12.5.sp, modifier = Modifier.weight(1f).padding(horizontal = 10.dp), color = if (SyncState.ok.value) InkFaint else Danger)
            TextButton(onClick = { sync(true) }, enabled = !busy) { Text("Synchroniser", maxLines = 1, softWrap = false) }
        }
    }
}

/** Carte « Google Drive » de l'écran Données : identifiant client, guide de configuration, connexion. */
@Composable
fun DriveCard(sync: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    var cid by remember { mutableStateOf(DriveSync.clientId(ctx)) }
    var enabled by remember { mutableStateOf(DriveSync.enabled(ctx)) }
    val busy by SyncState.busy
    val msg by SyncState.msg
    val proj = DriveSync.projectNumber(cid)
    SectionTitle("Google Drive (synchronisation directe)")
    CardBox {
        Text("Synchronise l'application avec la même sauvegarde Google Drive que le carnet web (« ${DriveSync.APP_FOLDER} / ${DriveSync.BACKUP_FOLDER} / ${DriveSync.BACKUP_NAME} »).", fontSize = 13.5.sp)
        OutlinedTextField(value = cid, onValueChange = { cid = it; DriveSync.setClientId(ctx, it) }, singleLine = true,
            label = { Text("Identifiant client Google (celui du carnet web)") }, placeholder = { Text("123456789-xxxx.apps.googleusercontent.com") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        Text("Il se trouve dans le carnet web : Paramètres → Synchronisation, ou dans Google Cloud → API et services → Identifiants.", color = InkFaint, fontSize = 11.5.sp, modifier = Modifier.padding(top = 4.dp))

        SectionTitle("Configuration unique dans Google Cloud")
        Text("1. Ouvrez Google Cloud → API et services → Identifiants" + (if (proj.isNotEmpty()) ", dans le projet n° $proj (celui de votre identifiant client)." else ", dans le projet de votre identifiant client web."), fontSize = 13.sp)
        Text("2. « Créer des identifiants » → « ID client OAuth » → type « Android ».", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Text("3. Renseignez exactement :", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        CopyRow("Nom du package", DriveSync.PACKAGE) { clip.setText(AnnotatedString(it)); toast(ctx, "Copié.") }
        CopyRow("Empreinte SHA-1", DriveSync.SHA1) { clip.setText(AnnotatedString(it)); toast(ctx, "Copié.") }
        Text("4. Enregistrez, attendez quelques minutes, puis touchez « Connecter et synchroniser ». L'API Google Drive est déjà activée pour le carnet web.", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { openUri(ctx, "https://console.cloud.google.com/apis/credentials" + (if (proj.isNotEmpty()) "?project=$proj" else "")) }) { Text("Ouvrir Google Cloud", maxLines = 1, softWrap = false) }
        }
        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { sync(true); enabled = true }, enabled = !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = OnBrand) else Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(6.dp)); Text(if (enabled) "Synchroniser" else "Connecter et synchroniser")
            }
            if (enabled) OutlinedButton(onClick = { DriveSync.forget(ctx); enabled = false; SyncState.msg.value = "Synchronisation automatique désactivée." }) { Text("Déconnecter", maxLines = 1, softWrap = false) }
        }
        if (enabled) Text("Synchronisation automatique à l'ouverture de l'application. Dernière : " + DriveSync.lastSync(ctx).let { if (it.isEmpty()) "jamais" else fmtDate(it) + " " + it.substring(11, 16) },
            color = InkFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        if (msg.isNotEmpty()) Text(msg, color = if (SyncState.ok.value) Brand else Danger, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun CopyRow(label: String, value: String, onCopy: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = InkFaint, fontSize = 11.5.sp)
            Text(value, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        IconButton(onClick = { onCopy(value) }) { Icon(Icons.Default.Share, "Copier") }
    }
}
