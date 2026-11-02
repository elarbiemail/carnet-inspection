package ma.carnet.inspection.natif.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.UUID

/**
 * Échange de données avec l'application de bureau iNSPECTAPP (même format que le carnet web :
 * « carnet-inspectapp », version 1). Établissements, enseignant(e)s, zones et tâches circulent dans les
 * deux sens ; les éléments connus des deux côtés sont mis à jour, jamais dupliqués, rien n'est supprimé.
 */
object Exchange {
    const val FORMAT = "carnet-inspectapp"; const val VERSION = 1
    const val FILE_OUT = "carnet-vers-inspectapp.json"; const val FILE_IN = "inspectapp-vers-carnet.json"; const val FOLDER = "Échange InspectApp"
    private val TYPE_OUT = mapOf("inspection" to "Inspection", "visite_conseil" to "Visit", "reunion_pedagogique" to "Meeting", "cours_experimental" to "ExperimentalLesson",
        "formation" to "ContinuingEducation", "reunion_distance" to "Meeting", "reunion_administrative" to "Other", "recherche_action" to "ActionResearch",
        "observation_examen" to "ExamObservation", "conception_examen" to "ExamDesign", "validation_ts" to "ServiceTableValidation", "autre" to "Other")
    private val TYPE_IN = mapOf("inspection" to "inspection", "visit" to "visite_conseil", "visite" to "visite_conseil", "visiteconseil" to "visite_conseil", "meeting" to "reunion_pedagogique",
        "reunion" to "reunion_pedagogique", "reunionpedagogique" to "reunion_pedagogique", "experimentallesson" to "cours_experimental", "leconexperimentale" to "cours_experimental",
        "coursexperimental" to "cours_experimental", "continuingeducation" to "formation", "formation" to "formation", "formationcontinue" to "formation", "actionresearch" to "recherche_action",
        "rechercheaction" to "recherche_action", "examobservation" to "observation_examen", "observationexamens" to "observation_examen", "examdesign" to "conception_examen",
        "conceptionexamens" to "conception_examen", "servicetablevalidation" to "validation_ts", "validationtableauxservice" to "validation_ts", "validationts" to "validation_ts",
        "titularisation" to "autre", "other" to "autre", "autre" to "autre", "autres" to "autre")
    private val STATUS_OUT = mapOf("a_planifier" to "Planned", "planifiee" to "Planned", "realisee" to "InProgress", "rapport" to "InProgress", "cloturee" to "Completed")
    private val STATUS_IN = mapOf("planned" to "planifiee", "planifiee" to "planifiee", "inprogress" to "realisee", "encours" to "realisee", "completed" to "cloturee", "terminee" to "cloturee", "done" to "cloturee")

    private fun key(t: String) = Normalizer.normalize(t.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9\\u0600-\\u06FF]+"), " ").trim()
    private fun code(t: String) = t.lowercase().replace(Regex("[^a-z]"), "")
    private fun ext(r: JSONObject?) = r?.optJSONObject("extIds")?.s("inspectapp") ?: ""
    private fun arabic(s: String) = Regex("[\\u0600-\\u06FF]").containsMatchIn(s)
    private fun o(vararg p: Pair<String, Any?>) = JSONObject().apply { p.forEach { (k, v) -> if (v != null) put(k, v) } }
    private fun opt(s: String) = s.ifEmpty { null }

    private fun etabRef(id: String) = Store.byId("etablissements", id)?.let { o("carnetId" to it.s("id"), "inspectappId" to opt(ext(it)), "codeMassar" to opt(it.s("codeMassar")), "nom" to it.s("nom")) }
    private fun ensRef(id: String, nom: String): JSONObject? {
        val e = Store.byId("enseignants", id) ?: Store.list("enseignants").firstOrNull { nom.isNotEmpty() && it.s("nom") == nom }
        return e?.let { o("carnetId" to it.s("id"), "inspectappId" to opt(ext(it)), "ppr" to opt(it.s("ppr")), "nom" to it.s("nom")) } ?: if (nom.isNotEmpty()) o("nom" to nom) else null
    }

