package com.example.recemotion.data.di

import com.example.recemotion.data.parser.CabochaDependencyParser
import com.example.recemotion.data.parser.DependencyParser
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ParserModule {

    @Binds
    @Singleton
    abstract fun bindDependencyParser(impl: CabochaDependencyParser): DependencyParser
}
