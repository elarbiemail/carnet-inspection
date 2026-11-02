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
        for (i in 0 until a.length()) {
            if (a.optJSONObject(i)?.optString("id") == rec.optString("id")) { a.put(i, rec); replaced = true; break }
        }
        if (!replaced) a.put(rec)
        save()
    }

    fun delete(coll: String, id: String) {
        val a = root.optJSONArray(coll) ?: return
        val out = JSONArray()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            if (o.optString("id") != id) out.put(o)
        }
        root.put(coll, out)
        root.getJSONObject("tombstones").getJSONObject(coll).put(id, now())
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
                if (tomb.isNotEmpty() && tomb >= o.s("updatedAt")) removed++ else out.put(o)
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
