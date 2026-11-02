package ma.carnet.inspection.natif.data

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

/**
 * Modèles de documents personnalisés créés dans le carnet web (templates.customDocs) :
 * même syntaxe — # Titre, ## Sous-titre, **gras**, [centre]/[droite]/[gauche]/[justifie],
 * {{logo}}, {{entete}}, {{signature}}, {{ia:z1}}, {{liste:…}} et balises {{profil.nom}}, {{tache.date}}…
 */
object CustomTpl {
    fun templates(): List<JSONObject> = Store.root.optJSONObject("templates")?.arr("customDocs") ?: emptyList()
    fun forTask(t: JSONObject): List<JSONObject> = templates().filter {
        val tt = it.optJSONArray("taskTypes"); tt == null || tt.length() == 0 || (0 until tt.length()).any { i -> tt.optString(i) == Ref.type(t) }
    }

    fun values(d: JSONObject, t: JSONObject): Map<String, String> {
        val p = Store.profil()
        val et = Store.byId("etablissements", t.s("etablissementId"))
        val en = Store.byId("enseignants", t.s("enseignantId"))
        val li = en?.let { ensInspections(it).firstOrNull() }
        val today = LocalDate.now().toString()
        val ech = t.s("echeance")
        return mapOf(
            "profil.nom" to p.s("nomSignature").ifEmpty { p.s("nom") }, "profil.nom_ar" to p.s("nomAr"), "profil.grade" to p.s("grade"),
            "profil.matiere" to p.s("matiere"), "profil.matiere_ar" to p.s("matiereAr"), "profil.academie" to p.s("academieRegionale"),
            "profil.academie_ar" to p.s("academieAr"), "profil.province" to p.s("province"), "profil.province_ar" to p.s("provinceAr"),
            "profil.telephone" to p.s("telephone"), "profil.email" to p.s("email"),
            "app.nom" to "Carnet d'Inspection", "date.jour" to fmtDate(today), "date.jour_ar" to Docs.dateAr(today),
            "annee_scolaire" to LocalDate.now().let { if (it.monthValue >= 9) "${it.year}/${it.year + 1}" else "${it.year - 1}/${it.year}" },
            "doc.date" to fmtDate(d.s("date")), "doc.date_ar" to Docs.dateAr(d.s("date")), "doc.titre" to d.s("titre"),
            "tache.titre" to t.s("titre"), "tache.type" to (Ref.TASK_TYPES[Ref.type(t)] ?: ""), "tache.type_ar" to (Docs.TT_AR[Ref.type(t)] ?: ""),
            "tache.date" to fmtDate(ech), "tache.date_ar" to (if (ech.isEmpty()) "" else Docs.dateAr(ech)), "tache.heure" to taskTime(t), "tache.heure_fin" to t.s("heureFin"),
            "tache.lieu" to t.s("lieu").ifEmpty { et?.s("nom") ?: "" }, "tache.description" to t.s("description"), "tache.statut" to (Ref.WORKFLOW[Ref.statut(t)] ?: ""),
            "tache.nb_invites" to t.arr("invites").size.toString(), "tache.lien" to t.s("lienConnexion"),
            "etablissement.nom" to (et?.s("nom") ?: taskEtabName(t)), "etablissement.nom_ar" to (et?.s("nomAr") ?: ""), "etablissement.nom_fr" to (et?.s("nomFr") ?: ""),
            "etablissement.directeur" to (et?.s("directeur") ?: ""), "etablissement.directeur_tel" to (et?.s("directeurTel") ?: ""), "etablissement.telephone" to (et?.s("telephone") ?: ""),
            "etablissement.adresse" to (et?.s("adresse") ?: ""), "etablissement.code" to (et?.s("codeMassar") ?: ""), "etablissement.zone" to zoneName(et?.s("zoneId")),
            "etablissement.nb_enseignants" to (et?.let { e -> Store.list("enseignants").count { it.s("etablissementId") == e.s("id") }.toString() } ?: ""),
            "enseignant.nom" to (en?.s("nom") ?: taskEnsName(t)), "enseignant.nom_ar" to (en?.s("nomAr") ?: ""), "enseignant.civilite" to when (en?.s("genre")) { "F" -> "Mme"; "H" -> "M."; else -> "" },
            "enseignant.ppr" to (en?.s("ppr") ?: ""), "enseignant.cadre" to (en?.s("cadre") ?: ""), "enseignant.matiere" to (en?.s("matiere") ?: ""),
            "enseignant.telephone" to (en?.s("telephone") ?: ""), "enseignant.email" to (en?.s("email") ?: ""),
            "enseignant.statut" to (if (en == null) "" else if (en.s("statut") == "stagiaire") "Stagiaire" else "Titulaire"),
            "enseignant.derniere_note" to (li?.note ?: ""), "enseignant.date_derniere_note" to (li?.let { inspDateTxt(it.date) } ?: ""),
            "zone.nom" to zoneName(et?.s("zoneId")),
            // anciennes balises simples
            "etablissement" to (et?.s("nom") ?: taskEtabName(t)), "enseignant" to taskEnsName(t), "date" to fmtDate(d.s("date")), "lieu" to t.s("lieu"),
            "objet" to t.s("titre"), "inspecteur" to p.s("nom"), "grade" to p.s("grade"), "academie" to p.s("academieRegionale"), "province" to p.s("province"),
            "heure" to taskTime(t), "heureFin" to t.s("heureFin"), "ppr" to (en?.s("ppr") ?: ""), "cadre" to (en?.s("cadre") ?: ""), "matiere" to (en?.s("matiere") ?: "")
        )
    }

