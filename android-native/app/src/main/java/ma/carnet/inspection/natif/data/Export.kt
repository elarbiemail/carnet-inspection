package ma.carnet.inspection.natif.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume

/** Production de fichiers à partir des documents HTML de l'application : PDF, Word (.docx), Excel (.xlsx). */
object Export {
    fun outDir(ctx: Context) = File(ctx.cacheDir, "exports").also { it.mkdirs() }
    fun safeName(s: String) = s.replace(Regex("[\\\\/:*?\"<>|\\n\\r]+"), "_").trim().take(80).ifEmpty { "document" }

    // ---------------- PDF (sans fenêtre d'impression) ----------------
    /**
     * Rend le HTML dans une WebView hors écran puis dessine les pages A4 dans un PdfDocument.
     * Les sauts de page sont placés entre deux éléments (paragraphe, ligne de tableau…), jamais au milieu d'une ligne.
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun pdf(ctx: Context, html: String, out: File, landscape: Boolean = false): File = suspendCancellableCoroutine { cont ->
        val pageW = if (landscape) 842 else 595; val pageH = if (landscape) 595 else 842; val margin = 34
        val contentWpt = pageW - 2 * margin; val contentHpt = pageH - 2 * margin
        val density = ctx.resources.displayMetrics.density
        val cssW = (contentWpt / 72f * 96f).toInt()               // largeur en px CSS d'une page
        val widthPx = (cssW * density).toInt()
        val scale = contentWpt.toFloat() / widthPx                // points PDF par pixel écran
        val w = WebView(ctx)
        w.settings.javaScriptEnabled = true
        w.settings.allowFileAccess = true
        w.settings.loadWithOverviewMode = false; w.settings.useWideViewPort = false
        w.setInitialScale((density * 100).toInt())
        val done = booleanArrayOf(false)
        var hostRef: android.view.ViewGroup? = null
        fun fail(e: Throwable) { if (!done[0]) { done[0] = true; try { hostRef?.removeView(w) } catch (_: Throwable) {}; try { w.destroy() } catch (_: Throwable) {}; cont.resumeWith(Result.failure(e)) } }
        w.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                Handler(Looper.getMainLooper()).postDelayed({
                    val js = "(function(){var o=[];document.querySelectorAll('p,tr,h1,h2,h3,li,img,.sig,.hd,.meta div,.box,table').forEach(function(e){var r=e.getBoundingClientRect();o.push(r.bottom+window.scrollY);});return JSON.stringify(o);})()"
                    view.evaluateJavascript(js) { res ->
                        try {
                            val raw = res?.trim('"')?.replace("\\\"", "\"") ?: "[]"
                            val a = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
                            val bottoms = (0 until a.length()).map { (a.optDouble(it) * density).toFloat() }.sorted()
                            view.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                            val totalPx = maxOf(view.measuredHeight, (view.contentHeight * density).toInt(), 1)
                            view.layout(0, 0, widthPx, totalPx)
                            val pagePx = contentHpt / scale
                            val doc = PdfDocument()
                            var top = 0f; var n = 1
                            while (top < totalPx - 2) {
                                var bottom = top + pagePx
                                if (bottom < totalPx) {
                                    val cands = bottoms.filter { it > top + pagePx * 0.5f && it <= bottom }
                                    if (cands.isNotEmpty()) bottom = cands.last() + 1
                                } else bottom = totalPx.toFloat()
                                val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, n++).create())
                                val c = page.canvas
                                c.translate(margin.toFloat(), margin.toFloat()); c.scale(scale, scale)
                                c.clipRect(0f, 0f, widthPx.toFloat(), bottom - top)
                                c.translate(0f, -top)
                                view.draw(c)
                                doc.finishPage(page)
                                top = bottom
                                if (n > 300) break
                            }
                            out.parentFile?.mkdirs()
                            out.outputStream().use { doc.writeTo(it) }; doc.close()
                            done[0] = true; try { hostRef?.removeView(w) } catch (_: Throwable) {}; w.destroy(); cont.resume(out)
                        } catch (e: Throwable) { fail(e) }
                    }
                }, 350)
            }
        }
        // rattachée (invisible) à la fenêtre pour que le moteur web dessine bien la page
        var host: android.view.ViewGroup? = null
        run { var c: Context? = ctx; while (c is android.content.ContextWrapper) { if (c is android.app.Activity) { host = c.window?.decorView as? android.view.ViewGroup; break }; c = c.baseContext } }
        hostRef = host
        try { host?.addView(w, android.view.ViewGroup.LayoutParams(widthPx, pageH * 3)); w.translationX = -widthPx * 2f; w.alpha = 0.01f } catch (_: Throwable) { host = null }
        cont.invokeOnCancellation { try { host?.removeView(w) } catch (_: Throwable) {} }
        w.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(pageH * 3, View.MeasureSpec.EXACTLY))
        w.layout(0, 0, widthPx, pageH * 3)
        w.loadDataWithBaseURL("file:///android_asset/", html.replace("width=device-width", "width=$cssW"), "text/html", "utf-8", null)
        cont.invokeOnCancellation { try { w.destroy() } catch (_: Throwable) {} }
    }

    // ---------------- Word (.docx) ----------------
    private fun x(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private class Docx(val ctx: Context, val rtl: Boolean) {
        val body = StringBuilder()
        val media = mutableListOf<Pair<String, ByteArray>>()
        var imgN = 0

        val titleFont = Fonts.slot(rtl, true)
        fun rPr(bold: Boolean, size: Int?) = "<w:rPr>" + (if (size != null && size >= 24 && titleFont.isNotEmpty()) "<w:rFonts w:ascii=\"$titleFont\" w:hAnsi=\"$titleFont\" w:cs=\"$titleFont\"/>" else "") + (if (bold) "<w:b/><w:bCs/>" else "") + (if (size != null) "<w:sz w:val=\"$size\"/><w:szCs w:val=\"$size\"/>" else "") + (if (rtl) "<w:rtl/>" else "") + "</w:rPr>"
        fun pPr(align: String?, spacingAfter: Int = 80, border: Boolean = false, keep: Boolean = false): String {
            val jc = when (align) { "center" -> "center"; "right" -> if (rtl) "left" else "right"; "left" -> if (rtl) "right" else "left"; "justify" -> "both"; else -> null }
            return "<w:pPr>" + (if (keep) "<w:keepNext/>" else "") + (if (border) "<w:pBdr><w:top w:val=\"single\" w:sz=\"8\" w:space=\"4\" w:color=\"000000\"/><w:left w:val=\"single\" w:sz=\"8\" w:space=\"4\" w:color=\"000000\"/><w:bottom w:val=\"single\" w:sz=\"8\" w:space=\"4\" w:color=\"000000\"/><w:right w:val=\"single\" w:sz=\"8\" w:space=\"4\" w:color=\"000000\"/></w:pBdr>" else "") +
                (if (rtl) "<w:bidi/>" else "") + "<w:spacing w:after=\"$spacingAfter\" w:line=\"276\" w:lineRule=\"auto\"/>" + (if (jc != null) "<w:jc w:val=\"$jc\"/>" else "") + "</w:pPr>"
        }

        fun image(src: String, maxWmm: Float, maxHmm: Float): String {
            val bytes: ByteArray = try {
                when {
                    src.startsWith("data:") -> Base64.decode(src.substringAfter(","), Base64.DEFAULT)
                    else -> ctx.assets.open(src.removePrefix("file:///android_asset/")).use { it.readBytes() }
                }
            } catch (_: Exception) { return "" }
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            if (o.outWidth <= 0) return ""
            val ext = if (src.contains("image/png") || src.endsWith(".png")) "png" else "jpeg"
            imgN++; val name = "image$imgN.$ext"; media.add(name to bytes)
            var wmm = maxWmm; var hmm = wmm * o.outHeight / o.outWidth
            if (hmm > maxHmm) { hmm = maxHmm; wmm = hmm * o.outWidth / o.outHeight }
            val cx = (wmm * 36000).toLong(); val cy = (hmm * 36000).toLong()
            return "<w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\"><wp:extent cx=\"$cx\" cy=\"$cy\"/><wp:docPr id=\"$imgN\" name=\"Image $imgN\"/>" +
                "<a:graphic xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
                "<pic:pic xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:nvPicPr><pic:cNvPr id=\"$imgN\" name=\"$name\"/><pic:cNvPicPr/></pic:nvPicPr>" +
                "<pic:blipFill><a:blip r:embed=\"rIdImg$imgN\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>" +
                "</a:graphicData></a:graphic></wp:inline></w:drawing></w:r>"
        }

        private fun mm(style: String, prop: String): Float? = Regex("$prop\\s*:\\s*([\\d.]+)mm").find(style)?.groupValues?.get(1)?.toFloatOrNull()

        /** Runs (texte, gras, retours à la ligne, images) d'un élément. */
        fun runs(n: Node, bold: Boolean = false, size: Int? = null): String {
            val sb = StringBuilder()
            when (n) {
                is TextNode -> { val t = n.wholeText.replace(Regex("\\s+"), " "); if (t.isNotEmpty()) sb.append("<w:r>").append(rPr(bold, size)).append("<w:t xml:space=\"preserve\">").append(x(t)).append("</w:t></w:r>") }
                is Element -> when (n.tagName()) {
                    "br" -> sb.append("<w:r><w:br/></w:r>")
                    "img" -> { val st = n.attr("style"); sb.append(image(n.attr("src"), mm(st, "max-width") ?: mm(st, "width") ?: 60f, mm(st, "max-height") ?: 40f)) }
                    "b", "strong", "th" -> n.childNodes().forEach { sb.append(runs(it, true, size)) }
                    else -> n.childNodes().forEach { sb.append(runs(it, bold || n.hasClass("b"), size)) }
                }
            }
            return sb.toString()
        }

