package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AddContactDialog
import com.example.ui.components.GitHubInfoDialog
import com.example.ui.components.MeshSettingsDialog
import com.example.ui.components.SimConfigDialog
import com.example.ui.components.WhatsAppTopBar
import com.example.ui.viewmodel.ChatMeshViewModel

@Composable
fun MainAppScreen(
    viewModel: ChatMeshViewModel,
    modifier: Modifier = Modifier
) {
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val chatContacts by viewModel.chatContacts.collectAsStateWithLifecycle()
    val allContacts by viewModel.allContacts.collectAsStateWithLifecycle()
    val meshNodes by viewModel.meshNodes.collectAsStateWithLifecycle()
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val engineState by viewModel.engineState.collectAsStateWithLifecycle()
    val realSimDetails by viewModel.realSimDetails.collectAsStateWithLifecycle()
    val selectedContact by viewModel.selectedContact.collectAsStateWithLifecycle()
    val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showSimConfigDialog by remember { mutableStateOf(false) }
    var showAddContactDialog by remember { mutableStateOf(false) }
    var showGitHubDialog by remember { mutableStateOf(false) }
    var showMeshSettingsDialog by remember { mutableStateOf(false) }

    // Request necessary runtime permissions
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshContacts()
        viewModel.reloadSimDetails()
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            permissions.add(Manifest.permission.READ_PHONE_NUMBERS)
        }
        permissionsLauncher.launch(permissions.toTypedArray())
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Priority 1: Full-screen Call Management UI if a call is active (incoming, connecting, or ongoing)
        if (engineState.isCallActive && engineState.activeCallPeer != null) {
            CallScreen(
                contact = engineState.activeCallPeer!!,
                engineState = engineState,
                onAnswerCall = { viewModel.answerCall() },
                onEndCall = { viewModel.endCall() },
                onToggleMute = { viewModel.toggleMute() },
                onToggleSpeaker = { viewModel.toggleSpeaker() },
                onDeclineWithMessage = { reason -> viewModel.declineCallWithMessage(reason) }
            )
        }
        // Priority 2: Chat Detail Screen if a contact is selected
        else if (selectedContact != null) {
            ChatDetailScreen(
                contact = selectedContact!!,
                messages = activeMessages,
                activeTypingPhone = engineState.activeTypingContactPhone,
                activeRecordingPhone = engineState.activeRecordingContactPhone,
                onBackClick = { viewModel.selectContact(null) },
                onSendMessage = { text -> viewModel.sendTextMessage(text) },
                onSendImage = { uri, caption, base64 ->
                    viewModel.sendImageMessage(uri, caption, base64)
                },
                onSendAudio = { durationSec ->
                    viewModel.sendAudioMessage(durationSec)
                },
                onSendFile = { fileName ->
                    viewModel.sendFileMessage(fileName, fileName)
                },
                onAudioCallClick = { viewModel.startAudioCall(selectedContact!!) },
                onVideoCallClick = { viewModel.startVideoCall(selectedContact!!) },
                onTypingChange = { status ->
                    viewModel.sendUserStatus(status)
                }
            )
        }
        // Priority 3: Main WhatsApp Tab View
        else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                WhatsAppTopBar(
                    selectedTabIndex = selectedTabIndex,
                    onTabSelected = { selectedTabIndex = it },
                    onSearchClick = { /* Search toggled */ },
                    onProfileClick = { showProfileDialog = true },
                    onSyncContactsClick = { viewModel.refreshContacts() },
                    onSimConfigClick = { showSimConfigDialog = true },
                    onMeshSettingsClick = { showMeshSettingsDialog = true },
                    onGitHubClick = { showGitHubDialog = true },
                    unreadChatsCount = chatContacts.sumOf { it.unreadCount },
                    connectedNodesCount = engineState.connectedPeersCount,
                    ssidName = engineState.ssid
                )

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTabIndex) {
                        0 -> ChatsTab(
                            chats = chatContacts,
                            onChatClick = { contact -> viewModel.selectContact(contact) }
                        )
                        1 -> ContactsTab(
                            contacts = allContacts,
                            searchQuery = searchQuery,
                            onContactClick = { contact -> viewModel.selectContact(contact) },
                            onInviteContact = { contact -> viewModel.inviteContact(contact) }
                        )
                        2 -> MeshNodesTab(
                            engineState = engineState,
                            meshNodes = meshNodes,
                            onScanPeers = { viewModel.startP2pDiscovery() },
                            onConnectDevice = { device -> viewModel.connectToP2pDevice(device) }
                        )
                        3 -> CallsTab(
                            calls = calls,
                            onStartCall = { contact, isVideo ->
                                if (isVideo) {
                                    viewModel.startVideoCall(contact)
                                } else {
                                    viewModel.startAudioCall(contact)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Dialogs
        if (showProfileDialog) {
            ProfileDialog(
                userProfile = userProfile,
                onSaveProfile = { nick, phone ->
                    viewModel.updateProfile(nick, phone)
                    showProfileDialog = false
                },
                onDismiss = { showProfileDialog = false }
            )
        }

        if (showSimConfigDialog) {
            SimConfigDialog(
                simInfo = realSimDetails,
                onSaveNumber = { num ->
                    viewModel.saveRealSimPhoneNumber(num)
                    showSimConfigDialog = false
                },
                onDismiss = { showSimConfigDialog = false }
            )
        }

        if (showAddContactDialog) {
            AddContactDialog(
                onAddContact = { name, phone ->
                    viewModel.addNewManualContact(name, phone)
                    showAddContactDialog = false
                },
                onDismiss = { showAddContactDialog = false }
            )
        }

        if (showGitHubDialog) {
            GitHubInfoDialog(onDismiss = { showGitHubDialog = false })
        }

        if (showMeshSettingsDialog) {
            MeshSettingsDialog(
                engineState = engineState,
                onReCreateGroup = { viewModel.reCreateP2pGroup() },
                onScanPeers = { viewModel.startP2pDiscovery() },
                onDismiss = { showMeshSettingsDialog = false }
            )
        }
    }
}
