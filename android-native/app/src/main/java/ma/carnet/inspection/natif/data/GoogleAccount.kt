package ma.carnet.inspection.natif.data

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/** Compte Google de l'utilisateur : connexion « Se connecter avec Google », nom, adresse et photo. */
object GoogleAccount {
    val version = mutableIntStateOf(0)
    private fun p(ctx: Context) = ctx.getSharedPreferences("carnet", Context.MODE_PRIVATE)
    fun name(ctx: Context) = p(ctx).getString("googleName", "") ?: ""
    fun email(ctx: Context) = p(ctx).getString("googleEmail", "") ?: ""
    fun signedIn(ctx: Context) = email(ctx).isNotEmpty()
    fun photoFile(ctx: Context) = File(ctx.filesDir, "img_google.bin")
    fun welcomeDone(ctx: Context) = p(ctx).getBoolean("welcomeDone", false)
    fun setWelcomeDone(ctx: Context) = p(ctx).edit().putBoolean("welcomeDone", true).apply()

    private suspend fun save(ctx: Context, name: String, email: String, photo: String) {
        p(ctx).edit().putString("googleName", name).putString("googleEmail", email).apply()
        if (photo.isNotEmpty()) withContext(Dispatchers.IO) {
            try { photoFile(ctx).writeBytes(URL(photo.replace(Regex("=s\\d+(-c)?$"), "=s256-c")).readBytes()) } catch (_: Exception) {}
        }
        version.intValue++
    }

    /** « Se connecter avec Google » (Credential Manager). Nécessite l'identifiant client Web configuré. */
    suspend fun signIn(activity: Activity): String {
        val cid = DriveSync.clientId(activity)
        if (cid.isBlank()) throw Exception("Configurez d'abord l'identifiant client Google (Données → Google Drive).")
        val cm = CredentialManager.create(activity)
        // 1) bouton « Se connecter avec Google » (sélecteur de compte complet, ajout de compte possible)
        val res = try {
            cm.getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(GetSignInWithGoogleOption.Builder(cid).build()).build())
        } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) { throw e }
        catch (e: Exception) {
            // 2) repli : liste des comptes Google du téléphone
            cm.getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(
                GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false).setServerClientId(cid).setAutoSelectEnabled(false).build()).build())
        }
        val c = res.credential
        if (c is CustomCredential && c.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val g = GoogleIdTokenCredential.createFrom(c.data)
            save(activity, g.displayName ?: g.id, g.id, g.profilePictureUri?.toString() ?: "")
            return g.id
        }
        throw Exception("Connexion Google non reconnue.")
    }

    /** Met à jour le nom, l'adresse et la photo à partir de Google Drive (après autorisation). */
    suspend fun refresh(ctx: Context, token: String) {
        val u = withContext(Dispatchers.IO) { DriveSync.about(token) }
        val email = u.s("emailAddress"); if (email.isEmpty()) return
        if (email == email(ctx) && photoFile(ctx).exists() && name(ctx).isNotEmpty()) return
        save(ctx, u.s("displayName"), email, u.s("photoLink"))
    }

    suspend fun signOut(activity: Activity) {
        try { CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest()) } catch (_: Exception) {}
        p(activity).edit().remove("googleName").remove("googleEmail").apply()
        photoFile(activity).delete()
        DriveSync.forget(activity)
        version.intValue++
    }
}
