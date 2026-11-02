package ma.carnet.inspection.natif.data

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * L'IA du téléphone (MediaPipe) tourne dans un processus séparé (« :llm ») : si le moteur natif plante
 * (mémoire, modèle incompatible), seule l'IA s'arrête et l'application reste ouverte.
 */
class LlmService : Service() {
    private lateinit var messenger: Messenger
    override fun onCreate() {
        super.onCreate()
        val th = HandlerThread("llm").apply { start() }
        messenger = Messenger(object : Handler(th.looper) {
            override fun handleMessage(m: Message) {
                val reply = m.replyTo ?: return
                val out = Message.obtain()
                out.data = Bundle().apply {
                    try {
                        if (m.what == 2) { OnDevice.close(); putString("text", "ok") }
                        else putString("text", OnDevice.generate(applicationContext, m.data.getString("prompt") ?: ""))
                    } catch (t: Throwable) { putString("error", t.message ?: t.javaClass.simpleName) }
                }
                try { reply.send(out) } catch (_: Exception) {}
            }
        })
    }
    override fun onBind(intent: Intent): IBinder = messenger.binder
    override fun onDestroy() { OnDevice.close(); super.onDestroy() }
}

object OnDeviceClient {
    private var remote: Messenger? = null
    private var conn: ServiceConnection? = null
    private val replyThread = HandlerThread("llm-reply").apply { start() }

    private suspend fun connect(ctx: Context): Messenger {
        remote?.let { if (it.binder.isBinderAlive) return it }
        return suspendCancellableCoroutine { cont ->
            val c = object : ServiceConnection {
                override fun onServiceConnected(n: ComponentName?, b: IBinder?) { val m = Messenger(b); remote = m; if (cont.isActive) cont.resume(m) }
                override fun onServiceDisconnected(n: ComponentName?) { remote = null }
            }
            conn = c
            if (!ctx.applicationContext.bindService(Intent(ctx, LlmService::class.java), c, Context.BIND_AUTO_CREATE))
                cont.resumeWithException(Exception("Le moteur IA du téléphone n'a pas pu démarrer."))
        }
    }

    suspend fun generate(ctx: Context, prompt: String): String = withTimeout(15 * 60 * 1000L) {
        val m = connect(ctx)
        suspendCancellableCoroutine { cont ->
            val died = IBinder.DeathRecipient {
                remote = null
                if (cont.isActive) cont.resumeWithException(Exception("L'IA du téléphone s'est arrêtée (mémoire insuffisante ou modèle incompatible). Essayez un modèle plus petit (gemma3-1b-it-int4.task), une consigne plus courte, ou le serveur local / Gemini."))
            }
            try { m.binder.linkToDeath(died, 0) } catch (_: Exception) {}
            val reply = Messenger(object : Handler(replyThread.looper) {
                override fun handleMessage(r: Message) {
                    try { m.binder.unlinkToDeath(died, 0) } catch (_: Exception) {}
                    val err = r.data.getString("error")
                    if (!cont.isActive) return
                    if (err != null) cont.resumeWithException(Exception(err)) else cont.resume(r.data.getString("text") ?: "")
                }
            })
            val msg = Message.obtain(null, 1).apply { data = Bundle().apply { putString("prompt", prompt) }; replyTo = reply }
            try { m.send(msg) } catch (e: Exception) { remote = null; cont.resumeWithException(Exception("L'IA du téléphone ne répond pas : " + (e.message ?: ""))) }
        }
    }

    /** Libère le modèle (avant de le remplacer ou de le supprimer). */
    fun release(ctx: Context) {
        try { conn?.let { ctx.applicationContext.unbindService(it) } } catch (_: Exception) {}
        conn = null; remote = null
    }
}
