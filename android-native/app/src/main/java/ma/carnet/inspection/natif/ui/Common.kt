@file:OptIn(ExperimentalMaterial3Api::class)

package ma.carnet.inspection.natif.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ma.carnet.inspection.natif.data.*
import org.json.JSONObject
import java.time.LocalDate

// ---------------- thème ----------------
val Brand = Color(0xFF2F6F62)
val BrandSoft = Color(0xFFDDEBE7)
val Paper = Color(0xFFF6F4EE)
val Ink = Color(0xFF1F2523)
val InkFaint = Color(0xFF6B7470)
val Danger = Color(0xFFB3261E)
val Warn = Color(0xFFB26A00)
val FemColor = Color(0xFFB0427A)
val HomColor = Color(0xFF2A6FB0)

private val LightColors = lightColorScheme(
    primary = Brand, onPrimary = Color.White, primaryContainer = BrandSoft, onPrimaryContainer = Color(0xFF0E2D27),
    secondary = Color(0xFF6A5D3F), background = Paper, surface = Color.White, onSurface = Ink,
    surfaceVariant = Color(0xFFEDEAE1), onSurfaceVariant = InkFaint, error = Danger
)

@Composable
fun CarnetTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}

// ---------------- actions système ----------------
fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

fun openUri(ctx: Context, uri: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (e: Exception) { toast(ctx, "Aucune application pour ouvrir ce lien.") }
}

fun dial(ctx: Context, tel: String) {
    if (tel.isBlank()) { toast(ctx, "Aucun numéro de téléphone."); return }
    try { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + tel.replace(" ", ""))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (e: Exception) { toast(ctx, "Impossible d'ouvrir le téléphone.") }
}

fun whatsapp(ctx: Context, tel: String, msg: String) {
    val n = waNumber(tel)
    if (n.isEmpty()) { toast(ctx, "Aucun numéro de téléphone."); return }
    openUri(ctx, "https://wa.me/$n?text=" + Uri.encode(msg))
}

fun mail(ctx: Context, to: String, subject: String = "") {
    try { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$to?subject=" + Uri.encode(subject))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (e: Exception) { toast(ctx, "Aucune application de messagerie.") }
}

fun greeting(nom: String): String =
    if (Regex("[\\u0600-\\u06FF]").containsMatchIn(nom)) "السلام عليكم السيد(ة) $nom، " else "Bonjour $nom, "

// ---------------- composants ----------------
@Composable
fun Avatar(text: String, color: Color = Brand, size: Int = 42) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) { Text(initials(text), color = color, fontWeight = FontWeight.Bold, fontSize = (size / 2.8).sp) }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier = modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelMedium, color = InkFaint, fontWeight = FontWeight.SemiBold)
}

@Composable
fun CardBox(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.fillMaxWidth().padding(vertical = 4.dp)
    if (onClick != null) Card(onClick = onClick, modifier = m, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)) { Column(Modifier.padding(14.dp), content = content) }
    else Card(modifier = m, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)) { Column(Modifier.padding(14.dp), content = content) }
}

@Composable
fun InfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.width(120.dp), color = InkFaint, fontSize = 13.sp)
        Text(value, Modifier.weight(1f), fontSize = 14.sp)
    }
}

@Composable
fun Tag(text: String, color: Color = Brand) {
    if (text.isBlank()) return
    Text(text, Modifier.padding(end = 6.dp, bottom = 2.dp).clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.12f))
        .padding(horizontal = 7.dp, vertical = 2.dp), color = color, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
        Text(text, color = InkFaint, fontSize = 14.sp)
    }
}

@Composable
fun ContactButtons(tel: String, nom: String, email: String = "") {
    val ctx = LocalContext.current
    if (tel.isBlank() && email.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (tel.isNotBlank()) {
            FilledTonalButton(onClick = { dial(ctx, tel) }) { Icon(Icons.Default.Call, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Appeler") }
            FilledTonalButton(onClick = { whatsapp(ctx, tel, greeting(nom)) }) { Icon(Icons.Default.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("WhatsApp") }
        }
        if (email.isNotBlank()) FilledTonalButton(onClick = { mail(ctx, email) }) { Icon(Icons.Default.Email, null, Modifier.size(18.dp)) }
    }
}

@Composable
fun ChipRow(options: Map<String, String>, selected: String, onSelect: (String) -> Unit, allLabel: String? = "Tous") {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (allLabel != null) FilterChip(selected = selected.isEmpty(), onClick = { onSelect("") }, label = { Text(allLabel) })
        options.forEach { (k, v) -> FilterChip(selected = selected == k, onClick = { onSelect(if (selected == k && allLabel != null) "" else k) }, label = { Text(v) }) }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(), singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text(placeholder) },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Clear, "Effacer") } },
        shape = RoundedCornerShape(14.dp))
}

