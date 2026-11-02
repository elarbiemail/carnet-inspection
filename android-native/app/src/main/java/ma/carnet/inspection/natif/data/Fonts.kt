package ma.carnet.inspection.natif.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.io.File

/**
 * Polices des documents (comme le carnet web : Paramètres → Apparence → Polices des documents).
 * Réglage partagé : templates.docFonts = {arTitle, arBody, frTitle, frBody}.
 * Les polices installées sur le téléphone (.ttf/.otf/.woff2) sont intégrées aux aperçus et aux PDF ;
 * dans Word, c'est le nom de la police qui est indiqué (elle doit exister sur l'ordinateur).
 */
object Fonts {
    val SLOTS = linkedMapOf("arTitle" to "Titres en arabe", "arBody" to "Texte en arabe", "frTitle" to "Titres en français", "frBody" to "Texte en français")
    val COMMON = listOf("Sakkal Majalla", "Traditional Arabic", "Simplified Arabic", "Amiri", "Aref Ruqaa", "Reem Kufi", "El Messiri", "Cairo", "Noto Naskh Arabic",
        "Arial", "Calibri", "Times New Roman", "Cambria", "Georgia", "Garamond")
    /** Polices libres téléchargeables (Google Fonts, dépôt officiel). */
    val DOWNLOADS = linkedMapOf(
        "Amiri" to "https://github.com/google/fonts/raw/main/ofl/amiri/Amiri-Regular.ttf",
        "Amiri Bold" to "https://github.com/google/fonts/raw/main/ofl/amiri/Amiri-Bold.ttf",
        "Aref Ruqaa" to "https://github.com/google/fonts/raw/main/ofl/arefruqaa/ArefRuqaa-Regular.ttf",
        "Lateef" to "https://github.com/google/fonts/raw/main/ofl/lateef/Lateef-Regular.ttf",
        "Scheherazade New" to "https://github.com/google/fonts/raw/main/ofl/scheherazadenew/ScheherazadeNew-Regular.ttf"
    )
    private var app: Context? = null
    fun init(ctx: Context) { app = ctx.applicationContext }
    fun dir(ctx: Context) = File(ctx.filesDir, "polices").also { it.mkdirs() }
    fun installed(ctx: Context): List<File> = dir(ctx).listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()
    fun familyOf(f: File) = f.nameWithoutExtension.replace('_', ' ')

    fun install(ctx: Context, uri: Uri, name: String): File {
        val ext = name.substringAfterLast('.', "ttf").lowercase().let { if (it in listOf("ttf", "otf", "woff", "woff2")) it else "ttf" }
        val fam = name.substringBeforeLast('.').replace(Regex("[^\\p{L}\\p{N} _-]"), "").replace(' ', '_').ifEmpty { "Police" }
        val out = File(dir(ctx), "$fam.$ext")
        ctx.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { i.copyTo(it) } }
        return out
    }
    fun installBytes(ctx: Context, family: String, bytes: ByteArray): File = File(dir(ctx), family.replace(' ', '_') + ".ttf").also { it.writeBytes(bytes) }

    fun prefs(): JSONObject = Store.root.optJSONObject("templates")?.optJSONObject("docFonts") ?: JSONObject()
    fun set(slot: String, family: String) {
        val tpl = Store.root.optJSONObject("templates") ?: JSONObject().also { Store.root.put("templates", it) }
        val f = tpl.optJSONObject("docFonts") ?: JSONObject().also { tpl.put("docFonts", it) }
        f.put(slot, family); tpl.put("updatedAt", Store.now()); Store.save()
    }
    fun slot(rtl: Boolean, title: Boolean) = prefs().s((if (rtl) "ar" else "fr") + if (title) "Title" else "Body")

    private fun fontFile(ctx: Context, fam: String): File? = installed(ctx).firstOrNull { familyOf(it).equals(fam, true) }

    /** Règles CSS (avec les polices installées intégrées) pour un document. */
    fun css(rtl: Boolean): String {
        val ctx = app ?: return ""
        val body = slot(rtl, false); val title = slot(rtl, true)
        if (body.isEmpty() && title.isEmpty()) return ""
        val sb = StringBuilder()
        listOf(body, title).filter { it.isNotEmpty() }.toSet().forEach { fam ->
            fontFile(ctx, fam)?.let { f ->
                val fmt = when (f.extension.lowercase()) { "otf" -> "opentype"; "woff" -> "woff"; "woff2" -> "woff2"; else -> "truetype" }
                if (f.length() < 6_000_000) sb.append("@font-face{font-family:'$fam';src:url(data:font/${f.extension};base64,").append(Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)).append(") format('$fmt');}\n")
            }
        }
        if (body.isNotEmpty()) sb.append("body,p,td,th,div{font-family:'$body','Noto Naskh Arabic',Arial,sans-serif;}\n")
        if (title.isNotEmpty()) sb.append("h1,h2,h3,.box,.hd .b{font-family:'$title','$body',Arial,sans-serif;}\n")
        return sb.toString()
    }
}
