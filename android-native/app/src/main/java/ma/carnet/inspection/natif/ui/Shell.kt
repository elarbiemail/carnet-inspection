@file:OptIn(ExperimentalMaterial3Api::class)

package ma.carnet.inspection.natif.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ma.carnet.inspection.natif.data.*
import java.time.LocalDate
import java.time.YearMonth

/** Sous-rubrique (barre du bas). action ≠ null : bouton d'action (ex. « Nouvelle »), pas un écran. */
data class SubDef(val key: String, val label: String, val icon: ImageVector, val action: (() -> Unit)? = null)
/** Rubrique du menu principal (☰). */
data class SecDef(val key: String, val label: String, val icon: ImageVector, val subs: List<SubDef>)

object ShellState { val logout = mutableStateOf(false); val openWeb = mutableIntStateOf(0) }

val SECTIONS = listOf(
    SecDef("accueil", "Accueil", Icons.Outlined.Home, listOf(
        SubDef("dash", "Tableau de bord", Icons.Outlined.Dashboard), SubDef("recherche", "Recherche", Icons.Outlined.Search),
        SubDef("rappels", "Rappels", Icons.Outlined.NotificationsActive), SubDef("perso", "Personnaliser", Icons.Outlined.Widgets))),
    SecDef("taches", "Tâches", Icons.Outlined.Assignment, listOf(
        SubDef("avenir", "À venir", Icons.Outlined.Today), SubDef("toutes", "Toutes", Icons.Outlined.ListAlt),
        SubDef("retard", "En retard", Icons.Outlined.WarningAmber), SubDef("calendrier", "Calendrier", Icons.Outlined.CalendarToday),
        SubDef("nouvelle", "Nouvelle", Icons.Outlined.AddCircleOutline) { Nav.push(Scr.TaskEdit(null)) })),
    SecDef("ens", "Enseignant(e)s", Icons.Outlined.School, listOf(
        SubDef("liste", "Liste", Icons.Outlined.Groups), SubDef("horaire", "Par horaire", Icons.Outlined.Schedule),
        SubDef("notes", "Notes", Icons.Outlined.Notes), SubDef("supprimes", "Supprimées", Icons.Outlined.DeleteOutline))),
    SecDef("etab", "Établissements", Icons.Outlined.Apartment, listOf(
        SubDef("liste", "Liste", Icons.Outlined.ListAlt), SubDef("parzone", "Par zone", Icons.Outlined.Map),
        SubDef("zones", "Zones", Icons.Outlined.Place), SubDef("edt", "Emplois du temps", Icons.Outlined.TableChart))),
    SecDef("docs", "Documents", Icons.Outlined.Description, listOf(
        SubDef("tous", "Tous", Icons.Outlined.Article), SubDef("modeles", "Modèles", Icons.Outlined.Bookmarks),
        SubDef("liseuse", "Liseuse", Icons.Outlined.MenuBook), SubDef("fichiers", "Fichiers", Icons.Outlined.AttachFile))),
    SecDef("suivi", "Suivi", Icons.Outlined.Insights, listOf(
        SubDef("bilan", "Bilan", Icons.Outlined.Assessment), SubDef("programme", "Programme", Icons.Outlined.EventNote),
        SubDef("historique", "Historique", Icons.Outlined.History))),
    SecDef("reglages", "Réglages", Icons.Outlined.Settings, listOf(
        SubDef("profil", "Profil", Icons.Outlined.Badge), SubDef("compte", "Compte Google", Icons.Outlined.AccountCircle),
        SubDef("ia", "Assistant IA", Icons.Outlined.AutoAwesome), SubDef("apparence", "Apparence", Icons.Outlined.Palette), SubDef("meteo", "Météo", Icons.Outlined.WbSunny),
        SubDef("vacances", "Vacances", Icons.Outlined.BeachAccess), SubDef("notifications", "Notifications", Icons.Outlined.NotificationsNone),
        SubDef("referentiel", "Régions / DP", Icons.Outlined.Public), SubDef("inspectapp", "InspectApp", Icons.Outlined.SwapHoriz),
        SubDef("donnees", "Données", Icons.Outlined.Storage))),
    SecDef("aide", "Aide", Icons.Outlined.HelpOutline, listOf(
        SubDef("aide", "Aide", Icons.Outlined.HelpOutline), SubDef("contact", "Contact", Icons.Outlined.ContactPhone),
        SubDef("web", "Carnet web", Icons.Outlined.Language) { ShellState.openWeb.intValue++ },
        SubDef("deconnexion", "Déconnexion", Icons.Outlined.Logout) { ShellState.logout.value = true }))
)

