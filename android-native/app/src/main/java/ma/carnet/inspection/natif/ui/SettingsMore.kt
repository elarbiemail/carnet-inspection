@file:OptIn(ExperimentalMaterial3Api::class)

package ma.carnet.inspection.natif.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ma.carnet.inspection.natif.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private fun tplRoot(): JSONObject = Store.root.optJSONObject("templates") ?: JSONObject().also { Store.root.put("templates", it) }
private fun tplSave() { tplRoot().put("updatedAt", Store.now()); Store.save() }
private fun JSONArray.strings(): List<String> = (0 until length()).map { optString(it) }

// ---------------- Modèles de documents ----------------
val DOC_TEMPLATE_FIELDS = mapOf(
    "compte_rendu" to listOf("ordreJour", "pointsDiscutes", "decisions"),
    "observation" to listOf("pointsForts", "axesAmelioration", "synthese"),
    "rapport" to listOf("texte"), "attestation" to listOf("texteLibre"), "lettre_information" to listOf("corps"),
    "rapport_inspection" to listOf("constats", "recommandations"), "bordereau" to listOf("observations"),
    "plan_action" to listOf("contexte", "objectifs"), "rapport_formation" to listOf("objectifs", "axes", "conclusions", "recommandations")
)
val DOC_TEMPLATE_TAGS = listOf("etablissement", "enseignant", "classe", "matiere", "date", "heure", "heureFin", "objet", "lieu", "inspecteur", "grade", "academie", "province", "ppr", "cadre")

@Composable
fun ModelesTab() {
    val v by Store.version
    var sel by remember { mutableStateOf("compte_rendu") }
    var tick by remember { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    SectionTitle("Modèles personnalisés (nouveaux documents)")
    CardBox {
        Text("Créez vos propres documents avec des balises ({{profil.nom}}, {{tache.date}}, {{liste:invites}}…) et des zones rédigées par l'IA. Ils apparaissent dans les tâches.", fontSize = 12.5.sp, color = InkFaint)
        CustomTpl.templates().forEach { ct ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, null, tint = Brand, modifier = Modifier.size(18.dp))
                Text(ct.s("nom").ifEmpty { "Sans nom" }, Modifier.weight(1f).padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { Nav.push(Scr.CustomTplEdit(ct.s("id"))) }) { Icon(Icons.Default.Edit, "Modifier", tint = Brand) }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CT_STARTERS.forEach { (k, l) -> AssistChip(onClick = { Nav.push(Scr.CustomTplEdit(null, k)) }, label = { Text(l) }, leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) }) }
        }
    }
    SectionTitle("Textes de départ des documents")
    ChipRow(DOC_TEMPLATE_FIELDS.keys.associateWith { Docs.LABELS[it] ?: it }, sel, { if (it.isNotEmpty()) { sel = it; tick++ } }, null)
    val dts = tplRoot().optJSONObject("docTemplates") ?: JSONObject().also { tplRoot().put("docTemplates", it) }
    val dt = dts.optJSONObject(sel) ?: JSONObject().also { dts.put(sel, it) }
    val fields = dt.optJSONObject("fields") ?: JSONObject().also { dt.put("fields", it) }
    key(sel, tick, v) {
        CardBox {
            if (sel == "observation") {
                Text("Critères de la grille d'observation", fontWeight = FontWeight.SemiBold)
                val crit = tplRoot().optJSONArray("criteres") ?: JSONArray(Docs.DEFAULT_CRITERES).also { tplRoot().put("criteres", it) }
                val items = remember { mutableStateListOf<String>().apply { addAll(crit.strings()) } }
                items.forEachIndexed { i, c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(c, { items[i] = it }, Modifier.weight(1f).padding(vertical = 2.dp), singleLine = true)
                        IconButton(onClick = { items.removeAt(i) }) { Icon(Icons.Default.Delete, "Retirer", tint = Danger) }
                    }
                }
                Row { TextButton(onClick = { items.add("") }) { Text("+ Critère") }
                    TextButton(onClick = { tplRoot().put("criteres", JSONArray(items.filter { it.isNotBlank() })); tplSave(); toast(ctx, "Critères enregistrés.") }) { Text("Enregistrer les critères") } }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
            Text("Balises utilisables : " + DOC_TEMPLATE_TAGS.joinToString(" ") { "{{$it}}" }, color = InkFaint, fontSize = 11.5.sp)
            val vals = remember { mutableStateMapOf<String, String>().apply { DOC_TEMPLATE_FIELDS[sel]?.forEach { put(it, fields.s(it)) } } }
            DOC_TEMPLATE_FIELDS[sel]?.forEach { f ->
                val label = Docs.fields(sel).firstOrNull { it.key == f }?.label ?: f
                TextFieldRow("Texte de départ — $label", vals[f] ?: "", { vals[f] = it }, multiline = true)
            }
            SectionTitle("Zones rédigées par l'IA (ajoutées à ce type de document)")
            val zones = remember { mutableStateListOf<JSONObject>().apply { dt.optJSONArray("aiZones")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.forEach { add(JSONObject(it.toString())) } } } }
            var zt by remember { mutableIntStateOf(0) }
            zones.forEachIndexed { i, z ->
                zt
                OutlinedTextField(z.s("label"), { z.put("label", it); zt++ }, Modifier.fillMaxWidth(), label = { Text("Libellé de la zone") }, singleLine = true)
                OutlinedTextField(z.s("prompt"), { z.put("prompt", it); zt++ }, Modifier.fillMaxWidth(), label = { Text("Consigne pour l'IA") })
                TextButton(onClick = { zones.removeAt(i) }) { Text("Retirer cette zone", color = Danger) }
            }
            TextButton(onClick = { zones.add(JSONObject().put("id", "z" + (zones.size + 1) + "_" + UUID.randomUUID().toString().take(4)).put("label", "").put("prompt", "")) }) { Text("+ Zone IA") }
            Button(onClick = {
                DOC_TEMPLATE_FIELDS[sel]?.forEach { f -> fields.put(f, vals[f] ?: "") }
                dt.put("aiZones", JSONArray().apply { zones.forEach { put(it) } })
                tplSave(); toast(ctx, "Modèle « ${Docs.LABELS[sel]} » enregistré.")
            }, modifier = Modifier.fillMaxWidth()) { Text("Enregistrer ce modèle") }
        }
    }
}

