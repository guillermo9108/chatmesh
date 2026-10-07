package com.example.data.repository

import com.example.data.dao.*
import com.example.data.entity.*
import kotlinx.coroutines.flow.Flow

class ChatMeshRepository(
    private val userDao: UserDao,
    private val contactDao: ContactDao,
    private val messageDao: MessageDao,
    private val meshNodeDao: MeshNodeDao,
    private val callDao: CallDao,
    private val storyDao: StoryDao,
    private val storySeenDao: StorySeenDao
) {
    val userProfileFlow: Flow<UserProfile?> = userDao.getUserProfileFlow()
    val allContactsFlow: Flow<List<ContactEntity>> = contactDao.getAllContactsFlow()
    val chatContactsFlow: Flow<List<ContactEntity>> = contactDao.getChatContactsFlow()
    fun getOnlineContactsFlow(activeThreshold: Long = System.currentTimeMillis() - 120_000L): Flow<List<ContactEntity>> =
        contactDao.getOnlineContactsFlow(activeThreshold)
    val allMeshNodesFlow: Flow<List<MeshNodeEntity>> = meshNodeDao.getAllNodesFlow()
    val allCallsFlow: Flow<List<CallEntity>> = callDao.getAllCallsFlow()

    fun getActiveStoriesFlow(now: Long = System.currentTimeMillis()): Flow<List<StoryEntity>> =
        storyDao.getActiveStoriesFlow(now)
    suspend fun saveStory(story: StoryEntity) = storyDao.insertOrUpdate(story)
    suspend fun getStory(storyId: String): StoryEntity? = storyDao.getStoryById(storyId)
    suspend fun getStoriesByAuthor(phone: String, now: Long = System.currentTimeMillis()): List<StoryEntity> =
        storyDao.getStoriesByAuthor(phone, now)
    suspend fun markStoryViewed(storyId: String, at: Long = System.currentTimeMillis()) =
        storyDao.markAsViewed(storyId, at)
    suspend fun deleteExpiredStories(now: Long = System.currentTimeMillis()) =
        storyDao.deleteExpired(now)
    suspend fun deleteStory(storyId: String) {
        storyDao.deleteStory(storyId)
        storySeenDao.deleteForStory(storyId)
    }
    suspend fun saveStorySeen(seen: StorySeenEntity) = storySeenDao.insert(seen)
    fun getStoryViewersFlow(storyId: String): Flow<List<StorySeenEntity>> =
        storySeenDao.getViewersFlow(storyId)

    suspend fun getUserProfile(): UserProfile? = userDao.getUserProfile()
    suspend fun saveUserProfile(profile: UserProfile) = userDao.insertOrUpdate(profile)

    suspend fun getAllContactsList(): List<ContactEntity> = contactDao.getAllContactsList()
    suspend fun getContact(phone: String): ContactEntity? = contactDao.getContactByPhone(phone)
    fun getContactFlow(phone: String): Flow<ContactEntity?> = contactDao.getContactFlow(phone)

    suspend fun insertContact(contact: ContactEntity): Long {
        if (!com.example.mesh.SimDetectionUtil.isValidPhoneNumber(contact.phoneNumber)) {
            return -1L
        }
        return contactDao.insertIfNotExist(contact)
    }

    suspend fun saveContact(contact: ContactEntity) {
        if (!com.example.mesh.SimDetectionUtil.isValidPhoneNumber(contact.phoneNumber)) {
            return
        }
        contactDao.insertOrUpdate(contact)
    }

    suspend fun insertAllContacts(contacts: List<ContactEntity>) {
        val valid = contacts.filter { com.example.mesh.SimDetectionUtil.isValidPhoneNumber(it.phoneNumber) }
        if (valid.isNotEmpty()) {
            contactDao.insertAll(valid)
        }
    }

    suspend fun cleanInvalidContacts() {
        try {
            contactDao.deleteInvalidContacts()
        } catch (_: Exception) {}
    }
    suspend fun updateLastMessage(phone: String, text: String, time: Long) =
        contactDao.updateLastMessage(phone, text, time)

    suspend fun updateConnectionStatus(phone: String, connected: Boolean, lastSeen: Long) =
        contactDao.updateConnectionStatus(phone, connected, lastSeen)

    suspend fun markChatAsRead(phone: String) {
        contactDao.markChatAsRead(phone)
        messageDao.markConversationAsRead(phone)
    }

    fun getConversationFlow(contactPhone: String, myPhone: String): Flow<List<MessageEntity>> =
        messageDao.getConversationFlow(contactPhone, myPhone)

    suspend fun saveMessage(message: MessageEntity): Long {
        val id = messageDao.insertMessage(message)
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

    suspend fun getActiveNodes(): List<MeshNodeEntity> =
        meshNodeDao.getActiveNodes()

    suspend fun saveMeshNode(node: MeshNodeEntity) =
        meshNodeDao.insertOrUpdate(node)

    suspend fun insertAllNodes(nodes: List<MeshNodeEntity>) =
        meshNodeDao.insertAll(nodes)

    suspend fun setNodeInactive(nodeId: String) =
        meshNodeDao.setNodeInactive(nodeId)

    suspend fun pruneOldNodes(olderThan: Long) =
        meshNodeDao.pruneOldNodes(olderThan)

    suspend fun insertCall(call: CallEntity): Long =
        callDao.insertCall(call)

    suspend fun deleteCall(id: Long) =
        callDao.deleteCall(id)
}
