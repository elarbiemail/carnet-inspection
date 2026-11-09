package ma.carnet.inspection.natif.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import ma.carnet.inspection.natif.data.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ma.carnet.inspection.natif.data.Photos
import ma.carnet.inspection.natif.data.SharedPhoto
import ma.carnet.inspection.natif.data.waNumber
import ma.carnet.inspection.natif.data.Store

/** Bouton « Photo de profil » : galerie (photo WhatsApp enregistrée) ou contacts du téléphone. */
@Composable
fun PhotoButton(id: String, tel: String, name: String = "") {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    fun done(ok: Boolean) { toast(ctx, if (ok) "Photo enregistrée." else "Photo introuvable ou illisible."); if (ok) Store.version.intValue++ }
    val cam = rememberCamera { b ->
        if (b != null) try {
            val f = java.io.File(java.io.File(ctx.cacheDir, "partages").also { it.mkdirs() }, "photo_" + System.currentTimeMillis() + ".jpg")
            f.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
            SharedPhoto.target = id; SharedPhoto.targetName = name; SharedPhoto.pending.value = f
        } catch (_: Exception) { toast(ctx, "Photo illisible.") }
    }
    // image de la galerie (ou capture d'écran WhatsApp) : recadrage avant enregistrement
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) try {
            val f = java.io.File(java.io.File(ctx.cacheDir, "partages").also { it.mkdirs() }, "photo_" + System.currentTimeMillis() + ".jpg")
            ctx.contentResolver.openInputStream(u)?.use { i -> f.outputStream().use { i.copyTo(it) } }
            SharedPhoto.target = id; SharedPhoto.targetName = name; SharedPhoto.pending.value = f
        } catch (_: Exception) { toast(ctx, "Image illisible.") }
    }
    fun fromContacts() {
        val u = Photos.contactPhotoUri(ctx, tel)
        if (u == null) toast(ctx, "Aucune photo pour ce numéro dans vos contacts. Enregistrez le contact avec sa photo, ou choisissez-la dans la galerie.")
        else done(Photos.save(ctx, id, u))
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) fromContacts() else toast(ctx, "Autorisation des contacts refusée.") }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.padding(top = 4.dp)) { Text(if (Photos.has(ctx, id)) "Changer la photo" else "Importer la photo") }
    if (open) AlertDialog(onDismissRequest = { open = false },
        title = { Text("Photo de profil") },
        text = { Text("WhatsApp interdit aux autres applications de lire la photo de profil d'un contact, et en bloque même la capture d'écran. Solutions : demander à la personne de vous envoyer une photo sur WhatsApp (une photo reçue dans la discussion peut être partagée vers « Carnet natif »), reprendre la photo enregistrée dans vos contacts, prendre une photo ou en choisir une dans la galerie. Vous recadrez ensuite le visage dans le cercle.") },
        confirmButton = { Column {
            if (tel.isNotBlank()) TextButton(onClick = {
                open = false
                SharedPhoto.target = id; SharedPhoto.targetName = name
                // la photo de profil WhatsApp n'est pas accessible : on demande à la personne d'envoyer une photo,
                // qui, elle, peut être partagée depuis la discussion vers le Carnet
                whatsapp(ctx, tel, greeting(name).trimEnd() + " Pourriez-vous m'envoyer ici une photo de vous (portrait) pour votre fiche ? Merci.")
                toast(ctx, "Quand la photo arrive : appuyez longuement dessus dans WhatsApp → Partager → Carnet natif.")
            }) { Text("Demander sa photo par WhatsApp") }
            TextButton(onClick = { open = false; cam() }) { Text("Prendre une photo") }
            TextButton(onClick = { open = false; pick.launch("image/*") }) { Text("Choisir dans la galerie (ou une capture d'écran)") }
            if (tel.isNotBlank()) TextButton(onClick = {
                open = false
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) fromContacts() else perm.launch(Manifest.permission.READ_CONTACTS)
            }) { Text("Depuis mes contacts") }
            if (Photos.has(ctx, id)) TextButton(onClick = { open = false; Photos.remove(ctx, id); Store.version.intValue++ }) { Text("Supprimer la photo") }
        } },
        dismissButton = { TextButton(onClick = { open = false }) { Text("Fermer") } })
}


