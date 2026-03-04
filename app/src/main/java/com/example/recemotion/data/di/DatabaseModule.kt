package com.example.recemotion.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.recemotion.data.db.AppDatabase
import com.example.recemotion.data.db.ConversationTopicDao
import com.example.recemotion.data.db.CuriosityNodeDao
import com.example.recemotion.data.db.EmotionTimelineDao
import com.example.recemotion.data.db.HypothesisDao
import com.example.recemotion.data.db.TaskDao
import com.example.recemotion.data.db.TaskPhaseDao
import com.example.recemotion.data.db.ThoughtAnalysisDao
import com.example.recemotion.data.db.ThoughtEntryDao
import com.example.recemotion.data.db.TimeAggregationDao
import com.example.recemotion.data.db.ToDoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Create tasks table
            database.execSQL(
                """CREATE TABLE IF NOT EXISTS tasks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    description TEXT NOT NULL,
                    importance INTEGER NOT NULL,
                    urgency INTEGER NOT NULL,
                    scope INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    target_completion_date INTEGER,
                    current_phase TEXT NOT NULL,
                    status TEXT NOT NULL,
                    actual_minutes INTEGER NOT NULL
                )""".trimIndent()
            )

            // Create task_phases table
            database.execSQL(
                """CREATE TABLE IF NOT EXISTS task_phases (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    task_id INTEGER NOT NULL,
                    phase_type TEXT NOT NULL,
                    status TEXT NOT NULL,
                    start_time INTEGER,
                    end_time INTEGER,
                    notes TEXT,
                    phase_order INTEGER NOT NULL,
                    FOREIGN KEY(task_id) REFERENCES tasks(id) ON DELETE CASCADE
                )""".trimIndent()
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS index_task_phases_task_id ON task_phases(task_id)")

            // Create hypotheses table
            database.execSQL(
                """CREATE TABLE IF NOT EXISTS hypotheses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    phase_id INTEGER NOT NULL,
                    hypothesis TEXT NOT NULL,
                    expected_outcome TEXT NOT NULL,
                    actual_outcome TEXT,
                    gap_analysis TEXT,
                    FOREIGN KEY(phase_id) REFERENCES task_phases(id) ON DELETE CASCADE
                )""".trimIndent()
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS index_hypotheses_phase_id ON hypotheses(phase_id)")

            // Create curiosity_nodes table
            database.execSQL(
                """CREATE TABLE IF NOT EXISTS curiosity_nodes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    parent_phase_id INTEGER NOT NULL,
                    question TEXT NOT NULL,
                    relevance INTEGER NOT NULL,
                    priority INTEGER NOT NULL,
                    depth INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    FOREIGN KEY(parent_phase_id) REFERENCES task_phases(id) ON DELETE CASCADE
                )""".trimIndent()
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS index_curiosity_nodes_parent_phase_id ON curiosity_nodes(parent_phase_id)")

            // Create time_aggregations table
            database.execSQL(
                """CREATE TABLE IF NOT EXISTS time_aggregations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    task_id INTEGER NOT NULL,
                    total_seconds INTEGER NOT NULL,
                    planned_seconds INTEGER NOT NULL,
                    variance INTEGER NOT NULL,
                    efficiency REAL NOT NULL,
                    FOREIGN KEY(task_id) REFERENCES tasks(id) ON DELETE CASCADE
                )""".trimIndent()
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS index_time_aggregations_task_id ON time_aggregations(task_id)")
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "recemotion.db"
        )
            .addMigrations(MIGRATION_4_5)
            .build()

    @Provides @Singleton
    fun provideThoughtEntryDao(db: AppDatabase): ThoughtEntryDao = db.thoughtEntryDao()

    @Provides @Singleton
    fun provideThoughtAnalysisDao(db: AppDatabase): ThoughtAnalysisDao = db.thoughtAnalysisDao()

    @Provides @Singleton
    fun provideConversationTopicDao(db: AppDatabase): ConversationTopicDao = db.conversationTopicDao()

    @Provides @Singleton
    fun provideToDoDao(db: AppDatabase): ToDoDao = db.todoDao()

    @Provides @Singleton
    fun provideEmotionTimelineDao(db: AppDatabase): EmotionTimelineDao = db.emotionTimelineDao()

    @Provides @Singleton
    fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()

    @Provides @Singleton
    fun provideTaskPhaseDao(db: AppDatabase): TaskPhaseDao = db.taskPhaseDao()

    @Provides @Singleton
    fun provideHypothesisDao(db: AppDatabase): HypothesisDao = db.hypothesisDao()

    @Provides @Singleton
    fun provideCuriosityNodeDao(db: AppDatabase): CuriosityNodeDao = db.curiosityNodeDao()

    @Provides @Singleton
    fun provideTimeAggregationDao(db: AppDatabase): TimeAggregationDao = db.timeAggregationDao()
}