    private val TAG = Regex("\\{\\{\\s*([a-zA-Z_][a-zA-Z0-9_.]*)\\s*\\}\\}")
    fun apply(s: String, v: Map<String, String>) = TAG.replace(s) { v[it.groupValues[1]] ?: "" }
    private fun runs(s: String) = Docs.h(s).replace(Regex("\\*\\*([^*]+)\\*\\*"), "<b>$1</b>")

    private val COLS = mapOf(
        "enseignants" to linkedMapOf("n" to ("N°" to "ر.ت"), "nom" to ("Nom et prénom" to "الاسم الكامل"), "nom_ar" to ("Nom (arabe)" to "الاسم بالعربية"), "ppr" to ("PPR" to "رقم التأجير"),
            "cadre" to ("Cadre" to "الإطار"), "matiere" to ("Matière" to "المادة"), "etablissement" to ("Établissement" to "المؤسسة"), "zone" to ("Zone" to "المنطقة"),
            "telephone" to ("Téléphone" to "الهاتف"), "statut" to ("Statut" to "الوضعية"), "derniere_note" to ("Dernière note" to "آخر نقطة"), "date_note" to ("Date" to "تاريخها"),
            "signature" to ("Signature" to "التوقيع"), "observations" to ("Observations" to "ملاحظات")),
        "etablissements" to linkedMapOf("n" to ("N°" to "ر.ت"), "nom" to ("Établissement" to "المؤسسة"), "nom_ar" to ("Nom (arabe)" to "الاسم بالعربية"), "zone" to ("Zone" to "المنطقة"),
            "cycle" to ("Cycle" to "السلك"), "type" to ("Type" to "النوع"), "directeur" to ("Directeur(trice)" to "المدير(ة)"), "directeur_tel" to ("Tél. directeur" to "هاتف المدير"),
            "telephone" to ("Téléphone" to "الهاتف"), "nb_enseignants" to ("Enseignant(e)s" to "عدد الأساتذة"), "observations" to ("Observations" to "ملاحظات")),
        "taches" to linkedMapOf("n" to ("N°" to "ر.ت"), "titre" to ("Tâche" to "المهمة"), "type" to ("Type" to "النوع"), "date" to ("Date" to "التاريخ"), "heure" to ("Heure" to "الساعة"),
            "statut" to ("Statut" to "الحالة"), "etablissement" to ("Établissement" to "المؤسسة"), "enseignant" to ("Enseignant(e)" to "الأستاذ(ة)"), "lieu" to ("Lieu" to "المكان"), "observations" to ("Observations" to "ملاحظات"))
    )

    private fun matchEtab(v: String, t: JSONObject): Set<String> =
        if (norm(v) == "tache") setOf(t.s("etablissementId").ifEmpty { Store.byId("enseignants", t.s("enseignantId"))?.s("etablissementId") ?: "" })
        else Store.list("etablissements").filter { e -> listOf(e.s("nom"), e.s("nomAr"), e.s("nomFr")).any { it.isNotEmpty() && norm(it).contains(norm(v)) } }.map { it.s("id") }.toSet()
    private fun matchZone(v: String, t: JSONObject): Set<String> = when (norm(v)) {
        "tache" -> setOf(Store.byId("etablissements", t.s("etablissementId"))?.s("zoneId") ?: "")
        "filtre" -> Store.zoneFilter
        else -> Store.list("zones").filter { norm(it.s("nom")).contains(norm(v)) }.map { it.s("id") }.toSet()
    }

