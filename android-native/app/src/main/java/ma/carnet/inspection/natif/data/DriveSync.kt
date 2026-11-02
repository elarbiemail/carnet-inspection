package ma.carnet.inspection.natif.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Synchronisation directe avec Google Drive, sur la MÊME sauvegarde que le carnet web :
 * « Carnet d'Inspection — Données / Sauvegardes / sauvegarde-donnees.json ».
 *
 * Connexion par les services Google du téléphone (pas de mot de passe dans l'application).
 * Le carnet web utilise l'autorisation « drive.file » (accès aux seuls fichiers créés par
 * l'application) : pour que l'application Android voie les mêmes fichiers, son identifiant
 * OAuth « Android » doit être créé dans le MÊME projet Google Cloud que l'identifiant client
 * du carnet web (voir l'écran Données → Google Drive).
 */
object DriveSync {
    const val SCOPE = "https://www.googleapis.com/auth/drive.file"
    const val PACKAGE = "net.fm6education.carnetinspection.natif"
    const val SHA1 = "DD:45:C4:A2:B8:F4:CF:E6:78:1E:67:F4:B2:23:73:8C:80:44:0B:6E"
    const val APP_FOLDER = "Carnet d'Inspection — Données"
    const val BACKUP_FOLDER = "Sauvegardes"
    const val BACKUP_NAME = "sauvegarde-donnees.json"
    private const val API = "https://www.googleapis.com/drive/v3/files"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"

    @Volatile private var token: String? = null
    @Volatile private var tokenAt = 0L

    fun prefs(ctx: Context) = ctx.getSharedPreferences("carnet", Context.MODE_PRIVATE)
    fun clientId(ctx: Context) = prefs(ctx).getString("googleClientId", "") ?: ""
    fun setClientId(ctx: Context, v: String) = prefs(ctx).edit().putString("googleClientId", v.trim()).apply()
    fun enabled(ctx: Context) = prefs(ctx).getBoolean("driveEnabled", false)
    fun setEnabled(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("driveEnabled", v).apply()
    fun lastSync(ctx: Context) = prefs(ctx).getString("lastDriveSync", "") ?: ""

    /** Numéro du projet Google Cloud, lu dans l'identifiant client (1234567890-xxxx.apps.googleusercontent.com). */
    fun projectNumber(clientId: String): String = Regex("^(\\d+)-").find(clientId.trim())?.groupValues?.get(1) ?: ""

    fun request(): AuthorizationRequest = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    /** Résultat : jeton obtenu, ou une fenêtre d'autorisation à afficher (consentement). */
    sealed class Auth {
        data class Ok(val token: String) : Auth()
        data class NeedsConsent(val result: AuthorizationResult) : Auth()
    }

    suspend fun authorize(activity: Activity): Auth {
        val tk = token
        if (tk != null && System.currentTimeMillis() - tokenAt < 45 * 60 * 1000) return Auth.Ok(tk)
        val res = Identity.getAuthorizationClient(activity).authorize(request()).await()
        return if (res.hasResolution()) Auth.NeedsConsent(res)
        else { val t = res.accessToken ?: throw Exception("Aucun jeton reçu de Google."); token = t; tokenAt = System.currentTimeMillis(); Auth.Ok(t) }
    }

    fun tokenFromIntent(activity: Activity, data: Intent?): String {
        val res = Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)
        val t = res.accessToken ?: throw Exception("Autorisation refusée.")
        token = t; tokenAt = System.currentTimeMillis(); return t
    }

    fun forget(ctx: Context) { token = null; tokenAt = 0; setEnabled(ctx, false) }

    // ---------------- HTTP ----------------
    private class HttpError(val code: Int, msg: String) : Exception(msg)

    private fun http(method: String, url: String, tk: String, body: ByteArray? = null, contentType: String? = null): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = if (method == "PATCH") "POST" else method
        if (method == "PATCH") c.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        c.setRequestProperty("Authorization", "Bearer $tk")
        c.connectTimeout = 20000; c.readTimeout = 60000
        if (body != null) {
            c.doOutput = true
            if (contentType != null) c.setRequestProperty("Content-Type", contentType)
            c.outputStream.use { it.write(body) }
        }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val out = ByteArrayOutputStream(); stream?.use { it.copyTo(out) }
        val txt = out.toString("UTF-8")
        if (code == 401) { token = null }
        if (code !in 200..299) {
            val msg = try { JSONObject(txt).optJSONObject("error")?.optString("message") ?: txt } catch (e: Exception) { txt }
            throw HttpError(code, "Google Drive ($code) : $msg")
        }
        return txt
    }

    private fun q(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun esc(s: String) = s.replace("\\", "\\\\").replace("'", "\\'")

    private fun findOne(tk: String, query: String): JSONObject? {
        val r = JSONObject(http("GET", "$API?q=${q(query)}&orderBy=modifiedTime%20desc&pageSize=5&fields=files(id,name,modifiedTime,parents)&spaces=drive", tk))
        return r.optJSONArray("files")?.optJSONObject(0)
    }

    private fun createFolder(tk: String, name: String, parent: String?): String {
        val meta = JSONObject().put("name", name).put("mimeType", "application/vnd.google-apps.folder")
        if (parent != null) meta.put("parents", org.json.JSONArray().put(parent))
        return JSONObject(http("POST", "$API?fields=id", tk, meta.toString().toByteArray(), "application/json; charset=UTF-8")).getString("id")
    }

    private fun backupFolder(tk: String): String {
        val app = findOne(tk, "name = '${esc(APP_FOLDER)}' and mimeType = 'application/vnd.google-apps.folder' and trashed = false")?.getString("id")
            ?: createFolder(tk, APP_FOLDER, null)
        return findOne(tk, "name = '$BACKUP_FOLDER' and mimeType = 'application/vnd.google-apps.folder' and '$app' in parents and trashed = false")?.getString("id")
            ?: createFolder(tk, BACKUP_FOLDER, app)
    }

    data class Report(val imported: Store.ImportReport?, val created: Boolean)

    /**
     * Synchronisation complète : télécharge la sauvegarde Drive, la fusionne avec les données de
     * l'appareil (le plus récent l'emporte, suppressions comprises), puis renvoie le résultat sur
     * Drive, dans le même fichier — que le carnet web relit à sa prochaine synchronisation.
     */
    suspend fun sync(ctx: Context, tk: String): Report = withContext(Dispatchers.IO) {
        var file = findOne(tk, "name = '$BACKUP_NAME' and trashed = false")
        var rep: Store.ImportReport? = null
        if (file != null) {
            val txt = http("GET", "$API/${file.getString("id")}?alt=media", tk)
            val remote = JSONObject(txt.trimStart('﻿'))
            rep = withContext(Dispatchers.Main) { Store.importBackup(remote) }
        }
        val body = withContext(Dispatchers.Main) { Store.exportJson() }.toByteArray(Charsets.UTF_8)
        var created = false
        if (file != null) {
            http("PATCH", "$UPLOAD/${file.getString("id")}?uploadType=media&fields=id", tk, body, "application/json; charset=UTF-8")
        } else {
            val parent = backupFolder(tk)
            val boundary = "carnet" + System.currentTimeMillis()
            val meta = JSONObject().put("name", BACKUP_NAME).put("mimeType", "application/json").put("parents", org.json.JSONArray().put(parent))
            val mp = ("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n--$boundary\r\nContent-Type: application/json\r\n\r\n").toByteArray() +
                body + "\r\n--$boundary--".toByteArray()
            http("POST", "$UPLOAD?uploadType=multipart&fields=id", tk, mp, "multipart/related; boundary=$boundary")
            created = true
        }
        try { fetchImages(ctx, tk) } catch (_: Exception) {}
        prefs(ctx).edit().putString("lastDriveSync", Store.now()).apply()
        Report(rep, created)
    }

    private fun httpBytes(url: String, tk: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("Authorization", "Bearer $tk"); c.connectTimeout = 20000; c.readTimeout = 60000
        if (c.responseCode !in 200..299) throw HttpError(c.responseCode, "Google Drive (${c.responseCode})")
        return c.inputStream.use { it.readBytes() }
    }

    /** Logo, signature et cachet du profil (déposés sur Drive par le carnet web, voir assetsIndex). */
    fun fetchImages(ctx: Context, tk: String): Int {
        val p = Store.profil(); val idx = Store.root.optJSONObject("assetsIndex") ?: return 0
        var n = 0
        listOf("logo" to "logoAssetId", "signature" to "signatureAssetId", "cachet" to "cachetAssetId").forEach { (key, field) ->
            val f = Docs.imgFile(ctx, key)
            val aid = p.s(field)
            if (aid.isEmpty()) { if (f.exists()) f.delete(); return@forEach }
            val fid = idx.optJSONObject(aid)?.s("driveFileId") ?: ""
            if (fid.isEmpty()) return@forEach
            val mark = java.io.File(ctx.filesDir, "img_$key.id")
            if (f.exists() && mark.exists() && mark.readText() == fid) return@forEach
            try { f.writeBytes(httpBytes("$API/$fid?alt=media", tk)); mark.writeText(fid); n++ } catch (_: Exception) {}
        }
        return n
    }
}