        fun align(e: Element, def: String? = null): String? {
            val st = e.attr("style")
            return Regex("text-align\\s*:\\s*(\\w+)").find(st)?.groupValues?.get(1) ?: if (e.hasClass("hd")) "center" else def
        }

        fun para(e: Element, alignDef: String? = null, bold: Boolean = false, size: Int? = null, after: Int = 80, border: Boolean = false) {
            val r = runs(e, bold, size)
            body.append("<w:p>").append(pPr(align(e, alignDef), after, border, keep = size != null)).append(r).append("</w:p>")
        }

        fun table(t: Element) {
            val rows = t.select("tr")
            if (rows.isEmpty()) return
            val ncol = rows.maxOf { it.select("th,td").size }.coerceAtLeast(1)
            val widths = rows.first()!!.select("th,td").map { c -> Regex("width\\s*:\\s*([\\d.]+)%").find(c.attr("style"))?.groupValues?.get(1)?.toFloatOrNull() }
            val total = 9600
            val ws = (0 until ncol).map { i -> ((widths.getOrNull(i) ?: (100f / ncol)) / 100f * total).toInt() }
            body.append("<w:tbl><w:tblPr>").append(if (rtl) "<w:bidiVisual/>" else "").append("<w:tblW w:w=\"5000\" w:type=\"pct\"/><w:tblLayout w:type=\"fixed\"/><w:tblBorders>")
            listOf("top", "left", "bottom", "right", "insideH", "insideV").forEach { body.append("<w:$it w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>") }
            body.append("</w:tblBorders><w:tblCellMar><w:left w:w=\"80\" w:type=\"dxa\"/><w:right w:w=\"80\" w:type=\"dxa\"/></w:tblCellMar></w:tblPr><w:tblGrid>")
            ws.forEach { body.append("<w:gridCol w:w=\"$it\"/>") }
            body.append("</w:tblGrid>")
            rows.forEachIndexed { ri, tr ->
                val head = tr.parent()?.tagName() == "thead" || tr.select("th").isNotEmpty()
                body.append("<w:tr>").append(if (head && ri == 0) "<w:trPr><w:tblHeader/></w:trPr>" else "<w:trPr><w:cantSplit/></w:trPr>")
                val cells = tr.select("th,td")
                (0 until ncol).forEach { ci ->
                    val c = cells.getOrNull(ci)
                    body.append("<w:tc><w:tcPr><w:tcW w:w=\"${ws[ci]}\" w:type=\"dxa\"/>").append(if (head || c?.hasClass("lbl") == true) "<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"EEEEEE\"/>" else "").append("</w:tcPr>")
                    body.append("<w:p>").append(pPr(if (c != null) align(c) else null, 0)).append(if (c != null) runs(c, head, 20) else "").append("</w:p></w:tc>")
                }
                body.append("</w:tr>")
            }
            body.append("</w:tbl><w:p>").append(pPr(null, 60)).append("</w:p>")
        }