    fun payload(): JSONObject {
        val p = Store.profil()
        return o("format" to FORMAT, "version" to VERSION, "source" to "carnet", "exportedAt" to Store.now(),
            "inspecteur" to o("nom" to p.s("nomSignature").ifEmpty { p.s("nom") }, "nomAr" to p.s("nomAr"), "matiere" to p.s("matiere"), "academie" to p.s("academieRegionale"), "direction" to p.s("province")),
            "zones" to JSONArray(Store.list("zones").map { z -> o("carnetId" to z.s("id"), "inspectappId" to opt(ext(z)), "nom" to z.s("nom"), "academie" to z.s("academieRegionale"), "direction" to z.s("directionProvinciale"), "commune" to z.s("commune"), "cycle" to z.s("cycle"), "updatedAt" to z.s("updatedAt")) }),
            "etablissements" to JSONArray(Store.list("etablissements").map { e -> o("carnetId" to e.s("id"), "inspectappId" to opt(ext(e)), "nom" to e.s("nom"), "nomAr" to e.s("nomAr"), "nomFr" to e.s("nomFr"),
                "codeMassar" to e.s("codeMassar"), "type" to e.s("type"), "cycle" to e.s("cycle"), "pionnier" to e.optBoolean("pionnier"), "adresse" to e.s("adresse"), "telephone" to e.s("telephone"),
                "directeur" to e.s("directeur"), "directeurTel" to e.s("directeurTel"), "latitude" to e.s("latitude"), "longitude" to e.s("longitude"),
                "zone" to (if (e.s("zoneId").isNotEmpty()) o("carnetId" to e.s("zoneId"), "nom" to zoneName(e.s("zoneId"))) else JSONObject.NULL), "updatedAt" to e.s("updatedAt")) }),
            "enseignants" to JSONArray(Store.list("enseignants").map { e -> o("carnetId" to e.s("id"), "inspectappId" to opt(ext(e)), "nom" to e.s("nom"), "nomAr" to e.s("nomAr"), "nomFr" to e.s("nomFr"),
                "ppr" to e.s("ppr"), "genre" to e.s("genre"), "matiere" to e.s("matiere"), "cycle" to e.s("cycle"), "cadre" to e.s("cadre"), "statut" to e.s("statut"), "sessionTitularisation" to e.s("sessionTitularisation"),
                "telephone" to e.s("telephone"), "email" to e.s("email"),
                "horaire" to JSONArray(e.arr("horaire").map { sl -> o("jour" to sl.s("jour"), "debut" to sl.s("debut"), "fin" to sl.s("fin"), "classe" to sl.s("classe"), "matiere" to sl.s("matiere"), "salle" to sl.s("salle")) }),
                "etablissement" to (etabRef(e.s("etablissementId")) ?: JSONObject.NULL), "updatedAt" to e.s("updatedAt")) }),
            "taches" to JSONArray(Store.list("tasks").map { t ->
                val car = Store.list("documents").any { it.s("taskId") == t.s("id") && it.s("type") == "demande_voiture" }
                o("carnetId" to t.s("id"), "inspectappId" to opt(ext(t)), "type" to (TYPE_OUT[Ref.type(t)] ?: "Other"), "typeCarnet" to Ref.type(t), "statut" to (STATUS_OUT[Ref.statut(t)] ?: "Planned"), "statutCarnet" to Ref.statut(t),
                    "titre" to t.s("titre"), "date" to t.s("echeance"), "heureDebut" to t.s("heureDebut"), "heureFin" to t.s("heureFin"), "lieu" to t.s("lieu"), "description" to t.s("description"), "priorite" to t.s("priorite"),
                    "note" to t.s("noteGlobale"), "voitureService" to car,
                    "tableauxService" to (if (Ref.type(t) == "validation_ts") JSONArray(t.arr("feuilles").map { f -> val e = Store.byId("enseignants", f.s("enseignantId"))
                        o("enseignant" to o("carnetId" to opt(f.s("enseignantId")), "inspectappId" to opt(ext(e)), "ppr" to opt(f.s("ppr")), "nom" to f.s("nom")), "statut" to f.s("statut"), "observation" to f.s("observation")) }) else null),
                    "enseignant" to ((if (t.s("enseignantId").isNotEmpty() || t.s("enseignantNom").isNotEmpty()) ensRef(t.s("enseignantId"), t.s("enseignantNom")) else null) ?: JSONObject.NULL),
                    "etablissement" to (if (t.s("etablissementId").isNotEmpty()) etabRef(t.s("etablissementId")) ?: JSONObject.NULL else if (t.s("etablissementNom").isNotEmpty()) o("nom" to t.s("etablissementNom")) else JSONObject.NULL),
                    "participants" to JSONArray(t.arr("invites").map { iv -> (ensRef(iv.s("enseignantId"), iv.s("nom")) ?: JSONObject()).put("nom", iv.s("nom")).put("ppr", iv.s("ppr")).put("etablissement", iv.s("etablissementNom")) }),
                    "updatedAt" to t.s("updatedAt"))
            }))
    }

