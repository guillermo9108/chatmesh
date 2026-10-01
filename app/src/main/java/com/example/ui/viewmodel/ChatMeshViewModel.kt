package com.example.ui.viewmodel

import android.app.Application
import android.net.wifi.p2p.WifiP2pDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ChatMeshConfig
import com.example.data.db.ChatMeshDatabase
import com.example.data.entity.*
import com.example.data.repository.ChatMeshRepository
import com.example.mesh.ContactSyncUtil
import com.example.mesh.DeviceIdentity
import com.example.mesh.MeshEngineHolder
import com.example.mesh.MeshEngineState
import com.example.mesh.PhoneRegistrationApi
import com.example.mesh.SimCardInfo
import com.example.mesh.SimDetectionUtil
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
        database.callDao()
    )

    private val meshEngine: WiFiMeshEngine =
        MeshEngineHolder.engine ?: MeshEngineHolder.init(application, repository)

    val userProfile: StateFlow<UserProfile?> = repository.userProfileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val chatContacts: StateFlow<List<ContactEntity>> = repository.chatContactsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allContacts: StateFlow<List<ContactEntity>> = repository.allContactsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val meshNodes: StateFlow<List<MeshNodeEntity>> = repository.allMeshNodesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val calls: StateFlow<List<CallEntity>> = repository.allCallsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val engineState: StateFlow<MeshEngineState> = meshEngine.engineState

    private val _realSimDetails = MutableStateFlow(SimDetectionUtil.getRealSimDetails(application))
    val realSimDetails: StateFlow<SimCardInfo> = _realSimDetails.asStateFlow()

    // ============================================================
    //  ESTADO DE REGISTRO
    // ============================================================
    private val _registrationRequired = MutableStateFlow(false)
    val registrationRequired: StateFlow<Boolean> = _registrationRequired.asStateFlow()

    private val _isRegistering = MutableStateFlow(false)
    val isRegistering: StateFlow<Boolean> = _isRegistering.asStateFlow()

    private val _registrationError = MutableStateFlow<String?>(null)
    val registrationError: StateFlow<String?> = _registrationError.asStateFlow()

    private val _deviceId = MutableStateFlow("")
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()

    val registrationApiUrl: String = ChatMeshConfig.REGISTRATION_API_URL

    // ============================================================
    //  ESTADO UI
    // ============================================================
    private val _selectedContact = MutableStateFlow<ContactEntity?>(null)
    val selectedContact: StateFlow<ContactEntity?> = _selectedContact.asStateFlow()

    private val _activeMessages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val activeMessages: StateFlow<List<MessageEntity>> = _activeMessages.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        viewModelScope.launch {
            bootstrapUser()
        }
    }

    // ============================================================
    //  BOOTSTRAP: detecta SIM → si no, pide registro
    // ============================================================
    private suspend fun bootstrapUser() {
        _deviceId.value = DeviceIdentity.getDeviceFingerprint(getApplication())

        val realPhone = SimDetectionUtil.detectRealPhoneNumber(getApplication())
        if (realPhone != null) {
            SimDetectionUtil.saveUserSimPhoneNumber(getApplication(), realPhone)
            completeInitialization(realPhone)
            return
        }

        val registeredPhone = SimDetectionUtil.getRegisteredPhoneNumber(getApplication())
        if (registeredPhone != null) {
            completeInitialization(registeredPhone)
            return
        }

        // No hay número de SIM ni registro previo: pedir registro
        _registrationRequired.value = true
    }

    private suspend fun completeInitialization(phoneNumber: String) {
        _registrationRequired.value = false
        _registrationError.value = null

        val existing = repository.getUserProfile()
        if (existing == null) {
            val newProfile = UserProfile(
                phoneNumber = phoneNumber,
                nickname = "Usuario",
                avatarUri = null,
                ssid = SimDetectionUtil.generateSsid(phoneNumber)
            )
            repository.saveUserProfile(newProfile)
            meshEngine.initialize(newProfile.phoneNumber, newProfile.nickname, newProfile.avatarUri)
        } else if (existing.phoneNumber != phoneNumber) {
            // El número cambió (SIM nueva o registro nuevo) → actualizar perfil y motor
            val updated = existing.copy(
                phoneNumber = phoneNumber,
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
    //  REGISTRO VÍA API
    // ============================================================
    fun registerDeviceWithApi() {
        if (_isRegistering.value) return
        _isRegistering.value = true
        _registrationError.value = null

        viewModelScope.launch {
            val app = getApplication<Application>()
            val deviceId = _deviceId.value.ifBlank {
                DeviceIdentity.getDeviceFingerprint(app).also { _deviceId.value = it }
            }
            val androidId = DeviceIdentity.getAndroidId(app)
            val imei = DeviceIdentity.getImei(app)
            val simInfo = SimDetectionUtil.getRealSimDetails(app)

            val result = PhoneRegistrationApi.register(
                deviceId = deviceId,
                androidId = androidId,
                imei = imei,
                countryIso = simInfo.countryIso,
                carrierName = simInfo.carrierName
            )

            result.fold(
                onSuccess = { reg ->
                    SimDetectionUtil.saveRegisteredPhoneNumber(app, reg.phoneNumber)
                    _isRegistering.value = false
                    completeInitialization(reg.phoneNumber)
                },
                onFailure = { err ->
                    _isRegistering.value = false
                    _registrationError.value = friendlyError(err)
                }
            )
        }
    }

    private fun friendlyError(err: Throwable): String {
        val msg = err.message.orEmpty()
        return when {
            msg.contains("Failed to connect", true) ||
            msg.contains("Unable to resolve host", true) ||
            msg.contains("Network is unreachable", true) ->
                "No hay conexión a Internet. Conéctate y reintenta."
            msg.contains("HTTP 4", true) ->
                "Error del servidor (${msg.take(80)}). Reintenta más tarde."
            msg.contains("HTTP 5", true) ->
                "Servidor no disponible. Reintenta en unos minutos."
            msg.contains("timeout", true) ->
                "El servidor tardó demasiado. Reintenta."
            else -> "Error: ${msg.take(140).ifBlank { "desconocido" }}"
        }
    }

    // ============================================================
    //  Resto de funciones (sin cambios respecto a tu versión actual)
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
        viewModelScope.launch {
            repository.insertContact(
                ContactEntity(
                    phoneNumber = cleanPhone,
                    displayName = displayName.ifBlank { cleanPhone },
                    isRegisteredInMesh = false
                )
            )
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

    fun startP2pDiscovery() { meshEngine.startP2pDiscovery() }
    fun reCreateP2pGroup() { meshEngine.reCreateP2pGroup() }
    fun sendAudioMessage(base64Audio: String, durationSeconds: Int) { sendAudioVoiceMessage(base64Audio, durationSeconds) }
    fun sendUserStatus(status: String) { _selectedContact.value?.let { meshEngine.sendUserStatus(it.phoneNumber, status) } }
    fun sendUserStatus(phoneNumber: String, status: String) { meshEngine.sendUserStatus(phoneNumber, status) }
}