        fun block(e: Element) {
            when (e.tagName()) {
                "h1" -> para(e, "center", true, 32, 200)
                "h2" -> para(e, null, true, 26, 100)
                "h3" -> para(e, null, true, 24, 80)
                "p", "li" -> para(e, if (e.parents().any { it.hasClass("hd") }) "center" else null)
                "table" -> table(e)
                "img" -> body.append("<w:p>").append(pPr("center")).append(runs(e)).append("</w:p>")
                "br" -> body.append("<w:p/>")
                "ul", "ol", "tbody", "thead" -> e.children().forEach { block(it) }
                else -> {
                    val blockKids = e.children().any { it.tagName() in listOf("p", "div", "table", "h1", "h2", "h3", "ul", "ol") }
                    if (e.hasClass("box")) para(e, "center", true, 30, 120, border = true)
                    else if (e.hasClass("sig")) {
                        e.children().forEach { k -> if (k.tagName() == "img") body.append("<w:p>").append(pPr(align(e, "right"), 40)).append(runs(k)).append("</w:p>") else { body.append("<w:p>").append(pPr(align(e, "right"), 40)).append(runs(k)).append("</w:p>") } }
                    } else if (e.hasClass("hd")) {
                        e.childNodes().forEach { k -> if (k is Element) body.append("<w:p>").append(pPr("center", 20)).append(runs(k, k.hasClass("b"), 21)).append("</w:p>") }
                        body.append("<w:p>").append(pPr(null, 120)).append("</w:p>")
                    } else if (!blockKids) { if (e.text().isNotBlank() || e.select("img").isNotEmpty()) para(e) else if (e.attr("style").contains("height")) body.append("<w:p/>") }
                    else e.childNodes().forEach { k -> when (k) { is Element -> block(k); is TextNode -> if (k.text().isNotBlank()) body.append("<w:p>").append(pPr(null)).append(runs(k)).append("</w:p>") } }
                }
            }
        }
    }

    fun docx(ctx: Context, html: String, out: File, landscape: Boolean = false): File {
        val doc = Jsoup.parse(html)
        val rtl = doc.selectFirst("html")?.attr("dir") == "rtl"
        val d = Docx(ctx, rtl)
        doc.body().childNodes().forEach { n -> when (n) { is Element -> d.block(n); is TextNode -> if (n.text().isNotBlank()) d.body.append("<w:p>").append(d.pPr(null)).append(d.runs(n)).append("</w:p>") } }
        val pgW = if (landscape) 16838 else 11906; val pgH = if (landscape) 11906 else 16838
        val docXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" " +
            "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" " +
            "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><w:body>" + d.body +
            "<w:sectPr>" + (if (landscape) "<w:pgSz w:w=\"$pgW\" w:h=\"$pgH\" w:orient=\"landscape\"/>" else "<w:pgSz w:w=\"$pgW\" w:h=\"$pgH\"/>") +
            "<w:pgMar w:top=\"850\" w:right=\"850\" w:bottom=\"850\" w:left=\"850\" w:header=\"400\" w:footer=\"400\" w:gutter=\"0\"/>" + (if (rtl) "<w:bidi/>" else "") + "</w:sectPr></w:body></w:document>"
        val rels = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rIdStyles\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>")
        d.media.forEachIndexed { i, (name, _) -> rels.append("<Relationship Id=\"rIdImg${i + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/$name\"/>") }
        rels.append("</Relationships>")
        val font = Fonts.slot(rtl, false).ifEmpty { if (rtl) "Arial" else "Calibri" }
        val styles = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:docDefaults><w:rPrDefault><w:rPr>" +
            "<w:rFonts w:ascii=\"$font\" w:hAnsi=\"$font\" w:cs=\"$font\" w:eastAsia=\"$font\"/><w:sz w:val=\"23\"/><w:szCs w:val=\"23\"/><w:lang w:val=\"fr-FR\" w:bidi=\"ar-MA\"/></w:rPr></w:rPrDefault>" +
            "<w:pPrDefault><w:pPr><w:spacing w:after=\"80\" w:line=\"276\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults>" +
            "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/></w:style></w:styles>"
        val ct = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Default Extension=\"png\" ContentType=\"image/png\"/><Default Extension=\"jpeg\" ContentType=\"image/jpeg\"/>" +
            "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
            "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/></Types>"
        val root = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>"
        out.parentFile?.mkdirs()
        ZipOutputStream(out.outputStream()).use { z ->
            fun put(n: String, b: ByteArray) { z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() }
            put("[Content_Types].xml", ct.toByteArray()); put("_rels/.rels", root.toByteArray())
            put("word/document.xml", docXml.toByteArray()); put("word/styles.xml", styles.toByteArray()); put("word/_rels/document.xml.rels", rels.toString().toByteArray())
            d.media.forEach { (n, b) -> put("word/media/$n", b) }
        }
        return out
    }

    // ---------------- Excel (.xlsx) ----------------
    private fun col(i: Int): String { var n = i + 1; val sb = StringBuilder(); while (n > 0) { val r = (n - 1) % 26; sb.insert(0, ('A' + r)); n = (n - 1) / 26 }; return sb.toString() }

    /** Classeur Excel : une feuille par tableau (nom, lignes ; la première ligne est l'en-tête). */
    fun xlsx(sheets: List<Pair<String, List<List<String>>>>, out: File): File {
        val shs = sheets.ifEmpty { listOf("Feuille 1" to emptyList()) }
        out.parentFile?.mkdirs()
        ZipOutputStream(out.outputStream()).use { z ->
            fun put(n: String, s: String) { z.putNextEntry(ZipEntry(n)); z.write(s.toByteArray()); z.closeEntry() }
            put("[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
                shs.indices.joinToString("") { "<Override PartName=\"/xl/worksheets/sheet${it + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" } + "</Types>")
            put("_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>")
            val names = mutableSetOf<String>()
            put("xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>" +
                shs.mapIndexed { i, (n, _) -> var nm = n.replace(Regex("[\\\\/?*\\[\\]:]"), " ").take(28).ifBlank { "Feuille ${i + 1}" }; while (!names.add(nm)) nm = nm.take(25) + " ${i + 1}"; "<sheet name=\"${x(nm)}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>" }.joinToString("") + "</sheets></workbook>")
            put("xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                shs.indices.joinToString("") { "<Relationship Id=\"rId${it + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${it + 1}.xml\"/>" } +
                "<Relationship Id=\"rIdS\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>")
            put("xl/styles.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
                "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
                "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFDDEBE7\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>" +
                "<borders count=\"2\"><border/><border><left style=\"thin\"/><right style=\"thin\"/><top style=\"thin\"/><bottom style=\"thin\"/></border></borders><cellStyleXfs count=\"1\"><xf/></cellStyleXfs>" +
                "<cellXfs count=\"3\"><xf/><xf fontId=\"1\" fillId=\"2\" borderId=\"1\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\"><alignment wrapText=\"1\" vertical=\"center\"/></xf><xf borderId=\"1\" applyBorder=\"1\"><alignment wrapText=\"1\" vertical=\"top\"/></xf></cellXfs></styleSheet>")
            shs.forEachIndexed { si, (_, rows) ->
                val ncol = rows.maxOfOrNull { it.size } ?: 1
                val widths = (0 until ncol).map { c -> (rows.maxOfOrNull { it.getOrNull(c)?.length ?: 0 } ?: 8).coerceIn(5, 60) + 2 }
                val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews><cols>")
                widths.forEachIndexed { i, w -> sb.append("<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$w\" customWidth=\"1\"/>") }
                sb.append("</cols><sheetData>")
                rows.forEachIndexed { r, row ->
                    sb.append("<row r=\"${r + 1}\">")
                    row.forEachIndexed { c, v ->
                        val ref = col(c) + (r + 1); val st = if (r == 0) 1 else 2
                        val num = v.trim().replace(',', '.').toDoubleOrNull()
                        if (num != null && r > 0 && v.trim().length < 12 && !v.trim().startsWith("0")) sb.append("<c r=\"$ref\" s=\"$st\"><v>$num</v></c>")
                        else sb.append("<c r=\"$ref\" s=\"$st\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(x(v)).append("</t></is></c>")
                    }
                    sb.append("</row>")
                }
                sb.append("</sheetData><pageSetup orientation=\"landscape\" fitToWidth=\"1\"/></worksheet>")
                put("xl/worksheets/sheet${si + 1}.xml", sb.toString())
            }
        }
        return out
    }

    /** Extrait les tableaux d'un document HTML (pour l'export Excel). */
    fun tablesOf(html: String): List<Pair<String, List<List<String>>>> {
        val doc = Jsoup.parse(html)
        val title = doc.selectFirst("h1")?.text() ?: "Tableau"
        return doc.select("table").mapIndexed { i, t ->
            val head = t.previousElementSiblings().firstOrNull { it.tagName() in listOf("h2", "h1", "p") }?.text()?.take(28)
            (head ?: title).ifBlank { "Tableau ${i + 1}" } to t.select("tr").map { tr -> tr.select("th,td").map { it.wholeText().trim().replace(Regex("\\s+"), " ") } }
        }
    }
}