    data class Count(var add: Int = 0, var upd: Int = 0, var skip: Int = 0)
    data class Report(val zones: Count = Count(), val etabs: Count = Count(), val ens: Count = Count(), val taches: Count = Count()) {
        fun total() = listOf(zones, etabs, ens, taches).sumOf { it.add + it.upd }
        fun text() = listOf("Établissements" to etabs, "Enseignant(e)s" to ens, "Zones" to zones, "Tâches" to taches)
            .joinToString("\n") { (l, c) -> "$l : ${c.add} nouveau(x), ${c.upd} mis à jour" + if (c.skip > 0) ", ${c.skip} ignoré(s) (annulés)" else "" }
    }

    private fun newer(inc: JSONObject, loc: JSONObject) = inc.s("updatedAt").isNotEmpty() && (loc.s("updatedAt").isEmpty() || inc.s("updatedAt") > loc.s("updatedAt"))
    private fun merge(local: JSONObject, fields: Map<String, Any?>, force: Boolean): Boolean {
        var ch = false
        fields.forEach { (k, v) ->
            if (v == null || v == "") return@forEach
            val cur = if (local.has(k) && !local.isNull(k)) local.get(k).toString() else ""
            if ((cur.isEmpty() || force) && cur != v.toString()) { local.put(k, v); ch = true }
        }
        return ch
    }
    private fun setExt(local: JSONObject, inc: JSONObject): Boolean {
        val id = inc.s("inspectappId").ifEmpty { if (inc.s("source") != "carnet") inc.s("id") else "" }
        if (id.isNotEmpty() && ext(local) != id) { (local.optJSONObject("extIds") ?: JSONObject().also { local.put("extIds", it) }).put("inspectapp", id); return true }
        return false
    }
    private fun findBy(arr: List<JSONObject>, inc: JSONObject, natural: () -> JSONObject?): JSONObject? {
        val iid = inc.s("inspectappId").ifEmpty { inc.s("id") }
        return (if (inc.s("carnetId").isNotEmpty()) arr.firstOrNull { it.s("id") == inc.s("carnetId") } else null)
            ?: (if (iid.isNotEmpty()) arr.firstOrNull { ext(it) == iid } else null) ?: natural()
    }
    private fun findEtab(etabs: List<JSONObject>, r: JSONObject?): JSONObject? {
        if (r == null) return null
        val keys = listOf(r.s("nom"), r.s("nomAr"), r.s("nomFr")).filter { it.isNotEmpty() }.map { key(it) }
        return findBy(etabs, r) {
            (if (r.s("codeMassar").isNotEmpty()) etabs.firstOrNull { it.s("codeMassar").equals(r.s("codeMassar"), true) } else null)
                ?: etabs.firstOrNull { e -> listOf(e.s("nom"), e.s("nomAr"), e.s("nomFr")).filter { it.isNotEmpty() }.any { key(it) in keys } }
        }
    }
    private fun findEns(ens: List<JSONObject>, r: JSONObject?, etabId: String?): JSONObject? {
        if (r == null) return null
        val keys = listOf(r.s("nom"), r.s("nomAr"), r.s("nomFr")).filter { it.isNotEmpty() }.map { key(it) }
        return findBy(ens, r) {
            (if (r.s("ppr").isNotEmpty()) ens.firstOrNull { it.s("ppr").trim() == r.s("ppr").trim() } else null)
                ?: ens.firstOrNull { x -> (etabId == null || x.s("etablissementId").isEmpty() || x.s("etablissementId") == etabId) && listOf(x.s("nom"), x.s("nomAr"), x.s("nomFr")).filter { it.isNotEmpty() }.any { key(it) in keys } }
        }
    }

