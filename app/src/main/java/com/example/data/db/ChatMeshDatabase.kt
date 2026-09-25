package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.*
import com.example.data.entity.*

@Database(
    entities = [
        MessageEntity::class,
        ContactEntity::class,
        MeshNodeEntity::class,
        UserProfile::class,
        CallEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ChatMeshDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun contactDao(): ContactDao
    abstract fun meshNodeDao(): MeshNodeDao
    abstract fun userDao(): UserDao
    abstract fun callDao(): CallDao

    companion object {
        @Volatile
        private var INSTANCE: ChatMeshDatabase? = null

        fun getDatabase(context: Context): ChatMeshDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatMeshDatabase::class.java,
                    "chatmesh_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
