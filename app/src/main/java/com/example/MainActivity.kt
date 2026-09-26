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
import com.example.ui.screens.MainAppScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ChatMeshViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: ChatMeshViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainAppScreen(viewModel = viewModel)
                }
            }
        }
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
    }
}
