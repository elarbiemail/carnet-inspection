package ma.carnet.inspection.natif.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ma.carnet.inspection.natif.data.FileOps
import java.io.File

/**
 * Prise de photo par l'application Appareil photo du téléphone (photo en pleine résolution, redressée).
 * Aucune autorisation n'est nécessaire : c'est l'appareil photo du téléphone qui prend la photo.
 */
@Composable
fun rememberCamera(onPhoto: (Bitmap?) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val file = remember { File(File(ctx.cacheDir, "photos").also { it.mkdirs() }, "prise.jpg") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (!ok || !file.exists() || file.length() == 0L) { onPhoto(null); return@rememberLauncherForActivityResult }
        onPhoto(try { decode(file) } catch (_: Throwable) { null })
    }
    return {
        try { file.delete(); launcher.launch(FileOps.uri(ctx, file)) }
        catch (e: Throwable) { toast(ctx, "Impossible d'ouvrir l'appareil photo : " + (e.message ?: e.javaClass.simpleName)) }
    }
}

/** Décode une photo en limitant sa taille (mémoire) et en appliquant l'orientation EXIF. */
fun decode(f: File, max: Int = 2000): Bitmap? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.absolutePath, o)
    var sample = 1
    while (o.outWidth / (sample * 2) >= max || o.outHeight / (sample * 2) >= max) sample *= 2
    val b = BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val rot = try {
        when (ExifInterface(f.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f; ExifInterface.ORIENTATION_ROTATE_180 -> 180f; ExifInterface.ORIENTATION_ROTATE_270 -> 270f; else -> 0f
        }
    } catch (_: Throwable) { 0f }
    return if (rot == 0f) b else Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(rot) }, true)
}
