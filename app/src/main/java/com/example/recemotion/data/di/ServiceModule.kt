package com.example.recemotion.data.di

import com.example.recemotion.data.llm.LLMInferenceServiceImpl
import com.example.recemotion.data.llm.ThoughtAnalysisJsonParser
import com.example.recemotion.data.llm.ThoughtPromptBuilder
import com.example.recemotion.data.parser.LogicalFlowAnalyzerImpl
import com.example.recemotion.data.parser.ResourceCheckerImpl
import com.example.recemotion.data.parser.ThoughtStructureParserAdapter
import com.example.recemotion.data.parser.TopicChangeDetectorImpl
import com.example.recemotion.data.serialization.ThoughtStructureJsonAdapter
import com.example.recemotion.domain.service.IPromptBuilder
import com.example.recemotion.domain.service.IResourceChecker
import com.example.recemotion.domain.service.IThoughtJsonParser
import com.example.recemotion.domain.service.IThoughtStructureParser
import com.example.recemotion.domain.service.IThoughtStructureSerializer
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

    @Binds @Singleton
    abstract fun bindLLMInferenceService(impl: LLMInferenceServiceImpl): LLMInferenceService

    @Binds @Singleton
    abstract fun bindLogicalFlowService(impl: LogicalFlowAnalyzerImpl): LogicalFlowService

    @Binds @Singleton
    abstract fun bindTopicChangeService(impl: TopicChangeDetectorImpl): TopicChangeService

    @Binds @Singleton
    abstract fun bindThoughtStructureParser(impl: ThoughtStructureParserAdapter): IThoughtStructureParser

    @Binds @Singleton
    abstract fun bindPromptBuilder(impl: ThoughtPromptBuilder): IPromptBuilder

    @Binds @Singleton
    abstract fun bindThoughtJsonParser(impl: ThoughtAnalysisJsonParser): IThoughtJsonParser

    @Binds @Singleton
    abstract fun bindThoughtStructureSerializer(impl: ThoughtStructureJsonAdapter): IThoughtStructureSerializer

    @Binds @Singleton
    abstract fun bindResourceChecker(impl: ResourceCheckerImpl): IResourceChecker
}
