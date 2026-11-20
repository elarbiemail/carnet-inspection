package ma.carnet.inspection.natif.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.carnet.inspection.natif.data.*
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Jours fériés et vacances : type, couleur et informations affichées dans le calendrier des tâches. */
object Feries {
    enum class Kind(val label: String) { NATIONAL("Jour férié national"), RELIGIEUX("Fête religieuse (jour férié)"), VACANCES("Vacances scolaires"), AUTRE("Congé") }

    private data class Info(val keys: List<String>, val kind: Kind, val text: String)
    private val INFOS = listOf(
        Info(listOf("unite", "wahda"), Kind.NATIONAL, "Aïd Al-Wahda, fête nationale instituée en 2025 pour célébrer l'unité territoriale du Royaume."),
        Info(listOf("marche verte"), Kind.NATIONAL, "Commémore la Marche Verte du 6 novembre 1975, qui a mené à la récupération des provinces du Sud."),
        Info(listOf("independance"), Kind.NATIONAL, "Commémore le retour d'exil du roi Mohammed V (novembre 1955) et l'accession du Maroc à l'indépendance."),
        Info(listOf("manifeste"), Kind.NATIONAL, "Commémore la présentation du Manifeste de l'Indépendance, le 11 janvier 1944."),
        Info(listOf("amazigh", "yennayer"), Kind.NATIONAL, "Yennayer, premier jour de l'année amazighe, jour férié officiel depuis 2024."),
        Info(listOf("nouvel an hegire", "hegire", "moharram", "muharram"), Kind.RELIGIEUX, "1er Moharram : premier jour de l'année hégirienne. Date fixée selon l'observation du croissant lunaire."),
        Info(listOf("nouvel an"), Kind.NATIONAL, "Premier jour de l'année grégorienne."),
        Info(listOf("travail"), Kind.NATIONAL, "Fête internationale du Travail."),
        Info(listOf("trone"), Kind.NATIONAL, "Fête du Trône : anniversaire de l'accession au trône de S.M. le Roi Mohammed VI (30 juillet 1999)."),
        Info(listOf("oued eddahab"), Kind.NATIONAL, "Commémore la récupération de la région d'Oued Eddahab (14 août 1979)."),
        Info(listOf("revolution du roi"), Kind.NATIONAL, "Commémore la Révolution du Roi et du Peuple (20 août 1953)."),
        Info(listOf("jeunesse"), Kind.NATIONAL, "Fête de la Jeunesse (21 août)."),
        Info(listOf("fitr"), Kind.RELIGIEUX, "Aïd Al-Fitr marque la fin du mois de ramadan. Congé scolaire du 29 ramadan au 2 chawwal (3 ou 4 jours) ; la date dépend de l'observation du croissant lunaire."),
        Info(listOf("adha"), Kind.RELIGIEUX, "Aïd Al-Adha, fête du sacrifice. Congé du 9 au 11 dhou al-hijja ; la date dépend de l'observation du croissant lunaire."),
        Info(listOf("mawlid", "mouloud"), Kind.RELIGIEUX, "Aïd Al-Mawlid : anniversaire de la naissance du Prophète (12 rabia al-awal)."),
        Info(listOf("vacances", "periode intermediaire", "mi-annee"), Kind.VACANCES, "Interruption des cours dans l'enseignement public (calendrier fixé par l'arrêté ministériel n° 047.26). Le personnel administratif reste en service.")
    )

    private fun info(v: JSONObject): Info? { val n = norm(v.s("nom")); return INFOS.firstOrNull { i -> i.keys.any { n.contains(it) } } }
    fun kind(v: JSONObject): Kind = info(v)?.kind ?: (if (days(v) >= 5) Kind.VACANCES else Kind.AUTRE)
    fun text(v: JSONObject): String = info(v)?.text ?: ""
    fun days(v: JSONObject): Int { val a = parseDate(v.s("dateDebut")) ?: return 1; val b = parseDate(v.s("dateFin")) ?: a; return ChronoUnit.DAYS.between(a, b).toInt() + 1 }
    fun estimated(v: JSONObject) = norm(v.s("nom")).contains("estim")
    fun isHoliday(v: JSONObject) = kind(v).let { it == Kind.NATIONAL || it == Kind.RELIGIEUX }

    /** Toutes les périodes qui couvrent ce jour (un jour férié peut tomber pendant des vacances). */
    fun on(d: LocalDate): List<JSONObject> = Store.list("vacances").filter { v ->
        val a = parseDate(v.s("dateDebut")); val b = parseDate(v.s("dateFin")) ?: a
        a != null && b != null && !d.isBefore(a) && !d.isAfter(b)
    }.sortedBy { if (isHoliday(it)) 0 else 1 }

    fun color(k: Kind): Color = when (k) { Kind.NATIONAL -> Danger; Kind.RELIGIEUX -> Color(0xFF2E7D5B); Kind.VACANCES -> Earth; Kind.AUTRE -> Warn }
    fun icon(k: Kind): ImageVector = when (k) { Kind.NATIONAL -> Icons.Outlined.Flag; Kind.RELIGIEUX -> Icons.Outlined.NightsStay; Kind.VACANCES -> Icons.Outlined.BeachAccess; Kind.AUTRE -> Icons.Outlined.Event }
}

/** Fiche d'information d'un jour férié ou d'une période de vacances. */
@Composable
fun FerieCard(v: JSONObject, tasksThatDay: Int = 0) {
    val k = Feries.kind(v); val c = Feries.color(k); val n = Feries.days(v)
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = c.copy(alpha = 0.10f)), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(12.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(c.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Icon(Feries.icon(k), null, tint = c, modifier = Modifier.size(20.dp)) }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(v.s("nom").replace(Regex("\\s*\\(date estimée[^)]*\\)", RegexOption.IGNORE_CASE), ""), fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                Text(k.label + " · " + (if (n == 1) fmtLong(v.s("dateDebut")) else "du " + fmtDate(v.s("dateDebut")) + " au " + fmtDate(v.s("dateFin")) + " ($n jours)"), color = c, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                Feries.text(v).takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 12.5.sp, color = Ink, modifier = Modifier.padding(top = 4.dp)) }
                if (Feries.estimated(v)) Text("Date estimée : à corriger dans Réglages → Vacances dès l'annonce officielle.", fontSize = 11.5.sp, color = Warn, modifier = Modifier.padding(top = 4.dp))
                if (tasksThatDay > 0) Text("⚠ $tasksThatDay tâche(s) prévue(s) ce jour-là.", fontSize = 12.sp, color = Danger, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun FeriesLegend() {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(Feries.Kind.NATIONAL to "Férié national", Feries.Kind.RELIGIEUX to "Fête religieuse", Feries.Kind.VACANCES to "Vacances").forEach { (k, l) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(Feries.color(k)))
                Text(l, fontSize = 11.5.sp, color = InkFaint, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}
