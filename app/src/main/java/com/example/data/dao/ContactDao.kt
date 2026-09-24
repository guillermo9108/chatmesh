package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY isConnected DESC, lastMessageTime DESC, displayName ASC")
    fun getAllContactsFlow(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts")
    suspend fun getAllContactsList(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE isRegisteredInMesh = 1 OR isConnected = 1 OR lastMessageText IS NOT NULL ORDER BY lastMessageTime DESC")
    fun getChatContactsFlow(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE phoneNumber = :phone LIMIT 1")
    suspend fun getContactByPhone(phone: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE phoneNumber = :phone LIMIT 1")
    fun getContactFlow(phone: String): Flow<ContactEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNotExist(contact: ContactEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(contact: ContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<ContactEntity>)

    @Update
    suspend fun update(contact: ContactEntity)

    @Query("UPDATE contacts SET lastMessageText = :text, lastMessageTime = :time WHERE phoneNumber = :phone")
    suspend fun updateLastMessage(phone: String, text: String, time: Long)

    @Query("UPDATE contacts SET isConnected = :connected, lastSeen = :lastSeen WHERE phoneNumber = :phone")
    suspend fun updateConnectionStatus(phone: String, connected: Boolean, lastSeen: Long)

    @Query("UPDATE contacts SET unreadCount = 0 WHERE phoneNumber = :phone")
    suspend fun markChatAsRead(phone: String)

    @Query("UPDATE contacts SET unreadCount = unreadCount + 1 WHERE phoneNumber = :phone")
    suspend fun incrementUnread(phone: String)
}
