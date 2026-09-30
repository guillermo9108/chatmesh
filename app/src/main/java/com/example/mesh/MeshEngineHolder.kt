package com.example.mesh

import android.content.Context
import com.example.data.repository.ChatMeshRepository

/**
 * Singleton a nivel de proceso que contiene una única instancia de WiFiMeshEngine.
 *
 * El motor NO debe vivir en el ViewModel porque el VM muere con la Activity.
 * Al vivir aquí, sobrevive mientras el proceso esté vivo (garantizado por
 * MeshForegroundService).
 */
object MeshEngineHolder {

    @Volatile
    private var _engine: WiFiMeshEngine? = null

    val engine: WiFiMeshEngine?
        get() = _engine

    @Synchronized
    fun init(context: Context, repository: ChatMeshRepository): WiFiMeshEngine {
        _engine?.let { return it }
        val newEngine = WiFiMeshEngine(context.applicationContext, repository)
        _engine = newEngine
        return newEngine
    }

    @Synchronized
    fun clear() {
        try { _engine?.cleanUp() } catch (_: Exception) {}
        _engine = null
    }
}
