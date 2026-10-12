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
    private val storySeenDao: StorySeenDao,
    private val groupDao: GroupDao,
    private val groupMemberDao: GroupMemberDao
) {
    val userProfileFlow: Flow<UserProfile?> = userDao.getUserProfileFlow()
    val allContactsFlow: Flow<List<ContactEntity>> = contactDao.getAllContactsFlow()
    val chatContactsFlow: Flow<List<ContactEntity>> = contactDao.getChatContactsFlow()
    val allGroupsFlow: Flow<List<GroupEntity>> = groupDao.getAllGroupsFlow()
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

    /**
     * Busca un contacto existente cuyo número sea equivalente al dado
     * usando la lógica de SimDetectionUtil.isMatchingPhone.
     */
    suspend fun findContactMatching(phone: String): ContactEntity? {
        if (!com.example.mesh.SimDetectionUtil.isValidPhoneNumber(phone)) return null
        val all = contactDao.getAllContactsList()
        return all.firstOrNull {
            com.example.mesh.SimDetectionUtil.isMatchingPhone(it.phoneNumber, phone)
        }
    }

    /**
     * Inserta o actualiza un contacto preservando SIEMPRE los campos de conversación
     * (lastMessageTime, lastMessageText, unreadCount, isConnected).
     *
     * Si ya existe un contacto que coincide con isMatchingPhone, fusiona los datos
     * y NO crea un registro nuevo.
     *
     * Si el nuevo número tiene más dígitos que el existente (por ejemplo, +5358182585 vs 58182585),
     * migra la fila al número más completo y actualiza también los mensajes asociados.
     */
    suspend fun saveContactPreservingChat(contact: ContactEntity) {
        if (!com.example.mesh.SimDetectionUtil.isValidPhoneNumber(contact.phoneNumber)) return

        val existing = findContactMatching(contact.phoneNumber)

        if (existing == null) {
            // No hay duplicado, insertar directo
            contactDao.insertOrUpdate(contact)
            return
        }

        // Ya existe un contacto equivalente.
        val newDigits = contact.phoneNumber.filter { it.isDigit() }
        val oldDigits = existing.phoneNumber.filter { it.isDigit() }

        // ¿Debemos migrar al número más largo? (más específico)
        val targetPhone = if (newDigits.length > oldDigits.length) {
            contact.phoneNumber
        } else {
            existing.phoneNumber
        }

        // Fusionar campos
        val mergedName = when {
            contact.displayName.isNotBlank() && contact.displayName != contact.phoneNumber -> contact.displayName
            existing.displayName.isNotBlank() && existing.displayName != existing.phoneNumber -> existing.displayName
            else -> targetPhone
        }

        val mergedAvatar = contact.avatarUri ?: existing.avatarUri
        val mergedRegistered = existing.isRegisteredInMesh || contact.isRegisteredInMesh
        val mergedConnected = existing.isConnected || contact.isConnected

        if (targetPhone != existing.phoneNumber) {
            // Migrar la fila al número más completo
            try {
                // 1. Actualizar mensajes apuntando al viejo número
                messageDao.updateSenderPhone(existing.phoneNumber, targetPhone)
                messageDao.updateRecipientPhone(existing.phoneNumber, targetPhone)

                // 2. Borrar el contacto viejo
                contactDao.deleteContactByPhone(existing.phoneNumber)

                // 3. Insertar con el número nuevo preservando todo
                contactDao.insertOrUpdate(
                    existing.copy(
                        phoneNumber = targetPhone,
                        displayName = mergedName,
                        avatarUri = mergedAvatar,
                        isRegisteredInMesh = mergedRegistered,
                        isConnected = mergedConnected
                    )
                )
            } catch (_: Exception) {
                // Si falla la migración, al menos actualizamos los campos del existente
                contactDao.updateContactProfileOnly(existing.phoneNumber, mergedName, mergedAvatar)
            }
        } else {
            // Mismo número canónico: solo actualizar nombre, avatar y estado
            contactDao.updateContactProfileOnly(existing.phoneNumber, mergedName, mergedAvatar)
            if (mergedConnected && !existing.isConnected) {
                contactDao.updateConnectionStatus(existing.phoneNumber, true, System.currentTimeMillis())
            }
            if (mergedRegistered && !existing.isRegisteredInMesh) {
                contactDao.insertOrUpdate(existing.copy(isRegisteredInMesh = true))
            }
        }
    }

    /**
     * Sincroniza una lista de contactos de la libreta preservando los chats activos.
     * NO destruye lastMessageTime, lastMessageText, unreadCount ni isConnected.
     * Detecta y fusiona duplicados por isMatchingPhone.
     */
    suspend fun insertOrUpdateContactsPreservingChat(contacts: List<ContactEntity>) {
        for (c in contacts) {
            try {
                saveContactPreservingChat(c)
            } catch (_: Exception) {}
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
        if (message.recipientGroupId != null) {
            groupDao.updateLastMessage(message.recipientGroupId, preview, message.timestamp)
            if (!message.isOutgoing) {
                groupDao.incrementUnread(message.recipientGroupId)
            }
        } else {
            val peerPhone = if (message.isOutgoing) message.recipientPhone ?: "" else message.senderPhone
            if (peerPhone.isNotBlank()) {
                contactDao.updateLastMessage(peerPhone, preview, message.timestamp)
                if (!message.isOutgoing) {
                    contactDao.incrementUnread(peerPhone)
                }
            }
        }
        return id
    }

    suspend fun saveGroup(group: GroupEntity) = groupDao.insertOrUpdate(group)
    suspend fun saveGroups(groups: List<GroupEntity>) = groupDao.insertAll(groups)
    suspend fun getGroup(groupId: String): GroupEntity? = groupDao.getGroupById(groupId)
    fun getGroupFlow(groupId: String): Flow<GroupEntity?> = groupDao.getGroupFlow(groupId)
    suspend fun saveGroupMember(member: GroupMemberEntity) = groupMemberDao.insertOrUpdate(member)
    suspend fun saveGroupMembers(members: List<GroupMemberEntity>) = groupMemberDao.insertAll(members)
    fun getGroupMembersFlow(groupId: String): Flow<List<GroupMemberEntity>> = groupMemberDao.getMembersFlow(groupId)
    suspend fun getGroupMembersList(groupId: String): List<GroupMemberEntity> = groupMemberDao.getMembersList(groupId)
    suspend fun removeGroupMember(groupId: String, phone: String) = groupMemberDao.deleteMember(groupId, phone)
    suspend fun deleteAllGroupMembers(groupId: String) = groupMemberDao.deleteAllMembers(groupId)
    fun getGroupConversationFlow(groupId: String): Flow<List<MessageEntity>> = messageDao.getGroupConversationFlow(groupId)
    suspend fun markGroupChatAsRead(groupId: String) = groupDao.markAsRead(groupId)
    suspend fun incrementGroupUnread(groupId: String) = groupDao.incrementUnread(groupId)

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
