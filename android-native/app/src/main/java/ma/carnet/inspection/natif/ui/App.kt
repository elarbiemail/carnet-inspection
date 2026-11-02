package ma.carnet.inspection.natif.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/** Écrans de l'application (navigation simple par pile). */
sealed class Scr {
    data class Tab(val i: Int) : Scr()
    data class TaskDetail(val id: String) : Scr()
    data class TaskEdit(val id: String?, val etabId: String? = null, val ensId: String? = null) : Scr()
    data class EnsDetail(val id: String) : Scr()
    data class EnsEdit(val id: String?, val etabId: String? = null) : Scr()
    data class EtabDetail(val id: String) : Scr()
    data class EtabEdit(val id: String?) : Scr()
    data object Bilan : Scr()
    data object Data : Scr()
    data object About : Scr()
    data class DocEdit(val id: String?, val taskId: String, val type: String, val tplId: String?) : Scr()
    data class DocPreview(val id: String) : Scr()
    data object DocsAll : Scr()
    data object Zones : Scr()
    data class Settings(val tab: String = "profil") : Scr()
    data object Programme : Scr()
    data object Notes : Scr()
    data object HtmlPreview : Scr()
    data class CustomTplEdit(val id: String?, val starter: String = "vierge") : Scr()
    data object Viewer : Scr()
    data class Help(val topic: String) : Scr()
    data object Search : Scr()
}

object Nav {
    val stack = mutableStateListOf<Scr>(Scr.Tab(0))
    fun push(s: Scr) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.size - 1) else stack[0] = Scr.Tab(0) }
    fun tab(i: Int) { stack.clear(); stack.add(Scr.Tab(i)) }
    /** Remplace l'écran courant (ex. formulaire enregistré → fiche). */
    fun replace(s: Scr) { if (stack.isNotEmpty()) stack[stack.size - 1] = s else stack.add(s) }
}

private data class TabDef(val label: String, val icon: ImageVector)
private val TABS = listOf(
    TabDef("Accueil", Icons.Default.Home), TabDef("Tâches", Icons.Default.DateRange),
    TabDef("Enseignants", Icons.Default.Person), TabDef("Établissements", Icons.Default.LocationOn),
    TabDef("Plus", Icons.Default.Menu)
)

@Composable
fun App() {
    val cur = Nav.stack.lastOrNull() ?: Scr.Tab(0)
    val sync = rememberDriveSyncer()
    SyncHolder.fn = sync
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { if (ma.carnet.inspection.natif.data.DriveSync.enabled(ctx)) sync(false) }
    val ver by ma.carnet.inspection.natif.data.Store.version
    LaunchedEffect(ver) { kotlinx.coroutines.delay(800); try { ma.carnet.inspection.natif.data.Reminders.schedule(ctx) } catch (_: Throwable) {} }
    var welcome by remember { mutableStateOf(!ma.carnet.inspection.natif.data.GoogleAccount.welcomeDone(ctx)) }
    if (welcome) { WelcomeScreen { welcome = false }; return }
    AutoScreenshot(cur)
    BackHandler(enabled = Nav.stack.size > 1 || (cur is Scr.Tab && cur.i != 0)) { Nav.pop() }
    when (cur) {
        is Scr.Tab -> Scaffold(
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            bottomBar = {
                NavigationBar {
                    TABS.forEachIndexed { i, t ->
                        NavigationBarItem(selected = cur.i == i, onClick = { Nav.tab(i) },
                            icon = { Icon(t.icon, null) }, label = { Text(t.label, maxLines = 1) })
                    }
                }
            }
        ) { pad ->
            androidx.compose.foundation.layout.Box(Modifier.padding(pad)) {
                when (cur.i) {
                    0 -> HomeScreen()
                    1 -> TasksScreen()
                    2 -> TeachersScreen()
                    3 -> EtabsScreen()
                    else -> MoreScreen()
                }
            }
        }
        is Scr.TaskDetail -> TaskDetailScreen(cur.id)
        is Scr.TaskEdit -> TaskEditScreen(cur.id, cur.etabId, cur.ensId)
        is Scr.EnsDetail -> TeacherDetailScreen(cur.id)
        is Scr.EnsEdit -> TeacherEditScreen(cur.id, cur.etabId)
        is Scr.EtabDetail -> EtabDetailScreen(cur.id)
        is Scr.EtabEdit -> EtabEditScreen(cur.id)
        Scr.Bilan -> BilanScreen()
        Scr.Data -> DataScreen()
        Scr.About -> AboutScreen()
        is Scr.DocEdit -> DocEditScreen(cur.id, cur.taskId, cur.type, cur.tplId)
        is Scr.DocPreview -> DocPreviewScreen(cur.id)
        Scr.DocsAll -> DocsAllScreen()
        Scr.Zones -> ZonesScreen()
        is Scr.Settings -> SettingsScreen(cur.tab)
        Scr.Programme -> ProgrammeScreen()
        Scr.Notes -> NotesScreen()
        Scr.HtmlPreview -> HtmlPreviewScreen()
        is Scr.CustomTplEdit -> CustomTplEditScreen(cur.id, cur.starter)
        Scr.Viewer -> ViewerScreen()
        is Scr.Help -> HelpScreen(cur.topic)
        Scr.Search -> SearchScreen()
    }
}
