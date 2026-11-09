@file:OptIn(ExperimentalMaterial3Api::class)

package ma.carnet.inspection.natif.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.view.drawToBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/** Une rubrique d'aide : étapes numérotées, illustrées par la capture réelle de l'écran concerné. */
data class HelpTopic(val id: String, val title: String, val screen: String, val steps: List<String>, val open: Scr? = null)

val HELP = listOf(
    HelpTopic("demarrer", "Bien démarrer", "accueil", listOf(
        "Plus → Paramètres → Profil : nom (français et arabe), grade, matière, académie, direction provinciale, puis photo, logo, cachet et signature.",
        "Plus → Paramètres → Compte Google : « Se connecter avec Google ». Vos données restent sur le téléphone et sont sauvegardées sur votre Drive (même fichier que le carnet web).",
        "Créez vos zones, puis vos établissements et enseignant(e)s (saisie ou import d'une sauvegarde / d'un CSV).",
        "Planifiez vos tâches avec le bouton + et produisez leurs documents (aperçu, PDF, Word).")),
    HelpTopic("accueil", "L'accueil", "accueil", listOf(
        "En haut : votre photo de profil (touchez-la pour la changer), la date et le filtre de zones.",
        "Les chiffres clés (à venir, en retard, réalisées) ouvrent la liste correspondante.",
        "Le bandeau Google Drive indique la dernière synchronisation ; « Synchroniser » envoie et récupère les modifications."), Scr.Tab(0)),
    HelpTopic("taches", "Les tâches", "taches", listOf(
        "Filtrez par statut ou par type avec les puces, cherchez par mot-clé.",
        "Le bouton + crée une tâche : type, date, heure, lieu, établissement, enseignant(e), invité(e)s.",
        "Une tâche prévue pendant des vacances est signalée, et un rappel est envoyé avant son début (Paramètres → Notifications)."), Scr.Tab(1)),
    HelpTopic("tache", "Fiche d'une tâche et documents", "tache", listOf(
        "Touchez une étape du suivi (À planifier → Réalisée → Rapport…) pour faire avancer la tâche.",
        "« Nouveau document » : choisissez le document (liste de présence, compte rendu, rapport d'inspection, lettre, attestation…).",
        "Dans le formulaire, « Rédiger avec l'IA » rédige chaque rubrique à partir des notes de la tâche.",
        "Pièces jointes : importez des fichiers (photo, PDF, Word…) et ouvrez-les dans la liseuse.")),
    HelpTopic("document", "Produire un document", "document", listOf(
        "L'aperçu montre le document au format A4, avec l'en-tête, la signature et le cachet.",
        "« PDF » enregistre ou partage un PDF ; « Word » produit un fichier .docx modifiable ; « Drive » l'enregistre dans « Documents générés ».",
        "Les cases des formulaires officiels ont une taille limitée : un texte trop long est tronqué (…) et signalé dans le formulaire.")),
    HelpTopic("enseignants", "Enseignant(e)s", "enseignants", listOf(
        "Cherchez par nom, PPR ou établissement ; « Par horaire » trouve qui enseigne à un jour et une heure donnés.",
        "La fiche montre les notes d'inspection, l'emploi du temps et les tâches ; touchez le téléphone pour appeler ou écrire sur WhatsApp.",
        "« Importer une fiche (IA) » lit la photo d'une fiche personnelle et pré-remplit la fiche."), Scr.Tab(2)),
    HelpTopic("etablissements", "Établissements", "etablissements", listOf(
        "Les établissements sont groupés par zone. La fiche donne le directeur, son téléphone, la carte et les enseignant(e)s.",
        "« Emploi du temps » produit l'emploi du temps hebdomadaire de l'établissement (PDF, Word, Excel)."), Scr.Tab(3)),
    HelpTopic("modeles", "Modèles de documents et balises", "modeles", listOf(
        "Paramètres → Modèles : créez un modèle (vierge, émargement, compte rendu IA, استدعاء) ou modifiez les textes de départ des documents.",
        "Touchez une balise pour l'insérer à l'emplacement du curseur : {{profil.nom}}, {{tache.date}}, {{etablissement.directeur}}…",
        "{{liste:invites|colonnes=n,nom,ppr,etablissement,signature}} insère un tableau ; filtres : etablissement=, zone=, cycle=, statut=, du=, au=…",
        "{{ia:z1}} insère une zone rédigée par l'IA selon la consigne que vous donnez ; « Créer sur Google Docs » permet d'éditer le modèle dans Google Docs.")),
    HelpTopic("ia", "Assistant IA", "parametres", listOf(
        "Paramètres → Assistant IA : choisissez Gemini, une autre API (Groq, Mistral, OpenRouter, OpenAI, Claude…), un serveur local (Ollama, LM Studio) ou l'IA du téléphone.",
        "Collez la clé du fournisseur, touchez « Tester ». « Lister les modèles » propose les modèles disponibles.",
        "Hors ligne : importez un modèle .task (Gemma, Qwen) : la rédaction se fait sur le téléphone, sans Internet.")),
    HelpTopic("liseuse", "Liseuse de documents", "liseuse", listOf(
        "Plus → Liseuse ouvre n'importe quel fichier du téléphone : PDF, Word (.docx), Excel (.xlsx), images, texte, CSV, HTML.",
        "Pincez pour zoomer ; pour un PDF, faites défiler les pages.",
        "Le bouton Partager envoie le fichier vers une autre application.")),
    HelpTopic("parametres", "Paramètres", "parametres", listOf(
        "Profil, Compte Google, Assistant IA, Apparence (thèmes, couleur, taille du texte), Modèles, Régions / DP, Notifications, Vacances.",
        "Les réglages d'apparence ne concernent que ce téléphone.")),
    HelpTopic("contact", "Contact", "accueil", listOf(
        "El Arbi EL ADLOUNI — 0700024598",
        "elarbi.eladlouni@fm6education.net · elarbi.eladlouni@taalim.ma",
        "« Signaler avec une capture » envoie la capture de l'écran par e-mail ou WhatsApp."))
)

