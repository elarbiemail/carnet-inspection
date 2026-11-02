package ma.carnet.inspection.natif.data

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID

/**
 * Documents officiels, avec les mêmes champs que le carnet web (un document créé ici s'ouvre et se
 * régénère dans le carnet web, et inversement). Rendu en HTML (A4) : aperçu, impression et PDF
 * par le système d'impression d'Android.
 */
object Docs {
    val LABELS = linkedMapOf(
        "presence" to "Liste de présence", "compte_rendu" to "Compte-rendu de réunion",
        "observation" to "Grille d'observation", "rapport" to "Rapport de synthèse",
        "attestation" to "Attestation de présence", "lettre_information" to "Lettre d'information",
        "demande_voiture" to "Demande de voiture de service", "rapport_inspection" to "Rapport d'inspection",
        "bordereau" to "Bordereau d'envoi", "rapport_formation" to "Rapport de synthèse de la formation (modèle 4)",
        "plan_action" to "Plan d'action", "custom" to "Document personnalisé"
    )
    val FOR_TYPE = mapOf(
        "inspection" to listOf("demande_voiture", "observation", "rapport_inspection", "bordereau"),
        "visite_conseil" to listOf("demande_voiture", "observation", "rapport", "bordereau"),
        "reunion_pedagogique" to listOf("lettre_information", "presence", "compte_rendu", "attestation", "bordereau"),
        "cours_experimental" to listOf("lettre_information", "presence", "compte_rendu", "attestation", "bordereau"),
        "formation" to listOf("lettre_information", "presence", "rapport_formation", "attestation", "compte_rendu", "bordereau"),
        "reunion_distance" to listOf("lettre_information", "compte_rendu", "bordereau"),
        "reunion_administrative" to listOf("lettre_information", "compte_rendu", "bordereau"),
        "recherche_action" to listOf("rapport", "bordereau"),
        "observation_examen" to listOf("observation", "rapport", "bordereau"),
        "conception_examen" to listOf("rapport", "bordereau"),
        "validation_ts" to listOf("bordereau"),
        "autre" to listOf("rapport", "bordereau")
    )
    val RTL = setOf("presence", "compte_rendu", "attestation", "lettre_information", "bordereau", "plan_action", "demande_voiture", "rapport_formation")
    val DEFAULT_CRITERES = listOf("Préparation de la séance", "Maîtrise des contenus", "Démarche pédagogique", "Gestion de la classe",
        "Utilisation des supports didactiques", "Évaluation et rétroaction", "Participation des élèves", "Respect du timing")
    val TT_AR = mapOf("inspection" to "زيارة تفتيشية", "visite_conseil" to "زيارة تأطيرية", "reunion_pedagogique" to "لقاء تربوي",
        "cours_experimental" to "درس تجريبي", "formation" to "دورة تكوينية", "reunion_distance" to "اجتماع عن بعد",
        "reunion_administrative" to "اجتماع إداري", "recherche_action" to "بحث تدخلي", "observation_examen" to "تتبع امتحان",
        "conception_examen" to "إعداد امتحان", "validation_ts" to "المصادقة على جداول الحصص", "autre" to "نشاط تربوي")

    fun label(d: JSONObject): String = if (d.s("type") == "custom") d.s("titre").ifEmpty { "Document personnalisé" } else LABELS[d.s("type")] ?: d.s("titre").ifEmpty { d.s("type") }
    fun isRtl(d: JSONObject): Boolean = if (d.s("type") == "custom") d.optBoolean("rtl") else d.s("type") in RTL

    // ---------- champs du formulaire ----------
    enum class Kind { TEXT, AREA, DATE, TIME, SELECT }
    data class Field(val key: String, val label: String, val kind: Kind = Kind.TEXT, val ai: Boolean = false, val options: List<String> = emptyList())
    data class Col(val key: String, val label: String, val weight: Float = 1f)
    data class ListDef(val key: String, val label: String, val cols: List<Col>, val fromTeachers: Boolean = false)

