@file:OptIn(ExperimentalMaterial3Api::class)

package ma.carnet.inspection.natif.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ma.carnet.inspection.natif.data.*
import org.json.JSONObject
import java.io.File

val SETTINGS_TABS = linkedMapOf(
    "profil" to "Profil", "compte" to "Compte Google", "ia" to "Assistant IA", "apparence" to "Apparence",
    "modeles" to "Modèles de documents", "referentiel" to "Régions / DP", "inspectapp" to "InspectApp", "notifications" to "Notifications", "vacances" to "Vacances"
)

@Composable
fun SettingsScreen(initial: String) {
    var tab by remember { mutableStateOf(initial) }
    ScreenScaffold("Paramètres", { Nav.pop() }, actions = { HelpButton("parametres") }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SETTINGS_TABS.forEach { (k, l) -> FilterChip(selected = tab == k, onClick = { tab = k }, label = { Text(l) }) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
                when (tab) {
                    "compte" -> CompteTab()
                    "ia" -> IaTab()
                    "apparence" -> ApparenceTab()
                    "modeles" -> ModelesTab()
                    "referentiel" -> ReferentielTab()
                    "inspectapp" -> InspectAppTab()
                    "notifications" -> NotificationsTab()
                    "vacances" -> VacancesTab()
                    else -> ProfilTab()
                }
                Spacer(Modifier.height(60.dp))
            }
        }
    }
}

// ---------------- images du profil ----------------
@Composable
fun rememberImg(file: File, vararg keys: Any?): ImageBitmap? {
    val v by Store.version
    val gv by GoogleAccount.version
    return remember(v, gv, file.absolutePath, file.exists(), file.lastModified(), *keys) {
        if (!file.exists()) null else try { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() } catch (_: Throwable) { null }
    }
}

/** Photo de profil ronde (photo choisie, sinon photo du compte Google, sinon initiales). */
@Composable
fun ProfileAvatar(size: Int, onClick: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val p = Store.profil()
    val img = rememberImg(Docs.imgFile(ctx, "avatar")) ?: rememberImg(GoogleAccount.photoFile(ctx))
    val m = Modifier.size(size.dp).clip(CircleShape).border(2.dp, OnBrand.copy(alpha = 0.7f), CircleShape).let { if (onClick != null) it.clickable { onClick() } else it }
    if (img != null) Image(img, "Photo de profil", m, contentScale = ContentScale.Crop)
    else Box(m.background(BrandSoft), contentAlignment = Alignment.Center) {
        Text(initials(p.s("nomSignature").ifEmpty { p.s("nom") }.ifEmpty { GoogleAccount.name(ctx) }.ifEmpty { "?" }), color = Brand, fontWeight = FontWeight.Bold, fontSize = (size / 2.6).sp)
    }
}

@Composable
fun ImagePickField(key: String, label: String, square: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    val img = rememberImg(Docs.imgFile(ctx, key), tick)
    fun accept(bmp: android.graphics.Bitmap?) {
        if (bmp == null) return
        scope.launch {
            val bytes = withContext(Dispatchers.Default) { ProfileImages.toJpeg(bmp, square) }
            ProfileImages.set(ctx, key, bytes); tick++
            toast(ctx, "$label enregistré(e)." + if (DriveSync.enabled(ctx)) " Envoi sur Drive à la prochaine synchronisation." else "")
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) accept(try { ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } } catch (_: Throwable) { null })
    }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { b -> accept(b) }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        val shape = if (square) CircleShape else RoundedCornerShape(8.dp)
        Box(Modifier.size(64.dp).clip(shape).background(Track), contentAlignment = Alignment.Center) {
            if (img != null) Image(img, label, Modifier.fillMaxSize(), contentScale = if (square) ContentScale.Crop else ContentScale.Fit)
            else Icon(Icons.Default.Person, null, tint = InkFaint)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick = { pick.launch("image/*") }) { Text("Importer") }
                TextButton(onClick = { cam.launch(null) }) { Text("Appareil photo") }
                if (img != null) TextButton(onClick = { ProfileImages.remove(ctx, key); tick++ }) { Text("Retirer", color = Danger) }
            }
        }
    }
}

