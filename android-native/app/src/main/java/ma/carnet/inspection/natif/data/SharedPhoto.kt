package ma.carnet.inspection.natif.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.mutableStateOf
import java.io.File

/**
 * Photo partagée vers le Carnet depuis une autre application (WhatsApp : photo de profil d'un contact → Partager → Carnet).
 * Si l'utilisateur est parti de la fiche d'une personne (« Depuis WhatsApp »), la photo lui est attribuée directement.
 */
object SharedPhoto {
    /** Fichier reçu, en attente d'attribution. */
    val pending = mutableStateOf<File?>(null)
    /** Fiche (enseignant ou établissement) dont on est parti pour aller chercher la photo dans WhatsApp. */
    var target: String? = null
    var targetName: String = ""

    fun handle(ctx: Context, intent: Intent?): Boolean {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return false
        val uri: Uri = (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)) ?: return false
        return try {
            val f = File(File(ctx.cacheDir, "partages").also { it.mkdirs() }, "photo_" + System.currentTimeMillis() + ".jpg")
            ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { i.copyTo(it) } }
            if (f.length() > 0) pending.value = f
            true
        } catch (_: Exception) { false }
    }

    fun assignBitmap(ctx: Context, id: String, bmp: android.graphics.Bitmap): Boolean {
        val ok = Photos.saveBitmap(ctx, id, bmp)
        pending.value?.delete(); pending.value = null; target = null; targetName = ""
        if (ok) Store.version.intValue++
        return ok
    }

    fun assign(ctx: Context, id: String): Boolean {
        val f = pending.value ?: return false
        val ok = Photos.save(ctx, id, Uri.fromFile(f))
        f.delete(); pending.value = null; target = null; targetName = ""
        if (ok) Store.version.intValue++
        return ok
    }
}