    /** Analyse (dry = true) ou applique un fichier d'échange. */
    fun apply(data: JSONObject, dry: Boolean): Report {
        if (data.s("format") != FORMAT) throw Exception("Ce fichier n'est pas un fichier d'échange Carnet ↔ InspectApp.")
        val rep = Report()
        val zones = Store.list("zones").map { JSONObject(it.toString()) }.toMutableList()
        val etabs = Store.list("etablissements").map { JSONObject(it.toString()) }.toMutableList()
        val ens = Store.list("enseignants").map { JSONObject(it.toString()) }.toMutableList()
        val tasks = Store.list("tasks").map { JSONObject(it.toString()) }.toMutableList()
        val changed = mutableMapOf<String, MutableList<JSONObject>>()
        fun put(coll: String, r: JSONObject) { changed.getOrPut(coll) { mutableListOf() }.let { l -> if (l.none { it === r }) l.add(r) } }
        val zoneMap = mutableMapOf<String, String>()
        data.arr("zones").forEach { z ->
            if (z.s("nom").isEmpty()) return@forEach
            var loc = findBy(zones, z) { zones.firstOrNull { key(it.s("nom")) == key(z.s("nom")) } }
            val isNew = loc == null
            if (loc == null) { loc = JSONObject().put("id", UUID.randomUUID().toString()).put("nom", z.s("nom")); zones.add(loc) }
            var ch = merge(loc, mapOf("nom" to z.s("nom"), "academieRegionale" to z.s("academie"), "directionProvinciale" to z.s("direction"), "commune" to z.s("commune"), "cycle" to z.s("cycle")), isNew || newer(z, loc))
            ch = setExt(loc, z) || ch
            if (isNew) { rep.zones.add++; put("zones", loc) } else if (ch) { rep.zones.upd++; put("zones", loc) }
            zoneMap[z.s("carnetId").ifEmpty { z.s("inspectappId").ifEmpty { z.s("id").ifEmpty { z.s("nom") } } }] = loc.s("id")
        }
        val etabMap = mutableMapOf<String, String>()
        data.arr("etablissements").forEach { e ->
            if (listOf("nom", "nomAr", "nomFr").all { e.s(it).isEmpty() }) return@forEach
            var loc = findEtab(etabs, e); val isNew = loc == null
            if (loc == null) { loc = JSONObject().put("id", UUID.randomUUID().toString()).put("nom", e.s("nom").ifEmpty { e.s("nomFr").ifEmpty { e.s("nomAr") } }).put("type", "public"); etabs.add(loc) }
            val zr = e.optJSONObject("zone")
            val zid = zr?.let { zoneMap[it.s("carnetId").ifEmpty { it.s("inspectappId").ifEmpty { it.s("id").ifEmpty { it.s("nom") } } }] ?: zones.firstOrNull { x -> it.s("nom").isNotEmpty() && key(x.s("nom")) == key(it.s("nom")) }?.s("id") }
            var ch = merge(loc, mapOf("nomAr" to e.s("nomAr").takeIf { arabic(it) }, "nomFr" to e.s("nomFr").takeIf { it.isNotEmpty() && !arabic(it) }, "codeMassar" to e.s("codeMassar"), "type" to e.s("type"),
                "cycle" to e.s("cycle"), "adresse" to e.s("adresse"), "telephone" to e.s("telephone"), "directeur" to e.s("directeur"), "directeurTel" to e.s("directeurTel"),
                "latitude" to e.s("latitude"), "longitude" to e.s("longitude"), "zoneId" to zid), isNew || newer(e, loc))
            if (e.optBoolean("pionnier") && !loc.optBoolean("pionnier")) { loc.put("pionnier", true); ch = true }
            ch = setExt(loc, e) || ch
            if (isNew) { rep.etabs.add++; put("etablissements", loc) } else if (ch) { rep.etabs.upd++; put("etablissements", loc) }
            etabMap[e.s("carnetId").ifEmpty { e.s("inspectappId").ifEmpty { e.s("id").ifEmpty { e.s("codeMassar").ifEmpty { e.s("nom") } } } }] = loc.s("id")
        }
        fun resolveEtab(r: JSONObject?): String? { if (r == null) return null; val k = r.s("carnetId").ifEmpty { r.s("inspectappId").ifEmpty { r.s("id").ifEmpty { r.s("codeMassar").ifEmpty { r.s("nom") } } } }; return etabMap[k] ?: findEtab(etabs, r)?.s("id") }
        data.arr("enseignants").forEach { e ->
            if (listOf("nom", "nomAr", "nomFr").all { e.s(it).isEmpty() }) return@forEach
            val etId = resolveEtab(e.optJSONObject("etablissement"))
            var loc = findEns(ens, e, etId); val isNew = loc == null
            if (loc == null) { loc = JSONObject().put("id", UUID.randomUUID().toString()).put("nom", e.s("nom").ifEmpty { e.s("nomFr").ifEmpty { e.s("nomAr") } }).put("statut", "titulaire").put("horaire", JSONArray()); ens.add(loc) }
            val g = e.s("genre").lowercase()
            val genre = if (Regex("^(f|femme|female|feminin|féminin|mme|madame)$").matches(g)) "F" else if (Regex("^(h|m|homme|male|masculin|m\\.|monsieur)$").matches(g)) "H" else ""
            var ch = merge(loc, mapOf("nomAr" to e.s("nomAr").takeIf { arabic(it) }, "nomFr" to e.s("nomFr").takeIf { it.isNotEmpty() && !arabic(it) }, "ppr" to e.s("ppr"), "genre" to genre,
                "matiere" to e.s("matiere"), "cycle" to e.s("cycle"), "cadre" to e.s("cadre"), "statut" to e.s("statut"), "sessionTitularisation" to e.s("sessionTitularisation"),
                "telephone" to e.s("telephone"), "email" to e.s("email"), "etablissementId" to etId), isNew || newer(e, loc))
            val hor = e.arr("horaire").filter { it.s("jour").isNotEmpty() && it.s("debut").isNotEmpty() && it.s("fin").isNotEmpty() }
            if (hor.isNotEmpty() && (loc.arr("horaire").isEmpty() || newer(e, loc))) { val ja = JSONArray(hor.map { o("jour" to it.s("jour"), "debut" to it.s("debut"), "fin" to it.s("fin"), "classe" to it.s("classe"), "matiere" to it.s("matiere"), "salle" to it.s("salle")) }); if (ja.toString() != loc.optJSONArray("horaire")?.toString()) { loc.put("horaire", ja); ch = true } }
            if (e.s("notes").isNotEmpty()) { val have = loc.s("notes"); val add = e.s("notes").lines().map { it.trim() }.filter { it.isNotEmpty() && !have.contains(it) }; if (add.isNotEmpty()) { loc.put("notes", (if (have.isNotEmpty()) have.trimEnd() + "\n" else "") + add.joinToString("\n")); ch = true } }
            ch = setExt(loc, e) || ch
            if (isNew) { rep.ens.add++; put("enseignants", loc) } else if (ch) { rep.ens.upd++; put("enseignants", loc) }
        }
        data.arr("taches").forEach { t ->
            val type = t.s("typeCarnet").takeIf { it in Ref.TASK_TYPES } ?: (TYPE_IN[code(t.s("type"))] ?: "autre")
            val statut = t.s("statutCarnet").takeIf { it in Ref.WORKFLOW } ?: STATUS_IN[code(t.s("statut"))]
            val cancelled = Regex("(?i)cancel|annul").containsMatchIn(t.s("statut"))
            val etId = resolveEtab(t.optJSONObject("etablissement"))
            val en = findEns(ens, t.optJSONObject("enseignant"), etId)
            val date = t.s("date").take(10)
            var loc = findBy(tasks, t) { tasks.firstOrNull { x -> Ref.type(x) == type && x.s("echeance").isNotEmpty() && x.s("echeance") == date && ((en != null && x.s("enseignantId") == en.s("id")) || (en == null && etId != null && x.s("etablissementId") == etId) || (en == null && etId == null && key(x.s("titre")) == key(t.s("titre")))) } }
            if (loc == null && cancelled) { rep.taches.skip++; return@forEach }
            val isNew = loc == null
            if (loc == null) { loc = JSONObject().put("id", UUID.randomUUID().toString()).put("titre", "").put("statut", "a_planifier").put("priorite", "moyenne").put("taskType", type).put("invites", JSONArray()).put("attachments", JSONArray()); tasks.add(loc) }
            val et = etabs.firstOrNull { it.s("id") == etId }
            val force = isNew || newer(t, loc) || cancelled
            var ch = merge(loc, mapOf("taskType" to type, "statut" to (if (cancelled) null else statut), "titre" to t.s("titre"), "echeance" to date, "heureDebut" to t.s("heureDebut"), "heureFin" to t.s("heureFin"),
                "lieu" to t.s("lieu"), "description" to (if (cancelled) (loc.s("description") + if (loc.s("description").contains("[Annulée")) "" else "\n[Annulée dans InspectApp]").trim() else t.s("description")),
                "priorite" to t.s("priorite"), "noteGlobale" to t.s("note").takeIf { it in Ref.NOTE_SCALE }, "enseignantId" to en?.s("id"),
                "enseignantNom" to (en?.s("nom") ?: t.optJSONObject("enseignant")?.s("nom")), "etablissementId" to etId, "etablissementNom" to (et?.s("nom") ?: t.optJSONObject("etablissement")?.s("nom"))), force)
            val parts = t.arr("participants")
            if (parts.isNotEmpty() && (isNew || newer(t, loc) || loc.arr("invites").isEmpty())) {
                val inv = JSONArray(parts.map { pp -> val r = findEns(ens, pp, null); o("enseignantId" to (r?.s("id") ?: JSONObject.NULL), "nom" to (r?.s("nom") ?: pp.s("nom")), "ppr" to (r?.s("ppr")?.ifEmpty { null } ?: pp.s("ppr")),
                    "etablissementNom" to (pp.optJSONObject("etablissement")?.s("nom") ?: pp.s("etablissement").ifEmpty { r?.let { etabName(it.s("etablissementId")) } ?: "" })) })
                if (inv.toString() != loc.optJSONArray("invites")?.toString()) { loc.put("invites", inv); ch = true }
            }
            val ts = t.arr("tableauxService")
            if (ts.isNotEmpty() && (isNew || newer(t, loc) || loc.arr("feuilles").isEmpty())) {
                val fs = JSONArray(ts.map { x -> val r = findEns(ens, x.optJSONObject("enseignant") ?: JSONObject(), etId); val st = x.s("statut").lowercase()
                    o("enseignantId" to (r?.s("id") ?: JSONObject.NULL), "nom" to (r?.s("nom") ?: x.optJSONObject("enseignant")?.s("nom") ?: ""), "ppr" to (r?.s("ppr")?.ifEmpty { null } ?: x.optJSONObject("enseignant")?.s("ppr") ?: ""),
                        "statut" to (if (Regex("non|invalid|reject|refus").containsMatchIn(st)) "non_validee" else if (Regex("valid|approv|ok").containsMatchIn(st)) "validee" else "en_attente"), "observation" to x.s("observation")) })
                if (fs.toString() != loc.optJSONArray("feuilles")?.toString()) { loc.put("feuilles", fs); ch = true }
            }
            if (loc.s("titre").isEmpty()) { loc.put("titre", (Ref.TASK_TYPES[type] ?: "Tâche") + listOf(loc.s("enseignantNom"), loc.s("etablissementNom")).filter { it.isNotEmpty() }.joinToString("") { " · $it" }); ch = true }
            ch = setExt(loc, t) || ch
            if (isNew) { rep.taches.add++; put("tasks", loc) } else if (ch) { rep.taches.upd++; put("tasks", loc) }
        }
        if (!dry) changed.forEach { (coll, list) -> list.forEach { Store.upsert(coll, it) } }
        return rep
    }