val CT_STARTERS = linkedMapOf("vierge" to "Vierge", "emargement" to "Liste d'émargement", "compte_rendu_ia" to "Compte rendu IA", "convocation_ar" to "استدعاء")

fun ctStarter(k: String): JSONObject {
    val o = JSONObject().put("id", "ct_" + UUID.randomUUID().toString().take(8)).put("taskTypes", JSONArray()).put("aiZones", JSONArray())
    when (k) {
        "emargement" -> o.put("nom", "Liste d'émargement des invité(e)s").put("rtl", false).put("corps", "{{logo}}\n{{entete}}\n\n# Liste d'émargement\n\n[centre] {{tache.titre}} — {{tache.date}} à {{tache.heure}} — {{tache.lieu}}\n\n{{liste:invites|colonnes=n,nom,ppr,etablissement,signature}}\n\n[droite] **{{profil.nom}}**, inspecteur(trice) pédagogique\n{{signature}}\n")
        "compte_rendu_ia" -> o.put("nom", "Compte rendu rédigé par l'IA").put("rtl", false)
            .put("aiZones", JSONArray().put(JSONObject().put("id", "z1").put("label", "Synthèse").put("prompt", "Rédige en 6 à 8 lignes la synthèse de la rencontre « {{tache.titre}} » à partir des notes de la tâche."))
                .put(JSONObject().put("id", "z2").put("label", "Recommandations").put("prompt", "Propose 3 à 5 recommandations concrètes et numérotées découlant de la rencontre.")))
            .put("corps", "{{logo}}\n{{entete}}\n\n# Compte rendu — {{tache.titre}}\n\n[centre] {{tache.date}} · {{tache.lieu}}\n\n## Participant(e)s\n{{liste:invites|colonnes=n,nom,etablissement}}\n\n## Synthèse\n{{ia:z1}}\n\n## Recommandations\n{{ia:z2}}\n\n[droite] **{{profil.nom}}**\n{{signature}}\n")
        "convocation_ar" -> o.put("nom", "استدعاء (عربي)").put("rtl", true).put("corps", "{{logo}}\n\n[centre] **الأكاديمية الجهوية للتربية والتكوين لجهة {{profil.academie_ar}}**\n[centre] **المديرية الإقليمية ب{{profil.province_ar}}**\n\n# استدعاء\n\nيتشرف مفتش مادة {{profil.matiere_ar}} بدعوة السادة الأساتذة الواردة أسماؤهم أسفله لحضور {{tache.type_ar}} يوم {{tache.date_ar}} على الساعة {{tache.heure}} بـ {{tache.lieu}}.\n\n{{liste:invites|colonnes=n,nom_ar,ppr,etablissement|titre=لائحة المدعوين}}\n\n[gauche] **{{profil.nom_ar}}**\n{{signature}}\n")
        else -> o.put("nom", "Nouveau modèle").put("rtl", false).put("corps", "{{logo}}\n{{entete}}\n\n# Titre du document\n\n[droite] {{profil.province}}, le {{date.jour}}\n\nObjet : {{tache.titre}}\n\nTexte du document…\n\n{{signature}}\n")
    }
    return o
}

