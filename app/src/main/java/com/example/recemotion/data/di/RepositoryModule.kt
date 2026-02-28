package com.example.recemotion.data.di

import com.example.recemotion.data.repository.EmotionRepositoryImpl
import com.example.recemotion.data.repository.ThoughtRepositoryImpl
import com.example.recemotion.domain.repository.EmotionRepository
import com.example.recemotion.domain.repository.ThoughtRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindThoughtRepository(impl: ThoughtRepositoryImpl): ThoughtRepository

    @Binds @Singleton
    abstract fun bindEmotionRepository(impl: EmotionRepositoryImpl): EmotionRepository
}
