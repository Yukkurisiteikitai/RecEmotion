package com.example.recemotion.data.di

import com.example.recemotion.data.llm.LLMInferenceServiceImpl
import com.example.recemotion.data.parser.LogicalFlowAnalyzerImpl
import com.example.recemotion.data.parser.TopicChangeDetectorImpl
import com.example.recemotion.domain.service.LLMInferenceService
import com.example.recemotion.domain.service.LogicalFlowService
import com.example.recemotion.domain.service.TopicChangeService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {

    @Binds
    @Singleton
    abstract fun bindLLMInferenceService(impl: LLMInferenceServiceImpl): LLMInferenceService

    @Binds
    @Singleton
    abstract fun bindLogicalFlowService(impl: LogicalFlowAnalyzerImpl): LogicalFlowService

    @Binds
    @Singleton
    abstract fun bindTopicChangeService(impl: TopicChangeDetectorImpl): TopicChangeService
}
