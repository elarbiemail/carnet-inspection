package ma.carnet.inspection.natif.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Assistant de rédaction : Google Gemini avec la clé personnelle de l'utilisateur (comme le carnet web). */
object Ai {
    const val MODEL = "gemini-flash-latest"
    fun key(ctx: Context) = ctx.getSharedPreferences("carnet", Context.MODE_PRIVATE).getString("geminiKey", "") ?: ""
    fun setKey(ctx: Context, k: String) = ctx.getSharedPreferences("carnet", Context.MODE_PRIVATE).edit().putString("geminiKey", k.trim()).apply()
    fun ready(ctx: Context) = key(ctx).isNotBlank()

    suspend fun generate(ctx: Context, prompt: String): String = withContext(Dispatchers.IO) {
        val k = key(ctx); if (k.isBlank()) throw Exception("Ajoutez votre clé Gemini dans Plus → Paramètres.")
        val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 20000; c.readTimeout = 120000
        c.setRequestProperty("Content-Type", "application/json; charset=UTF-8"); c.setRequestProperty("x-goog-api-key", k)
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
        c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
        if (code !in 200..299) {
            val msg = try { JSONObject(txt).optJSONObject("error")?.optString("message") ?: txt } catch (e: Exception) { txt }
            throw Exception(when (code) { 400, 403 -> "Clé Gemini refusée ou invalide."; 429 -> "Quota Gemini atteint, réessayez plus tard."; 503 -> "Gemini est surchargé, réessayez dans un instant."; else -> "Gemini ($code) : $msg" })
        }
        val parts = JSONObject(txt).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        val out = StringBuilder()
        for (i in 0 until parts.length()) { val p = parts.optJSONObject(i) ?: continue; if (!p.optBoolean("thought")) out.append(p.optString("text")) }
        out.toString().trim().ifEmpty { throw Exception("Réponse vide de Gemini.") }
    }

    /** Contexte d'une tâche, pour les consignes IA. */
    fun taskContext(t: JSONObject): String {
        val et = Store.byId("etablissements", t.s("etablissementId"))
        return listOf(
            if (t.s("titre").isNotEmpty()) "Tâche : ${t.s("titre")} (${Ref.TASK_TYPES[Ref.type(t)] ?: ""})" else "",
            if (t.s("echeance").isNotEmpty()) "Date : ${fmtDate(t.s("echeance"))}" + (if (taskTime(t).isNotEmpty()) " à ${taskTime(t)}" else "") else "",
            (t.s("lieu").ifEmpty { taskEtabName(t) }).let { if (it.isNotEmpty()) "Lieu / établissement : $it" else "" },
            et?.s("directeur")?.let { if (it.isNotEmpty()) "Directeur(trice) : $it" else "" } ?: "",
            taskEnsName(t).let { if (it.isNotEmpty()) "Enseignant(e) : $it" else "" },
            t.arr("invites").let { if (it.isNotEmpty()) "Invité(e)s (${it.size}) : " + it.joinToString(", ") { i -> i.s("nom") } else "" },
            t.optJSONObject("formation")?.s("sujet")?.let { if (it.isNotEmpty()) "Thème de la formation : $it" else "" } ?: "",
            t.optJSONObject("formation")?.s("deroulement")?.let { if (it.isNotEmpty()) "Déroulement : $it" else "" } ?: "",
            if (t.s("description").isNotEmpty()) "Notes de l'inspecteur(trice) :\n${t.s("description")}" else ""
        ).filter { it.isNotEmpty() }.joinToString("\n")
    }

    fun fieldPrompt(doc: JSONObject, fieldLabel: String, fieldKey: String, task: JSONObject, rtl: Boolean): String {
        val p = Store.profil()
        val others = Docs.fields(doc.s("type")).filter { it.key != fieldKey && it.kind == Docs.Kind.AREA && doc.s(it.key).isNotBlank() }
            .joinToString("\n") { "${it.label} (déjà rédigé) : ${doc.s(it.key).take(800)}" }
        val existing = doc.s(fieldKey).trim()
        return "Tu es inspecteur(trice) pédagogique" + (if (p.s("matiere").isNotEmpty()) " de ${p.s("matiere")}" else "") + " au Maroc. " +
            "Rédige la rubrique « $fieldLabel » du document « ${Docs.label(doc)} », " + (if (rtl) "en arabe standard moderne" else "en français") +
            ", sur un ton administratif sobre. Appuie-toi uniquement sur les éléments ci-dessous, sans inventer de noms, de chiffres ni de faits. Retourne uniquement le texte de la rubrique, sans titre." +
            (if (existing.isNotEmpty()) "\n\nNotes ou brouillon déjà saisis pour cette rubrique (à développer et améliorer, en gardant la langue) :\n$existing" else "") +
            "\n\nÉléments :\n" + taskContext(task) + (if (others.isNotEmpty()) "\n$others" else "")
    }
}
