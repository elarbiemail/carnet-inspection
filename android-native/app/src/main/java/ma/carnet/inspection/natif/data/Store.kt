package ma.carnet.inspection.natif.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * Données du carnet, au même format que la sauvegarde JSON du carnet web
 * (sauvegarde-donnees.json) : zones, etablissements, enseignants, tasks, documents,
 * vacances, templates, programmeAnnuel, tombstones…
 *
 * Les enregistrements restent des JSONObject : les champs que l'application native ne
 * connaît pas sont conservés tels quels, pour que l'aller-retour avec le carnet web ne
 * perde rien. Chaque modification met à jour updatedAt ; chaque suppression laisse une
 * « pierre tombale » (tombstones), exactement comme le carnet web, pour que la fusion
 * entre appareils fonctionne dans les deux sens.
 */
object Store {
    val COLLECTIONS = listOf("zones", "etablissements", "enseignants", "tasks", "documents", "vacances")

    private var file: File? = null
    private var prefs: android.content.SharedPreferences? = null
    var root: JSONObject = JSONObject()
        private set

    /** Incrémenté à chaque modification : les écrans le lisent pour se redessiner. */
    val version = mutableIntStateOf(0)

    /** Filtre global de zones (identifiants ; "__none" = établissements sans zone). Vide = toutes. */
    var zoneFilter: Set<String> = emptySet()
        private set

    fun init(ctx: Context) {
        val f = File(ctx.filesDir, "carnet.json")
        file = f
        prefs = ctx.getSharedPreferences("carnet", Context.MODE_PRIVATE)
        root = try {
            if (f.exists()) JSONObject(f.readText()) else JSONObject()
        } catch (e: Exception) {
            JSONObject()
        }
        ensureShape()
        zoneFilter = prefs?.getStringSet("zoneFilter", emptySet())?.toSet() ?: emptySet()
        bump()
    }

    private fun ensureShape() {
        COLLECTIONS.forEach { if (!root.has(it) || root.isNull(it)) root.put(it, JSONArray()) }
        if (!root.has("tombstones") || root.isNull("tombstones")) root.put("tombstones", JSONObject())
        val tb = root.getJSONObject("tombstones")
        COLLECTIONS.forEach { if (!tb.has(it)) tb.put(it, JSONObject()) }
        if (!root.has("templates") || root.isNull("templates")) root.put("templates", JSONObject())
        if (!root.has("journal") || root.isNull("journal")) root.put("journal", JSONArray())
        if (!root.has("archive") || root.isNull("archive")) root.put("archive", JSONObject())
    }

    private fun bump() { version.intValue = version.intValue + 1 }

    fun save() {
        try { file?.writeText(root.toString()) } catch (_: Exception) {}
        bump()
    }

    fun now(): String = Instant.now().toString()

    fun isEmpty(): Boolean = COLLECTIONS.all { (root.optJSONArray(it)?.length() ?: 0) == 0 }

    fun list(coll: String): List<JSONObject> {
        val a = root.optJSONArray(coll) ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }

    fun byId(coll: String, id: String?): JSONObject? {
        if (id.isNullOrEmpty()) return null
        return list(coll).firstOrNull { it.optString("id") == id }
    }

    /** Ajoute ou remplace un enregistrement (par id) et enregistre. */
    fun upsert(coll: String, rec: JSONObject) {
        val ts = now()
        if (rec.s("id").isEmpty()) rec.put("id", UUID.randomUUID().toString())
        if (rec.s("createdAt").isEmpty()) rec.put("createdAt", ts)
        rec.put("updatedAt", ts)
        val a = root.optJSONArray(coll) ?: JSONArray().also { root.put(coll, it) }
        var replaced = false
        var old: JSONObject? = null
        for (i in 0 until a.length()) {
            if (a.optJSONObject(i)?.optString("id") == rec.optString("id")) { old = JSONObject(a.optJSONObject(i).toString()); a.put(i, rec); replaced = true; break }
        }
        if (!replaced) a.put(rec)
        if (old == null) log("création", coll, rec)
        else { val d = diff(old, rec); if (d.isNotEmpty()) log("modification", coll, rec, d) }
        save()
    }

