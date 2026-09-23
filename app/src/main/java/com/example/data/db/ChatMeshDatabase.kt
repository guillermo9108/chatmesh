package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.CallDao
import com.example.data.dao.ContactDao
import com.example.data.dao.MeshNodeDao
import com.example.data.dao.MessageDao
import com.example.data.dao.UserDao
import com.example.data.entity.CallEntity
import com.example.data.entity.ContactEntity
import com.example.data.entity.MeshNodeEntity
import com.example.data.entity.MessageEntity
import com.example.data.entity.UserProfile

@Database(
    entities = [
        UserProfile::class,
        ContactEntity::class,
        MessageEntity::class,
        MeshNodeEntity::class,
        CallEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ChatMeshDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun contactDao(): ContactDao
    abstract fun messageDao(): MessageDao
    abstract fun meshNodeDao(): MeshNodeDao
    abstract fun callDao(): CallDao

    companion object {
        @Volatile
        private var INSTANCE: ChatMeshDatabase? = null

        fun getInstance(context: Context): ChatMeshDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatMeshDatabase::class.java,
                    "chatmesh_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
