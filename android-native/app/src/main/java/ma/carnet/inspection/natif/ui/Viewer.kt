@file:OptIn(ExperimentalMaterial3Api::class)

package ma.carnet.inspection.natif.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ma.carnet.inspection.natif.data.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.util.zip.ZipFile

/** Liseuse : PDF, Word, Excel, PowerPoint (texte), images, texte, CSV, HTML. */
object Reader {
    fun ext(name: String) = name.substringAfterLast('.', "").lowercase()
    fun kind(name: String, mime: String = ""): String = when {
        ext(name) == "pdf" || mime == "application/pdf" -> "pdf"
        ext(name) in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic") || mime.startsWith("image/") -> "image"
        ext(name) == "docx" || mime.contains("wordprocessingml") -> "docx"
        ext(name) == "xlsx" || mime.contains("spreadsheetml") -> "xlsx"
        ext(name) == "pptx" || mime.contains("presentationml") -> "pptx"
        ext(name) in listOf("html", "htm") || mime == "text/html" -> "html"
        ext(name) == "csv" || mime == "text/csv" -> "csv"
        ext(name) in listOf("txt", "md", "json", "xml", "ics", "log") || mime.startsWith("text/") -> "text"
        else -> "autre"
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    private const val CSS = "<style>body{font-family:sans-serif;font-size:15px;line-height:1.5;margin:12px;color:#111;background:#fff;word-wrap:break-word}table{border-collapse:collapse;margin:8px 0;width:100%}td,th{border:1px solid #999;padding:4px 6px;font-size:13px;vertical-align:top}th{background:#eee}img{max-width:100%}h2{font-size:18px}.slide{border:1px solid #ccc;border-radius:8px;padding:10px;margin:10px 0}pre{white-space:pre-wrap;font-size:13px}</style>"
    private fun page(body: String, rtl: Boolean = false) = "<!DOCTYPE html><html dir=\"${if (rtl) "rtl" else "auto"}\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">$CSS</head><body>$body</body></html>"

    fun docxHtml(f: File): String = ZipFile(f).use { z ->
        val docXml = z.getEntry("word/document.xml")?.let { e -> z.getInputStream(e).use { String(it.readBytes(), Charsets.UTF_8) } } ?: return@use page("<p>Document Word illisible.</p>")
        val rels = z.getEntry("word/_rels/document.xml.rels")?.let { e -> z.getInputStream(e).use { Jsoup.parse(String(it.readBytes()), "", Parser.xmlParser()) } }
        fun img(rid: String): String {
            val target = rels?.select("Relationship")?.firstOrNull { it.attr("Id") == rid }?.attr("Target") ?: return ""
            val e = z.getEntry("word/" + target.removePrefix("/word/").removePrefix("./")) ?: return ""
            val b = z.getInputStream(e).use { it.readBytes() }
            val mime = if (target.endsWith(".png")) "image/png" else "image/jpeg"
            return "<img src=\"data:$mime;base64," + Base64.encodeToString(b, Base64.NO_WRAP) + "\">"
        }
        val x = Jsoup.parse(docXml, "", Parser.xmlParser())
        var rtl = false
        fun runs(p: Element): String {
            val sb = StringBuilder()
            p.select("w|r").forEach { r ->
                val b = r.selectFirst("w|rPr > w|b") != null; val i = r.selectFirst("w|rPr > w|i") != null
                if (r.selectFirst("w|rPr > w|rtl") != null) rtl = true
                r.children().forEach { k ->
                    when (k.tagName()) {
                        "w:t" -> { var t = esc(k.text()); if (b) t = "<b>$t</b>"; if (i) t = "<i>$t</i>"; sb.append(t) }
                        "w:br" -> sb.append("<br>"); "w:tab" -> sb.append(" &nbsp; ")
                        "w:drawing" -> k.selectFirst("a|blip")?.attr("r:embed")?.let { sb.append(img(it)) }
                    }
                }
            }
            return sb.toString()
        }
        fun para(p: Element): String {
            val style = p.selectFirst("w|pPr > w|pStyle")?.attr("w:val") ?: ""
            val jc = p.selectFirst("w|pPr > w|jc")?.attr("w:val") ?: ""
            if (p.selectFirst("w|pPr > w|bidi") != null) rtl = true
            val align = when (jc) { "center" -> "center"; "right", "end" -> "right"; "both" -> "justify"; else -> "" }
            val tag = if (Regex("(?i)heading|titre|title").containsMatchIn(style)) "h2" else "p"
            return "<$tag" + (if (align.isNotEmpty()) " style=\"text-align:$align\"" else "") + ">" + runs(p).ifEmpty { "&nbsp;" } + "</$tag>"
        }
        val body = x.selectFirst("w|body") ?: return@use page("")
        val sb = StringBuilder()
        body.children().forEach { e ->
            when (e.tagName()) {
                "w:p" -> sb.append(para(e))
                "w:tbl" -> { sb.append("<table>"); e.select("w|tr").forEach { tr -> sb.append("<tr>"); tr.select("w|tc").forEach { tc -> sb.append("<td>").append(tc.select("w|p").joinToString("") { para(it) }).append("</td>") }; sb.append("</tr>") }; sb.append("</table>") }
            }
        }
        page(sb.toString(), rtl)
    }

    fun xlsxHtml(f: File): String = ZipFile(f).use { z ->
        fun read(n: String) = z.getEntry(n)?.let { e -> z.getInputStream(e).use { String(it.readBytes(), Charsets.UTF_8) } }
        val shared = read("xl/sharedStrings.xml")?.let { Jsoup.parse(it, "", Parser.xmlParser()).select("si").map { si -> si.select("t").joinToString("") { it.text() } } } ?: emptyList()
        val wb = read("xl/workbook.xml")?.let { Jsoup.parse(it, "", Parser.xmlParser()) }
        val rels = read("xl/_rels/workbook.xml.rels")?.let { Jsoup.parse(it, "", Parser.xmlParser()) }
        val sheets = wb?.select("sheet")?.map { s -> s.attr("name") to (rels?.select("Relationship")?.firstOrNull { it.attr("Id") == s.attr("r:id") }?.attr("Target") ?: "") } ?: emptyList()
        val sb = StringBuilder()
        sheets.forEach { (name, target) ->
            val xml = read("xl/" + target.removePrefix("/xl/")) ?: return@forEach
            val d = Jsoup.parse(xml, "", Parser.xmlParser())
            sb.append("<h2>").append(esc(name)).append("</h2><table>")
            d.select("row").take(2000).forEach { r ->
                sb.append("<tr>")
                var expected = 0
                r.select("c").forEach { c ->
                    val ref = c.attr("r"); val colIdx = ref.takeWhile { it.isLetter() }.fold(0) { a, ch -> a * 26 + (ch - 'A' + 1) } - 1
                    while (expected < colIdx) { sb.append("<td></td>"); expected++ }
                    val v = when (c.attr("t")) { "s" -> shared.getOrElse(c.selectFirst("v")?.text()?.toIntOrNull() ?: -1) { "" }; "inlineStr" -> c.select("t").text(); else -> c.selectFirst("v")?.text() ?: "" }
                    sb.append("<td>").append(esc(v)).append("</td>"); expected++
                }
                sb.append("</tr>")
            }
            sb.append("</table>")
        }
        page(sb.toString().ifEmpty { "<p>Classeur vide.</p>" })
    }

    fun pptxHtml(f: File): String = ZipFile(f).use { z ->
        val slides = z.entries().toList().map { it.name }.filter { Regex("ppt/slides/slide\\d+\\.xml").matches(it) }.sortedBy { Regex("\\d+").find(it.substringAfterLast('/'))?.value?.toIntOrNull() ?: 0 }
        page(slides.mapIndexed { i, n ->
            val d = Jsoup.parse(z.getInputStream(z.getEntry(n)).use { String(it.readBytes()) }, "", Parser.xmlParser())
            "<div class=\"slide\"><b>Diapositive ${i + 1}</b>" + d.select("a|p").joinToString("") { p -> p.select("a|t").joinToString("") { esc(it.text()) }.let { if (it.isBlank()) "" else "<p>$it</p>" } } + "</div>"
        }.joinToString(""))
    }

    fun csvHtml(txt: String): String {
        val lines = txt.removePrefix("\uFEFF").lines().filter { it.isNotBlank() }
        val sep = if (lines.firstOrNull()?.count { it == ';' } ?: 0 > (lines.firstOrNull()?.count { it == ',' } ?: 0)) ';' else ','
        return page("<table>" + lines.take(3000).mapIndexed { i, l -> "<tr>" + l.split(sep).joinToString("") { c -> val t = if (i == 0) "th" else "td"; "<$t>" + esc(c.trim().trim('"')) + "</$t>" } + "</tr>" }.joinToString("") + "</table>")
    }

    fun toHtml(f: File, kind: String): String? = when (kind) {
        "docx" -> docxHtml(f); "xlsx" -> xlsxHtml(f); "pptx" -> pptxHtml(f)
        "html" -> f.readText(); "csv" -> csvHtml(f.readText())
        "text" -> page("<pre>" + esc(f.readText().take(400_000)) + "</pre>")
        else -> null
    }
}

object ViewerHolder { var file: File? = null; var title = ""; var mime = "" }

fun openInViewer(f: File, title: String, mime: String = "") { ViewerHolder.file = f; ViewerHolder.title = title; ViewerHolder.mime = mime; Nav.push(Scr.Viewer) }

@Composable
fun ViewerScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var file by remember { mutableStateOf(ViewerHolder.file) }
    var title by remember { mutableStateOf(ViewerHolder.title) }
    var mime by remember { mutableStateOf(ViewerHolder.mime) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val name = Attach.nameOf(ctx, uri)
            val f = withContext(Dispatchers.IO) { File(File(ctx.cacheDir, "liseuse").also { it.mkdirs() }, Export.safeName(name)).also { out -> ctx.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { i.copyTo(it) } } } }
            file = f; title = name; mime = Attach.mimeOf(ctx, uri, name)
        }
    }
    val f = file
    ScreenScaffold(title.ifEmpty { "Liseuse" }, { Nav.pop() }, actions = {
        IconButton(onClick = { pick.launch(arrayOf("*/*")) }) { Icon(Icons.Default.Search, "Ouvrir un fichier") }
        if (f != null) IconButton(onClick = { FileOps.share(ctx, f, title) }) { Icon(Icons.Default.Share, "Partager") }
        HelpButton("liseuse")
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (f == null || !f.exists()) {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Search, null, tint = Brand, modifier = Modifier.size(48.dp))
                    Text("Ouvrez un PDF, un document Word, un classeur Excel, une présentation, une image ou un fichier texte.", color = InkFaint, modifier = Modifier.padding(16.dp))
                    Button(onClick = { pick.launch(arrayOf("*/*")) }) { Text("Ouvrir un fichier") }
                }
            } else {
                val kind = Reader.kind(f.name.ifEmpty { title }, mime).let { if (it == "autre") Reader.kind(title, mime) else it }
                when (kind) {
                    "pdf" -> PdfView(f)
                    "image" -> ZoomImage(f)
                    "autre" -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Ce format ne peut pas être affiché ici.", color = InkFaint)
                        Button(onClick = { FileOps.openWith(ctx, f) }) { Text("Ouvrir avec une autre application") }
                    }
                    else -> {
                        var html by remember(f) { mutableStateOf<String?>(null) }
                        var err by remember(f) { mutableStateOf("") }
                        LaunchedEffect(f) { try { html = withContext(Dispatchers.IO) { Reader.toHtml(f, kind) } } catch (e: Throwable) { err = "Lecture impossible : " + (e.message ?: "") } }
                        when {
                            err.isNotEmpty() -> Column(Modifier.padding(24.dp)) { Text(err, color = Danger); Button(onClick = { FileOps.openWith(ctx, f) }) { Text("Ouvrir avec une autre application") } }
                            html == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                            else -> AndroidView(factory = { c -> WebView(c).apply {
                                settings.builtInZoomControls = true; settings.displayZoomControls = false; settings.loadWithOverviewMode = true; settings.useWideViewPort = kind == "html"
                                webViewClient = WebViewClient()
                                loadDataWithBaseURL("file:///android_asset/", html!!, "text/html", "utf-8", null)
                            } }, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomImage(f: File) {
    val img = remember(f) { try { BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = if (f.length() > 6_000_000) 2 else 1 })?.asImageBitmap() } catch (_: Throwable) { null } }
    var scale by remember { mutableFloatStateOf(1f) }
    var off by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val st = rememberTransformableState { z, p, _ -> scale = (scale * z).coerceIn(1f, 6f); off += p }
    Box(Modifier.fillMaxSize().background(Color.Black).transformable(st), contentAlignment = Alignment.Center) {
        if (img != null) Image(img, null, Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = off.x, translationY = off.y), contentScale = ContentScale.Fit)
        else Text("Image illisible.", color = Color.White)
    }
}

@Composable
fun PdfView(f: File) {
    val ctx = LocalContext.current
    val state = remember(f) {
        try { val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY); PdfRenderer(pfd) } catch (_: Throwable) { null }
    }
    DisposableEffect(f) { onDispose { try { state?.close() } catch (_: Throwable) {} } }
    if (state == null) { Column(Modifier.padding(24.dp)) { Text("PDF illisible (protégé ou endommagé).", color = Danger); Button(onClick = { FileOps.openWith(ctx, f) }) { Text("Ouvrir avec une autre application") } }; return }
    var scale by remember { mutableFloatStateOf(1f) }
    val st = rememberTransformableState { z, _, _ -> scale = (scale * z).coerceIn(1f, 4f) }
    val width = ctx.resources.displayMetrics.widthPixels
    Column(Modifier.fillMaxSize()) {
        Text("${state.pageCount} page(s) — pincez pour zoomer", color = InkFaint, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
        LazyColumn(Modifier.fillMaxSize().background(Track).transformable(st).graphicsLayer(scaleX = scale, scaleY = scale)) {
            items(state.pageCount) { i ->
                var bmp by remember(i) { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(i) {
                    bmp = withContext(Dispatchers.IO) {
                        synchronized(state) {
                            state.openPage(i).use { p ->
                                val w = (width * 1.5f).toInt(); val h = (w.toFloat() * p.height / p.width).toInt()
                                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); b.eraseColor(android.graphics.Color.WHITE)
                                p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); b.asImageBitmap()
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().padding(6.dp).aspectRatio(0.707f).background(Color.White), contentAlignment = Alignment.Center) {
                    bmp?.let { Image(it, "Page ${i + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) } ?: CircularProgressIndicator()
                }
            }
        }
    }
}

/** Liste des pièces jointes d'un enregistrement, avec ajout, ouverture dans la liseuse et suppression. */
@Composable
fun AttachmentsSection(coll: String, recId: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val v by Store.version
    val rec = remember(v, recId) { Store.byId(coll, recId) }
    val list = rec?.arr("attachments") ?: emptyList()
    var del by remember { mutableStateOf<org.json.JSONObject?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch { val n = withContext(Dispatchers.IO) { Attach.add(ctx, coll, recId, uris) }; toast(ctx, "$n fichier(s) joint(s)." + if (DriveSync.enabled(ctx)) " Envoi sur Drive à la prochaine synchronisation." else "") }
    }
    val cam = rememberCamera { b ->
        if (b != null) scope.launch {
            val f = withContext(Dispatchers.IO) { File(ctx.cacheDir, "photo.jpg").also { o -> o.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 88, it) } } }
            Attach.addFile(ctx, coll, recId, f, "Photo " + java.time.LocalDateTime.now().toString().take(16).replace('T', ' ') + ".jpg", "image/jpeg")
        }
    }
    fun open(a: org.json.JSONObject) {
        val lf = Attach.local(ctx, a)
        if (lf.exists()) { openInViewer(lf, a.s("titre"), a.s("type")); return }
        if (a.s("driveFileId").isEmpty()) { toast(ctx, "Fichier absent de ce téléphone."); return }
        SyncHolder.withToken("Téléchargement de « ${a.s("titre")} »…") { tk ->
            val b = withContext(Dispatchers.IO) { DriveSync.download(tk, a.s("driveFileId")) }
            withContext(Dispatchers.IO) { lf.writeBytes(b) }
            SyncState.msg.value = "Fichier téléchargé."; openInViewer(lf, a.s("titre"), a.s("type"))
        }
    }
    SectionTitle("Pièces jointes (${list.size})")
    CardBox {
        list.forEach { a ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(when (Reader.kind(a.s("titre"), a.s("type"))) { "image" -> Icons.Default.Face; "pdf" -> Icons.Default.Info; else -> Icons.Default.Email }, null, tint = Brand, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(a.s("titre"), fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOf(fmtDate(a.s("addedAt")), if (a.s("driveFileId").isNotEmpty()) "sur Drive" else "sur le téléphone", (a.optLong("size") / 1024).let { if (it > 0) "$it Ko" else "" }).filter { it.isNotEmpty() }.joinToString(" · "), color = InkFaint, fontSize = 11.5.sp, maxLines = 1)
                }
                IconButton(onClick = { open(a) }) { Icon(Icons.Default.Search, "Ouvrir", tint = Brand) }
                IconButton(onClick = { del = a }) { Icon(Icons.Default.Delete, "Retirer", tint = Danger) }
            }
        }
        Row(Modifier.horizontalScrollRow(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pick.launch(arrayOf("*/*")) }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Importer des fichiers") }
            OutlinedButton(onClick = { cam() }) { Icon(Icons.Default.Face, null); Spacer(Modifier.width(4.dp)); Text("Photo") }
        }
    }
    del?.let { a -> ConfirmDialog("Retirer « ${a.s("titre")} » ?", "Le fichier est retiré de cette fiche (la copie sur Drive est conservée).", onDismiss = { del = null }) { Attach.remove(ctx, coll, recId, a.s("id")); del = null } }
}
