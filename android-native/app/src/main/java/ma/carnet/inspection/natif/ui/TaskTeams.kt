@file:OptIn(ExperimentalMaterial3Api::class)

package ma.carnet.inspection.natif.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.carnet.inspection.natif.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private fun JSONObject.list(k: String): JSONArray = optJSONArray(k) ?: JSONArray().also { put(k, it) }
private fun JSONArray.objs() = (0 until length()).mapNotNull { optJSONObject(it) }
private fun JSONArray.without(i: Int): JSONArray { val n = JSONArray(); for (j in 0 until length()) if (j != i) n.put(opt(j)); return n }
private fun memberOf(e: JSONObject) = JSONObject().put("enseignantId", e.s("id")).put("nom", e.s("nom")).put("ppr", e.s("ppr")).put("etablissementNom", etabName(e.s("etablissementId"))).put("role", "")

/** Libellé d'un responsable de sous-tâche (équipe ou membre). */
fun respLabel(t: JSONObject, r: String): String {
    if (r.isEmpty()) return ""
    t.arr("equipes").firstOrNull { it.s("id") == r }?.let { return "Équipe « " + it.s("nom") + " »" }
    t.arr("equipes").flatMap { it.arr("membres") }.firstOrNull { it.s("enseignantId") == r || it.s("nom") == r }?.let { return it.s("nom") }
    return r
}

// ---------------- Formulaire : équipes ----------------
@Composable
fun TeamsEditor(t: JSONObject, tick: Int, changed: () -> Unit) {
    var pickFor by remember { mutableStateOf(-1) }
    val eq = t.list("equipes")
    SectionTitle("Équipes de recherche (${eq.length().also { tick }})")
    eq.objs().forEachIndexed { i, team ->
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { TextFieldRow("Nom de l'équipe", team.s("nom"), { team.put("nom", it); changed() }) }
                IconButton(onClick = { t.put("equipes", eq.without(i)); changed() }) { Icon(Icons.Default.Delete, "Supprimer l'équipe", tint = Danger) }
            }
            TextFieldRow("Thème / question de recherche", team.s("theme"), { team.put("theme", it); changed() }, multiline = true)
            val mem = team.list("membres")
            SelectField("Coordinateur(trice)", linkedMapOf("" to "—") + mem.objs().associate { (it.s("enseignantId").ifEmpty { it.s("nom") }) to it.s("nom") }, team.s("coordinateur"), { team.put("coordinateur", it); changed() })
            Text("Membres (${mem.length()})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            mem.objs().forEachIndexed { j, m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        MarqueeText(m.s("nom") + if ((m.s("enseignantId").ifEmpty { m.s("nom") }) == team.s("coordinateur")) "  ★ coordinateur(trice)" else "", fontSize = 13.5.sp)
                        MarqueeText(listOf(m.s("ppr"), m.s("etablissementNom")).filter { it.isNotEmpty() }.joinToString(" · "), color = InkFaint, fontSize = 11.5.sp)
                    }
                    OutlinedTextField(m.s("role"), { m.put("role", it); changed() }, Modifier.width(120.dp), singleLine = true, label = { Text("Rôle", fontSize = 11.sp) })
                    IconButton(onClick = { team.put("membres", mem.without(j)); changed() }) { Icon(Icons.Default.Close, "Retirer", tint = InkFaint) }
                }
            }
            Row(Modifier.horizontalScrollRow(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickFor = i }) { Icon(Icons.Default.Person, null); Spacer(Modifier.width(4.dp)); Text("Ajouter des enseignant(e)s", maxLines = 1, softWrap = false) }
                var ext by remember(i) { mutableStateOf("") }
                OutlinedTextField(ext, { ext = it }, Modifier.width(170.dp), singleLine = true, label = { Text("Autre membre (nom)", fontSize = 11.sp) })
                TextButton(enabled = ext.isNotBlank(), onClick = { mem.put(JSONObject().put("enseignantId", "").put("nom", ext.trim()).put("ppr", "").put("etablissementNom", "").put("role", "")); ext = ""; changed() }) { BtnText("Ajouter", maxLines = 1, softWrap = false) }
            }
        }
    }
    OutlinedButton(onClick = { eq.put(JSONObject().put("id", "eq_" + UUID.randomUUID().toString().take(8)).put("nom", "Équipe " + (eq.length() + 1)).put("theme", "").put("coordinateur", "").put("membres", JSONArray())); changed() }) {
        Icon(Icons.Default.Add, null); Text("Nouvelle équipe")
    }
    if (pickFor >= 0) {
        val team = eq.optJSONObject(pickFor)
        if (team == null) pickFor = -1 else MultiTeacherPicker(team.list("membres").objs().map { it.s("enseignantId") }.filter { it.isNotEmpty() }.toSet(), t.s("etablissementId"), { pickFor = -1 }) { picked ->
            val keep = team.list("membres").objs().filter { it.s("enseignantId").isEmpty() || picked.any { p -> p.s("id") == it.s("enseignantId") } }
            val n = JSONArray(); keep.forEach { n.put(it) }
            picked.filter { p -> keep.none { it.s("enseignantId") == p.s("id") } }.forEach { n.put(memberOf(it)) }
            team.put("membres", n); pickFor = -1; changed()
        }
    }
}