/** Captures des écrans de l'application, faites automatiquement pour illustrer l'aide. */
object Shots {
    fun dir(ctx: Context) = File(ctx.filesDir, "aide").also { it.mkdirs() }
    fun file(ctx: Context, key: String) = File(dir(ctx), "$key.jpg")
    fun keyOf(s: Scr): String = when (s) {
        is Scr.Tab -> when (s.sec) { "accueil" -> "accueil"; "taches" -> "taches"; "ens" -> "enseignants"; "etab" -> "etablissements"; "reglages" -> "parametres"; else -> "plus" }
        is Scr.TaskDetail -> "tache"; is Scr.DocPreview -> "document"; is Scr.Settings -> "parametres"; is Scr.CustomTplEdit -> "modeles"
        is Scr.Viewer -> "liseuse"; is Scr.EnsDetail -> "enseignant"; is Scr.EtabDetail -> "etablissement"
        else -> ""
    }
    fun save(view: View, f: File) {
        val b = view.drawToBitmap(Bitmap.Config.ARGB_8888)
        val sc = 540f / b.width
        val s = Bitmap.createScaledBitmap(b, 540, (b.height * sc).toInt(), true)
        f.outputStream().use { s.compress(Bitmap.CompressFormat.JPEG, 82, it) }
    }
    fun share(ctx: Context, f: File, subject: String) {
        val uri = FileProvider.getUriForFile(ctx, "net.fm6education.carnetinspection.natif.fichiers", f)
        val i = Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_EMAIL, arrayOf("elarbi.eladlouni@fm6education.net")).putExtra(Intent.EXTRA_SUBJECT, subject)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Envoyer la capture").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** À placer dans App() : capture l'écran affiché (une fois qu'il est dessiné) pour l'aide. */
@Composable
fun AutoScreenshot(cur: Scr) {
    val view = LocalView.current
    val ctx = LocalContext.current
    val key = Shots.keyOf(cur)
    LaunchedEffect(cur) {
        if (key.isEmpty()) return@LaunchedEffect
        val f = Shots.file(ctx, key)
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < 6 * 3600_000L) return@LaunchedEffect
        delay(1500)
        try { val root = view.rootView; withContext(Dispatchers.Main) { Shots.save(root, f) } } catch (_: Throwable) {}
    }
}

