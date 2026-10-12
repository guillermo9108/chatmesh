package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.data.db.ChatMeshDatabase
import com.example.data.repository.ChatMeshRepository
import com.example.mesh.MeshEngineHolder
import com.example.mesh.MeshForegroundService
import com.example.ui.screens.MainAppScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ChatMeshViewModel
import com.example.util.CallRingtonePlayer

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.example.ui.theme.ProvideAppDimensions

class MainActivity : ComponentActivity() {

    private val viewModel: ChatMeshViewModel by viewModels()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Crear el motor singleton ANTES de que el ViewModel lo use
        try {
            val db = ChatMeshDatabase.getDatabase(applicationContext)
            val repo = ChatMeshRepository(
                db.userDao(),
                db.contactDao(),
                db.messageDao(),
                db.meshNodeDao(),
                db.callDao(),
                db.storyDao(),
                db.storySeenDao(),
                db.groupDao(),
                db.groupMemberDao()
            )
            MeshEngineHolder.init(applicationContext, repo)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error inicializando MeshEngineHolder", e)
        }

        // 2. Arrancar el servicio en primer plano y el servicio de gestión WiFi Direct P2P
        try {
            MeshForegroundService.start(applicationContext)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error iniciando MeshForegroundService", e)
        }

        try {
            com.example.mesh.WifiDirectP2pService.start(applicationContext)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error iniciando WifiDirectP2pService", e)
        }

        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            ProvideAppDimensions(windowSizeClass) {
                MyApplicationTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        MainAppScreen(viewModel = viewModel)
                    }
                }
            }
        }

        CallRingtonePlayer.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        CallRingtonePlayer.stop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val callerPhone = intent.getStringExtra("EXTRA_CONTACT_PHONE")
        val isIncomingCall = intent.getBooleanExtra("EXTRA_INCOMING_CALL", false)
        val isVideo = intent.getBooleanExtra("EXTRA_IS_VIDEO", false)
        val isAnswer = intent.getBooleanExtra("EXTRA_ACTION_ANSWER", false)
        val isReject = intent.getBooleanExtra("EXTRA_ACTION_REJECT", false)

        if (isAnswer) {
            viewModel.answerCall()
        } else if (isReject) {
            viewModel.endCall()
        } else if (isIncomingCall && !callerPhone.isNullOrBlank()) {
            viewModel.handleIncomingCallIntent(callerPhone, isVideo)
        }

        val hotspotRequestPhone = intent.getStringExtra("EXTRA_HOTSPOT_REQUEST_PHONE")
        if (!hotspotRequestPhone.isNullOrBlank()) {
            viewModel.setHotspotRequestFromIntent(hotspotRequestPhone)
        }
    }
}