    private fun listHtml(spec: String, t: JSONObject, rtl: Boolean): String {
        val parts = spec.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        var coll = parts.firstOrNull()?.lowercase() ?: return ""
        val f = mutableMapOf<String, String>(); var cols: List<String>? = null; var titre = ""
        parts.drop(1).forEach { pp -> val i = pp.indexOf('='); if (i > 0) { val k = pp.substring(0, i).trim().lowercase(); val v = pp.substring(i + 1).trim()
            when (k) { "colonnes" -> cols = v.split(",").map { it.trim() }; "titre" -> titre = v; else -> f[k] = v } } }
        val defsKey = if (coll == "invites") "enseignants" else coll
        val defs = COLS[defsKey] ?: return "<p>[liste inconnue : ${Docs.h(coll)}]</p>"
        val use = (cols ?: when (defsKey) { "etablissements" -> listOf("n", "nom", "zone", "directeur", "directeur_tel"); "taches" -> listOf("n", "titre", "type", "date", "statut", "etablissement"); else -> listOf("n", "nom", "ppr", "etablissement", "signature") }).filter { it in defs }
        val rows: List<Map<String, String>> = when (coll) {
            "enseignants", "invites" -> {
                var l = if (coll == "invites") t.arr("invites").map { Store.byId("enseignants", it.s("enseignantId")) ?: JSONObject().put("nom", it.s("nom")).put("ppr", it.s("ppr")) } else Store.list("enseignants")
                f["etablissement"]?.let { v -> val ids = matchEtab(v, t); l = l.filter { it.s("etablissementId") in ids } }
                f["zone"]?.let { v -> val z = matchZone(v, t); l = l.filter { (Store.byId("etablissements", it.s("etablissementId"))?.s("zoneId") ?: "") in z } }
                f["cycle"]?.let { v -> l = l.filter { ensCycle(it) == norm(v) } }
                f["statut"]?.let { v -> l = l.filter { norm(it.s("statut").ifEmpty { "titulaire" }) == norm(v) } }
                f["genre"]?.let { v -> l = l.filter { it.s("genre").equals(v, true) } }
                f["matiere"]?.let { v -> l = l.filter { norm(it.s("matiere")).contains(norm(v)) } }
                f["note"]?.let { v -> val avec = norm(v).startsWith("avec"); l = l.filter { (ensInspections(it).isNotEmpty()) == avec } }
                if (coll != "invites") l = l.sortedBy { norm(it.s("nom")) }
                l.mapIndexed { i, e -> val li = if (e.s("id").isNotEmpty()) ensInspections(e).firstOrNull() else null; val et = Store.byId("etablissements", e.s("etablissementId"))
                    mapOf("n" to "${i + 1}", "nom" to e.s("nom"), "nom_ar" to e.s("nomAr"), "ppr" to e.s("ppr"), "cadre" to e.s("cadre"), "matiere" to e.s("matiere"),
                        "etablissement" to (et?.s("nom") ?: ""), "zone" to zoneName(et?.s("zoneId")), "telephone" to e.s("telephone"),
                        "statut" to (if (e.s("statut") == "stagiaire") "Stagiaire" else "Titulaire"), "derniere_note" to (li?.note ?: ""), "date_note" to (li?.let { inspDateTxt(it.date) } ?: "")) }
            }
            "etablissements" -> {
                var l = Store.list("etablissements")
                f["zone"]?.let { v -> val z = matchZone(v, t); l = l.filter { it.s("zoneId") in z } }
                f["cycle"]?.let { v -> l = l.filter { norm(it.s("cycle")) == norm(v) } }
                f["type"]?.let { v -> l = l.filter { (if (it.s("type") == "prive") "prive" else "public") == norm(v) } }
                f["pionnier"]?.let { l = l.filter { it.optBoolean("pionnier") } }
                l.sortedBy { norm(it.s("nom")) }.mapIndexed { i, e -> mapOf("n" to "${i + 1}", "nom" to e.s("nom"), "nom_ar" to e.s("nomAr"), "zone" to zoneName(e.s("zoneId")),
                    "cycle" to (Ref.ETAB_CYCLES[e.s("cycle")] ?: ""), "type" to (if (e.s("type") == "prive") "Privé" else "Public"), "directeur" to e.s("directeur"),
                    "directeur_tel" to e.s("directeurTel"), "telephone" to e.s("telephone"), "nb_enseignants" to Store.list("enseignants").count { it.s("etablissementId") == e.s("id") }.toString()) }
            }
            "taches" -> {
                var l = Store.list("tasks")
                f["type"]?.let { v -> l = l.filter { Ref.type(it) == norm(v) || norm(Ref.TASK_TYPES[Ref.type(it)] ?: "") == norm(v) } }
                f["statut"]?.let { v -> l = l.filter { Ref.statut(it) == norm(v) || norm(Ref.WORKFLOW[Ref.statut(it)] ?: "") == norm(v) } }
                f["etablissement"]?.let { v -> val ids = matchEtab(v, t); l = l.filter { it.s("etablissementId") in ids } }
                f["du"]?.let { v -> l = l.filter { it.s("echeance") >= v } }
                f["au"]?.let { v -> l = l.filter { it.s("echeance").isNotEmpty() && it.s("echeance") <= v } }
                l.sortedBy { it.s("echeance") }.mapIndexed { i, x -> mapOf("n" to "${i + 1}", "titre" to x.s("titre"), "type" to (Ref.TASK_TYPES[Ref.type(x)] ?: ""), "date" to fmtDate(x.s("echeance")),
                    "heure" to taskTime(x), "statut" to (Ref.WORKFLOW[Ref.statut(x)] ?: ""), "etablissement" to taskEtabName(x), "enseignant" to taskEnsName(x), "lieu" to x.s("lieu")) }
            }
            else -> emptyList()
        }
        val sb = StringBuilder()
        if (titre.isNotEmpty()) sb.append("<p class=\"b\">").append(Docs.h(titre)).append("</p>")
        sb.append("<table class=\"t\"><thead><tr>").append(use.joinToString("") { "<th>" + Docs.h(if (rtl) defs[it]!!.second else defs[it]!!.first) + "</th>" }).append("</tr></thead><tbody>")
        rows.forEach { r -> sb.append("<tr>").append(use.joinToString("") { "<td>" + Docs.h(r[it] ?: "") + "</td>" }).append("</tr>") }
        if (rows.isEmpty()) sb.append("<tr>").append(use.joinToString("") { "<td>—</td>" }).append("</tr>")
        return sb.append("</tbody></table>").toString()
    }

