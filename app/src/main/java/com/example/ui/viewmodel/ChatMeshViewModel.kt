package com.example.ui.viewmodel

import android.app.Application
import android.net.wifi.p2p.WifiP2pDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.ChatMeshDatabase
import com.example.data.entity.*
import com.example.data.repository.ChatMeshRepository
import com.example.mesh.ContactSyncUtil
import com.example.mesh.MeshEngineState
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

    val meshEngine = WiFiMeshEngine(application, repository)

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

    private val _selectedContact = MutableStateFlow<ContactEntity?>(null)
    val selectedContact: StateFlow<ContactEntity?> = _selectedContact.asStateFlow()

    private val _activeMessages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val activeMessages: StateFlow<List<MessageEntity>> = _activeMessages.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isRecordingAudio = MutableStateFlow(false)
    val isRecordingAudio: StateFlow<Boolean> = _isRecordingAudio.asStateFlow()

    private val _recordingTimerSeconds = MutableStateFlow(0)
    val recordingTimerSeconds: StateFlow<Int> = _recordingTimerSeconds.asStateFlow()

    init {
        viewModelScope.launch {
            val phone = SimDetectionUtil.detectSimPhoneNumber(getApplication())
            val existing = repository.getUserProfile()
            if (existing == null) {
                val newProfile = UserProfile(
                    phoneNumber = if (phone.isNotEmpty()) phone else "+5300000000",
                    nickname = "Usuario",
                    ssid = SimDetectionUtil.generateSsid(phone)
                )
                repository.saveUserProfile(newProfile)
                meshEngine.initialize(newProfile.phoneNumber, newProfile.nickname)
            } else {
                meshEngine.initialize(existing.phoneNumber, existing.nickname)
            }
            refreshContacts()
        }
    }

    fun reloadSimDetails() {
        val details = SimDetectionUtil.getRealSimDetails(getApplication())
        _realSimDetails.value = details
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

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun sendTextMessage(text: String) {
        val contact = _selectedContact.value ?: return
        if (text.isBlank()) return
        meshEngine.sendChatMessage(
            recipientPhone = contact.phoneNumber,
            content = text.trim(),
            mediaType = "TEXT"
        )
    }

    fun sendImageMessage(imageUri: String, caption: String, base64Data: String) {
        val contact = _selectedContact.value ?: return
        meshEngine.sendChatMessage(
            recipientPhone = contact.phoneNumber,
            content = caption,
            mediaType = "IMAGE",
            mediaUri = imageUri,
            mediaData = base64Data
        )
    }

    fun sendAudioVoiceMessage(durationSeconds: Int) {
        val contact = _selectedContact.value ?: return
        meshEngine.sendChatMessage(
            recipientPhone = contact.phoneNumber,
            content = "Mensaje de voz",
            mediaType = "AUDIO",
            audioDuration = durationSeconds
        )
    }

    fun sendFileMessage(fileName: String, fileUri: String) {
        val contact = _selectedContact.value ?: return
        meshEngine.sendChatMessage(
            recipientPhone = contact.phoneNumber,
            content = fileName,
            mediaType = "FILE",
            mediaUri = fileUri
        )
    }

    fun startAudioCall(contact: ContactEntity) {
        meshEngine.startCall(contact, isVideo = false)
    }

    fun startVideoCall(contact: ContactEntity) {
        meshEngine.startCall(contact, isVideo = true)
    }

    fun answerCall() {
        meshEngine.answerCall()
    }

    fun endCall() {
        meshEngine.endCall()
    }

    fun declineCallWithMessage(reason: String) {
        val contact = engineState.value.activeCallPeer
        if (contact != null && reason.isNotBlank()) {
            meshEngine.sendChatMessage(
                recipientPhone = contact.phoneNumber,
                content = reason,
                mediaType = "TEXT"
            )
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

    fun toggleMute() {
        meshEngine.toggleMute()
    }

    fun toggleSpeaker() {
        meshEngine.toggleSpeaker()
    }

    fun inviteContact(contact: ContactEntity) {
        meshEngine.autoConnectToContact(contact)
    }

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

    fun updateProfile(nickname: String, phoneNumber: String) {
        val cleanPhone = SimDetectionUtil.sanitizePhoneNumber(phoneNumber)
        viewModelScope.launch {
            val current = repository.getUserProfile()
            if (current != null) {
                repository.saveUserProfile(
                    current.copy(
                        nickname = nickname,
                        phoneNumber = cleanPhone,
                        ssid = SimDetectionUtil.generateSsid(cleanPhone)
                    )
                )
            }
        }
    }

    fun refreshContacts() {
        viewModelScope.launch {
            val myPhone = engineState.value.myPhoneNumber
            ContactSyncUtil.syncDeviceContacts(getApplication(), repository, myPhone)
        }
    }

    fun reCreateWiFiDirectGroup() {
        meshEngine.reCreateP2pGroup()
    }

    fun scanP2pPeers() {
        meshEngine.startP2pDiscovery()
    }

    fun connectToP2pDevice(device: WifiP2pDevice) {
        meshEngine.connectToPeer(device)
    }

    fun setRecording(isRecording: Boolean, seconds: Int = 0) {
        _isRecordingAudio.value = isRecording
        _recordingTimerSeconds.value = seconds
        val contact = _selectedContact.value
        if (contact != null) {
            val status = if (isRecording) "RECORDING" else "IDLE"
            meshEngine.sendUserStatus(contact.phoneNumber, status)
        }
    }

    fun onUserTyping(text: String) {
        val contact = _selectedContact.value
        if (contact != null) {
            val status = if (text.isNotBlank()) "TYPING" else "IDLE"
            meshEngine.sendUserStatus(contact.phoneNumber, status)
        }
    }

    fun startP2pDiscovery() {
        meshEngine.startP2pDiscovery()
    }

    fun reCreateP2pGroup() {
        meshEngine.reCreateP2pGroup()
    }

    fun sendAudioMessage(durationSeconds: Int) {
        sendAudioVoiceMessage(durationSeconds)
    }

    fun sendUserStatus(status: String) {
        val contact = _selectedContact.value ?: return
        meshEngine.sendUserStatus(contact.phoneNumber, status)
    }

    fun sendUserStatus(phoneNumber: String, status: String) {
        meshEngine.sendUserStatus(phoneNumber, status)
    }

    override fun onCleared() {
        super.onCleared()
        meshEngine.cleanUp()
    }
}