    fun fields(type: String): List<Field> = when (type) {
        "presence" -> listOf(Field("objet", "Objet"), Field("lieu", "Lieu"), Field("date", "Date", Kind.DATE))
        "compte_rendu" -> listOf(Field("lieu", "Lieu"), Field("date", "Date", Kind.DATE), Field("presents", "Présents", Kind.AREA), Field("absents", "Absents"),
            Field("ordreJour", "Ordre du jour", Kind.AREA, true), Field("pointsDiscutes", "Points discutés", Kind.AREA, true), Field("decisions", "Décisions", Kind.AREA, true))
        "observation" -> listOf(Field("enseignantNom", "Enseignant(e)"), Field("classe", "Classe"), Field("matiere", "Matière"), Field("duree", "Durée"), Field("date", "Date", Kind.DATE),
            Field("pointsForts", "Points forts", Kind.AREA, true), Field("axesAmelioration", "Axes d'amélioration", Kind.AREA, true), Field("synthese", "Synthèse", Kind.AREA, true))
        "rapport" -> listOf(Field("date", "Date", Kind.DATE), Field("texte", "Rapport", Kind.AREA, true))
        "attestation" -> listOf(Field("beneficiaireNom", "Bénéficiaire"), Field("fonction", "Fonction"), Field("objet", "Objet / activité"), Field("lieu", "Lieu"),
            Field("date", "Date", Kind.DATE), Field("texteLibre", "Texte (facultatif, remplace le texte type)", Kind.AREA, true))
        "lettre_information" -> listOf(Field("destinataire", "Destinataire"), Field("objet", "Objet"), Field("date", "Date", Kind.DATE), Field("corps", "Corps de la lettre", Kind.AREA, true))
        "demande_voiture" -> listOf(Field("destinataire", "Destinataire"), Field("objet", "Objet"), Field("date", "Date", Kind.DATE), Field("heureDepart", "Heure de départ", Kind.TIME),
            Field("heureRetour", "Heure de retour", Kind.TIME), Field("destination", "Destination (facultatif)"), Field("corpsLibre", "Texte libre (facultatif)", Kind.AREA))
        "rapport_inspection" -> listOf(Field("enseignantNom", "Enseignant(e)"), Field("civilite", "Civilité", Kind.SELECT, options = listOf("M.", "Mme.")), Field("ppr", "PPR"),
            Field("cadre", "Cadre"), Field("lieu", "Établissement"), Field("localite", "Localité"), Field("classe", "Classe"), Field("matiere", "Matière"), Field("manuel", "Manuel"),
            Field("nationalite", "Nationalité"), Field("precInspection", "Précédente inspection (année)"), Field("precNote", "Note précédente"), Field("par", "Par"),
            Field("date", "Date", Kind.DATE), Field("note", "Note d'inspection (/20)"),
            Field("notesObs", "Observations de classe (notes)", Kind.AREA), Field("texte", "Rapport (texte rédigé)", Kind.AREA, true),
            Field("constats", "Constats", Kind.AREA, true), Field("recommandations", "Recommandations", Kind.AREA, true))
        "bordereau" -> listOf(Field("destinataire", "Destinataire"), Field("reference", "Référence"), Field("objet", "Objet"), Field("date", "Date", Kind.DATE), Field("observations", "Observations", Kind.AREA))
        "rapport_formation" -> listOf(Field("date", "التاريخ — Date", Kind.DATE), Field("lieu", "المكان — Lieu"), Field("objet", "الموضوع — Thème"),
            Field("organisateur", "الجهة المنظمة — Organisateur"), Field("formateurs", "المكون(ون) — Formateur(s)"), Field("cible", "الفئة المستفيدة — Public"), Field("nombre", "العدد — Nombre"),
            Field("objectifs", "الأهداف — Objectifs", Kind.AREA, true), Field("axes", "المحاور — Axes", Kind.AREA, true), Field("conclusions", "الخلاصات — Conclusions", Kind.AREA, true),
            Field("evalFormation", "Taux d'évaluation : التكوين"), Field("evalMethodologie", "Taux d'évaluation : المنهجية"), Field("evalGenerale", "Taux d'évaluation : التقييم العام"),
            Field("recommandations", "التوصيات والاقتراحات — Recommandations", Kind.AREA, true))
        "plan_action" -> listOf(Field("objet", "Objet"), Field("lieu", "Lieu"), Field("date", "Date", Kind.DATE), Field("contexte", "Contexte", Kind.AREA, true), Field("objectifs", "Objectifs", Kind.AREA, true))
        "custom" -> listOf(Field("titre", "Titre"), Field("date", "Date", Kind.DATE), Field("corps", "Contenu (balises {{…}})", Kind.AREA))
        else -> listOf(Field("date", "Date", Kind.DATE))
    }