val CT_TAG_GROUPS = linkedMapOf(
    "Éléments" to listOf("logo", "entete", "signature", "ia:z1", "liste:invites|colonnes=n,nom,ppr,etablissement,signature", "liste:enseignants|etablissement=tache", "liste:etablissements|zone=filtre", "liste:taches|du=2026-09-01"),
    "Profil" to listOf("profil.nom", "profil.nom_ar", "profil.grade", "profil.matiere", "profil.matiere_ar", "profil.academie", "profil.academie_ar", "profil.province", "profil.province_ar", "profil.telephone", "profil.email"),
    "Tâche" to listOf("tache.titre", "tache.type", "tache.type_ar", "tache.date", "tache.date_ar", "tache.heure", "tache.heure_fin", "tache.lieu", "tache.description", "tache.statut", "tache.nb_invites", "tache.lien"),
    "Établissement" to listOf("etablissement.nom", "etablissement.nom_ar", "etablissement.directeur", "etablissement.directeur_tel", "etablissement.telephone", "etablissement.adresse", "etablissement.zone", "etablissement.nb_enseignants"),
    "Enseignant(e)" to listOf("enseignant.nom", "enseignant.nom_ar", "enseignant.civilite", "enseignant.ppr", "enseignant.cadre", "enseignant.matiere", "enseignant.telephone", "enseignant.statut", "enseignant.derniere_note", "enseignant.date_derniere_note"),
    "Dates et application" to listOf("date.jour", "date.jour_ar", "annee_scolaire", "doc.date", "doc.titre", "app.nom", "zone.nom")
)

