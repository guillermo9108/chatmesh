package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.ContactEntity
import com.example.ui.components.WhatsAppTopBar
import com.example.ui.viewmodel.ChatMeshViewModel

@Composable
fun MainAppScreen(
    viewModel: ChatMeshViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Observe ViewModel states
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val chatContacts by viewModel.chatContacts.collectAsStateWithLifecycle()
    val allContacts by viewModel.allContacts.collectAsStateWithLifecycle()
    val meshNodes by viewModel.meshNodes.collectAsStateWithLifecycle()
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val engineState by viewModel.engineState.collectAsStateWithLifecycle()
    val selectedContact by viewModel.selectedContact.collectAsStateWithLifecycle()
    val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showProfileDialog by remember { mutableStateOf(false) }

    // Request necessary runtime permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.refreshContacts()
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissionsToRequest.add(Manifest.permission.READ_PHONE_NUMBERS)
        } else {
            permissionsToRequest.add(Manifest.permission.READ_PHONE_STATE)
        }

        val missing = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            // 1. Fullscreen Call Screen
            engineState.isCallActive && engineState.activeCallPeer != null -> {
                CallScreen(
                    contact = engineState.activeCallPeer!!,
                    engineState = engineState,
                    onEndCall = { viewModel.endCall() },
                    onToggleMute = { viewModel.toggleMute() },
                    onToggleSpeaker = { viewModel.toggleSpeaker() }
                )
            }

            // 2. Fullscreen Chat Conversation Screen
            selectedContact != null -> {
                ChatDetailScreen(
                    contact = selectedContact!!,
                    messages = activeMessages,
                    activeTypingPhone = engineState.activeTypingContactPhone,
                    activeRecordingPhone = engineState.activeRecordingContactPhone,
                    onBackClick = { viewModel.selectContact(null) },
                    onSendMessage = { text -> viewModel.sendTextMessage(text) },
                    onSendImage = { uri, caption -> viewModel.sendImageMessage(uri, caption) },
                    onSendAudio = { duration -> viewModel.sendAudioVoiceMessage(duration) },
                    onSendFile = { fileName -> viewModel.sendFileMessage(fileName) },
                    onAudioCallClick = { viewModel.startAudioCall(selectedContact!!) },
                    onVideoCallClick = { viewModel.startVideoCall(selectedContact!!) },
                    onTypingChange = { text -> viewModel.onUserTyping(text) }
                )
            }

            // 3. Main Screen with WhatsApp Tabs
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    val unreadTotal = chatContacts.sumOf { it.unreadCount }

                    WhatsAppTopBar(
                        selectedTabIndex = selectedTabIndex,
                        onTabSelected = { selectedTabIndex = it },
                        onSearchClick = { selectedTabIndex = 2 },
                        onProfileClick = { showProfileDialog = true },
                        onSyncContactsClick = { viewModel.refreshContacts() },
                        onToggleSimClick = { viewModel.toggleSimulationMode() },
                        unreadChatsCount = unreadTotal,
                        connectedNodesCount = meshNodes.size,
                        ssidName = engineState.ssid
                    )

                    when (selectedTabIndex) {
                        0 -> ChatsTab(
                            contacts = chatContacts,
                            activeTypingPhone = engineState.activeTypingContactPhone,
                            onContactClick = { contact -> viewModel.selectContact(contact) },
                            onFabClick = { selectedTabIndex = 2 }
                        )
                        1 -> MeshNodesTab(
                            engineState = engineState,
                            meshNodes = meshNodes,
                            onNodeChatClick = { contact -> viewModel.selectContact(contact) },
                            onToggleSimulation = { viewModel.toggleSimulationMode() }
                        )
                        2 -> ContactsTab(
                            contacts = allContacts,
                            searchQuery = searchQuery,
                            onSearchQueryChange = { viewModel.setSearchQuery(it) },
                            onChatClick = { contact -> viewModel.selectContact(contact) },
                            onInviteClick = { contact -> viewModel.inviteContact(contact) },
                            onAudioCallClick = { contact -> viewModel.startAudioCall(contact) },
                            onVideoCallClick = { contact -> viewModel.startVideoCall(contact) }
                        )
                        3 -> CallsTab(
                            calls = calls,
                            onCallClick = { contact, isVideo ->
                                if (isVideo) viewModel.startVideoCall(contact)
                                else viewModel.startAudioCall(contact)
                            }
                        )
                    }
                }
            }
        }

        // Profile Dialog
        if (showProfileDialog) {
            ProfileDialog(
                userProfile = userProfile,
                onDismiss = { showProfileDialog = false },
                onSaveProfile = { name, phone ->
                    viewModel.updateProfile(name, phone)
                }
            )
        }
    }
}
