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

    fun request(email: String = ""): AuthorizationRequest = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE)))
        .apply { if (email.isNotEmpty()) setAccount(android.accounts.Account(email, "com.google")) }.build()

    /** Résultat : jeton obtenu, ou une fenêtre d'autorisation à afficher (consentement). */
    sealed class Auth {
        data class Ok(val token: String) : Auth()
        data class NeedsConsent(val result: AuthorizationResult) : Auth()
        data object NeedsWeb : Auth()
    }

    /** Mode de connexion : "auto" (Google Play, puis navigateur en cas d'échec) ou "web" (toujours par le navigateur). */
    fun authMode(ctx: Context) = prefs(ctx).getString("authMode", "auto") ?: "auto"
    fun setAuthMode(ctx: Context, v: String) = prefs(ctx).edit().putString("authMode", v).apply()
    const val WEB_AUTH = "https://elarbiemail.github.io/carnet-inspection/natif-auth.html"
    /** chooseAccount = true : Google propose le choix du compte ; sinon renouvellement discret avec le compte déjà choisi. */
    fun webAuthUrl(ctx: Context, chooseAccount: Boolean) = WEB_AUTH + "?cid=" + q(clientId(ctx)) +
        (if (chooseAccount || GoogleAccount.email(ctx).isEmpty()) "&prompt=select_account" else "&prompt=&hint=" + q(GoogleAccount.email(ctx)))
    var forceChoose = false

    /** Jeton reçu par la page de connexion du navigateur (valable environ une heure). */
    fun acceptWebToken(ctx: Context, t: String, expSec: Long) {
        token = t; tokenAt = System.currentTimeMillis() - (3600 - expSec.coerceIn(60, 3600)) * 1000
        prefs(ctx).edit().putString("webToken", t).putLong("webTokenAt", tokenAt).apply()
    }

    suspend fun authorize(activity: Activity): Auth {
        val tk = token
        if (tk != null && System.currentTimeMillis() - tokenAt < 45 * 60 * 1000) return Auth.Ok(tk)
        val p = prefs(activity); val wt = p.getString("webToken", null); val wa = p.getLong("webTokenAt", 0)
        if (wt != null && System.currentTimeMillis() - wa < 45 * 60 * 1000) { token = wt; tokenAt = wa; return Auth.Ok(wt) }
        if (authMode(activity) == "web") return if (clientId(activity).isBlank()) throw Exception("Saisissez d'abord l'identifiant client Google (Paramètres → Compte Google).") else Auth.NeedsWeb
        val res = try { Identity.getAuthorizationClient(activity).authorize(request(GoogleAccount.email(activity))).await() }
            catch (e: com.google.android.gms.common.api.ApiException) {
                // identifiant OAuth Android absent ou refus de Google Play : on passe par le navigateur
                if (e.statusCode in listOf(10, 16, 12500, 17, 8) && clientId(activity).isNotBlank()) { setAuthMode(activity, "web"); return Auth.NeedsWeb }
                throw e
            }
        return if (res.hasResolution()) Auth.NeedsConsent(res)
        else { val t = res.accessToken ?: throw Exception("Aucun jeton reçu de Google."); token = t; tokenAt = System.currentTimeMillis(); Auth.Ok(t) }
    }

    fun tokenFromIntent(activity: Activity, data: Intent?): String {
        val res = Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)
        val t = res.accessToken ?: throw Exception("Autorisation refusée.")
        token = t; tokenAt = System.currentTimeMillis(); return t
    }

    fun forget(ctx: Context) { token = null; tokenAt = 0; setEnabled(ctx, false); prefs(ctx).edit().remove("webToken").remove("webTokenAt").apply() }

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
        if (code == 401) { token = null; tokenAt = 0 }
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
        try { pushImages(ctx, tk) } catch (_: Exception) {}
        try { Attach.pushPending(ctx, tk) } catch (_: Exception) {}
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

    /** Logo, signature, cachet et photo de profil déposés sur Drive par le carnet web (voir assetsIndex). */
    fun fetchImages(ctx: Context, tk: String): Int {
        val p = Store.profil(); val idx = Store.root.optJSONObject("assetsIndex") ?: JSONObject()
        var n = 0
        ProfileImages.KEYS.forEach { (key, field) ->
            val f = Docs.imgFile(ctx, key)
            if (ProfileImages.pending(ctx, key)) return@forEach
            val mark = java.io.File(ctx.filesDir, "img_$key.id")
            val aid = p.s(field)
            if (aid.isEmpty()) { if (mark.exists()) { f.delete(); mark.delete() }; return@forEach }
            val fid = idx.optJSONObject(aid)?.s("driveFileId") ?: ""
            if (fid.isEmpty()) return@forEach
            if (f.exists() && mark.exists() && mark.readText() == fid) return@forEach
            try { f.writeBytes(httpBytes("$API/$fid?alt=media", tk)); mark.writeText(fid); n++ } catch (_: Exception) {}
        }
        return n
    }

    /** Envoie sur Drive (dossier « Photos ») les images choisies sur le téléphone, pour le carnet web. */
    fun pushImages(ctx: Context, tk: String) {
        ProfileImages.KEYS.forEach { (key, field) ->
            if (!ProfileImages.pending(ctx, key)) return@forEach
            val f = Docs.imgFile(ctx, key); if (!f.exists()) { ProfileImages.clearPending(ctx, key); return@forEach }
            val folder = subFolder(tk, "Photos")
            val name = "$key-" + System.currentTimeMillis() + ".jpg"
            val fid = uploadBytes(tk, folder, name, "image/jpeg", f.readBytes())
            val aid = "ast_" + java.util.UUID.randomUUID().toString().replace("-", "").take(16)
            val tpl = Store.root.optJSONObject("templates") ?: JSONObject().also { Store.root.put("templates", it) }
            val prof = tpl.optJSONObject("profil") ?: JSONObject().also { tpl.put("profil", it) }
            prof.put(field, aid); tpl.put("updatedAt", Store.now())
            val idx = Store.root.optJSONObject("assetsIndex") ?: JSONObject().also { Store.root.put("assetsIndex", it) }
            idx.put(aid, JSONObject().put("driveFileId", fid).put("type", "image/jpeg").put("name", name))
            java.io.File(ctx.filesDir, "img_$key.id").writeText(fid)
            ProfileImages.clearPending(ctx, key)
        }
        Store.save()
    }

    /** Dossier « Carnet d'Inspection — Données / <nom> » (créé au besoin). */
    fun subFolder(tk: String, name: String): String {
        val app = findOne(tk, "name = '${esc(APP_FOLDER)}' and mimeType = 'application/vnd.google-apps.folder' and trashed = false")?.getString("id")
            ?: createFolder(tk, APP_FOLDER, null)
        return findOne(tk, "name = '${esc(name)}' and mimeType = 'application/vnd.google-apps.folder' and '$app' in parents and trashed = false")?.getString("id")
            ?: createFolder(tk, name, app)
    }
    private fun findOneFields(tk: String, query: String): JSONObject? {
        val r = JSONObject(http("GET", "$API?q=${q(query)}&orderBy=modifiedTime%20desc&pageSize=5&fields=files(id,name,modifiedTime)&spaces=drive", tk))
        return r.optJSONArray("files")?.optJSONObject(0)
    }

    /** Téléverse un fichier (multipart) et renvoie son identifiant Drive. */
    fun uploadBytes(tk: String, parent: String, name: String, mime: String, bytes: ByteArray): String {
        val boundary = "carnet" + System.currentTimeMillis()
        val meta = JSONObject().put("name", name).put("mimeType", mime).put("parents", org.json.JSONArray().put(parent))
        val mp = ("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n--$boundary\r\nContent-Type: $mime\r\n\r\n").toByteArray() +
            bytes + "\r\n--$boundary--".toByteArray()
        return JSONObject(http("POST", "$UPLOAD?uploadType=multipart&fields=id", tk, mp, "multipart/related; boundary=$boundary")).getString("id")
    }

    /** Crée un Google Doc à partir d'un texte brut et renvoie son identifiant. */
    fun createGoogleDoc(tk: String, parent: String, title: String, text: String): String {
        val boundary = "carnet" + System.currentTimeMillis()
        val meta = JSONObject().put("name", title).put("mimeType", "application/vnd.google-apps.document").put("parents", org.json.JSONArray().put(parent))
        val mp = ("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n--$boundary\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n").toByteArray() +
            text.toByteArray(Charsets.UTF_8) + "\r\n--$boundary--".toByteArray()
        return JSONObject(http("POST", "$UPLOAD?uploadType=multipart&fields=id", tk, mp, "multipart/related; boundary=$boundary")).getString("id")
    }

    /** Exporte un Google Doc (text/plain, application/pdf…). */
    fun export(tk: String, fileId: String, mime: String): ByteArray = httpBytes("$API/$fileId/export?mimeType=" + q(mime), tk)

    /** Liste les fichiers d'un dossier Drive (id, nom, type, taille). */
    fun listFolder(tk: String, folderId: String): List<JSONObject> {
        val r = JSONObject(http("GET", "$API?q=" + q("'$folderId' in parents and trashed = false") + "&orderBy=modifiedTime%20desc&pageSize=100&fields=files(id,name,mimeType,size,modifiedTime)", tk))
        val a = r.optJSONArray("files") ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }

    /** Fichier d'un nom donné dans un dossier (id, nom, date de modification), ou null. */
    fun findInFolder(tk: String, folderId: String, name: String): JSONObject? = findOne(tk, "name = '${esc(name)}' and '$folderId' in parents and trashed = false")

    /** Remplace le contenu d'un fichier Drive existant. */
    fun updateMedia(tk: String, fileId: String, bytes: ByteArray, mime: String) { http("PATCH", "$UPLOAD/$fileId?uploadType=media&fields=id", tk, bytes, mime) }

    /** Crée (ou remplace) une feuille Google Sheets à partir d'un CSV ; renvoie son identifiant. */
    fun sheetFromCsv(tk: String, parent: String, title: String, csv: String, existingId: String?): String {
        val bytes = csv.toByteArray(Charsets.UTF_8)
        if (!existingId.isNullOrEmpty()) try { updateMedia(tk, existingId, bytes, "text/csv"); return existingId } catch (_: Exception) {}
        val boundary = "carnet" + System.currentTimeMillis()
        val meta = JSONObject().put("name", title).put("mimeType", "application/vnd.google-apps.spreadsheet").put("parents", org.json.JSONArray().put(parent))
        val mp = ("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n--$boundary\r\nContent-Type: text/csv; charset=UTF-8\r\n\r\n").toByteArray() + bytes + "\r\n--$boundary--".toByteArray()
        return JSONObject(http("POST", "$UPLOAD?uploadType=multipart&fields=id", tk, mp, "multipart/related; boundary=$boundary")).getString("id")
    }

    /** Télécharge un fichier Drive (pièce jointe, document). */
    fun download(tk: String, fileId: String): ByteArray = httpBytes("$API/$fileId?alt=media", tk)

    /** Nom, adresse et photo du compte Google connecté. */
    fun about(tk: String): JSONObject = JSONObject(http("GET", "https://www.googleapis.com/drive/v3/about?fields=user(displayName,emailAddress,photoLink)", tk)).optJSONObject("user") ?: JSONObject()
}