@Composable
fun CustomTplEditScreen(id: String?, starter: String) {
    val ctx = LocalContext.current
    val orig = remember(id) { id?.let { i -> CustomTpl.templates().firstOrNull { it.s("id") == i } } }
    val ct = remember(id) { if (orig != null) JSONObject(orig.toString()) else ctStarter(starter) }
    var nom by remember { mutableStateOf(ct.s("nom")) }
    var rtl by remember { mutableStateOf(ct.optBoolean("rtl")) }
    var corps by remember { mutableStateOf(TextFieldValue(ct.s("corps"))) }
    val types = remember { mutableStateListOf<String>().apply { ct.optJSONArray("taskTypes")?.let { addAll(it.strings()) } } }
    val zones = remember { mutableStateListOf<JSONObject>().apply { ct.optJSONArray("aiZones")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.forEach { add(it) } } } }
    var zt by remember { mutableIntStateOf(0) }
    var group by remember { mutableStateOf("Éléments") }
    var askDel by remember { mutableStateOf(false) }
    var gmsg by remember { mutableStateOf("") }
    fun insert(tag: String) {
        val t = "{{$tag}}"; val s = corps.selection
        val nt = corps.text.substring(0, s.min) + t + corps.text.substring(s.max)
        corps = TextFieldValue(nt, TextRange(s.min + t.length))
    }
    fun build(): JSONObject {
        ct.put("nom", nom.trim().ifEmpty { "Modèle" }).put("rtl", rtl).put("corps", corps.text).put("taskTypes", JSONArray(types.toList()))
            .put("aiZones", JSONArray().apply { zones.forEach { put(it) } })
        return ct
    }
    fun save() {
        val list = tplRoot().optJSONArray("customDocs") ?: JSONArray().also { tplRoot().put("customDocs", it) }
        val o = build(); var done = false
        for (i in 0 until list.length()) if (list.optJSONObject(i)?.s("id") == o.s("id")) { list.put(i, o); done = true }
        if (!done) list.put(o)
        tplSave()
    }
    ScreenScaffold(if (orig == null) "Nouveau modèle" else "Modifier le modèle", { Nav.pop() }, actions = {
        HelpButton("modeles")
        if (orig != null) IconButton(onClick = { askDel = true }) { Icon(Icons.Default.Delete, "Supprimer") }
        TextButton(onClick = { save(); toast(ctx, "Modèle enregistré."); Nav.pop() }) { Text("Enregistrer", color = OnBrand, fontWeight = FontWeight.Bold) }
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(14.dp)) {
            TextFieldRow("Nom du modèle", nom, { nom = it })
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(rtl, { rtl = it }); Text("Document en arabe (de droite à gauche)", Modifier.padding(start = 8.dp), fontSize = 13.5.sp) }
            SectionTitle("Proposé pour les tâches (aucune = toutes)")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Ref.TASK_TYPES.forEach { (k, l) -> FilterChip(selected = k in types, onClick = { if (k in types) types.remove(k) else types.add(k) }, label = { Text(l) }) }
            }
            SectionTitle("Insérer une balise (à l'emplacement du curseur)")
            ChipRow(CT_TAG_GROUPS.keys.associateWith { it }, group, { if (it.isNotEmpty()) group = it }, null)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (CT_TAG_GROUPS[group] ?: emptyList()).forEach { t -> SuggestionChip(onClick = { insert(t) }, label = { Text("{{$t}}", fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 1) }) }
            }
            OutlinedTextField(corps, { corps = it }, Modifier.fillMaxWidth().heightIn(min = 260.dp).padding(top = 6.dp), label = { Text("Texte du modèle") }, textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp))
            Text("Syntaxe : # Titre · ## Sous-titre · **gras** · [centre] / [droite] / [gauche] en début de ligne · {{logo}}, {{entete}}, {{signature}}, {{ia:z1}} et {{liste:…}} seuls sur leur ligne. Filtres des listes : etablissement=, zone=, cycle=, statut=, genre=, matiere=, note=avec|sans, type=, du=, au=, colonnes=, titre=.",
                color = InkFaint, fontSize = 11.5.sp)
            SectionTitle("Zones rédigées par l'IA")
            zones.forEachIndexed { i, z ->
                zt
                CardBox {
                    Text("{{ia:${z.s("id")}}}", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Brand)
                    OutlinedTextField(z.s("label"), { z.put("label", it); zt++ }, Modifier.fillMaxWidth(), label = { Text("Libellé") }, singleLine = true)
                    OutlinedTextField(z.s("prompt"), { z.put("prompt", it); zt++ }, Modifier.fillMaxWidth(), label = { Text("Consigne pour l'IA (balises permises)") })
                    Row { TextButton(onClick = { insert("ia:" + z.s("id")) }) { Text("Insérer dans le texte") }; TextButton(onClick = { zones.removeAt(i) }) { Text("Retirer", color = Danger) } }
                }
            }
            OutlinedButton(onClick = { val n = "z" + (zones.size + 1); zones.add(JSONObject().put("id", n).put("label", "Zone $n").put("prompt", "")); insert("ia:$n") }) { Icon(Icons.Default.Add, null); Text("Ajouter une zone IA") }
            SectionTitle("Google Docs")
            CardBox {
                Text("Modifiez le modèle dans Google Docs (pratique pour un texte long), puis récupérez-le ici.", fontSize = 12.5.sp, color = InkFaint)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ct.s("gdocFileId").isEmpty()) OutlinedButton(onClick = {
                        save()
                        SyncHolder.withToken("Création du Google Doc…") { tk ->
                            val id2 = withContext(Dispatchers.IO) { DriveSync.createGoogleDoc(tk, DriveSync.subFolder(tk, "Modèles"), "Modèle - " + nom, ctGdocBuild(build())) }
                            ct.put("gdocFileId", id2).put("gdocUrl", "https://docs.google.com/document/d/$id2/edit"); save()
                            gmsg = "Google Doc créé."; openUri(ctx, ct.s("gdocUrl"))
                        }
                    }) { Text("Créer sur Google Docs") }
                    else {
                        OutlinedButton(onClick = { openUri(ctx, ct.s("gdocUrl")) }) { Text("Ouvrir sur Google Docs") }
                        Button(onClick = {
                            SyncHolder.withToken("Récupération du Google Doc…") { tk ->
                                val txt = withContext(Dispatchers.IO) { String(DriveSync.export(tk, ct.s("gdocFileId"), "text/plain"), Charsets.UTF_8) }
                                val parsed = ctGdocParse(zones, txt)
                                if (parsed == null) gmsg = "Repère « TEXTE DU MODÈLE » introuvable dans le Google Doc."
                                else { corps = TextFieldValue(parsed.first); parsed.second.forEach { (zid, pr) -> zones.firstOrNull { it.s("id") == zid }?.put("prompt", pr) }; zt++; gmsg = "Texte récupéré depuis Google Docs." }
                            }
                        }) { Text("Récupérer le texte") }
                    }
                }
                if (gmsg.isNotEmpty()) Text(gmsg, fontSize = 12.5.sp, color = Brand)
            }
            Spacer(Modifier.height(60.dp))
        }
    }
    if (askDel) ConfirmDialog("Supprimer ce modèle ?", "Les documents déjà produits restent disponibles.", onDismiss = { askDel = false }) {
        val list = tplRoot().optJSONArray("customDocs") ?: JSONArray(); val n = JSONArray()
        for (i in 0 until list.length()) if (list.optJSONObject(i)?.s("id") != ct.s("id")) n.put(list.opt(i))
        tplRoot().put("customDocs", n); tplSave(); Nav.pop()
    }
}

