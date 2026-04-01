package com.vaultzip.di

import com.vaultzip.storage.ArchiveCandidateScanner
import com.vaultzip.storage.ArchiveSiblingScanner
import com.vaultzip.storage.DocumentTreeArchiveCandidateScanner
import com.vaultzip.storage.DocumentTreeScanner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageModule {

    @Binds
    @Singleton
    abstract fun bindArchiveSiblingScanner(
        impl: DocumentTreeScanner
    ): ArchiveSiblingScanner

    @Binds
    @Singleton
    abstract fun bindArchiveCandidateScanner(
        impl: DocumentTreeArchiveCandidateScanner
    ): ArchiveCandidateScanner
}
