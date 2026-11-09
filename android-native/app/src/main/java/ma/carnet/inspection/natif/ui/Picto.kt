package ma.carnet.inspection.natif.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import ma.carnet.inspection.natif.data.norm

/** Pictogrammes : un mot-clé du libellé → une icône (menus, boutons, titres, fiches). */
object Picto {
    // ordre important : les expressions les plus précises d'abord
    private val MAP: List<Pair<List<String>, ImageVector>> = listOf(
        listOf("whatsapp") to Icons.Outlined.Chat,
        listOf("appeler", "appel", "telephone", "tel.") to Icons.Outlined.Phone,
        listOf("sms", "message") to Icons.Outlined.Sms,
        listOf("courriel", "e-mail", "email", "mail") to Icons.Outlined.Email,
        listOf("meteo") to Icons.Outlined.WbSunny,
        listOf("en retard", "retard") to Icons.Outlined.WarningAmber,
        listOf("a venir", "prochain", "semaine") to Icons.Outlined.Today,
        listOf("calendrier") to Icons.Outlined.CalendarToday,
        listOf("emploi du temps", "emplois du temps", "horaire") to Icons.Outlined.Schedule,
        listOf("rappel", "notification") to Icons.Outlined.NotificationsActive,
        listOf("tableau de service") to Icons.Outlined.TableChart,
        listOf("sous-tache", "sous-taches") to Icons.Outlined.PlaylistAddCheck,
        listOf("equipe") to Icons.Outlined.Groups,
        listOf("invite") to Icons.Outlined.GroupAdd,
        listOf("tache") to Icons.Outlined.Assignment,
        listOf("enseignant", "professeur", "membre") to Icons.Outlined.School,
        listOf("etablissement", "ecole", "college", "lycee") to Icons.Outlined.Apartment,
        listOf("zone", "commune", "province", "region", "lieu", "adresse", "position", "ville") to Icons.Outlined.Place,
        listOf("modele") to Icons.Outlined.Bookmarks,
        listOf("liseuse", "lire", "ouvrir") to Icons.Outlined.MenuBook,
        listOf("piece jointe", "pieces jointes", "joindre", "fichier") to Icons.Outlined.AttachFile,
        listOf("pdf", "docx", "word", "document", "rapport", "compte rendu") to Icons.Outlined.Description,
        listOf("excel", "xlsx", "sheets", "tableur") to Icons.Outlined.GridOn,
        listOf("bilan", "statistique") to Icons.Outlined.Insights,
        listOf("programme") to Icons.Outlined.EventNote,
        listOf("historique", "archive") to Icons.Outlined.History,
        listOf("note") to Icons.Outlined.Notes,
        listOf("photo", "camera", "image", "avatar") to Icons.Outlined.PhotoCamera,
        listOf("synchronis", "drive") to Icons.Outlined.Sync,
        listOf("google", "compte") to Icons.Outlined.AccountCircle,
        listOf("profil", "identite", "signature") to Icons.Outlined.Badge,
        listOf("ia", "assistant", "generer", "rediger", "moteur") to Icons.Outlined.AutoAwesome,
        listOf("apparence", "theme", "couleur", "police") to Icons.Outlined.Palette,
        listOf("vacance", "conge") to Icons.Outlined.BeachAccess,
        listOf("inspectapp", "echange") to Icons.Outlined.SwapHoriz,
        listOf("importer", "import", "telecharger") to Icons.Outlined.Download,
        listOf("exporter", "export", "envoyer", "partager") to Icons.Outlined.Share,
        listOf("enregistrer", "sauvegard", "valider") to Icons.Outlined.Save,
        listOf("supprimer", "effacer", "corbeille") to Icons.Outlined.Delete,
        listOf("modifier", "editer") to Icons.Outlined.Edit,
        listOf("ajouter", "nouvelle", "nouveau", "creer") to Icons.Outlined.AddCircleOutline,
        listOf("rechercher", "recherche", "chercher", "detecter") to Icons.Outlined.Search,
        listOf("aide", "guide") to Icons.Outlined.HelpOutline,
        listOf("contact") to Icons.Outlined.ContactPhone,
        listOf("deconnexion", "deconnecter") to Icons.Outlined.Logout,
        listOf("connexion", "connecter") to Icons.Outlined.Login,
        listOf("donnees") to Icons.Outlined.Storage,
        listOf("accueil", "tableau de bord") to Icons.Outlined.Home,
        listOf("reglage", "parametre") to Icons.Outlined.Settings,
        listOf("date", "echeance", "jour") to Icons.Outlined.Event,
        listOf("heure", "duree") to Icons.Outlined.AccessTime,
        listOf("statut", "etat") to Icons.Outlined.Flag,
        listOf("type", "categorie") to Icons.Outlined.Category,
        listOf("discipline", "matiere", "niveau", "classe") to Icons.Outlined.MenuBook,
        listOf("observation", "remarque", "commentaire", "description", "objet") to Icons.Outlined.Notes,
        listOf("annuler", "fermer") to Icons.Outlined.Close,
        listOf("ok", "terminer", "confirmer", "appliquer") to Icons.Outlined.Check,
        listOf("actualiser", "rafraichir", "reessayer") to Icons.Outlined.Refresh,
        listOf("imprimer") to Icons.Outlined.Print,
        listOf("widget", "bloc", "personnaliser") to Icons.Outlined.Widgets,
    )
    private val cache = HashMap<String, ImageVector?>()

    fun of(label: String): ImageVector? = cache.getOrPut(label) {
        val n = " " + norm(label).replace(Regex("[^a-z0-9.\\- ]"), " ") + " "
        MAP.firstOrNull { (keys, _) -> keys.any { k -> if (k.length <= 3) n.contains(" $k ") else n.contains(k) } }?.second
    }
}

/** Libellé de bouton avec pictogramme (si un mot-clé est reconnu). */
@Composable
fun BtnText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, fontSize: TextUnit = TextUnit.Unspecified,
            fontWeight: FontWeight? = null, maxLines: Int = 1, softWrap: Boolean = false, overflow: TextOverflow = TextOverflow.Ellipsis) {
    val ic = Picto.of(text)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (ic != null) Icon(ic, null, Modifier.size(18.dp).padding(end = 0.dp), tint = if (color == Color.Unspecified) LocalContentColor.current else color)
        if (ic != null) Spacer(Modifier.width(6.dp))
        Text(text, color = color, fontSize = fontSize, fontWeight = fontWeight, maxLines = maxLines, softWrap = softWrap, overflow = overflow)
    }
}