private const val CT_GDOC_BODY = "=== TEXTE DU MODÈLE (ne pas modifier cette ligne) ==="
fun ctGdocBuild(ct: JSONObject): String {
    val zones = ct.optJSONArray("aiZones")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } } ?: emptyList()
    return "Modèle : ${ct.s("nom")}\n\nModifiez le texte sous le repère « TEXTE DU MODÈLE » et, plus bas, les consignes des zones IA. Gardez les lignes « === … === » telles quelles, puis touchez « Récupérer le texte » dans le carnet.\n" +
        "Syntaxe : # Titre · ## Sous-titre · **gras** · [centre] / [droite] en début de ligne · {{logo}}, {{entete}}, {{signature}}, {{ia:z1}} et les listes {{liste:…}} seuls sur leur ligne.\n\n" +
        CT_GDOC_BODY + "\n" + ct.s("corps") + "\n" + zones.joinToString("\n") { z -> "\n=== ZONE IA ${z.s("id")} : ${z.s("label")} (ne pas modifier cette ligne) ===\n" + z.s("prompt") }
}
fun ctGdocParse(zones: List<JSONObject>, text0: String): Pair<String, Map<String, String>>? {
    val text = text0.removePrefix("﻿").replace("\r\n", "\n").replace("\r", "\n")
    val i = text.indexOf(CT_GDOC_BODY); if (i < 0) return null
    val start = i + CT_GDOC_BODY.length
    val marks = zones.mapNotNull { z -> val k = text.indexOf("=== ZONE IA ${z.s("id")} ", start); if (k >= 0) z.s("id") to k else null }.sortedBy { it.second }
    val corps = text.substring(start, marks.firstOrNull()?.second ?: text.length).removePrefix("\n").trimEnd() + "\n"
    val prompts = marks.mapIndexed { j, (zid, at) -> val ls = text.indexOf('\n', at); zid to (if (ls < 0) "" else text.substring(ls + 1, marks.getOrNull(j + 1)?.second ?: text.length).trim()) }.toMap()
    return corps to prompts
}

