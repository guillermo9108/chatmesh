package com.example.mesh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.data.db.ChatMeshDatabase
import com.example.data.repository.ChatMeshRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.util.Log

/**
 * Servicio en primer plano que mantiene viva la malla WiFi Direct incluso
 * cuando la app está en segundo plano o la pantalla apagada.
 *
 * - Adquiere un WakeLock parcial.
 * - Muestra una notificación persistente (obligatoria en Android 8+).
 * - Garantiza que MeshEngineHolder tenga el motor inicializado.
 */
class MeshForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "chatmesh_mesh_channel"
        private const val NOTIFICATION_ID = 4242

        @Volatile
        private var running = false

        fun start(ctx: Context) {
            val appCtx = ctx.applicationContext
            WifiDirectP2pService.start(appCtx)
            val intent = Intent(appCtx, MeshForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appCtx.startForegroundService(intent)
            } else {
                appCtx.startService(intent)
            }
        }

        fun stop(ctx: Context) {
            ctx.applicationContext.stopService(
                Intent(ctx.applicationContext, MeshForegroundService::class.java)
            )
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var monitorJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Malla P2P activa • Supervisando"))

        acquireWakeLock()

        // Asegurar que el motor exista (idempotente)
        try {
            val db = ChatMeshDatabase.getDatabase(applicationContext)
            val repo = ChatMeshRepository(
                db.userDao(),
                db.contactDao(),
                db.messageDao(),
                db.meshNodeDao(),
                db.callDao()
            )
            MeshEngineHolder.init(applicationContext, repo)
        } catch (_: Exception) {
            // Si falla, la app igual arranca y el VM lo reintenta
        }

        startBackgroundMeshMonitor()
    }

    private fun startBackgroundMeshMonitor() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive && running) {
                try {
                    val engine = MeshEngineHolder.engine
                    if (engine != null) {
                        val statusText = engine.checkAndRecoverMeshConnection()
                        updateNotification(statusText)
                    } else {
                        try {
                            val db = ChatMeshDatabase.getDatabase(applicationContext)
                            val repo = ChatMeshRepository(
                                db.userDao(),
                                db.contactDao(),
                                db.messageDao(),
                                db.meshNodeDao(),
                                db.callDao()
                            )
                            MeshEngineHolder.init(applicationContext, repo)
                        } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    Log.e("MeshForegroundService", "Error en monitor en segundo plano", e)
                }
                delay(7000)
            }
        }
    }

    private fun updateNotification(text: String) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(NOTIFICATION_ID, buildNotification(text))
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: Android reinicia el servicio si lo mata por memoria
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        monitorJob?.cancel()
        monitorJob = null
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {}
        wakeLock = null
        scope.cancel()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ChatMesh::MeshWakeLock"
            ).apply {
                setReferenceCounted(false)
                // 60 min; el sistema la renueva mientras el servicio siga vivo
                acquire(60 * 60 * 1000L)
            }
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ChatMesh Malla P2P",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene la malla WiFi Direct activa en segundo plano"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ChatMesh")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}