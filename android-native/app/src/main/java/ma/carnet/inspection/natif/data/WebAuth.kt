package ma.carnet.inspection.natif.data

import android.content.Context
import android.content.Intent

/** Retour de la page de connexion Google ouverte dans le navigateur (lien carnetnatif://auth?token=…). */
object WebAuth {
    var pending: ((String?, String?) -> Unit)? = null
    /** > 0 quand un jeton est arrivé sans action en attente (application relancée entre-temps). */
    val orphan = androidx.compose.runtime.mutableIntStateOf(0)
    fun handle(ctx: Context, intent: Intent?): Boolean {
        val u = intent?.data ?: return false
        if (u.scheme != "carnetnatif" || u.host != "auth") return false
        val tk = u.getQueryParameter("token")
        val err = u.getQueryParameter("error")
        if (!tk.isNullOrEmpty()) DriveSync.acceptWebToken(ctx, tk, u.getQueryParameter("exp")?.toLongOrNull() ?: 3600)
        val cb = pending; pending = null
        if (cb != null) cb.invoke(tk, err) else if (!tk.isNullOrEmpty()) orphan.intValue++
        return true
    }
}