// ---------------- Référentiel régions / DP ----------------
val DEFAULT_REGIONS = listOf(
    "AREF Tanger-Tétouan-Al Hoceïma" to listOf("DP Tanger-Assilah", "DP M'diq-Fnideq", "DP Tétouan", "DP Fahs-Anjra", "DP Larache", "DP Al Hoceïma", "DP Chefchaouen", "DP Ouezzane"),
    "AREF de l'Oriental" to listOf("DP Oujda-Angad", "DP Nador", "DP Driouch", "DP Jerada", "DP Berkane", "DP Taourirt", "DP Guercif", "DP Figuig"),
    "AREF Fès-Meknès" to listOf("DP Fès", "DP Meknès", "DP El Hajeb", "DP Ifrane", "DP Moulay Yacoub", "DP Sefrou", "DP Boulemane", "DP Taounate", "DP Taza"),
    "AREF Rabat-Salé-Kénitra" to listOf("DP Rabat", "DP Salé", "DP Skhirate-Témara", "DP Kénitra", "DP Khémisset", "DP Sidi Kacem", "DP Sidi Slimane"),
    "AREF Béni Mellal-Khénifra" to listOf("DP Béni Mellal", "DP Azilal", "DP Fquih Ben Salah", "DP Khénifra", "DP Khouribga"),
    "AREF Casablanca-Settat" to listOf("DP Casablanca", "DP Mohammedia", "DP El Jadida", "DP Nouaceur", "DP Médiouna", "DP Benslimane", "DP Berrechid", "DP Settat", "DP Sidi Bennour"),
    "AREF Marrakech-Safi" to listOf("DP Marrakech", "DP Chichaoua", "DP Al Haouz", "DP El Kelâa des Sraghna", "DP Essaouira", "DP Rehamna", "DP Safi", "DP Youssoufia"),
    "AREF Drâa-Tafilalet" to listOf("DP Errachidia", "DP Midelt", "DP Ouarzazate", "DP Tinghir", "DP Zagora"),
    "AREF Souss-Massa" to listOf("DP Agadir Ida Outanane", "DP Inezgane-Aït Melloul", "DP Chtouka-Aït Baha", "DP Taroudant", "DP Tiznit", "DP Tata"),
    "AREF Guelmim-Oued Noun" to listOf("DP Guelmim", "DP Assa-Zag", "DP Tan-Tan", "DP Sidi Ifni"),
    "AREF Laâyoune-Sakia El Hamra" to listOf("DP Laâyoune", "DP Boujdour", "DP Tarfaya", "DP Es-Semara"),
    "AREF Dakhla-Oued Ed-Dahab" to listOf("DP Oued Ed-Dahab", "DP Aousserd")
)

fun regions(): List<Pair<String, List<String>>> {
    val a = Store.root.optJSONArray("regionsMaroc")
    if (a == null || a.length() == 0) return DEFAULT_REGIONS
    return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { it.s("aref") to (it.optJSONArray("dps")?.strings() ?: emptyList()) }
}

@Composable
fun ReferentielTab() {
    val ctx = LocalContext.current
    val list = remember { mutableStateListOf<Pair<String, MutableList<String>>>().apply { regions().forEach { add(it.first to it.second.toMutableList()) } } }
    var tick by remember { mutableIntStateOf(0) }
    Text("Académies régionales et directions provinciales proposées dans le formulaire de zone. Synchronisé avec le carnet web.", color = InkFaint, fontSize = 12.5.sp, modifier = Modifier.padding(vertical = 8.dp))
    list.forEachIndexed { ai, (aref, dps) ->
        tick
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(aref, { list[ai] = it to dps; tick++ }, Modifier.weight(1f), label = { Text("Académie régionale") }, singleLine = true)
                IconButton(onClick = { list.removeAt(ai); tick++ }) { Icon(Icons.Default.Delete, "Supprimer", tint = Danger) }
            }
            dps.forEachIndexed { di, dp ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(dp, { dps[di] = it; tick++ }, Modifier.weight(1f).padding(start = 16.dp, top = 2.dp), singleLine = true)
                    IconButton(onClick = { dps.removeAt(di); tick++ }) { Icon(Icons.Default.Close, "Retirer", tint = InkFaint) }
                }
            }
            TextButton(onClick = { dps.add(""); tick++ }) { Text("+ Direction provinciale") }
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { list.add("" to mutableListOf()); tick++ }) { Text("+ Académie") }
        TextButton(onClick = { list.clear(); DEFAULT_REGIONS.forEach { list.add(it.first to it.second.toMutableList()) }; tick++ }) { Text("Liste par défaut") }
        Button(onClick = {
            Store.root.put("regionsMaroc", JSONArray().apply { list.filter { it.first.isNotBlank() }.forEach { (a, d) -> put(JSONObject().put("aref", a.trim()).put("dps", JSONArray(d.filter { it.isNotBlank() }.map { it.trim() }))) } })
            Store.root.put("regionsMarocUpdatedAt", Store.now()); Store.save(); toast(ctx, "Référentiel enregistré.")
        }) { Text("Enregistrer") }
    }
}

