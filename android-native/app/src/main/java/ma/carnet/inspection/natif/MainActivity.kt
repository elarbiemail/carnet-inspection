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
    }
}
