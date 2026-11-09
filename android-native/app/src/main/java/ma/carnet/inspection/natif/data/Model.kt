package ma.carnet.inspection.natif.data

import org.json.JSONObject
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Référentiels et libellés, identiques à ceux du carnet web. */
object Ref {
    /** Un(e) seul(e) enseignant(e) concerné(e). */
    val SINGLE_ENS = setOf("inspection", "visite_conseil")
    /** Plusieurs invité(e)s. */
    val MULTI_INV = setOf("reunion_pedagogique", "cours_experimental", "formation", "reunion_distance")
    /** Équipes et sous-tâches. */
    val TEAMS = setOf("recherche_action")
    val SUB_STATUTS = linkedMapOf("a_faire" to "À faire", "en_cours" to "En cours", "terminee" to "Terminée")
    val TASK_TYPES = linkedMapOf(
        "inspection" to "Inspection", "visite_conseil" to "Visite conseil",
        "reunion_pedagogique" to "Réunion pédagogique", "cours_experimental" to "Cours expérimental",
        "formation" to "Formation", "reunion_distance" to "Réunion à distance",
        "reunion_administrative" to "Réunion administrative", "recherche_action" to "Recherche action",
        "observation_examen" to "Observation d'examen", "conception_examen" to "Conception d'examen",
        "validation_ts" to "Validation des tableaux de service", "autre" to "Autre"
    )
    val WORKFLOW = linkedMapOf(
        "a_planifier" to "À planifier", "planifiee" to "Planifiée", "realisee" to "Réalisée",
        "rapport" to "CR rédigé", "cloturee" to "Clôturée"
    )
    val DONE = setOf("realisee", "rapport", "cloturee")
    val PRIORITES = linkedMapOf("basse" to "Basse", "moyenne" to "Moyenne", "haute" to "Haute")
    val ETAB_CYCLES = linkedMapOf(
        "primaire" to "Primaire", "college" to "Collégial", "qualifiant" to "Qualifiant",
        "college_qualifiant" to "Collégial + Qualifiant"
    )
    val ETAB_TYPES = linkedMapOf("public" to "Public", "prive" to "Privé")
    val ENS_CYCLES = linkedMapOf("primaire" to "Primaire", "college" to "Collégial", "qualifiant" to "Qualifiant")
    val GENRES = linkedMapOf("F" to "Femme", "H" to "Homme")
    val STATUTS = linkedMapOf("titulaire" to "Titulaire", "stagiaire" to "Stagiaire")
    val JOURS = listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi")
    val NOTE_SCALE = listOf("Insuffisant", "Satisfaisant", "Bien", "Très bien")

    private val LEGACY = mapOf("a_faire" to "a_planifier", "en_cours" to "realisee", "termine" to "cloturee")
    fun statut(t: JSONObject): String { val v = t.s("statut"); return if (v in WORKFLOW) v else LEGACY[v] ?: "a_planifier" }
    fun type(t: JSONObject): String { val v = t.s("taskType"); return if (v in TASK_TYPES) v else if (v == "visite") "inspection" else if (v == "reunion") "reunion_pedagogique" else "autre" }
}

fun norm(s: String): String =
    Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

private val FR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val FR_LONG = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)

fun parseDate(iso: String): LocalDate? = try { if (iso.length >= 10) LocalDate.parse(iso.substring(0, 10)) else null } catch (e: Exception) { null }
fun fmtDate(iso: String): String = parseDate(iso)?.format(FR_DATE) ?: iso
fun fmtLong(iso: String): String = parseDate(iso)?.format(FR_LONG)?.replaceFirstChar { it.uppercase() } ?: iso

fun initials(nom: String): String {
    val p = nom.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (p.isEmpty()) return "?"
    return (p[0].take(1) + (p.getOrNull(1)?.take(1) ?: "")).uppercase()
}

fun etabName(id: String?): String = Store.byId("etablissements", id)?.s("nom") ?: ""
fun ensName(id: String?): String = Store.byId("enseignants", id)?.s("nom") ?: ""
fun zoneName(id: String?): String = Store.byId("zones", id)?.s("nom") ?: ""

