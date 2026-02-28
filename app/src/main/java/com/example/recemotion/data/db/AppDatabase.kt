package com.example.recemotion.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ThoughtEntryEntity::class,
        ThoughtAnalysisEntity::class,
        ConversationTopicEntity::class,
        ToDoEntity::class,
        EmotionTimelineEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun thoughtEntryDao(): ThoughtEntryDao
    abstract fun thoughtAnalysisDao(): ThoughtAnalysisDao
    abstract fun conversationTopicDao(): ConversationTopicDao
    abstract fun todoDao(): ToDoDao
    abstract fun emotionTimelineDao(): EmotionTimelineDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "recemotion.db"
                )
                    // TODO: Add explicit migrations when bumping DB version to prevent data loss
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