// ---------------- Formulaire : sous-tâches ----------------
@Composable
fun SubtasksEditor(t: JSONObject, tick: Int, changed: () -> Unit) {
    val st = t.list("sousTaches")
    SectionTitle("Sous-tâches (${st.length().also { tick }})")
    val resp = linkedMapOf("" to "—") + t.arr("equipes").associate { it.s("id") to "Équipe « " + it.s("nom") + " »" } +
        t.arr("equipes").flatMap { it.arr("membres") }.distinctBy { it.s("enseignantId").ifEmpty { it.s("nom") } }.associate { (it.s("enseignantId").ifEmpty { it.s("nom") }) to it.s("nom") }
    st.objs().forEachIndexed { i, s ->
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}.", color = InkFaint, modifier = Modifier.width(24.dp))
                Box(Modifier.weight(1f)) { TextFieldRow("Intitulé", s.s("titre"), { s.put("titre", it); changed() }) }
                IconButton(onClick = { if (i > 0) { val a = st.objs().toMutableList(); a.add(i - 1, a.removeAt(i)); t.put("sousTaches", JSONArray(a)); changed() } }, enabled = i > 0) { Icon(Icons.Default.KeyboardArrowUp, "Monter") }
                IconButton(onClick = { t.put("sousTaches", st.without(i)); changed() }) { Icon(Icons.Default.Delete, "Supprimer", tint = Danger) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { DateField("Échéance", s.s("echeance"), { s.put("echeance", it); changed() }) }
                Box(Modifier.weight(1f)) { SelectField("État", Ref.SUB_STATUTS, s.s("statut").ifEmpty { "a_faire" }, { s.put("statut", it); changed() }) }
            }
            SelectField("Responsable", resp, s.s("responsable"), { s.put("responsable", it); changed() })
            TextFieldRow("Notes / livrable attendu", s.s("notes"), { s.put("notes", it); changed() }, multiline = true)
        }
    }
    OutlinedButton(onClick = { st.put(JSONObject().put("id", "st_" + UUID.randomUUID().toString().take(8)).put("titre", "").put("echeance", "").put("statut", "a_faire").put("responsable", "").put("notes", "")); changed() }) {
        Icon(Icons.Default.Add, null); Text("Nouvelle sous-tâche")
    }
    if (st.length() == 0) Text("Exemples : diagnostic, revue de littérature, élaboration des outils, expérimentation en classe, analyse des résultats, rapport final.", color = InkFaint, fontSize = 12.sp)
}

// ---------------- Fiche de la tâche : équipes et avancement ----------------
@Composable
fun TeamsSummary(t: JSONObject) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val eq = t.arr("equipes")
    if (eq.isNotEmpty()) {
        SectionTitle("Équipes (${eq.size})")
        eq.forEach { team ->
            CardBox {
                MarqueeText(team.s("nom"), fontWeight = FontWeight.Bold)
                if (team.s("theme").isNotEmpty()) Text(team.s("theme"), fontSize = 13.sp, color = InkFaint)
                team.arr("membres").forEach { m ->
                    val coord = (m.s("enseignantId").ifEmpty { m.s("nom") }) == team.s("coordinateur")
                    Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (coord) "★" else "•", color = if (coord) Brand else InkFaint, modifier = Modifier.width(18.dp))
                        MarqueeText(m.s("nom") + listOf(m.s("role"), m.s("etablissementNom")).filter { it.isNotEmpty() }.joinToString(" · ").let { if (it.isEmpty()) "" else " — $it" }, fontSize = 13.5.sp,
                            modifier = Modifier.weight(1f))
                        val e = Store.byId("enseignants", m.s("enseignantId"))
                        if (e != null && e.s("telephone").isNotEmpty()) IconButton(onClick = { whatsapp(ctx, e.s("telephone"), greeting(e.s("nom"))) }) { Icon(Icons.Default.Send, "WhatsApp", tint = Brand) }
                    }
                }
            }
        }
    }
    val st = t.arr("sousTaches")
    if (st.isNotEmpty()) {
        val done = st.count { it.s("statut") == "terminee" }
        SectionTitle("Sous-tâches ($done / ${st.size})")
        CardBox {
            LinearProgressIndicator(progress = { done.toFloat() / st.size }, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
            st.forEachIndexed { i, s ->
                val fin = s.s("statut") == "terminee"
                val late = !fin && parseDate(s.s("echeance"))?.isBefore(java.time.LocalDate.now()) == true
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(fin, { c ->
                        val n = JSONObject(t.toString()); n.optJSONArray("sousTaches")?.optJSONObject(i)?.put("statut", if (c) "terminee" else "a_faire"); Store.upsert("tasks", n)
                    })
                    Column(Modifier.weight(1f)) {
                        Text(s.s("titre").ifEmpty { "Sous-tâche ${i + 1}" }, fontSize = 13.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            textDecoration = if (fin) TextDecoration.LineThrough else null, color = if (fin) InkFaint else Ink)
                        MarqueeText(listOf(s.s("echeance").let { if (it.isEmpty()) "" else "Échéance " + fmtDate(it) }, respLabel(t, s.s("responsable")),
                            if (s.s("statut") == "en_cours") "En cours" else "").filter { it.isNotEmpty() }.joinToString(" · "), color = if (late) Danger else InkFaint, fontSize = 11.5.sp)
                    }
                }
            }
        }
    }
}