    fun delete(coll: String, id: String) {
        val a = root.optJSONArray(coll) ?: return
        val out = JSONArray()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            if (o.optString("id") != id) out.put(o) else { archive(coll, o); log("suppression", coll, o) }
        }
        root.put(coll, out)
        root.getJSONObject("tombstones").getJSONObject(coll).put(id, now())
        save()
    }

    // ---------- journal permanent et archives ----------
    /** Opérations enregistrées (création, modification, suppression, restauration, synchronisation) : jamais effacées. */
    fun journal(): List<JSONObject> {
        val a = root.optJSONArray("journal") ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }

    fun historyOf(id: String): List<JSONObject> = journal().filter { it.optString("recId") == id }.sortedByDescending { it.optString("t") }

    fun labelOf(coll: String, rec: JSONObject): String = when (coll) {
        "documents" -> Docs.label(rec) + (if (rec.s("beneficiaireNom").isNotEmpty()) " — " + rec.s("beneficiaireNom") else "")
        "tasks" -> rec.s("titre").ifEmpty { autoTaskTitle(rec) }
        else -> rec.s("nom").ifEmpty { rec.s("titre") }.ifEmpty { rec.s("id").take(8) }
    }

    fun log(op: String, coll: String, rec: JSONObject?, detail: String = "") {
        val a = root.optJSONArray("journal") ?: JSONArray().also { root.put("journal", it) }
        a.put(JSONObject().put("id", UUID.randomUUID().toString()).put("t", now()).put("op", op).put("coll", coll)
            .put("recId", rec?.optString("id") ?: "").put("label", if (rec != null) labelOf(coll, rec) else "").put("detail", detail).put("source", "natif"))
    }

    private val DIFF_SKIP = setOf("updatedAt", "createdAt", "attachments")
    private fun diff(old: JSONObject, new: JSONObject): String {
        val keys = (old.keys().asSequence().toList() + new.keys().asSequence().toList()).toSet() - DIFF_SKIP
        val out = mutableListOf<String>()
        for (k in keys.sorted()) {
            val a = if (old.isNull(k)) "" else old.opt(k).toString()
            val b = if (new.isNull(k)) "" else new.opt(k).toString()
            if (a == b) continue
            val complex = a.startsWith("[") || a.startsWith("{") || b.startsWith("[") || b.startsWith("{") || a.length > 60 || b.length > 60
            out.add(if (complex) k else "$k : ${a.take(40)} → ${b.take(40)}")
        }
        return out.take(12).joinToString("\n")
    }

    /** Fiches supprimées (conservées en permanence pour consultation et restauration). */
    fun archived(coll: String): List<JSONObject> {
        val a = root.optJSONObject("archive")?.optJSONArray(coll) ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.sortedByDescending { it.optString("deletedAt") }
    }
    fun archivedById(coll: String, id: String?): JSONObject? = if (id.isNullOrEmpty()) null else archived(coll).firstOrNull { it.optString("id") == id }

    private fun archive(coll: String, rec: JSONObject) {
        val ar = root.optJSONObject("archive") ?: JSONObject().also { root.put("archive", it) }
        val a = ar.optJSONArray(coll) ?: JSONArray().also { ar.put(coll, it) }
        val copy = JSONObject(rec.toString()); copy.put("deletedAt", now())
        val out = JSONArray()
        for (i in 0 until a.length()) { val o = a.optJSONObject(i) ?: continue; if (o.optString("id") != rec.optString("id")) out.put(o) }
        out.put(copy); ar.put(coll, out)
    }

    /** Remet une fiche supprimée dans le carnet. */
    fun restore(coll: String, id: String): Boolean {
        val rec = archivedById(coll, id) ?: return false
        val copy = JSONObject(rec.toString()); copy.remove("deletedAt"); copy.put("updatedAt", now())
        root.optJSONObject("tombstones")?.optJSONObject(coll)?.remove(id)
        val a = root.optJSONArray(coll) ?: JSONArray().also { root.put(coll, it) }
        a.put(copy)
        val ar = root.optJSONObject("archive")?.optJSONArray(coll)
        if (ar != null) { val out = JSONArray(); for (i in 0 until ar.length()) { val o = ar.optJSONObject(i) ?: continue; if (o.optString("id") != id) out.put(o) }; root.getJSONObject("archive").put(coll, out) }
        log("restauration", coll, copy)
        save()
        return true
    }

    /** Fusionne le journal et les archives d'une autre sauvegarde (union, sans doublon). */
    private fun mergeLog(remote: JSONObject) {
        val byId = LinkedHashMap<String, JSONObject>()
        journal().forEach { byId[it.optString("id")] = it }
        remote.optJSONArray("journal")?.let { ra -> for (i in 0 until ra.length()) ra.optJSONObject(i)?.let { if (it.optString("id").isNotEmpty() && it.optString("id") !in byId) byId[it.optString("id")] = it } }
        val sorted = JSONArray(); byId.values.sortedBy { it.optString("t") }.forEach { sorted.put(it) }
        root.put("journal", sorted)
        val rar = remote.optJSONObject("archive") ?: return
        val ar = root.optJSONObject("archive") ?: JSONObject().also { root.put("archive", it) }
        rar.keys().forEach { coll ->
            val ra = rar.optJSONArray(coll) ?: return@forEach
            val live = list(coll).map { it.s("id") }.toSet()
            val cur = LinkedHashMap<String, JSONObject>()
            ar.optJSONArray(coll)?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { cur[it.optString("id")] = it } }
            for (i in 0 until ra.length()) {
                val r = ra.optJSONObject(i) ?: continue; val rid = r.optString("id")
                if (rid.isEmpty() || rid in live) continue
                val l = cur[rid]
                if (l == null || r.optString("deletedAt") > l.optString("deletedAt")) cur[rid] = r
            }
            ar.put(coll, JSONArray().also { out -> cur.values.forEach { out.put(it) } })
        }
    }

    /** Supprime plusieurs enregistrements d'un coup (une seule sauvegarde, une pierre tombale chacun). */
    fun deleteMany(coll: String, ids: Collection<String>) {
        if (ids.isEmpty()) return
        val set = ids.toSet()
        val a = root.optJSONArray(coll) ?: return
        val out = JSONArray()
        val ts = now()
        val tb = root.getJSONObject("tombstones").getJSONObject(coll)
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            if (o.optString("id") in set) { tb.put(o.optString("id"), ts); archive(coll, o); log("suppression", coll, o, "Suppression en lot") } else out.put(o)
        }
        root.put(coll, out)
        save()
    }

    /** Modifie plusieurs enregistrements d'un coup (updatedAt mis à jour pour chacun). */
    fun updateMany(coll: String, ids: Collection<String>, change: (JSONObject) -> Unit) {
        if (ids.isEmpty()) return
        val set = ids.toSet()
        val a = root.optJSONArray(coll) ?: return
        val ts = now()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            if (o.optString("id") in set) {
                val before = JSONObject(o.toString())
                change(o); o.put("updatedAt", ts)
                val d = diff(before, o); if (d.isNotEmpty()) log("modification", coll, o, "Modification en lot\n$d")
            }
        }
        save()
    }

    fun profil(): JSONObject = root.optJSONObject("templates")?.optJSONObject("profil") ?: JSONObject()

    // ---------- filtre de zones ----------
    fun setZoneFilter(ids: Set<String>) {
        zoneFilter = ids
        prefs?.edit()?.putStringSet("zoneFilter", ids)?.apply()
        bump()
    }

    private fun etabInScope(e: JSONObject, known: Set<String>): Boolean {
        if (zoneFilter.isEmpty()) return true
        val z = e.s("zoneId")
        return if (z.isNotEmpty() && z in known) z in zoneFilter else "__none" in zoneFilter
    }

    fun etabs(): List<JSONObject> {
        val known = list("zones").map { it.s("id") }.toSet()
        return list("etablissements").filter { etabInScope(it, known) }
    }

    fun enseignants(): List<JSONObject> {
        if (zoneFilter.isEmpty()) return list("enseignants")
        val ids = etabs().map { it.s("id") }.toSet()
        val allEtabs = list("etablissements").map { it.s("id") }.toSet()
        return list("enseignants").filter {
            val e = it.s("etablissementId")
            if (e.isNotEmpty() && e in allEtabs) e in ids else "__none" in zoneFilter
        }
    }

    fun tasks(): List<JSONObject> {
        if (zoneFilter.isEmpty()) return list("tasks")
        val etIds = etabs().map { it.s("id") }.toSet()
        val enIds = enseignants().map { it.s("id") }.toSet()
        return list("tasks").filter { t ->
            var linked = false; var inScope = false
            if (t.s("etablissementId").isNotEmpty()) { linked = true; if (t.s("etablissementId") in etIds) inScope = true }
            if (t.s("enseignantId").isNotEmpty()) { linked = true; if (t.s("enseignantId") in enIds) inScope = true }
            val inv = t.optJSONArray("invites")
            if (inv != null) for (i in 0 until inv.length()) {
                val id = inv.optJSONObject(i)?.s("enseignantId") ?: ""
                if (id.isNotEmpty()) { linked = true; if (id in enIds) inScope = true }
            }
            if (linked) inScope else "__none" in zoneFilter
        }
    }

    // ---------- import / export ----------
    data class ImportReport(val added: Int, val updated: Int, val removed: Int)

    /**
     * Fusionne une sauvegarde du carnet web (ou de cette application) avec les données locales :
     * enregistrement par enregistrement, le plus récent (updatedAt) l'emporte ; les suppressions
     * (tombstones) des deux côtés s'appliquent. Même logique que le carnet web.
     */
    fun importBackup(remote: JSONObject): ImportReport {
        var added = 0; var updated = 0; var removed = 0
        val localTb = root.getJSONObject("tombstones")
        val remoteTb = remote.optJSONObject("tombstones") ?: JSONObject()
        for (coll in COLLECTIONS) {
            val lt = localTb.optJSONObject(coll) ?: JSONObject().also { localTb.put(coll, it) }
            val rt = remoteTb.optJSONObject(coll) ?: JSONObject()
            rt.keys().forEach { k ->
                val rv = rt.optString(k); val lv = lt.optString(k, "")
                if (lv.isEmpty() || rv > lv) lt.put(k, rv)
            }
            val local = LinkedHashMap<String, JSONObject>()
            list(coll).forEach { local[it.s("id")] = it }
            val ra = remote.optJSONArray(coll)
            if (ra != null) for (i in 0 until ra.length()) {
                val r = ra.optJSONObject(i) ?: continue
                val id = r.s("id"); if (id.isEmpty()) continue
                val tomb = lt.optString(id, "")
                if (tomb.isNotEmpty() && tomb >= r.s("updatedAt")) continue
                val l = local[id]
                if (l == null) { local[id] = r; added++ }
                else if (r.s("updatedAt") > l.s("updatedAt")) { local[id] = r; updated++ }
            }
            val out = JSONArray()
            local.values.forEach { o ->
                val tomb = lt.optString(o.s("id"), "")
                if (tomb.isNotEmpty() && tomb >= o.s("updatedAt")) { removed++; archive(coll, o); log("suppression", coll, o, "Suppression reçue par synchronisation") } else out.put(o)
            }
            root.put(coll, out)
        }
        // Objets « singleton » (modèles, programme annuel, référentiel…) : le plus récent l'emporte.
        listOf("templates", "programmeAnnuel", "ficheForm").forEach { k ->
            val r = remote.optJSONObject(k) ?: return@forEach
            val l = root.optJSONObject(k)
            if (l == null || l.length() == 0 || r.s("updatedAt") > l.s("updatedAt")) root.put(k, r)
        }
        remote.optJSONObject("assetsIndex")?.let { ra ->
            val la = root.optJSONObject("assetsIndex") ?: JSONObject().also { root.put("assetsIndex", it) }
            ra.keys().forEach { k -> if (!la.has(k)) la.put(k, ra.get(k)) }
        }
        listOf("regionsMaroc", "regionsMarocUpdatedAt").forEach { k ->
            if (remote.has(k) && (!root.has(k) || root.isNull(k))) root.put(k, remote.get(k))
        }
        mergeLog(remote)
        if (added + updated + removed > 0) log("synchronisation", "", null, "Fusion de données : $added ajout(s), $updated mise(s) à jour, $removed suppression(s)")
        save()
        return ImportReport(added, updated, removed)
    }

    /** Sauvegarde complète, au format attendu par le carnet web (Paramètres → InspectApp → Importer). */
    fun exportJson(): String {
        val copy = JSONObject(root.toString())
        copy.put("exportedAt", now())
        copy.put("source", "carnet-natif")
        return copy.toString(2)
    }
}

/** Champ texte sans « null ». */
fun JSONObject.s(k: String): String = if (!has(k) || isNull(k)) "" else optString(k, "")

fun JSONObject.arr(k: String): List<JSONObject> {
    val a = optJSONArray(k) ?: return emptyList()
    return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
}
