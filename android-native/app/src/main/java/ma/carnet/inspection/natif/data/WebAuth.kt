package ma.carnet.inspection.natif.data

import android.content.Context
import android.content.Intent

/** Retour de la page de connexion Google ouverte dans le navigateur (lien carnetnatif://auth?token=…). */
object WebAuth {
    var pending: ((String?, String?) -> Unit)? = null
    fun handle(ctx: Context, intent: Intent?): Boolean {
        val u = intent?.data ?: return false
        if (u.scheme != "carnetnatif" || u.host != "auth") return false
        val tk = u.getQueryParameter("token")
        val err = u.getQueryParameter("error")
        if (!tk.isNullOrEmpty()) DriveSync.acceptWebToken(ctx, tk, u.getQueryParameter("exp")?.toLongOrNull() ?: 3600)
        val cb = pending; pending = null
        cb?.invoke(tk, err)
        return true
    }
}
