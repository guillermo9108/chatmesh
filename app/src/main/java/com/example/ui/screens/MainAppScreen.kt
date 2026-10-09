package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mesh.MeshTransport
import com.example.ui.components.AddContactDialog
import com.example.ui.components.GitHubInfoDialog
import com.example.ui.components.MeshSettingsDialog
import com.example.ui.components.SimConfigDialog
import com.example.ui.components.VideoQualityDialog
import com.example.ui.components.WhatsAppTopBar
import com.example.ui.theme.*
import com.example.ui.viewmodel.ChatMeshViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: ChatMeshViewModel,
    modifier: Modifier = Modifier
) {
    val dims = LocalAppDimensions.current
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

    // Estados de historias
    val storiesGrouped by viewModel.storiesGrouped.collectAsStateWithLifecycle()
    var showStoryCreateScreen by remember { mutableStateOf(false) }
    var viewingStoryAuthorPhone by remember { mutableStateOf<String?>(null) }

    // Estado de registro manual
    val registrationRequired by viewModel.registrationRequired.collectAsStateWithLifecycle()
    val registrationError by viewModel.registrationError.collectAsStateWithLifecycle()
    val detectedPhoneForPrefill by viewModel.detectedPhoneForPrefill.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val videoQuality by viewModel.videoQuality.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showContactsSheet by remember { mutableStateOf(false) }
    var selectedFilterChip by remember { mutableStateOf("Todos") }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQueryText by remember { mutableStateOf("") }

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

    val myPhoneNumber = userProfile?.phoneNumber.orEmpty()
    val unreadStoriesCount = remember(storiesGrouped, myPhoneNumber) {
        storiesGrouped.filterKeys { it != myPhoneNumber }
            .values
            .count { list -> list.any { !it.viewedByMe } }
    }

    // Manejo de BackHandler para navegación por capas
    BackHandler(enabled = viewingStoryAuthorPhone != null) {
        viewingStoryAuthorPhone = null
    }
    BackHandler(enabled = viewingStoryAuthorPhone == null && showStoryCreateScreen) {
        showStoryCreateScreen = false
    }
    BackHandler(enabled = viewingStoryAuthorPhone == null && !showStoryCreateScreen && selectedContact != null) {
        viewModel.selectContact(null)
    }
    BackHandler(enabled = viewingStoryAuthorPhone == null && !showStoryCreateScreen && selectedContact == null && showContactsSheet) {
        showContactsSheet = false
    }
    BackHandler(enabled = viewingStoryAuthorPhone == null && !showStoryCreateScreen && selectedContact == null && !showContactsSheet && isSearchActive) {
        isSearchActive = false
        searchQueryText = ""
    }
    BackHandler(enabled = viewingStoryAuthorPhone == null && !showStoryCreateScreen && selectedContact == null && !showContactsSheet && !isSearchActive && selectedTabIndex != 0) {
        selectedTabIndex = 0
    }

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
        viewModel.restartBleDiscovery()
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
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

            // PRIORIDAD 2: Visor de historias
            viewingStoryAuthorPhone != null -> {
                val storiesForAuthor = storiesGrouped[viewingStoryAuthorPhone].orEmpty()
                StoryViewerScreen(
                    stories = storiesForAuthor,
                    onStoryViewed = { storyId -> viewModel.markStoryAsViewed(storyId) },
                    onDeleteStory = { storyId -> viewModel.deleteStory(storyId) },
                    onGetViewersFlow = { storyId -> viewModel.getStoryViewersFlow(storyId) },
                    onDismiss = { viewingStoryAuthorPhone = null }
                )
            }

            // PRIORIDAD 3: Creación de historia
            showStoryCreateScreen -> {
                StoryCreateScreen(
                    onPublishText = { text, color ->
                        viewModel.publishTextStory(text, color)
                        showStoryCreateScreen = false
                    },
                    onPublishImage = { base64, caption ->
                        viewModel.publishImageStory(base64, caption)
                        showStoryCreateScreen = false
                    },
                    onDismiss = { showStoryCreateScreen = false }
                )
            }

            // PRIORIDAD 4: Chat abierto
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

            // PRIORIDAD 5: Pantalla principal con apariencia moderna de WhatsApp
            else -> {
                Scaffold(
                    topBar = {
                        if (showContactsSheet) {
                            Column {
                                TopAppBar(
                                    title = {
                                        Column {
                                            Text(
                                                text = "Seleccionar contacto",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${allContacts.size} contactos",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    navigationIcon = {
                                        IconButton(onClick = { showContactsSheet = false }) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Atrás",
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    },
                                    actions = {
                                        IconButton(onClick = { viewModel.refreshContacts() }) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Actualizar",
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        IconButton(onClick = { showAddContactDialog = true }) {
                                            Icon(
                                                imageVector = Icons.Default.PersonAdd,
                                                contentDescription = "Nuevo contacto",
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        titleContentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    thickness = 0.6.dp
                                )
                            }
                        } else {
                            WhatsAppTopBar(
                                currentTabTitle = when (selectedTabIndex) {
                                    0 -> "WhatsApp"
                                    1 -> "Novedades"
                                    2 -> "Malla P2P"
                                    3 -> "Llamadas"
                                    else -> "WhatsApp"
                                },
                                selectedFilterChip = selectedFilterChip,
                                onFilterChipSelected = { chip ->
                                    if (chip == "Contactos") {
                                        showContactsSheet = true
                                    } else {
                                        selectedFilterChip = chip
                                    }
                                },
                                showFilterChips = (selectedTabIndex == 0),
                                isSearchActive = isSearchActive,
                                searchQuery = searchQueryText,
                                onSearchQueryChange = { searchQueryText = it },
                                onSearchActiveChange = { isSearchActive = it },
                                onCameraClick = { showStoryCreateScreen = true },
                                onProfileClick = { showProfileDialog = true },
                                onSyncContactsClick = { viewModel.refreshContacts() },
                                onSimConfigClick = { showSimConfigDialog = true },
                                onMeshSettingsClick = { showMeshSettingsDialog = true },
                                onGitHubClick = { showGitHubDialog = true },
                                onVideoQualityClick = { showVideoQualityDialog = true },
                                onShareAppClick = { shareApp(context) },
                                currentVideoQuality = videoQuality,
                                connectedNodesCount = engineState.connectedPeersCount,
                                ssidName = engineState.ssid,
                                isWifiDirectActive = engineState.transport == MeshTransport.WIFI_DIRECT,
                                isHotspotActive = engineState.isHotspotActive || engineState.transport == MeshTransport.HOTSPOT
                            )
                        }
                    },
                    bottomBar = {
                        if (!showContactsSheet) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 0.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("whatsapp_bottom_navigation")
                            ) {
                                val unreadChats = chatContacts.sumOf { it.unreadCount }

                                // 1. Chats
                                NavigationBarItem(
                                    selected = selectedTabIndex == 0,
                                    onClick = { selectedTabIndex = 0 },
                                    icon = {
                                        BadgedBox(
                                            badge = {
                                                if (unreadChats > 0) {
                                                    Badge(
                                                        containerColor = WhatsAppGreenPrimary,
                                                        contentColor = Color.White
                                                    ) {
                                                        Text("$unreadChats", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (selectedTabIndex == 0) Icons.Filled.Chat else Icons.Outlined.Chat,
                                                contentDescription = "Chats"
                                            )
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = "Chats",
                                            fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = WhatsAppChipSelectedLight,
                                        selectedIconColor = WhatsAppGreenDark,
                                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )

                                // 2. Novedades (Estados)
                                NavigationBarItem(
                                    selected = selectedTabIndex == 1,
                                    onClick = { selectedTabIndex = 1 },
                                    icon = {
                                        BadgedBox(
                                            badge = {
                                                if (unreadStoriesCount > 0) {
                                                    Badge(
                                                        containerColor = WhatsAppGreenPrimary,
                                                        modifier = Modifier.size(7.dp)
                                                    )
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (selectedTabIndex == 1) Icons.Filled.CircleNotifications else Icons.Outlined.CircleNotifications,
                                                contentDescription = "Novedades"
                                            )
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = "Novedades",
                                            fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = WhatsAppChipSelectedLight,
                                        selectedIconColor = WhatsAppGreenDark,
                                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )

                                // 3. Malla P2P
                                NavigationBarItem(
                                    selected = selectedTabIndex == 2,
                                    onClick = { selectedTabIndex = 2 },
                                    icon = {
                                        BadgedBox(
                                            badge = {
                                                if (engineState.connectedPeersCount > 0) {
                                                    Badge(
                                                        containerColor = WhatsAppGreenPrimary,
                                                        contentColor = Color.White
                                                    ) {
                                                        Text("${engineState.connectedPeersCount}", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (selectedTabIndex == 2) Icons.Filled.Hub else Icons.Outlined.Hub,
                                                contentDescription = "Malla P2P"
                                            )
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = "Malla P2P",
                                            fontWeight = if (selectedTabIndex == 2) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = WhatsAppChipSelectedLight,
                                        selectedIconColor = WhatsAppGreenDark,
                                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )

                                // 4. Llamadas
                                NavigationBarItem(
                                    selected = selectedTabIndex == 3,
                                    onClick = { selectedTabIndex = 3 },
                                    icon = {
                                        Icon(
                                            imageVector = if (selectedTabIndex == 3) Icons.Filled.Call else Icons.Outlined.Call,
                                            contentDescription = "Llamadas"
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "Llamadas",
                                            fontWeight = if (selectedTabIndex == 3) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = WhatsAppChipSelectedLight,
                                        selectedIconColor = WhatsAppGreenDark,
                                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    },
                    floatingActionButton = {
                        if (!showContactsSheet) {
                            when (selectedTabIndex) {
                                0 -> {
                                    FloatingActionButton(
                                        onClick = { showContactsSheet = true },
                                        containerColor = WhatsAppGreenPrimary,
                                        contentColor = Color.White,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.testTag("new_chat_fab")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Chat,
                                            contentDescription = "Nuevo chat",
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                1 -> {
                                    Column(horizontalAlignment = Alignment.End) {
                                        SmallFloatingActionButton(
                                            onClick = { showStoryCreateScreen = true },
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            shape = CircleShape,
                                            modifier = Modifier.testTag("text_story_fab")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Estado de texto",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        FloatingActionButton(
                                            onClick = { showStoryCreateScreen = true },
                                            containerColor = WhatsAppGreenPrimary,
                                            contentColor = Color.White,
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.testTag("camera_story_fab")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PhotoCamera,
                                                contentDescription = "Foto estado",
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                                2 -> {
                                    FloatingActionButton(
                                        onClick = { viewModel.startP2pDiscovery() },
                                        containerColor = WhatsAppGreenPrimary,
                                        contentColor = Color.White,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.testTag("scan_mesh_fab")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Escanear red malla",
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                3 -> {
                                    FloatingActionButton(
                                        onClick = { showContactsSheet = true },
                                        containerColor = WhatsAppGreenPrimary,
                                        contentColor = Color.White,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.testTag("new_call_fab")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Call,
                                            contentDescription = "Nueva llamada",
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        if (showContactsSheet) {
                            ContactsTab(
                                contacts = allContacts,
                                searchQuery = searchQuery,
                                onSearchQueryChange = { q -> viewModel.setSearchQuery(q) },
                                onContactClick = { contact ->
                                    showContactsSheet = false
                                    viewModel.selectContact(contact)
                                },
                                onInviteContact = { contact -> viewModel.inviteContact(contact) }
                            )
                        } else {
                            when (selectedTabIndex) {
                                0 -> ChatsTab(
                                    chats = chatContacts,
                                    onlineContacts = onlineContacts,
                                    onChatClick = { contact -> viewModel.selectContact(contact) },
                                    filterChip = selectedFilterChip,
                                    searchQuery = searchQueryText,
                                    onOpenContacts = { showContactsSheet = true }
                                )
                                1 -> StoryListScreen(
                                    userProfile = userProfile,
                                    myPhoneNumber = myPhoneNumber,
                                    storiesGrouped = storiesGrouped,
                                    onOpenCreateStory = { showStoryCreateScreen = true },
                                    onViewStory = { authorPhone -> viewingStoryAuthorPhone = authorPhone }
                                )
                                2 -> MeshNodesTab(
                                    engineState = engineState,
                                    meshNodes = meshNodes,
                                    onScanPeers = { viewModel.startP2pDiscovery() },
                                    onConnectDevice = { device -> viewModel.connectToP2pDevice(device) },
                                    onDisconnectDevice = { viewModel.disconnectP2pDevice() },
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