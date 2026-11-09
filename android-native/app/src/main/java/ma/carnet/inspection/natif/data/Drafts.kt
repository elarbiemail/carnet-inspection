package ma.carnet.inspection.natif.data

import android.content.Context
import org.json.JSONObject

/** Brouillons automatiques des formulaires (fiche, tâche, document) : repris si l'application est fermée avant l'enregistrement. */
object Drafts {
    private fun p(ctx: Context) = ctx.getSharedPreferences("carnet_drafts", Context.MODE_PRIVATE)
    fun save(ctx: Context, key: String, rec: JSONObject) { try { p(ctx).edit().putString(key, rec.toString()).apply() } catch (_: Exception) {} }
    fun load(ctx: Context, key: String): JSONObject? = try { p(ctx).getString(key, null)?.let { JSONObject(it) } } catch (_: Exception) { null }
    fun clear(ctx: Context, key: String) { try { p(ctx).edit().remove(key).apply() } catch (_: Exception) {} }
}
