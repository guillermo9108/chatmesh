package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AddContactDialog
import com.example.ui.components.GitHubInfoDialog
import com.example.ui.components.MeshSettingsDialog
import com.example.ui.components.SimConfigDialog
import com.example.ui.components.VideoQualityDialog
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
    val onlineContacts by viewModel.onlineContacts.collectAsStateWithLifecycle()
    val meshNodes by viewModel.meshNodes.collectAsStateWithLifecycle()
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val engineState by viewModel.engineState.collectAsStateWithLifecycle()
    val realSimDetails by viewModel.realSimDetails.collectAsStateWithLifecycle()
    val selectedContact by viewModel.selectedContact.collectAsStateWithLifecycle()
    val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val localVideoBitmap by viewModel.localVideoBitmap.collectAsStateWithLifecycle()
    val remoteVideoBitmap by viewModel.remoteVideoBitmap.collectAsStateWithLifecycle()

    // Estado de registro manual
    val registrationRequired by viewModel.registrationRequired.collectAsStateWithLifecycle()
    val registrationError by viewModel.registrationError.collectAsStateWithLifecycle()
    val detectedPhoneForPrefill by viewModel.detectedPhoneForPrefill.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val videoQuality by viewModel.videoQuality.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showSimConfigDialog by remember { mutableStateOf(false) }
    var showAddContactDialog by remember { mutableStateOf(false) }
    var showGitHubDialog by remember { mutableStateOf(false) }
    var showMeshSettingsDialog by remember { mutableStateOf(false) }
    var showVideoQualityDialog by remember { mutableStateOf(false) }

    val hotspotRequestFromPeer by viewModel.hotspotRequestFromPeer.collectAsStateWithLifecycle()
    val hotspotSharedFromPeer by viewModel.hotspotSharedFromPeer.collectAsStateWithLifecycle()
    val hotspotConnectionStatus by viewModel.hotspotConnectionStatus.collectAsStateWithLifecycle()

    val blePeersPhones by viewModel.blePeersPhones.collectAsStateWithLifecycle()
    val bleEnabled by viewModel.bleEnabled.collectAsStateWithLifecycle()
    val isBleNegotiatingGo by viewModel.isBleNegotiatingGo.collectAsStateWithLifecycle()

    LaunchedEffect(hotspotRequestFromPeer) {
        if (hotspotRequestFromPeer != null) {
            showMeshSettingsDialog = true
        }
    }

    LaunchedEffect(hotspotSharedFromPeer) {
        if (hotspotSharedFromPeer) {
            showMeshSettingsDialog = true
        }
    }

    // Solicitar permisos en runtime
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
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionsLauncher.launch(permissions.toTypedArray())
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            // PRIORIDAD 0: Registro manual si no se detecta SIM
            registrationRequired -> {
                PhoneRegistrationScreen(
                    initialPhone = detectedPhoneForPrefill,
                    errorMessage = registrationError,
                    onSubmit = { phone, nickname ->
                        viewModel.registerDeviceManually(phone, nickname)
                    }
                )
            }

            // PRIORIDAD 1: Llamada activa
            engineState.isCallActive && engineState.activeCallPeer != null -> {
                CallScreen(
                    contact = engineState.activeCallPeer!!,
                    engineState = engineState,
                    localVideoBitmap = localVideoBitmap,
                    remoteVideoBitmap = remoteVideoBitmap,
                    onAnswerCall = { viewModel.answerCall() },
                    onEndCall = { viewModel.endCall() },
                    onToggleMute = { viewModel.toggleMute() },
                    onToggleSpeaker = { viewModel.toggleSpeaker() },
                    onSwitchCamera = { viewModel.switchCamera() },
                    onDeclineWithMessage = { reason -> viewModel.declineCallWithMessage(reason) }
                )
            }

            // PRIORIDAD 2: Chat abierto
            selectedContact != null -> {
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
                    onSendAudio = { audioBase64, durationSec ->
                        viewModel.sendAudioMessage(audioBase64, durationSec)
                    },
                    onSendFile = { fileName ->
                        viewModel.sendFileMessage(fileName, fileName)
                    },
                    onAudioCallClick = { viewModel.startAudioCall(selectedContact!!) },
                    onVideoCallClick = { viewModel.startVideoCall(selectedContact!!) },
                    onTypingChange = { status ->
                        viewModel.sendUserStatus(status)
                    },
                    onSaveContact = { name, phone ->
                        viewModel.addNewManualContact(name, phone)
                    }
                )
            }

            // PRIORIDAD 3: Pantalla principal con tabs
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    WhatsAppTopBar(
                        selectedTabIndex = selectedTabIndex,
                        onTabSelected = { selectedTabIndex = it },
                        onSearchClick = { selectedTabIndex = 1 },
                        onProfileClick = { showProfileDialog = true },
                        onSyncContactsClick = { viewModel.refreshContacts() },
                        onSimConfigClick = { showSimConfigDialog = true },
                        onMeshSettingsClick = { showMeshSettingsDialog = true },
                        onGitHubClick = { showGitHubDialog = true },
                        onVideoQualityClick = { showVideoQualityDialog = true },
                        onShareAppClick = { shareApp(context) },
                        currentVideoQuality = videoQuality,
                        unreadChatsCount = chatContacts.sumOf { it.unreadCount },
                        connectedNodesCount = engineState.connectedPeersCount,
                        ssidName = engineState.ssid
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedTabIndex) {
                            0 -> ChatsTab(
                                chats = chatContacts,
                                onlineContacts = onlineContacts,
                                onChatClick = { contact -> viewModel.selectContact(contact) }
                            )
                            1 -> ContactsTab(
                                contacts = allContacts,
                                searchQuery = searchQuery,
                                onSearchQueryChange = { q -> viewModel.setSearchQuery(q) },
                                onContactClick = { contact -> viewModel.selectContact(contact) },
                                onInviteContact = { contact -> viewModel.inviteContact(contact) }
                            )
                            2 -> MeshNodesTab(
                                engineState = engineState,
                                meshNodes = meshNodes,
                                onScanPeers = { viewModel.startP2pDiscovery() },
                                onConnectDevice = { device -> viewModel.connectToP2pDevice(device) },
                                onStartHotspot = { viewModel.startHotspot() },
                                onStopHotspot = { viewModel.stopHotspot() }
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
        }

        // Dialogs (solo si no estamos en registro)
        if (!registrationRequired) {
            if (showProfileDialog) {
                ProfileDialog(
                    userProfile = userProfile,
                    onSaveProfile = { nick, phone, avatarUri ->
                        viewModel.updateProfile(nick, phone, avatarUri)
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
                    onStartHotspot = { viewModel.startHotspot() },
                    onStopHotspot = { viewModel.stopHotspot() },
                    hotspotRequestFromPeer = hotspotRequestFromPeer,
                    hotspotSharedFromPeer = hotspotSharedFromPeer,
                    hotspotConnectionStatus = hotspotConnectionStatus,
                    onRequestHotspotFromPeer = { viewModel.requestHotspotFromPeer(it) },
                    onShareHotspotWithPeer = { viewModel.shareHotspotWithPeer(it) },
                    onAcceptPendingHotspot = { viewModel.acceptPendingHotspot() },
                    onDismissHotspotRequest = { viewModel.dismissHotspotRequest() },
                    onDismissHotspotShared = { viewModel.dismissHotspotSharedFromPeer() },
                    availablePeerPhones = viewModel.getKnownPeerPhones(),
                    myBleScore = viewModel.getMyBleScore(),
                    blePeersPhones = blePeersPhones,
                    bleEnabled = bleEnabled,
                    isBleNegotiatingGo = isBleNegotiatingGo,
                    onDismiss = { showMeshSettingsDialog = false }
                )
            }

            if (showVideoQualityDialog) {
                VideoQualityDialog(
                    currentQuality = videoQuality,
                    onQualitySelected = { q -> viewModel.setVideoQuality(q) },
                    onDismiss = { showVideoQualityDialog = false }
                )
            }
        }
    }
}

private fun shareApp(context: Context) {
    try {
        val shareText = "¡Descarga y conéctate sin Internet con ChatMesh! Mensajería P2P, llamadas de voz y video en malla offline por WiFi Direct, Hotspot y Bluetooth LE."
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "ChatMesh - Mensajería P2P Offline")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir ChatMesh"))
    } catch (_: Exception) {}
}