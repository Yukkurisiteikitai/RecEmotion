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
        EmotionTimelineEntity::class,
        TaskEntity::class,
        TaskPhaseEntity::class,
        HypothesisEntity::class,
        CuriosityNodeEntity::class,
        TimeAggregationEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun thoughtEntryDao(): ThoughtEntryDao
    abstract fun thoughtAnalysisDao(): ThoughtAnalysisDao
    abstract fun conversationTopicDao(): ConversationTopicDao
    abstract fun todoDao(): ToDoDao
    abstract fun emotionTimelineDao(): EmotionTimelineDao

    abstract fun taskDao(): TaskDao
    abstract fun taskPhaseDao(): TaskPhaseDao
    abstract fun hypothesisDao(): HypothesisDao
    abstract fun curiosityNodeDao(): CuriosityNodeDao
    abstract fun timeAggregationDao(): TimeAggregationDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "recemotion.db"
                )
                    .fallbackToDestructiveMigration()
                    // TODO: Replace with explicit migrations before production release
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}