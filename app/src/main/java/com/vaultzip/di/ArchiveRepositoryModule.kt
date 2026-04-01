package com.vaultzip.di

import com.vaultzip.archive.data.ArchiveRepository
import com.vaultzip.archive.data.ArchiveRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ArchiveRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindArchiveRepository(
        impl: ArchiveRepositoryImpl
    ): ArchiveRepository
}
