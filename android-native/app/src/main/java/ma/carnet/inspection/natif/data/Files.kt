package ma.carnet.inspection.natif.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Pièces jointes (tâches, enseignant(e)s, établissements) : copie locale + envoi sur Drive (« Pièces jointes »). */
object Attach {
    const val AUTH = "net.fm6education.carnetinspection.natif.fichiers"
    fun dir(ctx: Context) = File(ctx.filesDir, "pj").also { it.mkdirs() }
    fun local(ctx: Context, a: JSONObject) = File(dir(ctx), a.s("id"))

    fun nameOf(ctx: Context, uri: Uri): String {
        var n = "fichier"
        try { ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) n = c.getString(0) ?: n } } catch (_: Exception) {}
        return n
    }
    fun mimeOf(ctx: Context, uri: Uri, name: String): String =
        ctx.contentResolver.getType(uri) ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"

    /** Ajoute des fichiers à un enregistrement (coll = tasks, enseignants, etablissements). */
    fun add(ctx: Context, coll: String, recId: String, uris: List<Uri>): Int {
        val rec = Store.byId(coll, recId) ?: return 0
        val c = JSONObject(rec.toString())
        val arr = c.optJSONArray("attachments") ?: JSONArray().also { c.put("attachments", it) }
        var n = 0
        uris.forEach { u ->
            try {
                val name = nameOf(ctx, u); val e = JSONObject().put("id", UUID.randomUUID().toString()).put("titre", name).put("type", mimeOf(ctx, u, name))
                    .put("addedAt", Store.now()).put("status", "pending").put("driveFileId", JSONObject.NULL).put("driveUrl", JSONObject.NULL).put("local", true)
                ctx.contentResolver.openInputStream(u)?.use { inp -> local(ctx, e).outputStream().use { inp.copyTo(it) } }
                e.put("size", local(ctx, e).length()); arr.put(e); n++
            } catch (_: Exception) {}
        }
        Store.upsert(coll, c)
        return n
    }

    fun addFile(ctx: Context, coll: String, recId: String, f: File, title: String, mime: String) {
        val rec = Store.byId(coll, recId) ?: return
        val c = JSONObject(rec.toString())
        val arr = c.optJSONArray("attachments") ?: JSONArray().also { c.put("attachments", it) }
        val e = JSONObject().put("id", UUID.randomUUID().toString()).put("titre", title).put("type", mime).put("addedAt", Store.now()).put("status", "pending").put("local", true)
        f.copyTo(local(ctx, e), true); e.put("size", f.length()); arr.put(e); Store.upsert(coll, c)
    }

    fun remove(ctx: Context, coll: String, recId: String, attId: String) {
        val rec = Store.byId(coll, recId) ?: return
        val c = JSONObject(rec.toString()); val a = c.optJSONArray("attachments") ?: return; val n = JSONArray()
        for (i in 0 until a.length()) { val o = a.optJSONObject(i) ?: continue; if (o.s("id") == attId) local(ctx, o).delete() else n.put(o) }
        c.put("attachments", n); Store.upsert(coll, c)
    }

    /** Envoie sur Drive les pièces jointes encore locales (appelé pendant la synchronisation). */
    fun pushPending(ctx: Context, tk: String) {
        listOf("tasks", "enseignants", "etablissements").forEach { coll ->
            Store.list(coll).forEach { rec ->
                val todo = rec.arr("attachments").filter { it.s("driveFileId").isEmpty() && local(ctx, it).exists() }
                if (todo.isEmpty()) return@forEach
                val folder = DriveSync.subFolder(tk, "Pièces jointes")
                val c = JSONObject(rec.toString()); val a = c.optJSONArray("attachments") ?: return@forEach
                for (i in 0 until a.length()) {
                    val o = a.optJSONObject(i) ?: continue
                    if (o.s("driveFileId").isNotEmpty() || !local(ctx, o).exists()) continue
                    try {
                        val id = DriveSync.uploadBytes(tk, folder, o.s("titre"), o.s("type").ifEmpty { "application/octet-stream" }, local(ctx, o).readBytes())
                        o.put("driveFileId", id).put("driveUrl", "https://drive.google.com/file/d/$id/view").put("status", "uploaded")
                    } catch (_: Exception) {}
                }
                Store.upsert(coll, c)
            }
        }
    }
}

/** Partage, ouverture et enregistrement des fichiers produits. */
object FileOps {
    fun uri(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, Attach.AUTH, f)
    fun mime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"; "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; "json" -> "application/json"; "csv" -> "text/csv"
        "html", "htm" -> "text/html"; "txt" -> "text/plain"; "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
    }
    fun share(ctx: Context, f: File, title: String = f.name) {
        val i = Intent(Intent.ACTION_SEND).setType(mime(f.name)).putExtra(Intent.EXTRA_STREAM, uri(ctx, f)).putExtra(Intent.EXTRA_SUBJECT, title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Partager").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    fun openWith(ctx: Context, f: File) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri(ctx, f), mime(f.name)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { share(ctx, f) }
    }
    fun copyTo(ctx: Context, f: File, dest: Uri) { ctx.contentResolver.openOutputStream(dest)?.use { o -> f.inputStream().use { it.copyTo(o) } } }
}