// ---------------- Notifications ----------------
@Composable
fun NotificationsTab() {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(Reminders.enabled(ctx)) }
    var perm by remember { mutableStateOf(Reminders.permitted(ctx)) }
    var lead by remember { mutableStateOf(Reminders.leadMinutes(ctx).toString()) }
    val req = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { perm = it; Reminders.schedule(ctx) }
    SectionTitle("Rappels des évènements imminents")
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Notifications", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Switch(on, { on = it; Reminders.setEnabled(ctx, it); if (it && !perm && Build.VERSION.SDK_INT >= 33) req.launch(android.Manifest.permission.POST_NOTIFICATIONS) })
        }
        if (!perm) {
            Text("Les notifications ne sont pas encore autorisées sur ce téléphone.", color = Warn, fontSize = 12.5.sp)
            if (Build.VERSION.SDK_INT >= 33) Button(onClick = { req.launch(android.Manifest.permission.POST_NOTIFICATIONS) }) { Text("Autoriser les notifications") }
        }
        OutlinedTextField(lead, { lead = it.filter { c -> c.isDigit() }.take(4) }, Modifier.fillMaxWidth().padding(top = 6.dp), label = { Text("Me prévenir combien de minutes avant le début ?") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { Reminders.setLeadMinutes(ctx, lead.toIntOrNull() ?: 60); toast(ctx, "Enregistré.") }) { Text("Enregistrer") }
            OutlinedButton(onClick = { Reminders.show(ctx, 4242, "Carnet d'Inspection", "Ceci est une notification de test.") }) { Text("Tester") }
        }
        Text("Les rappels fonctionnent même application fermée. Une tâche sans heure est rappelée sur la base de 8 h.", color = InkFaint, fontSize = 11.5.sp)
    }
    val next = remember { Reminders.upcoming(ctx).take(8) }
    if (next.isNotEmpty()) {
        SectionTitle("Prochains rappels")
        CardBox { next.forEach { a -> Text("• " + fmtDate(a.at.toLocalDate().toString()) + " " + a.at.toLocalTime().toString().take(5) + " — " + a.title, fontSize = 12.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) } }
    }
}