    fun lists(type: String): List<ListDef> = when (type) {
        "presence" -> listOf(ListDef("participants", "Participants", listOf(Col("nom", "Nom et prénom", 2f), Col("ppr", "PPR"), Col("etablissement", "Établissement", 2f)), true))
        "compte_rendu" -> listOf(ListDef("actions", "Plan d'action", listOf(Col("action", "Action", 2f), Col("responsable", "Responsable"), Col("echeance", "Échéance"))))
        "bordereau" -> listOf(ListDef("pieces", "Pièces envoyées", listOf(Col("designation", "Désignation", 2f), Col("nombre", "Nombre", 0.6f), Col("observation", "Observation", 1.4f))))
        "plan_action" -> listOf(ListDef("actions", "Actions", listOf(Col("action", "Action", 2f), Col("responsable", "Responsable"), Col("echeance", "Échéance"))))
        else -> emptyList()
    }

    // ---------- document par défaut (comme buildDefaultDoc du carnet web) ----------
    fun presenceRow(e: JSONObject?, inv: JSONObject?): JSONObject {
        val etId = e?.s("etablissementId") ?: ""
        val et = Store.byId("etablissements", etId)
        val etNom = et?.let { it.s("nomAr").ifEmpty { it.s("nom") } } ?: (inv?.s("etablissementNom") ?: "")
        return JSONObject().put("nom", e?.s("nom") ?: inv?.s("nom") ?: "").put("ppr", e?.s("ppr")?.ifEmpty { inv?.s("ppr") ?: "" } ?: inv?.s("ppr") ?: "").put("etablissement", etNom)
    }

    fun defaultDoc(task: JSONObject, type: String, tpl: JSONObject? = null): JSONObject {
        val d = JSONObject().put("id", UUID.randomUUID().toString()).put("taskId", task.s("id")).put("type", type)
            .put("titre", LABELS[type] ?: type).put("date", task.s("echeance").ifEmpty { LocalDate.now().toString() })
        val inv = task.arr("invites"); val p = Store.profil()
        val ens = Store.byId("enseignants", task.s("enseignantId"))
        val lieu = task.s("lieu").ifEmpty { taskEtabName(task) }
        when (type) {
            "presence" -> {
                val rows = JSONArray()
                if (inv.isNotEmpty()) inv.forEach { rows.put(presenceRow(Store.byId("enseignants", it.s("enseignantId")), it)) }
                else Store.list("enseignants").filter { it.s("etablissementId") == task.s("etablissementId") && task.s("etablissementId").isNotEmpty() }.forEach { rows.put(presenceRow(it, null)) }
                d.put("objet", task.s("titre")).put("lieu", lieu).put("participants", rows)
            }
            "compte_rendu" -> d.put("lieu", lieu).put("presents", inv.joinToString(", ") { it.s("nom") }).put("absents", "").put("ordreJour", "")
                .put("pointsDiscutes", "").put("decisions", "").put("actions", JSONArray())
            "observation" -> {
                val crit = JSONArray()
                val list = Store.root.optJSONObject("templates")?.optJSONArray("criteres")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: DEFAULT_CRITERES
                list.forEach { crit.put(JSONObject().put("nom", it).put("note", "").put("commentaire", "")) }
                d.put("enseignantNom", taskEnsName(task)).put("classe", "").put("matiere", ens?.s("matiere") ?: "").put("duree", "").put("criteres", crit)
                    .put("pointsForts", "").put("axesAmelioration", "").put("synthese", "").put("variante", "standard").put("seance", "normale")
            }
            "rapport" -> d.put("texte", "")
            "attestation" -> d.put("beneficiaireNom", taskEnsName(task)).put("fonction", "Enseignant(e)").put("objet", task.s("titre")).put("lieu", lieu).put("texteLibre", "")
            "demande_voiture" -> d.put("destinataire", "المدير الإقليمي").put("objet", "طلب سيارة المصلحة").put("heureDepart", taskTime(task)).put("heureRetour", task.s("heureFin")).put("destination", "").put("corpsLibre", "")
            "lettre_information" -> {
                d.put("destinataire", "المدير الإقليمي").put("objet", TT_AR[Ref.type(task)] ?: task.s("titre")).put("lieu", lieu)
                d.put("invites", JSONArray(inv.map { it }))
                val quand = dateAr(task.s("echeance")) + (if (taskTime(task).isNotEmpty()) " على الساعة " + taskTime(task) else "")
                d.put("corps", if (inv.isNotEmpty())
                    "يشرفني أن أطلب منكم دعوة السيدات والسادة الأساتذة الواردة أسماؤهم في الجدول أسفله لحضور " + (TT_AR[Ref.type(task)] ?: "لقاء تربوي") + " يوم " + quand + (if (lieu.isNotEmpty()) " ب" + lieu else "") + "."
                else "يشرفني أن أخبركم بأنني سأقوم ب" + (TT_AR[Ref.type(task)] ?: "زيارة تربوية") + " يوم " + quand + (if (lieu.isNotEmpty()) " ب" + lieu else "") + ".")
            }
            "rapport_inspection" -> {
                d.put("enseignantNom", taskEnsName(task)).put("lieu", taskEtabName(task).ifEmpty { lieu }).put("classe", "").put("matiere", ens?.s("matiere") ?: "")
                    .put("appreciation", task.s("noteGlobale")).put("constats", "").put("recommandations", "").put("ppr", ens?.s("ppr") ?: "").put("cadre", ens?.s("cadre") ?: "")
                    .put("civilite", when (ens?.s("genre")) { "F" -> "Mme."; "H" -> "M."; else -> "" }).put("localite", p.s("province")).put("manuel", "").put("nationalite", "Marocaine")
                    .put("precInspection", "").put("precNote", "").put("par", "Nous-même").put("note", "").put("notesObs", "").put("pistesObs", "").put("texte", "").put("redacteur", p.s("nomSignature"))
                if (ens != null) d.put("enseignantId", ens.s("id"))
                val prev = ens?.let { ensInspections(it).firstOrNull() }
                if (prev != null) { d.put("precInspection", prev.date.take(4)); d.put("precNote", prev.note) }
            }
            "bordereau" -> d.put("destinataire", "المدير الإقليمي").put("reference", "").put("objet", task.s("titre"))
                .put("pieces", JSONArray().put(JSONObject().put("designation", task.s("titre")).put("nombre", "1").put("observation", ""))).put("observations", "")
            "rapport_formation" -> {
                val fo = task.optJSONObject("formation") ?: JSONObject()
                d.put("lieu", lieu).put("objet", fo.s("sujet").ifEmpty { task.s("titre") }).put("organisateur", fo.s("organisateur")).put("formateurs", fo.s("formateurs"))
                    .put("cible", fo.s("cible")).put("nombre", fo.s("nombre").ifEmpty { if (inv.isNotEmpty()) inv.size.toString() else "" })
                    .put("objectifs", fo.s("objectifs")).put("axes", fo.s("axes")).put("conclusions", "").put("recommandations", "")
                    .put("evalFormation", "").put("evalMethodologie", "").put("evalGenerale", "")
            }
            "plan_action" -> d.put("lieu", lieu).put("objet", task.s("titre")).put("contexte", "").put("objectifs", "").put("actions", JSONArray())
            "custom" -> if (tpl != null) d.put("templateId", tpl.s("id")).put("titre", tpl.s("nom")).put("corps", tpl.s("corps")).put("rtl", tpl.optBoolean("rtl"))
        }
        return d
    }

