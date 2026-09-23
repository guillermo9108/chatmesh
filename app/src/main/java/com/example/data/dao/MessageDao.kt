package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("""
        SELECT * FROM messages 
        WHERE (senderPhone = :phone AND recipientPhone = :myPhone) 
           OR (senderPhone = :myPhone AND recipientPhone = :phone)
        ORDER BY timestamp ASC
    """)
    fun getConversationFlow(phone: String, myPhone: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("UPDATE messages SET status = :status WHERE messageUuid = :uuid")
    suspend fun updateStatus(uuid: String, status: String)

    @Query("SELECT * FROM messages WHERE status = 'PENDING' AND isOutgoing = 1")
    suspend fun getPendingMessages(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE recipientPhone = :recipientPhone AND status = 'PENDING' AND isOutgoing = 1")
    suspend fun getPendingMessagesForRecipient(recipientPhone: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE messageUuid = :uuid LIMIT 1")
    suspend fun getMessageByUuid(uuid: String): MessageEntity?

    @Query("UPDATE messages SET status = 'READ' WHERE senderPhone = :senderPhone AND isOutgoing = 0")
    suspend fun markConversationAsRead(senderPhone: String)

    @Query("SELECT COUNT(*) FROM messages WHERE senderPhone = :phone AND isOutgoing = 0 AND status != 'READ'")
    suspend fun getUnreadCount(phone: String): Int
}