// ---------------- Vacances ----------------
@Composable
fun VacancesTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val v by Store.version
    val list = remember(v) { Store.list("vacances").sortedBy { it.s("dateDebut") } }
    var nom by remember { mutableStateOf("") }
    var deb by remember { mutableStateOf("") }
    var fin by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var paste by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var leadD by remember { mutableStateOf(Reminders.vacLeadDays(ctx).toString()) }
    fun add(items: List<Triple<String, String, String>>, source: String) {
        var n = 0
        items.forEach { (no, a, b) ->
            if (parseDate(a) == null) return@forEach
            val f2 = if (parseDate(b) == null) a else b
            if (Store.list("vacances").any { it.s("dateDebut") == a && it.s("dateFin") == f2 && norm(it.s("nom")) == norm(no) }) return@forEach
            Store.upsert("vacances", JSONObject().put("nom", no.trim().ifEmpty { "Vacances" }).put("dateDebut", a).put("dateFin", f2).put("source", source)); n++
        }
        msg = if (n == 0) "Aucune nouvelle période trouvée." else "$n période(s) ajoutée(s)."
        Reminders.schedule(ctx)
    }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) add(Vacances.parseCsv(ctx.contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""), "csv")
    }
    Text("Vous serez prévenu(e) à l'approche des vacances, et toute tâche programmée pendant l'une d'elles sera signalée.", color = InkFaint, fontSize = 12.5.sp, modifier = Modifier.padding(vertical = 8.dp))
    if (list.isEmpty()) EmptyState("Aucune période de vacances enregistrée.")
    list.forEach { x ->
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DateRange, null, tint = Brand)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(x.s("nom").ifEmpty { "(sans nom)" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(fmtDate(x.s("dateDebut")) + " – " + fmtDate(x.s("dateFin")), color = InkFaint, fontSize = 12.5.sp)
                }
                IconButton(onClick = { Store.delete("vacances", x.s("id")); Reminders.schedule(ctx) }) { Icon(Icons.Default.Delete, "Supprimer", tint = Danger) }
            }
        }
    }
    SectionTitle("Ajouter manuellement")
    CardBox {
        TextFieldRow("Nom", nom, { nom = it })
        DateField("Début", deb, { deb = it }); DateField("Fin", fin, { fin = it })
        Button(onClick = { add(listOf(Triple(nom, deb, fin)), "manuel"); nom = ""; deb = ""; fin = "" }, enabled = deb.isNotEmpty()) { Text("Ajouter") }
    }
    SectionTitle("Importer")
    CardBox {
        Text("Fichier CSV : colonnes nom, debut (ou dateDebut), fin (ou dateFin), au format AAAA-MM-JJ.", fontSize = 12.5.sp, color = InkFaint)
        OutlinedButton(onClick = { csv.launch(arrayOf("text/*", "*/*")) }) { Text("Importer un CSV") }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        TextFieldRow("Adresse d'un agenda iCal (.ics) ou d'une API JSON", url, { url = it })
        OutlinedButton(enabled = url.startsWith("http"), onClick = {
            scope.launch {
                msg = "Récupération…"
                try { val txt = withContext(Dispatchers.IO) { java.net.URL(url).readText() }; add(Vacances.parseFeed(txt), "url") } catch (e: Throwable) { msg = "Échec : " + (e.message ?: "") }
            }
        }) { Text("Récupérer") }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        TextFieldRow("Ou coller le contenu .ics ou JSON", paste, { paste = it }, multiline = true)
        OutlinedButton(enabled = paste.isNotBlank(), onClick = { add(Vacances.parseFeed(paste), "url"); paste = "" }) { Text("Importer ce contenu") }
        if (msg.isNotEmpty()) Text(msg, color = Brand, fontSize = 12.5.sp)
    }
    SectionTitle("Rappel")
    CardBox {
        OutlinedTextField(leadD, { leadD = it.filter { c -> c.isDigit() }.take(2) }, Modifier.fillMaxWidth(), label = { Text("Me prévenir combien de jours avant le début ?") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Button(onClick = { Reminders.setVacLeadDays(ctx, leadD.toIntOrNull() ?: 7); toast(ctx, "Enregistré.") }) { Text("Enregistrer") }
    }
}

object Vacances {
    private fun iso(s: String): String {
        val t = s.trim().take(10)
        Regex("^(\\d{4})(\\d{2})(\\d{2})").find(s.trim())?.let { return "${it.groupValues[1]}-${it.groupValues[2]}-${it.groupValues[3]}" }
        Regex("^(\\d{1,2})/(\\d{1,2})/(\\d{4})").find(s.trim())?.let { return "${it.groupValues[3]}-${it.groupValues[2].padStart(2, '0')}-${it.groupValues[1].padStart(2, '0')}" }
        return t
    }
    fun parseCsv(txt: String): List<Triple<String, String, String>> {
        val lines = txt.removePrefix("﻿").lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val sep = if (lines[0].count { it == ';' } > lines[0].count { it == ',' }) ';' else ','
        val head = lines[0].split(sep).map { norm(it.trim().trim('"')) }
        val iN = head.indexOfFirst { it.startsWith("nom") || it == "name" }.let { if (it < 0) 0 else it }
        val iD = head.indexOfFirst { it.startsWith("debut") || it == "datedebut" || it == "start" }.let { if (it < 0) 1 else it }
        val iF = head.indexOfFirst { it.startsWith("fin") || it == "datefin" || it == "end" }.let { if (it < 0) 2 else it }
        return lines.drop(1).map { l -> val c = l.split(sep).map { it.trim().trim('"') }; Triple(c.getOrElse(iN) { "" }, iso(c.getOrElse(iD) { "" }), iso(c.getOrElse(iF) { "" })) }
    }
    fun parseFeed(txt: String): List<Triple<String, String, String>> {
        val t = txt.trim()
        if (t.contains("BEGIN:VEVENT")) {
            return t.split("BEGIN:VEVENT").drop(1).map { ev ->
                val un = ev.replace(Regex("\\r?\\n[ \\t]"), "")
                fun f(k: String) = Regex("(?m)^$k[^:]*:(.*)$").find(un)?.groupValues?.get(1)?.trim() ?: ""
                val a = iso(f("DTSTART")); var b = iso(f("DTEND"))
                // DTEND d'un évènement « journée entière » est exclusif
                if (f("DTSTART").length == 8 && parseDate(b) != null) b = parseDate(b)!!.minusDays(1).toString()
                Triple(f("SUMMARY").replace("\\,", ","), a, b)
            }
        }
        return try {
            val arr = if (t.startsWith("[")) JSONArray(t) else JSONObject(t).let { it.optJSONArray("vacances") ?: it.optJSONArray("results") ?: it.optJSONArray("records") ?: JSONArray() }
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { o ->
                val f = o.optJSONObject("fields") ?: o
                Triple(f.s("nom").ifEmpty { f.s("description").ifEmpty { f.s("name") } }, iso(f.s("dateDebut").ifEmpty { f.s("start_date").ifEmpty { f.s("debut").ifEmpty { f.s("start") } } }),
                    iso(f.s("dateFin").ifEmpty { f.s("end_date").ifEmpty { f.s("fin").ifEmpty { f.s("end") } } }))
            }
        } catch (_: Exception) { parseCsv(t) }
    }
}