// ---------------- Profil ----------------
private val PROFIL_FIELDS = listOf(
    "nom" to "Nom complet", "grade" to "Grade", "doti" to "DOTI", "cin" to "CIN", "rib" to "RIB",
    "telephone" to "Téléphone", "email" to "Email", "dateRecrutement" to "Date de recrutement (AAAA-MM-JJ)",
    "dateCadre" to "Date d'accès au cadre (AAAA-MM-JJ)", "dateAffectation" to "Date d'affectation actuelle (AAAA-MM-JJ)",
    "matiere" to "Matière", "numRemuneration" to "Numéro de rémunération", "academieRegionale" to "Académie régionale",
    "province" to "Province / Nyaba", "nbEncadres" to "Nombre d'encadré(e)s",
    "nomSignature" to "Nom tel qu'il apparaît sur le rapport d'inspection", "nomAr" to "الاسم الكامل", "matiereAr" to "المادة",
    "cycleAr" to "السلك", "academieAr" to "الجهة", "provinceAr" to "المديرية الإقليمية", "piedPageAr" to "تذييل الرسائل (العنوان، الهاتف، البريد)"
)

@Composable
fun ProfilTab() {
    val ctx = LocalContext.current
    val v by Store.version
    val vals = remember { mutableStateMapOf<String, String>().apply { val p = Store.profil(); PROFIL_FIELDS.forEach { (k, _) -> put(k, p.s(k)) } } }
    SectionTitle("Photo et images des documents")
    CardBox {
        ImagePickField("avatar", "Photo de profil", true)
        ImagePickField("logo", "Logo / en-tête", false)
        ImagePickField("cachet", "Cachet", false)
        ImagePickField("signature", "Signature", false)
        Text("Ces images sont insérées dans les documents. Avec Google Drive connecté, elles sont partagées avec le carnet web.", color = InkFaint, fontSize = 12.sp)
    }
    SectionTitle("Informations")
    CardBox {
        Text("Ces informations sont insérées automatiquement dans les documents administratifs.", color = InkFaint, fontSize = 12.sp)
        PROFIL_FIELDS.forEach { (k, l) -> TextFieldRow(l, vals[k] ?: "", { vals[k] = it }, multiline = k == "piedPageAr") }
        Button(onClick = {
            val tpl = Store.root.optJSONObject("templates") ?: JSONObject().also { Store.root.put("templates", it) }
            val np = tpl.optJSONObject("profil") ?: JSONObject()
            vals.forEach { (k, x) -> np.put(k, x.trim()) }
            tpl.put("profil", np); tpl.put("updatedAt", Store.now()); Store.save()
            toast(ctx, "Profil enregistré.")
        }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text("Enregistrer le profil") }
    }
    v
}

// ---------------- Compte Google ----------------
@Composable
fun CompteTab() {
    GoogleAccountCard()
    DriveCard(SyncHolder.fn)
    SectionTitle("Données")
    MenuItem(Icons.Default.Refresh, "Importer / exporter un fichier", "Sauvegarde JSON du carnet web, export de vos données") { Nav.push(Scr.Data) }
}

@Composable
fun GoogleAccountCard() {
    val ctx = LocalContext.current
    val act = ctx.findActivity()
    val scope = rememberCoroutineScope()
    val gv by GoogleAccount.version
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    SectionTitle("Compte Google")
    CardBox {
        gv
        if (GoogleAccount.signedIn(ctx)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val img = rememberImg(GoogleAccount.photoFile(ctx))
                if (img != null) Image(img, null, Modifier.size(48.dp).clip(CircleShape), contentScale = ContentScale.Crop) else Avatar(GoogleAccount.name(ctx), Brand, 48)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(GoogleAccount.name(ctx), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(GoogleAccount.email(ctx), color = InkFaint, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = { SyncHolder.fn(true) }) { Text("Synchroniser") }
                OutlinedButton(onClick = { if (act != null) scope.launch { GoogleAccount.signOut(act); toast(ctx, "Compte Google déconnecté.") } }) { Text("Se déconnecter") }
            }
        } else {
            Text("Connectez-vous avec votre compte Google : vos données sont enregistrées sur votre Google Drive et partagées avec le carnet web.", fontSize = 13.sp)
            GoogleSignInButton(busy) {
                if (act == null) return@GoogleSignInButton
                busy = true; err = ""
                scope.launch {
                    try { GoogleAccount.signIn(act) } catch (e: Throwable) { err = signInError(e) }
                    busy = false
                    SyncHolder.fn(true)
                }
            }
            if (err.isNotEmpty()) Text(err, color = Danger, fontSize = 12.5.sp)
        }
    }
}

fun signInError(e: Throwable): String {
    val n = e.javaClass.simpleName
    return when {
        n.contains("Cancellation") -> "Connexion annulée."
        n.contains("NoCredential") -> "Aucun compte Google disponible sur ce téléphone, ou l'identifiant OAuth « Android » n'est pas encore créé."
        else -> e.message ?: n
    }
}