fun secOf(k: String) = SECTIONS.firstOrNull { it.key == k } ?: SECTIONS[0]
fun firstSub(k: String) = secOf(k).subs.first { it.action == null }.key

@Composable
fun Shell(tab: Scr.Tab) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val sec = secOf(tab.sec)
    val sub = tab.sub.ifEmpty { firstSub(sec.key) }
    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(enabled = !drawer.isOpen && !(sec.key == "accueil" && sub == "dash")) { Nav.root("accueil", "dash") }
    LaunchedEffect(ShellState.openWeb.intValue) { if (ShellState.openWeb.intValue > 0) { ShellState.openWeb.intValue = 0; openUri(ctx, WEB_URL) } }
    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet(Modifier.width(300.dp)) { DrawerContent(sec.key) { k -> scope.launch { drawer.close() }; Nav.root(k, firstSub(k)) } }
    }) {
        Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = { SubBar(sec, sub) }) { pad ->
            CompositionLocalProvider(LocalDrawer provides { scope.launch { drawer.open() } }) {
                Box(Modifier.padding(pad)) { key(sec.key, sub) { SubContent(sec.key, sub) } }
            }
        }
    }
    if (ShellState.logout.value) LogoutDialog { ShellState.logout.value = false }
}

@Composable
private fun DrawerContent(current: String, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    val v by Store.version
    val gv by GoogleAccount.version
    val p = remember(v) { Store.profil() }
    Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().background(Brand).statusBarsPadding().padding(18.dp)) {
            ProfileAvatar(64) { onPick("reglages") }
            gv
            MarqueeText(p.s("nomSignature").ifEmpty { p.s("nom") }.ifEmpty { GoogleAccount.name(ctx) }.ifEmpty { "Carnet d'Inspection" },
                Modifier.padding(top = 10.dp), color = OnBrand, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            MarqueeText(listOf(p.s("grade"), p.s("matiere")).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "Inspecteur(trice) pédagogique" }, color = OnBrand.copy(alpha = 0.85f), fontSize = 12.5.sp)
            if (GoogleAccount.signedIn(ctx)) MarqueeText(GoogleAccount.email(ctx), color = OnBrand.copy(alpha = 0.75f), fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        SECTIONS.forEach { s ->
            NavigationDrawerItem(
                label = { Text(s.label, maxLines = 1, softWrap = false) }, icon = { Icon(s.icon, null) },
                selected = s.key == current, onClick = { onPick(s.key) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
        }
        Spacer(Modifier.height(10.dp))
        SyncStatusLine(SyncHolder.fn)
        Spacer(Modifier.height(20.dp))
    }
}

/** Barre du bas : sous-rubriques de la rubrique choisie (défile si elles sont nombreuses). */
@Composable
private fun SubBar(sec: SecDef, sub: String) {
    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp, color = MaterialTheme.colorScheme.surface) {
        val many = sec.subs.size > 5
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(72.dp).let { if (many) it.horizontalScroll(rememberScrollState()) else it },
            verticalAlignment = Alignment.CenterVertically) {
            sec.subs.forEach { s ->
                val sel = s.action == null && s.key == sub
                Column(
                    (if (many) Modifier.width(84.dp) else Modifier.weight(1f)).fillMaxHeight().clip(RoundedCornerShape(12.dp))
                        .clickable { if (s.action != null) s.action.invoke() else Nav.root(sec.key, s.key) }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                ) {
                    Box(Modifier.clip(RoundedCornerShape(16.dp)).background(if (sel) BrandSoft else androidx.compose.ui.graphics.Color.Transparent).padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Icon(s.icon, s.label, tint = if (sel) Brand else InkFaint, modifier = Modifier.size(22.dp))
                    }
                    MarqueeText(s.label, Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp), color = if (sel) Brand else InkFaint, fontSize = 11.5.sp,
                        fontWeight = if (sel) FontWeight.Bold else null, center = true, velocity = 22.dp)
                }
            }
        }
    }
}