/** Images du profil choisies sur le téléphone (photo, logo, cachet, signature). */
object ProfileImages {
    val KEYS = listOf("avatar" to "avatarAssetId", "logo" to "logoAssetId", "signature" to "signatureAssetId", "cachet" to "cachetAssetId")
    private fun pf(ctx: Context, key: String) = java.io.File(ctx.filesDir, "img_$key.pending")
    fun pending(ctx: Context, key: String) = pf(ctx, key).exists()
    fun clearPending(ctx: Context, key: String) { pf(ctx, key).delete() }
    fun set(ctx: Context, key: String, jpeg: ByteArray) {
        Docs.imgFile(ctx, key).writeBytes(jpeg); java.io.File(ctx.filesDir, "img_$key.id").delete(); pf(ctx, key).writeText("1"); Store.save()
    }
    fun remove(ctx: Context, key: String) {
        Docs.imgFile(ctx, key).delete(); java.io.File(ctx.filesDir, "img_$key.id").delete(); clearPending(ctx, key)
        val tpl = Store.root.optJSONObject("templates") ?: return
        val prof = tpl.optJSONObject("profil") ?: return
        val field = KEYS.first { it.first == key }.second
        prof.put(field, ""); tpl.put("updatedAt", Store.now()); Store.save()
    }
    /** Image JPEG (côté max), recadrée en carré pour la photo de profil. */
    fun toJpeg(bmp: android.graphics.Bitmap, square: Boolean, max: Int = 900): ByteArray {
        var b = bmp
        if (square) { val s = minOf(b.width, b.height); b = android.graphics.Bitmap.createBitmap(b, (b.width - s) / 2, (b.height - s) / 2, s, s) }
        val sc = minOf(1f, max.toFloat() / maxOf(b.width, b.height))
        if (sc < 1f) b = android.graphics.Bitmap.createScaledBitmap(b, (b.width * sc).toInt(), (b.height * sc).toInt(), true)
        val o = java.io.ByteArrayOutputStream(); b.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, o); return o.toByteArray()
    }
}
