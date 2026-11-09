package ma.carnet.inspection.natif.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> if (u != null) done(Photos.save(ctx, id, u)) }
    fun fromContacts() {
        val u = Photos.contactPhotoUri(ctx, tel)
        if (u == null) toast(ctx, "Aucune photo pour ce numéro dans vos contacts. Enregistrez le contact avec sa photo, ou choisissez-la dans la galerie.")
        else done(Photos.save(ctx, id, u))
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) fromContacts() else toast(ctx, "Autorisation des contacts refusée.") }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.padding(top = 4.dp)) { Text(if (Photos.has(ctx, id)) "Changer la photo" else "Importer la photo") }
    if (open) AlertDialog(onDismissRequest = { open = false },
        title = { Text("Photo de profil") },
        text = { Text("WhatsApp ne permet pas à une autre application de lire la photo d'un contact. Deux possibilités : choisir dans la galerie la photo que vous avez enregistrée depuis WhatsApp (ouvrez la photo de profil, puis « Enregistrer »), ou reprendre la photo du contact enregistré dans votre téléphone.") },
        confirmButton = { Column {
            if (tel.isNotBlank()) TextButton(onClick = {
                open = false
                SharedPhoto.target = id; SharedPhoto.targetName = name
                toast(ctx, "Dans WhatsApp : touchez la photo du contact, puis Partager → Carnet natif.")
                val n = waNumber(tel)
                try { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/$n")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (_: Exception) { toast(ctx, "WhatsApp n'est pas installé.") }
            }) { Text("Depuis WhatsApp (actualiser)") }
            TextButton(onClick = { open = false; pick.launch("image/*") }) { Text("Choisir dans la galerie") }
            if (tel.isNotBlank()) TextButton(onClick = {
                open = false
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) fromContacts() else perm.launch(Manifest.permission.READ_CONTACTS)
            }) { Text("Depuis mes contacts") }
            if (Photos.has(ctx, id)) TextButton(onClick = { open = false; Photos.remove(ctx, id); Store.version.intValue++ }) { Text("Supprimer la photo") }
        } },
        dismissButton = { TextButton(onClick = { open = false }) { Text("Fermer") } })
}


/** Photo reçue par « Partager » (WhatsApp…) : attribution à la fiche de départ, ou choix de la personne / de l'établissement. */
@Composable
fun SharedPhotoDialog() {
    val ctx = LocalContext.current
    val f = SharedPhoto.pending.value ?: return
    var pickWho by remember(f) { mutableStateOf(SharedPhoto.target == null) }
    val bmp = remember(f) { try { android.graphics.BitmapFactory.decodeFile(f.absolutePath, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap() } catch (_: Throwable) { null } }
    fun done(id: String) { toast(ctx, if (SharedPhoto.assign(ctx, id)) "Photo de profil actualisée." else "Photo illisible.") }
    if (!pickWho) AlertDialog(onDismissRequest = { SharedPhoto.pending.value = null },
        title = { Text("Actualiser la photo de profil") },
        text = { Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            bmp?.let { androidx.compose.foundation.Image(it, null, Modifier.size(120.dp).clip(androidx.compose.foundation.shape.CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
            Text("Attribuer cette photo à " + SharedPhoto.targetName.ifEmpty { "la fiche ouverte" } + " ?", modifier = Modifier.padding(top = 8.dp))
        } },
        confirmButton = { TextButton(onClick = { done(SharedPhoto.target!!) }) { Text("Oui") } },
        dismissButton = { Row { TextButton(onClick = { pickWho = true }) { Text("Autre personne") }; TextButton(onClick = { SharedPhoto.pending.value = null }) { Text("Annuler") } } })
    else {
        val people = remember(f) {
            Store.list("enseignants").map { Triple(it.s("id"), it.s("nom"), "Enseignant(e) · " + etabName(it.s("etablissementId"))) } +
                Store.list("etablissements").filter { it.s("directeur").isNotEmpty() }.map { Triple(it.s("id"), it.s("directeur"), "Directeur(trice) · " + it.s("nom")) }
        }.sortedBy { norm(it.second) }
        val items = people.map { org.json.JSONObject().put("id", it.first).put("nom", it.second).put("sub", it.third) }
        PickerDialog("À qui attribuer cette photo ?", items, { it.s("nom") }, { it.s("sub") }, onDismiss = { SharedPhoto.pending.value = null }) { it?.let { p -> done(p.s("id")) } }
    }
}