@Composable
private fun SubContent(sec: String, sub: String) {
    when (sec) {
        "accueil" -> when (sub) { "recherche" -> SearchScreen(); "rappels" -> RappelsScreen(); "perso" -> HomeEditScreen(); else -> HomeScreen() }
        "taches" -> when (sub) { "avenir" -> TasksScreen("upcoming"); "retard" -> TasksScreen("late"); "calendrier" -> CalendarScreen(); else -> TasksScreen() }
        "ens" -> when (sub) { "horaire" -> TeachersScreen("horaire"); "notes" -> NotesScreen(); "supprimes" -> ArchivesScreen("enseignants"); else -> TeachersScreen() }
        "etab" -> when (sub) { "parzone" -> EtabsScreen(true); "zones" -> ZonesScreen(); "edt" -> TimetablesScreen(); else -> EtabsScreen(false) }
        "docs" -> when (sub) { "modeles" -> SimplePage("Modèles de documents") { ModelesTab() }; "liseuse" -> ViewerScreen(); "fichiers" -> SimplePage("Fichiers générés") { GeneratedFilesCard() }; else -> DocsAllScreen() }
        "suivi" -> when (sub) { "programme" -> ProgrammeScreen(); "historique" -> ArchivesScreen("", "journal"); else -> BilanScreen() }
        "reglages" -> when (sub) { "donnees" -> DataScreen(); else -> SettingsScreen(sub, showTabs = false) }
        "aide" -> when (sub) { "contact" -> AboutScreen(); else -> HelpCenter() }
        else -> HomeScreen()
    }
}

/** Page simple (titre + contenu défilant) pour les éléments qui n'ont pas d'écran propre. */
@Composable
fun SimplePage(title: String, content: @Composable ColumnScope.() -> Unit) {
    ScreenScaffold(title, null) { pad -> Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(14.dp)) { content(); Spacer(Modifier.height(40.dp)) } }
}