// ---------------- champs de formulaire ----------------
@Composable
fun TextFieldRow(label: String, value: String, onChange: (String) -> Unit, multiline: Boolean = false, modifier: Modifier = Modifier) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        singleLine = !multiline, minLines = if (multiline) 3 else 1)
}

@Composable
fun SelectField(label: String, options: Map<String, String>, value: String, onChange: (String) -> Unit, allowEmpty: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        OutlinedTextField(value = options[value] ?: "—", onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }, modifier = Modifier.fillMaxWidth())
        Box(Modifier.matchParentSize().clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (allowEmpty) DropdownMenuItem(text = { Text("—") }, onClick = { onChange(""); open = false })
            options.forEach { (k, v) -> DropdownMenuItem(text = { Text(v) }, onClick = { onChange(k); open = false }) }
        }
    }
}

@Composable
fun DateField(label: String, iso: String, onChange: (String) -> Unit) {
    val ctx = LocalContext.current
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        OutlinedTextField(value = if (iso.isEmpty()) "" else fmtLong(iso), onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.DateRange, null) }, modifier = Modifier.fillMaxWidth())
        Box(Modifier.matchParentSize().clickable {
            val d = parseDate(iso) ?: LocalDate.now()
            DatePickerDialog(ctx, { _, y, m, day -> onChange(LocalDate.of(y, m + 1, day).toString()) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
        })
    }
}

@Composable
fun TimeField(label: String, hhmm: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Box(modifier.padding(vertical = 4.dp)) {
        OutlinedTextField(value = hhmm, onValueChange = {}, readOnly = true, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
        Box(Modifier.matchParentSize().clickable {
            val p = hhmm.split(":"); val h = p.getOrNull(0)?.toIntOrNull() ?: 9; val mi = p.getOrNull(1)?.toIntOrNull() ?: 0
            TimePickerDialog(ctx, { _, hh, mm -> onChange(String.format("%02d:%02d", hh, mm)) }, h, mi, true).show()
        })
    }
}

/** Sélecteur plein écran avec recherche (établissement, enseignant…). */
@Composable
fun PickerDialog(title: String, items: List<JSONObject>, label: (JSONObject) -> String, sub: (JSONObject) -> String = { "" },
                 onDismiss: () -> Unit, onPick: (JSONObject?) -> Unit) {
    var q by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Fermer") }
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onPick(null) }) { Text("Aucun") }
                }
                SearchField(q, { q = it }, "Rechercher…")
                val nq = norm(q)
                val list = items.filter { nq.isEmpty() || norm(label(it) + " " + sub(it)).contains(nq) }.sortedBy { norm(label(it)) }
                LazyColumn(Modifier.fillMaxSize().padding(top = 6.dp)) {
                    items(list, key = { it.s("id") }) { o ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(o) }.padding(vertical = 10.dp, horizontal = 6.dp)) {
                            Text(label(o), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val s = sub(o); if (s.isNotEmpty()) Text(s, color = InkFaint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun PickField(label: String, value: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        OutlinedTextField(value = value.ifEmpty { "—" }, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.KeyboardArrowRight, null) }, modifier = Modifier.fillMaxWidth())
        Box(Modifier.matchParentSize().clickable { onClick() })
    }
}

@Composable
fun ScreenScaffold(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {},
                   fab: @Composable () -> Unit = {}, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Retour") } },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White, actionIconContentColor = Color.White))
        },
        floatingActionButton = fab,
        containerColor = MaterialTheme.colorScheme.background,
        content = content
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "Supprimer", onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = Danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } })
}

/** Filtre global de zones (cases à cocher). */
@Composable
fun ZoneFilterBar() {
    val v by Store.version
    var open by remember { mutableStateOf(false) }
    val zones = remember(v) { Store.list("zones").sortedBy { norm(it.s("nom")) } }
    if (zones.isEmpty()) return
    val f = Store.zoneFilter
    val label = when {
        f.isEmpty() -> "Toutes les zones"
        f.size == 1 -> f.first().let { if (it == "__none") "Sans zone" else zoneName(it) }
        else -> "${f.size} zones"
    }
    Box {
        AssistChip(onClick = { open = true }, label = { Text(label) }, leadingIcon = { Icon(Icons.Default.LocationOn, null, Modifier.size(18.dp)) })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Toutes les zones") }, onClick = { Store.setZoneFilter(emptySet()); open = false })
            (zones.map { it.s("id") to it.s("nom") } + listOf("__none" to "Sans zone")).forEach { (id, nom) ->
                DropdownMenuItem(text = {
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = id in f, onCheckedChange = null); Spacer(Modifier.width(8.dp)); Text(nom) }
                }, onClick = { Store.setZoneFilter(if (id in f) f - id else f + id) })
            }
        }
    }
}