@Composable
fun HelpButton(topic: String) {
    IconButton(onClick = { Nav.push(Scr.Help(topic)) }) {
        Box(Modifier.size(24.dp).clip(CircleShape).border(2.dp, OnBrand, CircleShape), contentAlignment = Alignment.Center) { Text("?", color = OnBrand, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
    }
}

@Composable
fun HelpScreen(topicId: String) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val t = HELP.firstOrNull { it.id == topicId }
    if (t == null) { HelpCenter(); return }
    val f = Shots.file(ctx, t.screen)
    val img = remember(topicId) { if (f.exists()) try { BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() } catch (_: Throwable) { null } else null }
    ScreenScaffold("Aide : " + t.title, { Nav.pop() }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 14.dp)) {
            item {
                Spacer(Modifier.height(8.dp))
                if (img != null) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Image(img, "Capture de l'écran", Modifier.fillMaxWidth(0.72f).clip(RoundedCornerShape(14.dp)).border(2.dp, Brand, RoundedCornerShape(14.dp)), contentScale = ContentScale.FillWidth)
                    }
                    Text("Capture réelle de l'écran « ${t.title} » sur votre téléphone.", color = InkFaint, fontSize = 11.5.sp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                } else CardBox {
                    Text("La capture de cet écran apparaîtra ici après votre première visite de l'écran.", color = InkFaint, fontSize = 12.5.sp)
                    t.open?.let { s -> TextButton(onClick = { Nav.push(s) }) { Text("Ouvrir l'écran", maxLines = 1, softWrap = false) } }
                }
            }
            items(t.steps.size) { i ->
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(Brand), contentAlignment = Alignment.Center) { Text("${i + 1}", color = OnBrand, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    Text(t.steps[i], fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp))
                }
            }
            item {
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val sf = if (f.exists()) f else Shots.file(ctx, "signalement").also { try { Shots.save(view.rootView, it) } catch (_: Throwable) {} }
                        if (sf.exists()) Shots.share(ctx, sf, "Carnet d'Inspection — ${t.title}")
                    }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Signaler avec une capture", maxLines = 1, softWrap = false) }
                    TextButton(onClick = { Nav.push(Scr.Help("")) }) { Text("Toute l'aide", maxLines = 1, softWrap = false) }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun HelpCenter() {
    val ctx = LocalContext.current
    ScreenScaffold("Aide", { Nav.pop() }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 14.dp)) {
            item { Spacer(Modifier.height(8.dp)) }
            items(HELP, key = { it.id }) { t ->
                val f = Shots.file(ctx, t.screen)
                val img = remember(t.id) { if (f.exists()) try { BitmapFactory.Options().let { o -> o.inSampleSize = 4; BitmapFactory.decodeFile(f.absolutePath, o)?.asImageBitmap() } } catch (_: Throwable) { null } else null }
                CardBox(onClick = { Nav.push(Scr.Help(t.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(width = 44.dp, height = 70.dp).clip(RoundedCornerShape(6.dp)).background(Track), contentAlignment = Alignment.Center) {
                            if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Icon(Icons.Default.Info, null, tint = Brand)
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(t.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.steps.first(), color = InkFaint, fontSize = 12.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            item {
                SectionTitle("Contact")
                CardBox {
                    Text("El Arbi EL ADLOUNI", fontWeight = FontWeight.Bold)
                    ContactButtons("0700024598", "El Arbi EL ADLOUNI", "elarbi.eladlouni@fm6education.net")
                    TextButton(onClick = { mail(ctx, "elarbi.eladlouni@taalim.ma", "Carnet d'Inspection") }) { Text("elarbi.eladlouni@taalim.ma", maxLines = 1, softWrap = false) }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
