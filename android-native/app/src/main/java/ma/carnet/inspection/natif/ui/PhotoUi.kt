package ma.carnet.inspection.natif.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ma.carnet.inspection.natif.data.Photos
import ma.carnet.inspection.natif.data.Store

/** Bouton « Photo de profil » : galerie (photo WhatsApp enregistrée) ou contacts du téléphone. */
@Composable
fun PhotoButton(id: String, tel: String) {
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
            TextButton(onClick = { open = false; pick.launch("image/*") }) { Text("Choisir dans la galerie") }
            if (tel.isNotBlank()) TextButton(onClick = {
                open = false
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) fromContacts() else perm.launch(Manifest.permission.READ_CONTACTS)
            }) { Text("Depuis mes contacts") }
            if (Photos.has(ctx, id)) TextButton(onClick = { open = false; Photos.remove(ctx, id); Store.version.intValue++ }) { Text("Supprimer la photo") }
        } },
        dismissButton = { TextButton(onClick = { open = false }) { Text("Fermer") } })
}