@Composable
fun GoogleSignInButton(busy: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Color(0xFF1F1F1F))) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else Box(Modifier.size(22.dp).clip(CircleShape).background(Color(0xFF4285F4)), contentAlignment = Alignment.Center) { Text("G", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        Spacer(Modifier.width(10.dp)); Text("Se connecter avec Google", fontWeight = FontWeight.SemiBold)
    }
}

// ---------------- Assistant IA ----------------
@Composable
fun IaTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var prov by remember { mutableStateOf(Ai.provider(ctx)) }
    var think by remember { mutableStateOf(Ai.reasoning(ctx)) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    fun test() {
        busy = true; msg = "Test en cours…"
        scope.launch { msg = try { Ai.generate(ctx, "Réponds uniquement par le mot : OK"); "Le moteur répond correctement." } catch (e: Throwable) { e.message ?: "Erreur" }; busy = false }
    }
    SectionTitle("Moteur de rédaction")
    CardBox {
        Ai.PROVIDERS.forEach { (k, l) ->
            Row(Modifier.fillMaxWidth().clickable { prov = k; Ai.setProvider(ctx, k); msg = "" }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = prov == k, onClick = { prov = k; Ai.setProvider(ctx, k); msg = "" }); Text(l, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Switch(checked = think, onCheckedChange = { think = it; Ai.setReasoning(ctx, it) })
            Text("Mode raisonnement (textes plus rigoureux, génération plus longue)", fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
        }
    }
    when (prov) {
        "cloud" -> CloudAiCard { test() }
        "server" -> ServerAiCard { test() }
        "device" -> DeviceAiCard { test() }
        else -> {
            SectionTitle("Google Gemini")
            CardBox {
                var key by remember { mutableStateOf(Ai.key(ctx)) }
                Text("Clé d'API Gemini gratuite (aistudio.google.com → Get API key). Elle reste sur ce téléphone. Gemini lit aussi les photos (import de fiches).", fontSize = 12.5.sp, color = InkFaint)
                SecretField("Clé Gemini", key) { key = it; Ai.setKey(ctx, it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { Ai.setKey(ctx, key); test() }, enabled = !busy && key.isNotBlank()) { Text("Enregistrer et tester") }
                    TextButton(onClick = { openUri(ctx, "https://aistudio.google.com/app/apikey") }) { Text("Obtenir une clé") }
                }
            }
        }
    }
    if (msg.isNotEmpty()) CardBox { Row(verticalAlignment = Alignment.CenterVertically) { if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Text(msg, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp)) } }
}

@Composable
fun SecretField(label: String, value: String, onChange: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = { IconButton(onClick = { show = !show }) { Icon(if (show) Icons.Default.Lock else Icons.Default.Search, if (show) "Masquer" else "Afficher") } },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
}

@Composable
private fun ModelPicker(models: List<String>, current: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    if (models.isEmpty()) return
    Box {
        OutlinedButton(onClick = { open = true }) { Text("Choisir parmi ${models.size} modèles", maxLines = 1) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            models.forEach { m -> DropdownMenuItem(text = { Text(m + if (m == current) "  ✓" else "", maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { onPick(m); open = false }) }
        }
    }
}

@Composable
fun CloudAiCard(test: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pid by remember { mutableStateOf(Ai.cloudPreset(ctx).id) }
    val pr = Ai.CLOUD.first { it.id == pid }
    var key by remember(pid) { mutableStateOf(Ai.cloudKey(ctx, pid)) }
    var model by remember(pid) { mutableStateOf(Ai.cloudModel(ctx, pid)) }
    var base by remember { mutableStateOf(Ai.cloudBase(ctx)) }
    var models by remember(pid) { mutableStateOf(listOf<String>()) }
    var err by remember { mutableStateOf("") }
    SectionTitle("Fournisseur d'IA en ligne")
    CardBox {
        SelectField("Fournisseur", Ai.CLOUD.associate { it.id to it.label + if (Ai.cloudKey(ctx, it.id).isNotBlank()) " ✓" else "" }, pid, { pid = it; Ai.setCloudPreset(ctx, it) })
        Text(pr.note, color = InkFaint, fontSize = 12.sp)
        if (pr.keyUrl.isNotEmpty()) TextButton(onClick = { openUri(ctx, pr.keyUrl) }) { Text("Créer une clé ${pr.label}") }
        if (pr.id == "custom") TextFieldRow("Adresse de l'API (…/v1)", base, { base = it; Ai.setCloudBase(ctx, it) })
        SecretField("Clé API ${pr.label}", key) { key = it; Ai.setCloudKey(ctx, pid, it) }
        TextFieldRow("Modèle", model, { model = it; Ai.setCloudModel(ctx, pid, it) })
        ModelPicker(models, model) { model = it; Ai.setCloudModel(ctx, pid, it) }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { err = ""; scope.launch { try { models = Ai.listModels(ctx, false); if (models.isEmpty()) err = "Aucun modèle listé." } catch (e: Throwable) { err = e.message ?: "Erreur" } } }) { Text("Lister les modèles") }
            Button(onClick = test) { Text("Tester") }
        }
        if (err.isNotEmpty()) Text(err, color = Danger, fontSize = 12.sp)
        Text("Les quotas gratuits sont fixés par chaque fournisseur. Si l'un est épuisé, passez à un autre : chaque clé reste enregistrée.", color = InkFaint, fontSize = 11.5.sp)
    }
}

@Composable
fun ServerAiCard(test: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(Ai.serverUrl(ctx)) }
    var model by remember { mutableStateOf(Ai.serverModel(ctx)) }
    var models by remember { mutableStateOf(listOf<String>()) }
    var err by remember { mutableStateOf("") }
    SectionTitle("Serveur IA local")
    CardBox {
        Text("Le carnet envoie ses demandes à un moteur IA qui tourne à côté : Ollama ou llama-server (Termux) sur ce téléphone, ou LM Studio / Ollama sur un ordinateur du même réseau Wi-Fi. Aucune donnée ne sort de chez vous.", fontSize = 12.5.sp, color = InkFaint)
        TextFieldRow("Adresse du serveur", url, { url = it; Ai.setServerUrl(ctx, it) })
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Ollama" to "http://127.0.0.1:11434", "llama-server" to "http://127.0.0.1:8080", "LM Studio" to "http://192.168.1.10:1234").forEach { (l, u) ->
                AssistChip(onClick = { url = u; Ai.setServerUrl(ctx, u) }, label = { Text(l) })
            }
        }
        TextFieldRow("Modèle (vide = celui chargé)", model, { model = it; Ai.setServerModel(ctx, it) })
        ModelPicker(models, model) { model = it; Ai.setServerModel(ctx, it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { err = ""; scope.launch { try { models = Ai.listModels(ctx, true) } catch (e: Throwable) { err = e.message ?: "Serveur injoignable" } } }) { Text("Lister les modèles") }
            Button(onClick = test) { Text("Tester") }
        }
        if (err.isNotEmpty()) Text(err, color = Danger, fontSize = 12.sp)
        Text("Ollama sur ordinateur : lancez-le avec OLLAMA_HOST=0.0.0.0 et OLLAMA_ORIGINS=* puis indiquez l'adresse IP de l'ordinateur. Un modèle « Qwen 3 » permet le mode raisonnement.", color = InkFaint, fontSize = 11.5.sp)
    }
}