    private fun p(ctx: Context) = ctx.getSharedPreferences("carnet", Context.MODE_PRIVATE)
    fun lastPush(ctx: Context) = p(ctx).getString("xchLastPush", "") ?: ""
    fun lastImport(ctx: Context) = p(ctx).getString("xchLastImport", "") ?: ""
    fun markPush(ctx: Context) = p(ctx).edit().putString("xchLastPush", Store.now()).apply()
    fun markImport(ctx: Context) = p(ctx).edit().putString("xchLastImport", Store.now()).apply()
    fun auto(ctx: Context) = p(ctx).getBoolean("xchAuto", false)
    fun setAuto(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("xchAuto", v).apply()
    fun linked(): Triple<Int, Int, Int> = Triple(Store.list("etablissements").count { ext(it).isNotEmpty() }, Store.list("enseignants").count { ext(it).isNotEmpty() }, Store.list("tasks").count { ext(it).isNotEmpty() })

    /** Échange par Google Drive : lit le fichier d'InspectApp (s'il existe) puis dépose celui du carnet. */
    fun driveSync(ctx: Context, tk: String, importNow: Boolean): Pair<Report?, Boolean> {
        val folder = DriveSync.subFolder(tk, FOLDER)
        var rep: Report? = null
        val inF = DriveSync.findInFolder(tk, folder, FILE_IN)
        if (inF != null && importNow) {
            val data = JSONObject(String(DriveSync.download(tk, inF.getString("id")), Charsets.UTF_8).removePrefix("﻿"))
            rep = apply(data, false); markImport(ctx)
        }
        val bytes = payload().toString(2).toByteArray(Charsets.UTF_8)
        val outF = DriveSync.findInFolder(tk, folder, FILE_OUT)
        if (outF != null) DriveSync.updateMedia(tk, outF.getString("id"), bytes, "application/json; charset=UTF-8")
        else DriveSync.uploadBytes(tk, folder, FILE_OUT, "application/json", bytes)
        markPush(ctx)
        return rep to (inF != null)
    }
}
