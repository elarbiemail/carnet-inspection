package ma.carnet.inspection.natif.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import java.io.File

/** Photos d'avatar (enseignant, directeur), stockées en local dans filesDir/avatars. */
object Photos {
    private fun dir(ctx: Context) = File(ctx.filesDir, "avatars").also { it.mkdirs() }
    fun file(ctx: Context, id: String) = File(dir(ctx), "$id.jpg")
    fun has(ctx: Context, id: String) = id.isNotEmpty() && file(ctx, id).exists()
    fun load(ctx: Context, id: String): Bitmap? = if (has(ctx, id)) BitmapFactory.decodeFile(file(ctx, id).path) else null
    fun remove(ctx: Context, id: String) { file(ctx, id).delete() }

    /** Recadre au carré, réduit à 320 px et enregistre. */
    fun save(ctx: Context, id: String, uri: Uri): Boolean = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var s = 1
        while (bounds.outWidth / (s * 2) >= 640 && bounds.outHeight / (s * 2) >= 640) s *= 2
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
        if (bmp == null) false else {
            val side = minOf(bmp.width, bmp.height)
            val sq = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
            val out = Bitmap.createScaledBitmap(sq, 320, 320, true)
            file(ctx, id).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            true
        }
    } catch (e: Exception) { false }

    /** Enregistre une image déjà recadrée (carré). */
    fun saveBitmap(ctx: Context, id: String, bmp: Bitmap): Boolean = try {
        val out = Bitmap.createScaledBitmap(bmp, 320, 320, true)
        file(ctx, id).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }; true
    } catch (e: Exception) { false }

    /** Photo du contact du téléphone portant ce numéro (nécessite READ_CONTACTS). */
    fun contactPhotoUri(ctx: Context, tel: String): Uri? = try {
        val u = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(tel))
        ctx.contentResolver.query(u, arrayOf(ContactsContract.PhoneLookup.PHOTO_URI), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { Uri.parse(it) } else null
        }
    } catch (e: Exception) { null }
}
