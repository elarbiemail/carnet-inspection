package ma.carnet.inspection.natif.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Assistant de rédaction, avec plusieurs moteurs au choix (comme le carnet web) :
 *  - gemini : Google Gemini (clé personnelle) ;
 *  - cloud  : API compatibles OpenAI (Groq, OpenRouter, Mistral, Cerebras, OpenAI, DeepSeek, autre) ou Claude (Anthropic) ;
 *  - server : serveur IA local (Ollama, LM Studio, llama-server dans Termux, sur le téléphone ou le réseau) ;
 *  - device : modèle installé sur le téléphone (fichier .task MediaPipe : Gemma, Qwen…), sans Internet.
 */
object Ai {
    const val MODEL = "gemini-flash-latest"

    data class Preset(val id: String, val label: String, val base: String, val model: String, val keyUrl: String, val note: String, val anthropic: Boolean = false)
    val CLOUD = listOf(
        Preset("groq", "Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "https://console.groq.com/keys", "Gratuit sans carte bancaire, environ 1 000 demandes par jour, très rapide."),
        Preset("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "meta-llama/llama-3.3-70b-instruct:free", "https://openrouter.ai/keys", "Gratuit avec les modèles « :free » (environ 50 demandes par jour). Nombreux modèles."),
        Preset("mistral", "Mistral AI", "https://api.mistral.ai/v1", "mistral-small-latest", "https://console.mistral.ai/api-keys", "Offre gratuite « Experiment ». Très bon en français."),
        Preset("cerebras", "Cerebras", "https://api.cerebras.ai/v1", "llama-3.3-70b", "https://cloud.cerebras.ai/", "Gratuit sans carte bancaire, très rapide."),
        Preset("openai", "OpenAI (ChatGPT)", "https://api.openai.com/v1", "gpt-4o-mini", "https://platform.openai.com/api-keys", "Payant à l'usage."),
        Preset("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", "https://platform.deepseek.com/api_keys", "Peu coûteux, bon en rédaction."),
        Preset("anthropic", "Claude (Anthropic)", "https://api.anthropic.com/v1", "claude-haiku-4-5", "https://console.anthropic.com/settings/keys", "Payant à l'usage. Excellent en français et en arabe.", anthropic = true),
        Preset("custom", "Autre (compatible OpenAI)", "", "", "", "Indiquez l'adresse de l'API (se terminant en général par /v1), la clé et le modèle.")
    )
    val PROVIDERS = linkedMapOf("gemini" to "Gemini (en ligne)", "cloud" to "Autres API (Groq, Mistral, OpenAI, Claude…)", "server" to "Serveur IA local (Ollama, LM Studio…)", "device" to "IA sur le téléphone (hors ligne)")

    private fun p(ctx: Context) = ctx.getSharedPreferences("carnet", Context.MODE_PRIVATE)
    fun key(ctx: Context) = p(ctx).getString("geminiKey", "") ?: ""
    fun setKey(ctx: Context, k: String) = p(ctx).edit().putString("geminiKey", k.trim()).apply()
    fun provider(ctx: Context) = p(ctx).getString("aiProvider", "gemini") ?: "gemini"
    fun setProvider(ctx: Context, v: String) = p(ctx).edit().putString("aiProvider", v).apply()
    fun reasoning(ctx: Context) = p(ctx).getBoolean("aiReasoning", false)
    fun setReasoning(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("aiReasoning", v).apply()
    fun cloudPreset(ctx: Context): Preset = CLOUD.firstOrNull { it.id == p(ctx).getString("cloudPreset", "groq") } ?: CLOUD[0]
    fun setCloudPreset(ctx: Context, id: String) = p(ctx).edit().putString("cloudPreset", id).apply()
    fun cloudKey(ctx: Context, id: String) = p(ctx).getString("cloudKey_$id", "") ?: ""
    fun setCloudKey(ctx: Context, id: String, v: String) = p(ctx).edit().putString("cloudKey_$id", v.trim()).apply()
    fun cloudModel(ctx: Context, id: String) = (p(ctx).getString("cloudModel_$id", "") ?: "").ifEmpty { CLOUD.firstOrNull { it.id == id }?.model ?: "" }
    fun setCloudModel(ctx: Context, id: String, v: String) = p(ctx).edit().putString("cloudModel_$id", v.trim()).apply()
    fun cloudBase(ctx: Context) = p(ctx).getString("cloudBase", "") ?: ""
    fun setCloudBase(ctx: Context, v: String) = p(ctx).edit().putString("cloudBase", v.trim().trimEnd('/')).apply()
    fun serverUrl(ctx: Context) = (p(ctx).getString("aiServerUrl", "") ?: "").ifEmpty { "http://127.0.0.1:11434" }
    fun setServerUrl(ctx: Context, v: String) = p(ctx).edit().putString("aiServerUrl", v.trim().trimEnd('/')).apply()
    fun serverModel(ctx: Context) = p(ctx).getString("aiServerModel", "") ?: ""
    fun setServerModel(ctx: Context, v: String) = p(ctx).edit().putString("aiServerModel", v.trim()).apply()
    fun deviceModelFile(ctx: Context) = File(ctx.filesDir, "ia/modele.task")
    fun deviceModelName(ctx: Context) = p(ctx).getString("deviceModelName", "") ?: ""
    fun setDeviceModelName(ctx: Context, v: String) = p(ctx).edit().putString("deviceModelName", v).apply()

    fun providerLabel(ctx: Context): String = when (provider(ctx)) {
        "cloud" -> cloudPreset(ctx).label + " · " + cloudModel(ctx, cloudPreset(ctx).id)
        "server" -> "Serveur local · " + serverUrl(ctx)
        "device" -> "Sur le téléphone · " + deviceModelName(ctx).ifEmpty { "aucun modèle" }
        else -> "Gemini"
    }

    fun ready(ctx: Context): Boolean = when (provider(ctx)) {
        "cloud" -> cloudPreset(ctx).let { pr -> cloudKey(ctx, pr.id).isNotBlank() && (pr.id != "custom" || cloudBase(ctx).isNotBlank()) }
        "server" -> serverUrl(ctx).isNotBlank()
        "device" -> deviceModelFile(ctx).exists()
        else -> key(ctx).isNotBlank()
    }
    const val NOT_READY = "Configurez l'assistant IA dans Plus → Paramètres → Assistant IA."

    /** Génère un texte avec le moteur choisi. L'image (facultative) n'est lue que par Gemini et les API qui l'acceptent. */
    suspend fun generate(ctx: Context, prompt: String, image: ByteArray? = null, mime: String = "image/jpeg"): String = withContext(Dispatchers.IO) {
        val out = when (provider(ctx)) {
            "cloud" -> { val pr = cloudPreset(ctx); if (pr.anthropic) anthropic(ctx, pr, prompt, image, mime) else openAi((if (pr.id == "custom") cloudBase(ctx) else pr.base), cloudKey(ctx, pr.id), cloudModel(ctx, pr.id), prompt, image, mime, false, reasoning(ctx)) }
            "server" -> openAi(serverUrl(ctx) + "/v1", "", serverModel(ctx).ifEmpty { "local" }, prompt, image, mime, true, reasoning(ctx))
            "device" -> { if (image != null) throw Exception("L'IA sur le téléphone ne lit pas les images : utilisez Gemini pour cet import."); OnDevice.generate(ctx, prompt) }
            else -> gemini(ctx, prompt, image, mime)
        }
        cleanup(out).ifEmpty { throw Exception("Réponse vide de l'IA.") }
    }

    /** Retire la réflexion (<think>…</think>) et les clôtures de code éventuelles. */
    fun cleanup(s: String): String {
        var t = s.replace(Regex("(?s)<think>.*?</think>"), "")
        if (t.contains("</think>")) t = t.substringAfterLast("</think>")
        return t.trim().removePrefix("```").removeSuffix("```").trim()
    }

    private fun post(url: String, body: JSONObject, headers: Map<String, String>, timeoutMs: Int = 180000): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 20000; c.readTimeout = timeoutMs
        c.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
        return code to txt
    }

    private fun errMsg(txt: String): String = try {
        val j = JSONObject(txt); j.optJSONObject("error")?.optString("message")?.ifEmpty { null } ?: j.optString("error").ifEmpty { j.optString("message").ifEmpty { txt } }
    } catch (e: Exception) { txt }.take(220)

    private fun gemini(ctx: Context, prompt: String, image: ByteArray?, mime: String): String {
        val k = key(ctx); if (k.isBlank()) throw Exception("Ajoutez votre clé Gemini dans Paramètres → Assistant IA.")
        val parts = JSONArray().put(JSONObject().put("text", prompt))
        if (image != null) parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", mime).put("data", Base64.encodeToString(image, Base64.NO_WRAP))))
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
        if (reasoning(ctx)) body.put("generationConfig", JSONObject().put("thinkingConfig", JSONObject().put("thinkingLevel", "high")))
        var (code, txt) = post("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent", body, mapOf("x-goog-api-key" to k))
        if (code == 400 && reasoning(ctx)) { body.remove("generationConfig"); val r = post("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent", body, mapOf("x-goog-api-key" to k)); code = r.first; txt = r.second }
        if (code !in 200..299) throw Exception(when (code) { 400, 403 -> "Clé Gemini refusée ou invalide."; 429 -> "Quota Gemini atteint, réessayez plus tard ou changez de moteur."; 503 -> "Gemini est surchargé, réessayez dans un instant."; else -> "Gemini ($code) : ${errMsg(txt)}" })
        val ps = JSONObject(txt).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        val out = StringBuilder()
        for (i in 0 until ps.length()) { val x = ps.optJSONObject(i) ?: continue; if (!x.optBoolean("thought")) out.append(x.optString("text")) }
        return out.toString()
    }

    private fun openAi(base: String, key: String, model: String, prompt: String, image: ByteArray?, mime: String, local: Boolean, think: Boolean): String {
        if (base.isBlank()) throw Exception("Adresse de l'API manquante.")
        val content: Any = if (image == null) prompt else JSONArray().put(JSONObject().put("type", "text").put("text", prompt))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:$mime;base64," + Base64.encodeToString(image, Base64.NO_WRAP))))
        val body = JSONObject().put("model", model).put("stream", false).put("temperature", if (think) 0.6 else 0.3)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        if (local) body.put("chat_template_kwargs", JSONObject().put("enable_thinking", think))
        val h = if (key.isNotBlank()) mapOf("Authorization" to "Bearer $key") else emptyMap()
        val (code, txt) = try { post("$base/chat/completions", body, h, if (local) 600000 else 180000) } catch (e: java.net.ConnectException) {
            throw Exception(if (local) "Serveur IA injoignable à $base. Vérifiez qu'il est lancé (Ollama, LM Studio, llama-server) et l'adresse." else "Connexion impossible : ${e.message}")
        }
        if (code !in 200..299) throw Exception(when (code) { 401, 403 -> "Clé refusée par le fournisseur."; 429 -> "Quota atteint chez ce fournisseur : réessayez plus tard ou changez de fournisseur."; 404 -> "Modèle ou adresse introuvable : ${errMsg(txt)}"; else -> "IA ($code) : ${errMsg(txt)}" })
        val msg = JSONObject(txt).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message") ?: return ""
        val c = msg.opt("content")
        return when (c) {
            is String -> c
            is JSONArray -> (0 until c.length()).joinToString("") { i -> c.optJSONObject(i)?.let { o -> if (o.optString("type") in listOf("thinking", "reasoning")) "" else o.optString("text") } ?: c.optString(i) }
            else -> ""
        }
    }

    private fun anthropic(ctx: Context, pr: Preset, prompt: String, image: ByteArray?, mime: String): String {
        val k = cloudKey(ctx, pr.id)
        val content = JSONArray()
        if (image != null) content.put(JSONObject().put("type", "image").put("source", JSONObject().put("type", "base64").put("media_type", mime).put("data", Base64.encodeToString(image, Base64.NO_WRAP))))
        content.put(JSONObject().put("type", "text").put("text", prompt))
        val body = JSONObject().put("model", cloudModel(ctx, pr.id)).put("max_tokens", 4096)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val (code, txt) = post(pr.base + "/messages", body, mapOf("x-api-key" to k, "anthropic-version" to "2023-06-01"))
        if (code !in 200..299) throw Exception(when (code) { 401, 403 -> "Clé Claude refusée."; 429 -> "Quota Claude atteint."; else -> "Claude ($code) : ${errMsg(txt)}" })
        val arr = JSONObject(txt).optJSONArray("content") ?: JSONArray()
        return (0 until arr.length()).joinToString("") { i -> arr.optJSONObject(i)?.takeIf { it.optString("type") == "text" }?.optString("text") ?: "" }
    }

    /** Liste des modèles proposés par le fournisseur ou le serveur local. */
    suspend fun listModels(ctx: Context, server: Boolean): List<String> = withContext(Dispatchers.IO) {
        val (base, key, anth) = if (server) Triple(serverUrl(ctx) + "/v1", "", false) else cloudPreset(ctx).let { Triple(if (it.id == "custom") cloudBase(ctx) else it.base, cloudKey(ctx, it.id), it.anthropic) }
        val c = URL("$base/models").openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 30000
        if (anth) { c.setRequestProperty("x-api-key", key); c.setRequestProperty("anthropic-version", "2023-06-01") } else if (key.isNotBlank()) c.setRequestProperty("Authorization", "Bearer $key")
        val code = c.responseCode
        val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
        if (code !in 200..299) throw Exception("Liste des modèles indisponible ($code) : ${errMsg(txt)}")
        val a = JSONObject(txt).optJSONArray("data") ?: JSONArray()
        (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("id")?.ifEmpty { null } }.sorted()
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
        val lim = Docs.maxLen(doc.s("type"), fieldKey)
        return "Tu es inspecteur(trice) pédagogique" + (if (p.s("matiere").isNotEmpty()) " de ${p.s("matiere")}" else "") + " au Maroc. " +
            "Rédige la rubrique « $fieldLabel » du document « ${Docs.label(doc)} », " + (if (rtl) "en arabe standard moderne" else "en français") +
            ", sur un ton administratif sobre. Appuie-toi uniquement sur les éléments ci-dessous, sans inventer de noms, de chiffres ni de faits. Retourne uniquement le texte de la rubrique, sans titre." +
            (if (lim > 0) " Le texte doit tenir dans la case du formulaire : $lim caractères au maximum." else "") +
            (if (existing.isNotEmpty()) "\n\nNotes ou brouillon déjà saisis pour cette rubrique (à développer et améliorer, en gardant la langue) :\n$existing" else "") +
            "\n\nÉléments :\n" + taskContext(task) + (if (others.isNotEmpty()) "\n$others" else "")
    }
}

/** IA hors ligne sur le téléphone : MediaPipe LLM Inference (modèle .task importé par l'utilisateur). */
object OnDevice {
    private var engine: com.google.mediapipe.tasks.genai.llminference.LlmInference? = null
    private var loadedPath = ""

    @Synchronized
    fun generate(ctx: Context, prompt: String): String {
        val f = Ai.deviceModelFile(ctx)
        if (!f.exists()) throw Exception("Aucun modèle installé : importez un fichier .task dans Paramètres → Assistant IA.")
        if (engine == null || loadedPath != f.absolutePath + f.lastModified()) {
            close()
            val opts = com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions.builder()
                .setModelPath(f.absolutePath).setMaxTokens(2048).build()
            engine = com.google.mediapipe.tasks.genai.llminference.LlmInference.createFromOptions(ctx, opts)
            loadedPath = f.absolutePath + f.lastModified()
        }
        return engine!!.generateResponse(prompt.take(6000))
    }

    @Synchronized
    fun close() { try { engine?.close() } catch (_: Exception) {}; engine = null; loadedPath = "" }
}
