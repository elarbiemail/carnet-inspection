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
    }
}