// ---------------- Rappels ----------------
@Composable
fun RappelsScreen() {
    val ctx = LocalContext.current
    val v by Store.version
    val list = remember(v) { Reminders.upcoming(ctx) }
    ScreenScaffold("Rappels", null, actions = { IconButton(onClick = { Nav.root("reglages", "notifications") }) { Icon(Icons.Default.Settings, "Réglages des rappels") } }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 14.dp)) {
            item { Spacer(Modifier.height(8.dp)); if (!Reminders.enabled(ctx)) Text("Les rappels sont désactivés (Réglages → Notifications).", color = Warn, fontSize = 13.sp) }
            if (list.isEmpty()) item { EmptyState("Aucun rappel prévu.") }
            items(list) { a ->
                CardBox {
                    MarqueeText(a.title, fontWeight = FontWeight.SemiBold)
                    MarqueeText(fmtDate(a.at.toLocalDate().toString()) + " à " + a.at.toLocalTime().toString().take(5) + if (a.text.isNotEmpty()) " · " + a.text else "", color = InkFaint, fontSize = 12.5.sp)
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

// ---------------- Calendrier des tâches ----------------
@Composable
fun CalendarScreen() {
    val v by Store.version
    var ym by remember { mutableStateOf(YearMonth.now()) }
    var day by remember { mutableStateOf(LocalDate.now()) }
    val tasks = remember(v) { Store.tasks() }
    val byDay = remember(v) { tasks.groupBy { parseDate(it.s("echeance")) } }
    ScreenScaffold("Calendrier", null, fab = {
        FloatingActionButton(onClick = { Nav.push(Scr.TaskEdit(null)) }, containerColor = Brand, contentColor = OnBrand) { Icon(Icons.Default.Add, "Nouvelle tâche") }
    }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 12.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { ym = ym.minusMonths(1) }) { Icon(Icons.Default.KeyboardArrowLeft, "Mois précédent") }
                    Text(ym.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.FRENCH).replaceFirstChar { it.uppercase() } + " " + ym.year,
                        Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 17.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(onClick = { ym = ym.plusMonths(1) }) { Icon(Icons.Default.KeyboardArrowRight, "Mois suivant") }
                }
                Row(Modifier.fillMaxWidth()) { listOf("L", "M", "M", "J", "V", "S", "D").forEach { Text(it, Modifier.weight(1f), color = InkFaint, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
                val first = ym.atDay(1); val offset = first.dayOfWeek.value - 1; val n = ym.lengthOfMonth()
                val cells = (0 until ((offset + n + 6) / 7) * 7).map { i -> if (i < offset || i >= offset + n) null else ym.atDay(i - offset + 1) }
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { d ->
                            Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(RoundedCornerShape(10.dp))
                                .background(when { d == null -> androidx.compose.ui.graphics.Color.Transparent; d == day -> Brand; d == LocalDate.now() -> BrandSoft; else -> Surface })
                                .let { if (d != null) it.clickable { day = d } else it }, contentAlignment = Alignment.Center) {
                                if (d != null) {
                                    val vac = Reminders.vacanceFor(d.toString()) != null
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${d.dayOfMonth}", color = if (d == day) OnBrand else if (vac) Earth else Ink, fontWeight = if (d == LocalDate.now()) FontWeight.Bold else null, fontSize = 14.sp)
                                        val c = byDay[d]?.size ?: 0
                                        if (c > 0) Row { repeat(minOf(c, 3)) { Box(Modifier.padding(1.dp).size(5.dp).clip(CircleShape).background(if (d == day) OnBrand else Brand)) } }
                                    }
                                }
                            }
                        }
                    }
                }
                SectionTitle(fmtLong(day.toString()) + (Reminders.vacanceFor(day.toString())?.let { " — " + it.s("nom") } ?: ""))
            }
            val l = (byDay[day] ?: emptyList()).sortedBy { taskTime(it) }
            if (l.isEmpty()) item { EmptyState("Aucune tâche ce jour-là.") }
            items(l, key = { it.s("id") }) { TaskCard(it) }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ---------------- Emplois du temps des établissements ----------------
@Composable
fun TimetablesScreen() {
    val ctx = LocalContext.current
    val v by Store.version
    var q by remember { mutableStateOf("") }
    val etabs = remember(v) { Store.etabs().sortedBy { norm(it.s("nom")) } }
    val ens = remember(v) { Store.list("enseignants") }
    val nq = norm(q)
    ScreenScaffold("Emplois du temps", null) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 14.dp)) {
            item { Spacer(Modifier.height(8.dp)); ZoneFilterBar(); SearchField(q, { q = it }, "Établissement…") }
            items(etabs.filter { nq.isEmpty() || norm(it.s("nom")).contains(nq) }, key = { it.s("id") }) { e ->
                val n = ens.count { it.s("etablissementId") == e.s("id") && it.arr("horaire").isNotEmpty() }
                CardBox(onClick = { showHtml("Emploi du temps - " + e.s("nom"), etabTimetableHtml(ctx, e.s("id")), true) }) {
                    MarqueeText(e.s("nom"), fontWeight = FontWeight.SemiBold)
                    Text(if (n == 0) "Aucun horaire saisi" else "$n enseignant(e)(s) avec horaire", color = if (n == 0) InkFaint else Brand, fontSize = 12.5.sp)
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}