@Composable
fun DeviceAiCard(test: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(Ai.deviceModelName(ctx)) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val f = Ai.deviceModelFile(ctx)
    suspend fun install(open: () -> java.io.InputStream?, label: String, total: Long) {
        busy = true; progress = "Installation…"
        try {
            withContext(Dispatchers.IO) {
                f.parentFile?.mkdirs(); val tmp = File(f.parentFile, "tmp.task")
                open()?.use { inp -> tmp.outputStream().use { out ->
                    val buf = ByteArray(1 shl 16); var n: Int; var done = 0L
                    while (inp.read(buf).also { n = it } > 0) { out.write(buf, 0, n); done += n; if (done % (8 shl 20) < (1 shl 16)) withContext(Dispatchers.Main) { progress = "Installation… " + (done shr 20) + " Mo" + if (total > 0) " / ${total shr 20} Mo" else "" } }
                } } ?: throw Exception("Fichier illisible.")
                OnDevice.close(); f.delete(); tmp.renameTo(f)
            }
            Ai.setDeviceModelName(ctx, label); name = label; progress = "Modèle installé (${f.length() shr 20} Mo)."
        } catch (e: Throwable) { progress = "Échec : " + (e.message ?: e.javaClass.simpleName) }
        busy = false
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val label = uri.lastPathSegment?.substringAfterLast('/') ?: "modèle"
            install({ ctx.contentResolver.openInputStream(uri) }, label, -1)
        }
    }
    SectionTitle("IA sur le téléphone (hors ligne)")
    CardBox {
        Text("Le texte est rédigé par un petit modèle installé sur le téléphone, sans Internet (moteur MediaPipe de Google). Téléchargez un modèle au format .task (par exemple Gemma 3 1B ou Qwen 2.5 1.5B, dossier « litert-community » sur Hugging Face), puis importez-le ici. Comptez 0,5 à 2 Go et un téléphone récent.", fontSize = 12.5.sp, color = InkFaint)
        InfoRow("Modèle", if (f.exists()) "$name (${f.length() shr 20} Mo)" else "Aucun")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { pick.launch(arrayOf("*/*")) }, enabled = !busy) { Text("Importer un fichier .task") }
            OutlinedButton(onClick = { openUri(ctx, "https://huggingface.co/litert-community") }) { Text("Trouver un modèle") }
        }
        TextFieldRow("Ou adresse de téléchargement directe (.task)", url, { url = it })
        OutlinedButton(enabled = !busy && url.startsWith("http"), onClick = {
            scope.launch {
                val c = withContext(Dispatchers.IO) { (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply { instanceFollowRedirects = true; connectTimeout = 20000; readTimeout = 60000 } }
                install({ c.inputStream }, url.substringAfterLast('/').substringBefore('?'), withContext(Dispatchers.IO) { try { c.contentLengthLong } catch (_: Throwable) { -1L } })
            }
        }) { Text("Télécharger") }
        if (progress.isNotEmpty()) Text(progress, fontSize = 12.5.sp, color = if (progress.startsWith("Échec")) Danger else Brand)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = test, enabled = f.exists() && !busy) { Text("Tester") }
            if (f.exists()) TextButton(onClick = { OnDevice.close(); f.delete(); name = ""; Ai.setDeviceModelName(ctx, ""); progress = "Modèle supprimé." }) { Text("Supprimer le modèle", color = Danger) }
        }
        Text("L'IA du téléphone ne lit pas les photos : l'import de fiches par photo utilise Gemini.", color = InkFaint, fontSize = 11.5.sp)
    }
}