/** Photo reçue par « Partager » (capture d'écran WhatsApp, galerie…) : recadrage, puis attribution à la fiche. */
@Composable
fun SharedPhotoDialog() {
    val ctx = LocalContext.current
    val f = SharedPhoto.pending.value ?: return
    val src = remember(f) { try { decode(f, 1600) } catch (_: Throwable) { null } }
    var who by remember(f) { mutableStateOf(SharedPhoto.target) }
    var whoName by remember(f) { mutableStateOf(SharedPhoto.targetName) }
    var pickWho by remember(f) { mutableStateOf(false) }
    if (src == null) { LaunchedEffect(f) { toast(ctx, "Image illisible."); SharedPhoto.pending.value = null }; return }
    if (pickWho) {
        val items = remember(f) {
            (Store.list("enseignants").map { org.json.JSONObject().put("id", it.s("id")).put("nom", it.s("nom")).put("sub", "Enseignant(e) · " + etabName(it.s("etablissementId"))) } +
                Store.list("etablissements").filter { it.s("directeur").isNotEmpty() }.map { org.json.JSONObject().put("id", it.s("id")).put("nom", it.s("directeur")).put("sub", "Directeur(trice) · " + it.s("nom")) })
                .sortedBy { norm(it.s("nom")) }
        }
        PickerDialog("À qui attribuer cette photo ?", items, { it.s("nom") }, { it.s("sub") }, onDismiss = { pickWho = false }) { p -> if (p != null) { who = p.s("id"); whoName = p.s("nom") }; pickWho = false }
        return
    }
    // recadrage : pincer pour zoomer, glisser pour placer le visage dans le cercle
    var scale by remember(f) { mutableFloatStateOf(1f) }
    var off by remember(f) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val img = remember(src) { src.asImageBitmap() }
    val frameDp = 260.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val framePx = with(density) { frameDp.toPx() }
    val base = framePx / minOf(src.width, src.height)          // image ajustée pour couvrir le cadre
    val st = androidx.compose.foundation.gestures.rememberTransformableState { z, p, _ -> scale = (scale * z).coerceIn(1f, 8f); off += p }
    fun crop(): android.graphics.Bitmap {
        val k = base * scale                                        // pixels écran par pixel image
        val cx = src.width / 2f - off.x / k; val cy = src.height / 2f - off.y / k
        val half = framePx / 2f / k
        val l = (cx - half).toInt().coerceIn(0, src.width - 1); val t = (cy - half).toInt().coerceIn(0, src.height - 1)
        val side = (2 * half).toInt().coerceAtMost(minOf(src.width - l, src.height - t)).coerceAtLeast(1)
        return android.graphics.Bitmap.createBitmap(src, l, t, side, side)
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = { SharedPhoto.pending.value = null }) {
        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text("Photo de profil", style = MaterialTheme.typography.titleMedium)
                Text("Pincez pour zoomer, glissez pour placer le visage dans le cercle.", color = InkFaint, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
                Box(Modifier.size(frameDp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).background(androidx.compose.ui.graphics.Color.Black)
                    .then(Modifier.transformable(st)), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    val w = with(density) { (src.width * base).toDp() }; val h = with(density) { (src.height * base).toDp() }
                    androidx.compose.foundation.Image(img, null, Modifier.requiredSize(w, h).graphicsLayer(scaleX = scale, scaleY = scale, translationX = off.x, translationY = off.y), contentScale = androidx.compose.ui.layout.ContentScale.FillBounds)
                    Box(Modifier.size(frameDp).border(3.dp, androidx.compose.ui.graphics.Color.White, androidx.compose.foundation.shape.CircleShape))
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                    Text("Pour : " + whoName.ifEmpty { "(à choisir)" }, Modifier.weight(1f), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1)
                    TextButton(onClick = { pickWho = true }) { Text(if (who == null) "Choisir" else "Changer") }
                }
                Row {
                    TextButton(onClick = { SharedPhoto.pending.value = null }) { Text("Annuler") }
                    Button(enabled = who != null, onClick = { val ok = try { SharedPhoto.assignBitmap(ctx, who!!, crop()) } catch (_: Throwable) { false }; toast(ctx, if (ok) "Photo de profil actualisée." else "Recadrage impossible.") }) { Text("Enregistrer") }
                }
            }
        }
    }
}
