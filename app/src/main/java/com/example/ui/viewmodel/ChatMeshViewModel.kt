package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.wifi.p2p.WifiP2pDevice
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.ChatMeshDatabase
import com.example.data.entity.*
import com.example.data.repository.ChatMeshRepository
import com.example.mesh.BleScoreCalculator
import com.example.mesh.ContactSyncUtil
import com.example.mesh.DeviceIdentity
import com.example.mesh.HotspotConnector
import com.example.mesh.MeshEngineHolder
import com.example.mesh.MeshEngineState
import com.example.mesh.SimCardInfo
import com.example.mesh.SimDetectionUtil
import com.example.mesh.VideoQuality
import com.example.mesh.WiFiMeshEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatMeshViewModel(application: Application) : AndroidViewModel(application) {
    private val database = ChatMeshDatabase.getDatabase(application)
    private val repository = ChatMeshRepository(
        database.userDao(),
        database.contactDao(),
        database.messageDao(),
        database.meshNodeDao(),
        database.callDao(),
        database.storyDao(),
        database.storySeenDao()
    )

    private val meshEngine: WiFiMeshEngine =
        MeshEngineHolder.engine ?: MeshEngineHolder.init(application, repository)

    val userProfile: StateFlow<UserProfile?> = repository.userProfileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val chatContacts: StateFlow<List<ContactEntity>> = repository.chatContactsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allContacts: StateFlow<List<ContactEntity>> = repository.allContactsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val onlineContacts: StateFlow<List<ContactEntity>> = combine(
        repository.allContactsFlow,
        meshEngine.engineState
    ) { contacts, state ->
        val activePhones = mutableSetOf<String>()
        activePhones.addAll(state.blePeersPhones)
        activePhones.addAll(meshEngine.getKnownNeighborPeers().map { it.phoneNumber })
        activePhones.addAll(state.discoveredP2pDevices.mapNotNull { dev ->
            val name = dev.deviceName.orEmpty()
            if (name.startsWith("ChatMesh_")) name.removePrefix("ChatMesh_") else null
        })

        val now = System.currentTimeMillis()
        val matchedContacts = contacts.filter { contact ->
            contact.isConnected ||
            (now - contact.lastSeen < 120_000L && contact.isRegisteredInMesh) ||
            activePhones.any { SimDetectionUtil.isMatchingPhone(it, contact.phoneNumber) }
        }.toMutableList()

        val knownPeers = meshEngine.getKnownNeighborPeers()
        for (peer in knownPeers) {
            if (matchedContacts.none { SimDetectionUtil.isMatchingPhone(it.phoneNumber, peer.phoneNumber) }) {
                matchedContacts.add(
                    ContactEntity(
                        phoneNumber = peer.phoneNumber,
                        displayName = peer.displayName.ifBlank { peer.phoneNumber },
                        isRegisteredInMesh = true,
                        isConnected = true,
                        lastSeen = peer.lastSeenTimestamp,
                        meshNodeId = peer.nodeId,
                        statusText = "Conectado en red malla"
                    )
                )
            }
        }

        matchedContacts.sortedWith(
            compareByDescending<ContactEntity> { it.isConnected }
                .thenByDescending { it.lastSeen }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val meshNodes: StateFlow<List<MeshNodeEntity>> = repository.allMeshNodesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val calls: StateFlow<List<CallEntity>> = repository.allCallsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val storiesUpdated: StateFlow<Long> = meshEngine.storiesUpdated

    val allActiveStories: StateFlow<List<StoryEntity>> = combine(
        repository.getActiveStoriesFlow(),
        storiesUpdated
    ) { stories, _ ->
        stories
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val storiesGrouped: StateFlow<Map<String, List<StoryEntity>>> = allActiveStories
        .map { stories ->
            stories.groupBy { it.authorPhone }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val engineState: StateFlow<MeshEngineState> = meshEngine.engineState

    val blePeersPhones: StateFlow<List<String>> = engineState
        .map { it.blePeersPhones }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bleEnabled: StateFlow<Boolean> = engineState
        .map { it.bleEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isBleNegotiatingGo: StateFlow<Boolean> = engineState
        .map { it.isBleNegotiatingGo }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _realSimDetails = MutableStateFlow(SimDetectionUtil.getRealSimDetails(application))
    val realSimDetails: StateFlow<SimCardInfo> = _realSimDetails.asStateFlow()

    // ============================================================
    //  ESTADO DE REGISTRO MANUAL
    // ============================================================
    private val _registrationRequired = MutableStateFlow(false)
    val registrationRequired: StateFlow<Boolean> = _registrationRequired.asStateFlow()

    private val _registrationError = MutableStateFlow<String?>(null)
    val registrationError: StateFlow<String?> = _registrationError.asStateFlow()

    private val _detectedPhoneForPrefill = MutableStateFlow("")
    val detectedPhoneForPrefill: StateFlow<String> = _detectedPhoneForPrefill.asStateFlow()

    // ============================================================
    //  ESTADO UI
    // ============================================================
    private val _selectedContact = MutableStateFlow<ContactEntity?>(null)
    val selectedContact: StateFlow<ContactEntity?> = _selectedContact.asStateFlow()

    private val _activeMessages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val activeMessages: StateFlow<List<MessageEntity>> = _activeMessages.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Flujo de Hotspot Compartido
    private val _hotspotRequestFromPeer = MutableStateFlow<String?>(null)
    val hotspotRequestFromPeer: StateFlow<String?> = _hotspotRequestFromPeer.asStateFlow()

    private val _hotspotSharedFromPeer = MutableStateFlow(false)
    val hotspotSharedFromPeer: StateFlow<Boolean> = _hotspotSharedFromPeer.asStateFlow()

    private val _hotspotConnectionStatus = MutableStateFlow("")
    val hotspotConnectionStatus: StateFlow<String> = _hotspotConnectionStatus.asStateFlow()

    private val _videoQuality = MutableStateFlow(VideoQuality.MEDIUM)
    val videoQuality: StateFlow<VideoQuality> = _videoQuality.asStateFlow()

    fun setVideoQuality(quality: VideoQuality) {
        _videoQuality.value = quality
        meshEngine.setVideoQuality(quality)
        getApplication<Application>().getSharedPreferences("chatmesh_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("video_quality", quality.name)
            .apply()
    }

    init {
        val savedQuality = getApplication<Application>()
            .getSharedPreferences("chatmesh_prefs", Context.MODE_PRIVATE)
            .getString("video_quality", null)
        val initialQuality = VideoQuality.fromName(savedQuality)
        _videoQuality.value = initialQuality
        meshEngine.setVideoQuality(initialQuality)

        viewModelScope.launch {
            repository.cleanInvalidContacts()
        }

        viewModelScope.launch { bootstrapUser() }
        viewModelScope.launch {
            meshEngine.engineState.collect { state ->
                if (state.isHotspotSharedByPeer && state.hotspotSharedSsid.isNotBlank()) {
                    _hotspotSharedFromPeer.value = true
                }
                if (state.hotspotPeerPhone.isNotBlank() && state.isHotspotSharedByPeer && state.hotspotSharedSsid.isBlank()) {
                    _hotspotRequestFromPeer.value = state.hotspotPeerPhone
                }
            }
        }
    }

    // ============================================================
    //  BOOTSTRAP: detecta SIM → si no, registro manual
    // ============================================================
    private suspend fun bootstrapUser() {
        // 1. Intentar detectar número real de SIM
        val realPhone = SimDetectionUtil.detectRealPhoneNumber(getApplication())
        if (realPhone != null) {
            SimDetectionUtil.saveUserSimPhoneNumber(getApplication(), realPhone)
            completeInitialization(realPhone, null)
            return
        }

        // 2. ¿Ya hay un número guardado (manual o SIM previa)?
        val saved = SimDetectionUtil.getCurrentPhoneNumber(getApplication())
        if (!saved.isNullOrBlank()) {
            val existing = repository.getUserProfile()
            completeInitialization(saved, existing?.nickname)
            return
        }

        // 3. No hay nada → pedir registro manual
        _registrationRequired.value = true
    }

    private suspend fun completeInitialization(phoneNumber: String, nicknameOverride: String?) {
        _registrationRequired.value = false
        _registrationError.value = null

        val existing = repository.getUserProfile()

        if (existing == null) {
            val nickname = nicknameOverride?.takeIf { it.isNotBlank() } ?: "Usuario"
            val newProfile = UserProfile(
                phoneNumber = phoneNumber,
                nickname = nickname,
                avatarUri = null,
                ssid = SimDetectionUtil.generateSsid(phoneNumber)
            )
            repository.saveUserProfile(newProfile)
            meshEngine.initialize(newProfile.phoneNumber, newProfile.nickname, newProfile.avatarUri)
        } else if (existing.phoneNumber != phoneNumber) {
            val nickname = nicknameOverride?.takeIf { it.isNotBlank() } ?: existing.nickname
            val updated = existing.copy(
                phoneNumber = phoneNumber,
                nickname = nickname,
                ssid = SimDetectionUtil.generateSsid(phoneNumber)
            )
            repository.saveUserProfile(updated)
            meshEngine.initialize(updated.phoneNumber, updated.nickname, updated.avatarUri)
        } else {
            meshEngine.initialize(existing.phoneNumber, existing.nickname, existing.avatarUri)
        }

        reloadSimDetails()
        refreshContacts()
    }

    // ============================================================
    //  REGISTRO MANUAL
    // ============================================================
    fun registerDeviceManually(phone: String, nickname: String) {
        val cleanPhone = SimDetectionUtil.sanitizePhoneNumber(phone)
        val cleanNickname = nickname.trim().ifBlank { "Usuario" }

        val digits = cleanPhone.filter { it.isDigit() }
        if (digits.length < 7 || digits.length > 15 || digits.all { it == '0' }) {
            _registrationError.value = "Número inválido"
            return
        }

        _registrationError.value = null
        SimDetectionUtil.saveManualPhoneNumber(getApplication(), cleanPhone)

        viewModelScope.launch {
            completeInitialization(cleanPhone, cleanNickname)
        }
    }

    // ============================================================
    //  Resto de funciones
    // ============================================================
    fun reloadSimDetails() {
        _realSimDetails.value = SimDetectionUtil.getRealSimDetails(getApplication())
    }

    fun saveRealSimPhoneNumber(number: String) {
        SimDetectionUtil.saveUserSimPhoneNumber(getApplication(), number)
        reloadSimDetails()
        viewModelScope.launch {
            val current = repository.getUserProfile()
            if (current != null) {
                repository.saveUserProfile(
                    current.copy(
                        phoneNumber = number,
                        ssid = SimDetectionUtil.generateSsid(number)
                    )
                )
                meshEngine.updateUserProfile(number, current.nickname, current.avatarUri)
            }
        }
    }

    fun selectContact(contact: ContactEntity?) {
        _selectedContact.value = contact
        if (contact != null) {
            viewModelScope.launch {
                repository.markChatAsRead(contact.phoneNumber)
                val myPhone = engineState.value.myPhoneNumber
                repository.getConversationFlow(contact.phoneNumber, myPhone).collect {
                    _activeMessages.value = it
                }
            }
        } else {
            _activeMessages.value = emptyList()
        }
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun sendTextMessage(text: String) {
        val contact = _selectedContact.value ?: return
        if (text.isBlank()) return
        meshEngine.sendChatMessage(contact.phoneNumber, text.trim(), "TEXT")
    }

    fun sendImageMessage(imageUri: String, caption: String, base64Data: String) {
        val contact = _selectedContact.value ?: return
        meshEngine.sendChatMessage(contact.phoneNumber, caption, "IMAGE", imageUri, base64Data)
    }

    fun sendAudioVoiceMessage(base64Audio: String, durationSeconds: Int) {
        val contact = _selectedContact.value ?: return
        meshEngine.sendChatMessage(contact.phoneNumber, "Mensaje de voz", "AUDIO", null, base64Audio, durationSeconds)
    }

    val localVideoBitmap = meshEngine.videoCallManager.localVideoBitmap
    val remoteVideoBitmap = meshEngine.videoCallManager.remoteVideoBitmap

    fun switchCamera() { meshEngine.switchCamera() }

    fun sendFileMessage(fileName: String, fileUri: String) {
        val contact = _selectedContact.value ?: return
        meshEngine.sendChatMessage(contact.phoneNumber, fileName, "FILE", fileUri, null)
    }

    fun startAudioCall(contact: ContactEntity) { meshEngine.startCall(contact, isVideo = false) }
    fun startVideoCall(contact: ContactEntity) { meshEngine.startCall(contact, isVideo = true) }
    fun answerCall() { meshEngine.answerCall() }
    fun endCall() { meshEngine.endCall() }

    fun declineCallWithMessage(reason: String) {
        val contact = engineState.value.activeCallPeer
        if (contact != null && reason.isNotBlank()) {
            meshEngine.sendChatMessage(contact.phoneNumber, reason, "TEXT")
        }
        meshEngine.endCall()
    }

    fun handleIncomingCallIntent(callerPhone: String, isVideo: Boolean = false) {
        viewModelScope.launch {
            val contact = repository.getContact(callerPhone) ?: ContactEntity(
                phoneNumber = callerPhone,
                displayName = callerPhone,
                isRegisteredInMesh = true,
                isConnected = true
            )
            meshEngine.handleCallSignal(
                com.example.mesh.MeshPacket(
                    packetType = "CALL_SIGNAL",
                    sourceNodeId = callerPhone,
                    sourcePhone = callerPhone,
                    sourceName = contact.displayName,
                    destinationPhone = engineState.value.myPhoneNumber,
                    callSignalType = "OFFER",
                    callIsVideo = isVideo
                )
            )
        }
    }

    fun toggleMute() { meshEngine.toggleMute() }
    fun toggleSpeaker() { meshEngine.toggleSpeaker() }
    fun inviteContact(contact: ContactEntity) { meshEngine.autoConnectToContact(contact) }

    fun addNewManualContact(displayName: String, phoneNumber: String) {
        val cleanPhone = SimDetectionUtil.sanitizePhoneNumber(phoneNumber)
        if (!SimDetectionUtil.isValidPhoneNumber(cleanPhone)) return
        viewModelScope.launch {
            val existing = repository.getContact(cleanPhone)
            val nameToUse = displayName.ifBlank { cleanPhone }
            if (existing != null) {
                repository.saveContact(
                    existing.copy(displayName = nameToUse)
                )
            } else {
                repository.insertContact(
                    ContactEntity(
                        phoneNumber = cleanPhone,
                        displayName = nameToUse,
                        isRegisteredInMesh = false
                    )
                )
            }
            if (_selectedContact.value?.phoneNumber == cleanPhone) {
                _selectedContact.value = _selectedContact.value?.copy(displayName = nameToUse)
            }
        }
    }

    fun updateProfile(nickname: String, phoneNumber: String, avatarUri: String?) {
        val cleanPhone = SimDetectionUtil.sanitizePhoneNumber(phoneNumber)
        viewModelScope.launch {
            val current = repository.getUserProfile()
            val updated = if (current != null) {
                current.copy(
                    nickname = nickname,
                    phoneNumber = cleanPhone,
                    avatarUri = avatarUri,
                    ssid = SimDetectionUtil.generateSsid(cleanPhone)
                )
            } else {
                UserProfile(
                    phoneNumber = cleanPhone,
                    nickname = nickname,
                    avatarUri = avatarUri,
                    ssid = SimDetectionUtil.generateSsid(cleanPhone)
                )
            }
            repository.saveUserProfile(updated)
            meshEngine.updateUserProfile(cleanPhone, nickname, avatarUri)
        }
    }

    fun refreshContacts() {
        viewModelScope.launch {
            val myPhone = engineState.value.myPhoneNumber
            ContactSyncUtil.syncDeviceContacts(getApplication(), repository, myPhone)
        }
    }

    fun reCreateWiFiDirectGroup() { meshEngine.reCreateP2pGroup() }
    fun scanP2pPeers() { meshEngine.startP2pDiscovery() }
    fun connectToP2pDevice(device: WifiP2pDevice) { meshEngine.connectToPeer(device) }

    fun setRecording(isRecording: Boolean, seconds: Int = 0) {
        val contact = _selectedContact.value
        if (contact != null) {
            val status = if (isRecording) "RECORDING" else "IDLE"
            meshEngine.sendUserStatus(contact.phoneNumber, status)
        }
    }

    fun onUserTyping(text: String) {
        val contact = _selectedContact.value
        if (contact != null) {
            meshEngine.sendUserStatus(contact.phoneNumber, if (text.isNotBlank()) "TYPING" else "IDLE")
        }
    }

    fun startHotspot() = meshEngine.startHotspot()
    fun stopHotspot() = meshEngine.stopHotspot()
    fun clearHotspotError() = meshEngine.clearHotspotError()

    // Métodos para Hotspot Compartido P2P
    fun requestHotspotFromPeer(peerPhone: String) = meshEngine.requestHotspotFromPeer(peerPhone)
    fun shareHotspotWithPeer(peerPhone: String) = meshEngine.shareHotspotWithPeer(peerPhone)

    fun acceptPendingHotspot() {
        val creds = meshEngine.getPendingHotspotCredentials() ?: return
        val (ssid, pass, peer) = creds
        _hotspotConnectionStatus.value = "Conectando a $ssid..."

        if (!HotspotConnector.isSupported()) {
            _hotspotConnectionStatus.value = "Requiere Android 10+"
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            HotspotConnector.connectToHotspot(
                context = getApplication(),
                ssid = ssid,
                password = pass,
                onConnected = { network ->
                    _hotspotConnectionStatus.value = "Conectado a $ssid"
                    meshEngine.clearPendingHotspotCredentials()
                    _hotspotSharedFromPeer.value = false
                },
                onFailed = { error ->
                    _hotspotConnectionStatus.value = "Error: $error"
                }
            )
        }
    }

    fun dismissHotspotRequest() { _hotspotRequestFromPeer.value = null }
    fun dismissHotspotSharedFromPeer() { _hotspotSharedFromPeer.value = false }
    fun setHotspotRequestFromIntent(phone: String) { _hotspotRequestFromPeer.value = phone }
    fun getKnownPeerPhones(): List<String> = meshEngine.getKnownPeerPhones()

    fun getMyBleScore(): Int {
        return try {
            val bm = getApplication<Application>().getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val status = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_STATUS)
            val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                           status == android.os.BatteryManager.BATTERY_STATUS_FULL
            val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = cm.activeNetwork
            val caps = if (network != null) cm.getNetworkCapabilities(network) else null
            val hasInternet = caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            BleScoreCalculator.calculate(level, charging, hasInternet, android.os.Build.VERSION.SDK_INT)
        } catch (_: Exception) { 0 }
    }

    fun startP2pDiscovery() { meshEngine.startP2pDiscovery() }
    fun reCreateP2pGroup() { meshEngine.reCreateP2pGroup() }
    fun sendAudioMessage(base64Audio: String, durationSeconds: Int) { sendAudioVoiceMessage(base64Audio, durationSeconds) }
    fun sendUserStatus(status: String) { _selectedContact.value?.let { meshEngine.sendUserStatus(it.phoneNumber, status) } }
    fun sendUserStatus(phoneNumber: String, status: String) { meshEngine.sendUserStatus(phoneNumber, status) }

    fun publishTextStory(text: String, backgroundColor: Int) {
        meshEngine.publishStory("TEXT", text, null, backgroundColor)
    }

    fun publishImageStory(base64: String, caption: String) {
        meshEngine.publishStory("IMAGE", caption, base64, 0)
    }

    fun markStoryAsViewed(storyId: String) {
        val story = allActiveStories.value.firstOrNull { it.storyId == storyId }
        val author = story?.authorPhone ?: return
        meshEngine.sendStoryView(storyId, author)
    }

    fun deleteStory(storyId: String) {
        meshEngine.deleteStory(storyId)
    }

    fun getStoryViewersFlow(storyId: String): Flow<List<StorySeenEntity>> {
        return repository.getStoryViewersFlow(storyId)
    }
}