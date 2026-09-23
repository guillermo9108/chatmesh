package com.example.data.repository

import com.example.data.db.ChatMeshDatabase
import com.example.data.entity.CallEntity
import com.example.data.entity.ContactEntity
import com.example.data.entity.MeshNodeEntity
import com.example.data.entity.MessageEntity
import com.example.data.entity.UserProfile
import kotlinx.coroutines.flow.Flow

class ChatMeshRepository(private val database: ChatMeshDatabase) {
    private val userDao = database.userDao()
    private val contactDao = database.contactDao()
    private val messageDao = database.messageDao()
    private val meshNodeDao = database.meshNodeDao()
    private val callDao = database.callDao()

    // User profile
    val userProfileFlow: Flow<UserProfile?> = userDao.getUserProfileFlow()
    suspend fun getUserProfile(): UserProfile? = userDao.getUserProfile()
    suspend fun saveUserProfile(profile: UserProfile) = userDao.insertOrUpdate(profile)

    // Contacts
    val allContactsFlow: Flow<List<ContactEntity>> = contactDao.getAllContactsFlow()
    val chatContactsFlow: Flow<List<ContactEntity>> = contactDao.getChatContactsFlow()
    suspend fun getContact(phone: String): ContactEntity? = contactDao.getContactByPhone(phone)
    fun getContactFlow(phone: String): Flow<ContactEntity?> = contactDao.getContactFlow(phone)
    suspend fun insertContact(contact: ContactEntity) = contactDao.insertIfNotExist(contact)
    suspend fun saveContact(contact: ContactEntity) = contactDao.insertOrUpdate(contact)
    suspend fun insertAllContacts(contacts: List<ContactEntity>) = contactDao.insertAll(contacts)
    suspend fun updateLastMessage(phone: String, text: String, time: Long) = contactDao.updateLastMessage(phone, text, time)
    suspend fun markChatAsRead(phone: String) {
        contactDao.markChatAsRead(phone)
        messageDao.markConversationAsRead(phone)
    }

    // Messages
    fun getConversationFlow(contactPhone: String, myPhone: String): Flow<List<MessageEntity>> =
        messageDao.getConversationFlow(contactPhone, myPhone)

    suspend fun saveMessage(message: MessageEntity): Long {
        val id = messageDao.insertMessage(message)
        // Update contact last message
        val preview = when (message.mediaType) {
            "IMAGE" -> "📷 Foto"
            "AUDIO" -> "🎤 Mensaje de voz (${message.audioDurationSeconds}s)"
            "FILE" -> "📎 Archivo"
            else -> message.content
        }
        val peerPhone = if (message.isOutgoing) message.recipientPhone else message.senderPhone
        contactDao.updateLastMessage(peerPhone, preview, message.timestamp)
        if (!message.isOutgoing) {
            contactDao.incrementUnread(peerPhone)
        }
        return id
    }

    suspend fun updateMessageStatus(uuid: String, status: String) =
        messageDao.updateStatus(uuid, status)

    suspend fun getPendingMessages(): List<MessageEntity> =
        messageDao.getPendingMessages()

    suspend fun getPendingMessagesForRecipient(phone: String): List<MessageEntity> =
        messageDao.getPendingMessagesForRecipient(phone)

    suspend fun getMessageByUuid(uuid: String): MessageEntity? =
        messageDao.getMessageByUuid(uuid)

    // Mesh Nodes
    val allMeshNodesFlow: Flow<List<MeshNodeEntity>> = meshNodeDao.getAllNodesFlow()
    suspend fun getActiveNodes(): List<MeshNodeEntity> = meshNodeDao.getActiveNodes()
    suspend fun saveMeshNode(node: MeshNodeEntity) = meshNodeDao.insertOrUpdate(node)
    suspend fun insertAllNodes(nodes: List<MeshNodeEntity>) = meshNodeDao.insertAll(nodes)
    suspend fun setNodeInactive(nodeId: String) = meshNodeDao.setNodeInactive(nodeId)
    suspend fun pruneOldNodes(olderThan: Long) = meshNodeDao.pruneOldNodes(olderThan)

    // Calls
    val allCallsFlow: Flow<List<CallEntity>> = callDao.getAllCallsFlow()
    suspend fun insertCall(call: CallEntity): Long = callDao.insertCall(call)
    suspend fun deleteCall(id: Long) = callDao.deleteCall(id)
}