    // ---------- outils ----------
    private val MOIS_AR = listOf("يناير", "فبراير", "مارس", "أبريل", "ماي", "يونيو", "يوليوز", "غشت", "شتنبر", "أكتوبر", "نونبر", "دجنبر")
    private val JOURS_AR = listOf("الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")
    fun dateAr(iso: String): String { val d = parseDate(iso) ?: return ".................."; return JOURS_AR[d.dayOfWeek.value - 1] + " " + "%02d".format(d.dayOfMonth) + " " + MOIS_AR[d.monthValue - 1] + " " + d.year }

    fun h(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    fun para(s: String): String = s.split(Regex("\\n+")).filter { it.isNotBlank() }.joinToString("") { "<p>" + h(it.trim()) + "</p>" }.ifEmpty { "<p>&nbsp;</p>" }

    /** Images du profil (logo, signature, cachet) récupérées depuis Drive (voir Paramètres). */
    fun imgFile(ctx: Context, key: String) = File(ctx.filesDir, "img_$key.bin")
    private fun imgData(ctx: Context, key: String): String? {
        val f = imgFile(ctx, key); if (!f.exists() || f.length() == 0L) return null
        val b = f.readBytes()
        val mime = if (b.size > 3 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte()) "image/png" else "image/jpeg"
        return "data:$mime;base64," + Base64.encodeToString(b, Base64.NO_WRAP)
    }

    fun header(ctx: Context, rtl: Boolean): String {
        val p = Store.profil()
        val logo = imgData(ctx, "logo")
        val img = if (logo != null) "<img src=\"$logo\" style=\"max-height:26mm;max-width:120mm\">" else "<img src=\"entete-ministere.png\" style=\"width:105mm\">"
        val acad = p.s("academieAr").ifEmpty { "فاس- مكناس" }; val prov = p.s("provinceAr").ifEmpty { "صفرو" }
        return "<div class=\"hd\">$img" +
            (if (rtl) "<div class=\"b\">الأكاديمية الجهوية للتربية والتكوين لجهة ${h(acad)}</div><div class=\"b\">المديرية الإقليمية ب${h(prov)}</div>"
             else "<div class=\"b\">Académie Régionale d'Éducation et de Formation – ${h(p.s("academieRegionale").ifEmpty { "Fès-Meknès" })}</div><div class=\"b\">Direction Provinciale de ${h(p.s("province").ifEmpty { "Sefrou" })}</div>") + "</div>"
    }

    private fun signature(ctx: Context, rtl: Boolean, label: String): String {
        val sig = imgData(ctx, "signature"); val cachet = imgData(ctx, "cachet")
        val imgs = listOfNotNull(sig?.let { "<img src=\"$it\" style=\"max-height:22mm;max-width:55mm\">" }, cachet?.let { "<img src=\"$it\" style=\"max-height:28mm;max-width:40mm\">" }).joinToString(" ")
        return "<div class=\"sig\" style=\"text-align:${if (rtl) "left" else "right"}\"><div class=\"b\">${h(label)}</div>$imgs</div>"
    }

    fun table(headers: List<String>, rows: List<List<String>>, widths: List<Int>? = null): String {
        val sb = StringBuilder("<table class=\"t\"><thead><tr>")
        headers.forEachIndexed { i, x -> sb.append("<th").append(widths?.getOrNull(i)?.let { " style=\"width:$it%\"" } ?: "").append(">").append(h(x)).append("</th>") }
        sb.append("</tr></thead><tbody>")
        rows.forEach { r -> sb.append("<tr>"); r.forEach { c -> sb.append("<td>").append(h(c).replace("\n", "<br>")).append("</td>") }; sb.append("</tr>") }
        if (rows.isEmpty()) sb.append("<tr>").append(headers.joinToString("") { "<td>&nbsp;</td>" }).append("</tr>")
        return sb.append("</tbody></table>").toString()
    }

    val CSS = """
        @page { size: A4; margin: 15mm; }
        body { font-family: 'Noto Naskh Arabic', 'Droid Arabic Naskh', Arial, sans-serif; font-size: 12.5pt; color: #000; margin: 0; }
        .hd { text-align: center; margin-bottom: 6mm; } .hd .b { font-weight: bold; font-size: 10.5pt; }
        h1 { text-align: center; font-size: 17pt; margin: 6mm 0 5mm; }
        h2 { font-size: 13pt; margin: 5mm 0 2mm; }
        p { margin: 0 0 2mm; text-align: justify; line-height: 1.45; }
        .b { font-weight: bold; }
        .meta { margin: 2mm 0 4mm; } .meta div { margin: 1mm 0; }
        table.t { width: 100%; border-collapse: collapse; margin: 2mm 0 4mm; }
        table.t th, table.t td { border: 1px solid #000; padding: 1.6mm 2mm; vertical-align: top; font-size: 11pt; }
        table.t th { background: #eee; }
        .sig { margin-top: 12mm; } .box { border: 1.5px solid #000; padding: 3mm; text-align: center; font-size: 15pt; font-weight: bold; margin: 3mm 0; }
        .lbl { background: #f2f2f2; font-weight: bold; width: 20%; }
    """.trimIndent()

    /** Enveloppe une page HTML A4 (utilisé aussi par les listes et le programme annuel). */
    fun page(body: String, rtl: Boolean, landscape: Boolean = false): String =
        "<!DOCTYPE html><html dir=\"${if (rtl) "rtl" else "ltr"}\" lang=\"${if (rtl) "ar" else "fr"}\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\"><style>$CSS" +
            (if (landscape) "\n@page { size: A4 landscape; margin: 12mm; }" else "") + "</style></head><body>$body</body></html>"

    /** Document HTML complet (A4). */
    fun html(ctx: Context, d: JSONObject): String {
        val type = d.s("type"); val rtl = isRtl(d)
        val task = Store.byId("tasks", d.s("taskId")) ?: JSONObject()
        val p = Store.profil()
        val nomInsp = p.s("nomSignature").ifEmpty { p.s("nom") }
        val sb = StringBuilder()
        sb.append(header(ctx, rtl))
        when (type) {
            "presence" -> {
                sb.append("<h1>لائحة الحضور</h1><div class=\"meta\"><div><b>الموضوع :</b> ${h(d.s("objet"))}</div><div><b>المؤسسة :</b> ${h(d.s("lieu"))}</div><div><b>التاريخ :</b> ${h(dateAr(d.s("date")))}</div></div>")
                val rows = d.arr("participants").mapIndexed { i, r -> listOf((i + 1).toString(), r.s("nom"), r.s("ppr"), r.s("etablissement"), "") }
                sb.append(table(listOf("ر.ت", "الاسم الكامل", "رقم التأجير", "المؤسسة", "التوقيع"), rows, listOf(6, 30, 14, 30, 20)))
            }
            "compte_rendu" -> {
                sb.append("<h1>محضر اجتماع</h1><div class=\"meta\"><div><b>المكان :</b> ${h(d.s("lieu"))}</div><div><b>التاريخ :</b> ${h(dateAr(d.s("date")))}</div><div><b>الحاضرون :</b> ${h(d.s("presents"))}</div>" +
                    (if (d.s("absents").isNotEmpty()) "<div><b>المتغيبون :</b> ${h(d.s("absents"))}</div>" else "") + "</div>")
                sb.append("<h2>جدول الأعمال</h2>").append(para(d.s("ordreJour"))).append("<h2>النقط التي تمت مناقشتها</h2>").append(para(d.s("pointsDiscutes")))
                    .append("<h2>القرارات المتخذة</h2>").append(para(d.s("decisions")))
                val a = d.arr("actions"); if (a.isNotEmpty()) sb.append("<h2>خطة العمل</h2>").append(table(listOf("الإجراء", "المسؤول", "الأجل"), a.map { listOf(it.s("action"), it.s("responsable"), fmtDate(it.s("echeance"))) }))
                sb.append("<table style=\"width:100%;margin-top:12mm\"><tr><td style=\"text-align:center\"><b>رئيس(ة) الجلسة</b></td><td style=\"text-align:center\"><b>كاتب(ة) الجلسة</b></td></tr></table>")
            }
            "observation" -> {
                sb.append("<h1>Grille d'observation de séance</h1><div class=\"meta\"><div><b>Enseignant(e) :</b> ${h(d.s("enseignantNom"))}</div><div><b>Établissement :</b> ${h(taskEtabName(task))}</div>" +
                    "<div><b>Classe :</b> ${h(d.s("classe"))} &nbsp; <b>Matière :</b> ${h(d.s("matiere"))} &nbsp; <b>Durée :</b> ${h(d.s("duree"))}</div><div><b>Date :</b> ${h(fmtDate(d.s("date")))}</div></div>")
                sb.append(table(listOf("Critère", "Appréciation", "Commentaire"), d.arr("criteres").map { listOf(it.s("nom"), it.s("note"), it.s("commentaire")) }, listOf(35, 20, 45)))
                sb.append("<h2>Points forts</h2>").append(para(d.s("pointsForts"))).append("<h2>Axes d'amélioration</h2>").append(para(d.s("axesAmelioration"))).append("<h2>Synthèse</h2>").append(para(d.s("synthese")))
                sb.append("<table style=\"width:100%;margin-top:12mm\"><tr><td><b>Signature de l'inspecteur(trice)</b></td><td style=\"text-align:right\"><b>Signature de l'enseignant(e)</b></td></tr></table>")
            }
            "rapport" -> {
                sb.append("<h1>Rapport de synthèse</h1><div class=\"meta\"><div><b>Objet :</b> ${h(task.s("titre"))}</div><div><b>Établissement :</b> ${h(taskEtabName(task))}</div><div><b>Date :</b> ${h(fmtDate(d.s("date")))}</div></div>")
                sb.append(para(d.s("texte"))).append(signature(ctx, false, nomInsp.ifEmpty { "L'inspecteur(trice)" }))
            }
            "rapport_inspection" -> {
                sb.append("<h1>RAPPORT D'INSPECTION</h1>")
                sb.append("<table class=\"t\"><tr><td><b>Nom et prénom :</b> ${h(listOf(d.s("civilite"), d.s("enseignantNom")).filter { it.isNotEmpty() }.joinToString(" "))}</td><td><b>PPR :</b> ${h(d.s("ppr"))}</td></tr>" +
                    "<tr><td><b>Établissement :</b> ${h(d.s("lieu"))}</td><td><b>Localité :</b> ${h(d.s("localite"))}</td></tr>" +
                    "<tr><td><b>Classe :</b> ${h(d.s("classe"))}</td><td><b>Manuel :</b> ${h(d.s("manuel"))}</td></tr>" +
                    "<tr><td><b>Cadre :</b> ${h(d.s("cadre"))}</td><td><b>Nationalité :</b> ${h(d.s("nationalite"))}</td></tr>" +
                    "<tr><td><b>Précédente inspection :</b> ${h(d.s("precInspection"))} &nbsp; <b>Note :</b> ${h(d.s("precNote"))}</td><td><b>Par :</b> ${h(d.s("par"))}</td></tr>" +
                    "<tr><td><b>Date de l'inspection :</b> ${h(fmtDate(d.s("date")))}</td><td><b>Note attribuée :</b> ${h(noteTxt(d.s("note")))}</td></tr></table>")
                if (d.s("texte").isNotBlank()) sb.append(para(d.s("texte")))
                if (d.s("constats").isNotBlank()) sb.append("<h2>Constats</h2>").append(para(d.s("constats")))
                if (d.s("recommandations").isNotBlank()) sb.append("<h2>Recommandations</h2>").append(para(d.s("recommandations")))
                sb.append("<p style=\"margin-top:6mm\">Rapport établi par : <b>${h(d.s("redacteur").ifEmpty { nomInsp })}</b>, le ${h(fmtDate(d.s("date")))}</p>")
                sb.append(signature(ctx, false, "L'inspecteur(trice)"))
                sb.append("<p style=\"margin-top:8mm;font-size:10pt\">Copies : M. le directeur provincial — M. le chef d'établissement — L'intéressé(e)</p>")
            }
            "attestation" -> {
                sb.append("<h1>شهادة حضور</h1>")
                val txt = d.s("texteLibre").ifBlank {
                    "يشهد مفتش مادة " + p.s("matiereAr").ifEmpty { "اللغة الفرنسية" } + " " + nomInsp + " أن السيد(ة) " + d.s("beneficiaireNom") + "، " + d.s("fonction") +
                        "، قد حضر(ت) " + d.s("objet") + (if (d.s("lieu").isNotEmpty()) " ب" + d.s("lieu") else "") + " يوم " + dateAr(d.s("date")) + ".\nسلمت هذه الشهادة للمعني(ة) بالأمر بطلب منه(ا) للإدلاء بها عند الحاجة."
                }
                sb.append(para(txt)).append(signature(ctx, true, "مفتش(ة) المادة"))
            }
            "lettre_information", "demande_voiture" -> {
                val corps = if (type == "demande_voiture") d.s("corpsLibre").ifBlank {
                    "يشرفني أن ألتمس منكم التفضل بوضع سيارة المصلحة رهن إشارتي يوم " + dateAr(d.s("date")) + (if (d.s("heureDepart").isNotEmpty()) " ابتداء من الساعة " + d.s("heureDepart") else "") +
                        (if (d.s("heureRetour").isNotEmpty()) " إلى غاية الساعة " + d.s("heureRetour") else "") + "، وذلك قصد القيام بمهمة تربوية" + (if (d.s("destination").isNotBlank()) " ب" + d.s("destination") else "") + "."
                } else d.s("corps")
                val mat = p.s("matiereAr").ifEmpty { "اللغة الفرنسية" }
                sb.append("<p class=\"b\">من السيد : ${h(p.s("nomAr").ifEmpty { nomInsp })}</p><p class=\"b\">مفتش تربوي لمادة ${h(mat)}</p>")
                sb.append("<p class=\"b\" style=\"text-align:center;font-size:15pt;margin-top:5mm\">إلى السيد ${h(d.s("destinataire"))}</p>")
                sb.append("<p class=\"b\" style=\"text-align:center;font-size:14pt;margin:5mm 0\">الموضوع : ${h(d.s("objet"))}</p>")
                sb.append("<p class=\"b\">سلام تام بوجود مولانا الإمام،</p>").append(para(if (corps.trim().startsWith("وبعد")) corps else "وبعد، $corps"))
                val inv = d.arr("invites")
                if (type == "lettre_information" && inv.isNotEmpty()) sb.append("<h2>الأساتذة المدعوون</h2>").append(table(listOf("الاسم الكامل", "رقم التأجير", "المؤسسة"),
                    inv.map { val e = Store.byId("enseignants", it.s("enseignantId")); listOf(e?.s("nomAr")?.ifEmpty { e.s("nom") } ?: it.s("nom"), it.s("ppr").ifEmpty { e?.s("ppr") ?: "" }, etabName(e?.s("etablissementId")).ifEmpty { it.s("etablissementNom") }) }))
                sb.append("<p class=\"b\" style=\"text-align:center;margin-top:5mm\">والسلام</p>")
                sb.append(signature(ctx, true, "مفتش مادة $mat")).append("<p style=\"text-align:left\">${h(fmtDate(d.s("date")))}</p>")
            }
            "bordereau" -> {
                sb.append("<p>من السيد : ${h(p.s("nomAr").ifEmpty { nomInsp })}</p><p>مفتش مادة : ${h(p.s("matiereAr").ifEmpty { "اللغة الفرنسية" })}</p>")
                sb.append("<p style=\"text-align:center\">إلى السيد : ${h(d.s("destinataire"))}</p>")
                if (d.s("reference").isNotEmpty()) sb.append("<p style=\"text-align:center\">المرجع : ${h(d.s("reference"))}</p>")
                sb.append("<h1>ورقة إرسال</h1>")
                sb.append(table(listOf("الموضوع", "العدد", "ملاحظات"), d.arr("pieces").map { listOf(it.s("designation"), it.s("nombre"), it.s("observation")) }, listOf(45, 15, 40)))
                if (d.s("observations").isNotBlank()) sb.append(para(d.s("observations")))
                sb.append(signature(ctx, true, "التوقيع"))
            }
            "rapport_formation" -> {
                sb.append("<div class=\"box\">النموذج رقم 4 : تقرير تركيبي حول دورة تكوينية</div>")
                fun r(l: String, v: String, l2: String? = null, v2: String? = null) = if (l2 == null) "<tr><td class=\"lbl\">${h(l)}</td><td colspan=\"3\">${para(v)}</td></tr>"
                    else "<tr><td class=\"lbl\">${h(l)}</td><td>${h(v)}</td><td class=\"lbl\">${h(l2)}</td><td>${h(v2 ?: "")}</td></tr>"
                sb.append("<table class=\"t\">").append(r("التاريخ", dateAr(d.s("date")), "المكان", d.s("lieu"))).append(r("الموضوع", d.s("objet")))
                    .append(r("الجهة المنظمة", d.s("organisateur"), "المكون(ون)", d.s("formateurs"))).append(r("الفئة المستفيدة", d.s("cible"), "العدد", d.s("nombre")))
                    .append(r("الأهداف العامة والخاصة", d.s("objectifs"))).append(r("المحاور", d.s("axes"))).append(r("الخلاصات", d.s("conclusions")))
                    .append("<tr><td class=\"lbl\" rowspan=\"2\">معدلات التقييم(*)</td><td class=\"b\" style=\"text-align:center\">التكوين</td><td class=\"b\" style=\"text-align:center\">المنهجية</td><td class=\"b\" style=\"text-align:center\">التقييم العام</td></tr>")
                    .append("<tr><td style=\"text-align:center\">${h(d.s("evalFormation"))}</td><td style=\"text-align:center\">${h(d.s("evalMethodologie"))}</td><td style=\"text-align:center\">${h(d.s("evalGenerale"))}</td></tr>")
                    .append(r("التوصيات والاقتراحات", d.s("recommandations"))).append("</table>")
                sb.append("<p style=\"font-size:9.5pt\">(*) معدلات التقييم المستخلصة من استمارات تقييم المستفيدين.</p>")
                sb.append(signature(ctx, true, "المفتش(ة) التربوي(ة) : " + p.s("nomAr").ifEmpty { nomInsp }))
            }
            "plan_action" -> {
                sb.append("<h1>خطة العمل</h1><div class=\"meta\"><div><b>الموضوع :</b> ${h(d.s("objet"))}</div><div><b>المكان :</b> ${h(d.s("lieu"))}</div></div>")
                sb.append("<h2>السياق</h2>").append(para(d.s("contexte"))).append("<h2>الأهداف</h2>").append(para(d.s("objectifs")))
                sb.append(table(listOf("الإجراء", "المسؤول", "الأجل"), d.arr("actions").map { listOf(it.s("action"), it.s("responsable"), fmtDate(it.s("echeance"))) }))
            }
            "custom" -> sb.append(CustomTpl.render(ctx, d, task))
            else -> sb.append("<h1>${h(label(d))}</h1>")
        }
        return "<!DOCTYPE html><html dir=\"${if (rtl) "rtl" else "ltr"}\" lang=\"${if (rtl) "ar" else "fr"}\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\"><style>$CSS</style></head><body>$sb</body></html>"
    }
}