    fun render(ctx: Context, d: JSONObject, t: JSONObject): String {
        val v = values(d, t); val rtl = d.optBoolean("rtl")
        val ai = d.optJSONObject("aiValues") ?: JSONObject()
        val sb = StringBuilder()
        d.s("corps").split("\n").forEach { raw ->
            var tr = raw.trim()
            if (tr.isEmpty()) { sb.append("<div style=\"height:3mm\"></div>"); return@forEach }
            Regex("^\\{\\{\\s*(logo|entete|signature)\\s*\\}\\}$", RegexOption.IGNORE_CASE).find(tr)?.let {
                when (it.groupValues[1].lowercase()) {
                    "logo" -> sb.append("<div style=\"text-align:center\"><img src=\"entete-ministere.png\" style=\"width:100mm\"></div>")
                    "entete" -> (Store.root.optJSONObject("templates")?.s("entete") ?: "").split("\n").filter { l -> l.isNotBlank() }.forEach { l -> sb.append("<p class=\"b\" style=\"text-align:center\">").append(Docs.h(l)).append("</p>") }
                    else -> sb.append("<div style=\"margin-top:8mm;text-align:${if (rtl) "left" else "right"}\" class=\"b\">${Docs.h(v["profil.nom"] ?: "")}</div>")
                }
                return@forEach
            }
            Regex("^\\{\\{\\s*ia\\s*:\\s*([\\w-]+)\\s*\\}\\}$", RegexOption.IGNORE_CASE).find(tr)?.let {
                ai.s(it.groupValues[1]).split("\n").filter { l -> l.isNotBlank() }.forEach { l -> sb.append("<p>").append(runs(l.trim())).append("</p>") }; return@forEach
            }
            Regex("^\\{\\{\\s*liste\\s*:\\s*(.*?)\\}\\}$", RegexOption.IGNORE_CASE).find(tr)?.let { sb.append(listHtml(apply(it.groupValues[1], v), t, rtl)); return@forEach }
            var align = ""
            Regex("^\\[(centre|center|droite|gauche|justifie)\\]\\s*", RegexOption.IGNORE_CASE).find(tr)?.let {
                align = mapOf("centre" to "center", "center" to "center", "droite" to "right", "gauche" to "left", "justifie" to "justify")[it.groupValues[1].lowercase()] ?: ""
                tr = tr.substring(it.value.length)
            }
            tr = Regex("\\{\\{\\s*ia\\s*:\\s*([\\w-]+)\\s*\\}\\}").replace(tr) { m -> ai.s(m.groupValues[1]).replace("\n", " ") }
            val st = if (align.isNotEmpty()) " style=\"text-align:$align\"" else ""
            val m = Regex("^(#{1,3})\\s+(.*)$").find(tr)
            if (m != null) { val lvl = m.groupValues[1].length; sb.append(if (lvl == 1) "<h1$st>" else "<h2$st>").append(runs(apply(m.groupValues[2], v))).append(if (lvl == 1) "</h1>" else "</h2>") }
            else sb.append("<p$st>").append(runs(apply(tr, v))).append("</p>")
        }
        return sb.toString()
    }
}
