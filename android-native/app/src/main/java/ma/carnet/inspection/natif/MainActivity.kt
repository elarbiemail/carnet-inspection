package ma.carnet.inspection.natif

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import ma.carnet.inspection.natif.data.Store
import ma.carnet.inspection.natif.ui.App
import ma.carnet.inspection.natif.ui.CarnetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try { android.webkit.WebView.enableSlowWholeDocumentDraw() } catch (_: Throwable) {}
        Store.init(applicationContext)
        ma.carnet.inspection.natif.data.Fonts.init(applicationContext)
        ma.carnet.inspection.natif.ui.ThemeState.load(applicationContext)
        setContent { CarnetTheme { App() } }
        ma.carnet.inspection.natif.data.WebAuth.handle(applicationContext, intent)
        ma.carnet.inspection.natif.data.SharedPhoto.handle(applicationContext, intent)
        widget(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ma.carnet.inspection.natif.data.WebAuth.handle(applicationContext, intent)
        ma.carnet.inspection.natif.data.SharedPhoto.handle(applicationContext, intent)
        widget(intent)
    }

    /** Ouverture depuis un widget de l'écran d'accueil. */
    private fun widget(i: android.content.Intent?) {
        val sec = i?.getStringExtra("widgetSec") ?: return
        val sub = i.getStringExtra("widgetSub") ?: ""
        i.removeExtra("widgetSec")
        val ui = ma.carnet.inspection.natif.ui.Nav
        when (sec) {
            "task" -> { ui.root("taches", "avenir"); ui.push(ma.carnet.inspection.natif.ui.Scr.TaskDetail(sub)) }
            "jour" -> ui.root("taches", "calendrier")
            "ens" -> if (sub == "liste" || sub.isEmpty()) ui.root("ens", "liste") else { ui.root("ens", "liste"); ui.push(ma.carnet.inspection.natif.ui.Scr.EnsDetail(sub)) }
            else -> ui.root(sec, sub)
        }
    }
}