// ---------------- Apparence ----------------
@Composable
fun ApparenceTab() {
    val ctx = LocalContext.current
    fun save() = ThemeState.save(ctx)
    SectionTitle("Thème de l'application")
    SKINS.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { sk ->
                val sel = ThemeState.skin.value == sk.k
                Card(onClick = { ThemeState.skin.value = sk.k; save() }, modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = sk.surface), border = if (sel) androidx.compose.foundation.BorderStroke(2.5.dp, sk.brand) else null) {
                    Column(Modifier.padding(10.dp)) {
                        Row(Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(6.dp)).background(sk.bg), verticalAlignment = Alignment.CenterVertically) {
                            listOf(sk.brand, sk.accent2, sk.ink).forEach { c -> Box(Modifier.padding(start = 6.dp).size(14.dp).clip(CircleShape).background(c)) }
                        }
                        Text(sk.label + if (sel) "  ✓" else "", color = sk.ink, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, modifier = Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(sk.desc, color = sk.inkFaint, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    if (ThemeState.skin.value == "classique") {
        SectionTitle("Mode du thème classique")
        ChipRow(mapOf("" to "Automatique", "light" to "Clair", "dark" to "Sombre"), ThemeState.mode.value, { ThemeState.mode.value = it; save() }, null)
    }
    SectionTitle("Couleur d'accent")
    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.padding(4.dp).size(34.dp).clip(CircleShape).background(Track).border(if (ThemeState.accent.value.isEmpty()) 3.dp else 0.dp, Ink, CircleShape).clickable { ThemeState.accent.value = ""; save() }, contentAlignment = Alignment.Center) { Text("A", fontWeight = FontWeight.Bold) }
        ACCENTS.forEach { h ->
            Box(Modifier.padding(4.dp).size(34.dp).clip(CircleShape).background(hexToColor(h)).border(if (ThemeState.accent.value.equals(h, true)) 3.dp else 0.dp, Ink, CircleShape).clickable { ThemeState.accent.value = h; save() })
        }
    }
    SectionTitle("Taille du texte")
    ChipRow(mapOf("0.9" to "Petite", "1.0" to "Normale", "1.15" to "Grande", "1.3" to "Très grande"), "%.2f".format(java.util.Locale.US, ThemeState.scale.floatValue).trimEnd('0').let { if (it.endsWith(".")) it + "0" else it },
        { ThemeState.scale.floatValue = it.toFloatOrNull() ?: 1f; save() }, null)
    SectionTitle("Coins")
    ChipRow(mapOf("droit" to "Droits", "" to "Du thème", "rond" to "Très arrondis"), ThemeState.corners.value, { ThemeState.corners.value = it; save() }, null)
    TextButton(onClick = { ThemeState.reset(ctx) }, modifier = Modifier.padding(top = 10.dp)) { Text("Rétablir l'apparence par défaut") }
    Text("Ces réglages ne concernent que ce téléphone et n'affectent pas les documents imprimés ou exportés.", color = InkFaint, fontSize = 12.sp)
    FontsSection()
}
