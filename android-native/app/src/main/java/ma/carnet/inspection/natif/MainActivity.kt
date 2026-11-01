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
        Store.init(applicationContext)
        setContent { CarnetTheme { App() } }
    }
}