fun taskEtabName(t: JSONObject): String = t.s("etablissementNom").ifEmpty { etabName(t.s("etablissementId")) }
fun taskEnsName(t: JSONObject): String = t.s("enseignantNom").ifEmpty { ensName(t.s("enseignantId")) }
fun taskTime(t: JSONObject): String = t.s("heureDebut").ifEmpty { t.s("heure") }

/** Cycle d'un(e) enseignant(e) : choix explicite, sinon déduit des niveaux, sinon de l'établissement. */
fun ensCycle(e: JSONObject): String {
    val c = e.s("cycleEns"); if (c.isNotEmpty()) return c
    val t = " " + norm(e.s("cycle")) + " "
    if (Regex("\\b(tc|tronc|[12] ?bac|bac|qualif|lycee)\\b").containsMatchIn(t)) return "qualifiant"
    if (Regex("\\b([123] ?ac|asc|apic|college|colleg)").containsMatchIn(t)) return "college"
    if (Regex("\\b(primaire|cp|ce ?[1-6]?|cm ?[12]?|[1-6] ?ap)\\b").containsMatchIn(t)) return "primaire"
    val et = Store.byId("etablissements", e.s("etablissementId"))?.s("cycle") ?: ""
    return if (et in listOf("primaire", "college", "qualifiant")) et else ""
}

/** Notes d'inspection d'un(e) enseignant(e) (rapports du carnet, saisies manuelles, feuilles personnelles). */
data class Insp(val date: String, val note: String, val par: String, val source: String)

fun ensInspections(e: JSONObject): List<Insp> {
    val id = e.s("id"); val out = mutableListOf<Insp>()
    Store.list("documents").forEach { d ->
        if (d.s("type") != "rapport_inspection" || d.s("note").isEmpty()) return@forEach
        val ensId = d.s("enseignantId").ifEmpty { Store.byId("tasks", d.s("taskId"))?.s("enseignantId") ?: "" }
        if (ensId == id) out.add(Insp(d.s("date"), noteTxt(d.s("note")), "Nous-même", "Rapport"))
    }
    e.arr("inspections").forEach { i -> if (i.s("note").isNotEmpty() || i.s("date").isNotEmpty()) out.add(Insp(i.s("date"), noteTxt(i.s("note")), i.s("par"), "Saisie")) }
    val m = Regex("derni[eè]re note d.inspection\\s*:?\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:/\\s*20)?\\s*\\((\\d{4})\\)", RegexOption.IGNORE_CASE).find(e.s("notes"))
    if (m != null && out.none { it.date.take(4) == m.groupValues[2] }) out.add(Insp(m.groupValues[2], m.groupValues[1] + "/20", "", "Feuille personnelle"))
    return out.sortedByDescending { it.date }
}

fun noteTxt(n: String): String { val t = n.trim(); return if (t.isEmpty()) "" else if (Regex("/\\s*20$").containsMatchIn(t)) t.replace(" ", "") else "$t/20" }
fun noteNum(n: String): Double? = Regex("(\\d+(?:[.,]\\d+)?)").find(n)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
fun inspDateTxt(d: String): String = if (Regex("^\\d{4}$").matches(d)) d else fmtDate(d)

/** Titre automatique d'une tâche, comme dans le carnet web. */
fun autoTaskTitle(t: JSONObject): String {
    val parts = mutableListOf(Ref.TASK_TYPES[Ref.type(t)] ?: "Tâche")
    val who = taskEnsName(t); val wh = taskEtabName(t).ifEmpty { t.s("lieu") }
    if (who.isNotEmpty()) parts.add(who) else if (wh.isNotEmpty()) parts.add(wh)
    return parts.joinToString(" – ")
}

/** Numéro marocain local → format international pour WhatsApp. */
fun waNumber(tel: String): String {
    var d = tel.replace(Regex("[^\\d+]"), "")
    if (d.isEmpty()) return ""
    d = when {
        d.startsWith("+") -> d.substring(1)
        d.startsWith("00") -> d.substring(2)
        d.startsWith("0") -> "212" + d.substring(1)
        else -> d
    }
    return d
}
