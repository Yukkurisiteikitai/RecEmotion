package com.example.recemotion.data.di

import android.content.Context
import com.example.recemotion.data.db.AppDatabase
import com.example.recemotion.data.db.ConversationTopicDao
import com.example.recemotion.data.db.EmotionTimelineDao
import com.example.recemotion.data.db.ThoughtAnalysisDao
import com.example.recemotion.data.db.ThoughtEntryDao
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

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getInstance(context)

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
}
