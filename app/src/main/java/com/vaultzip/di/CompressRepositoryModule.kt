package com.vaultzip.di

import com.vaultzip.compress.CompressRepository
import com.vaultzip.compress.CompressRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CompressRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCompressRepository(
        impl: CompressRepositoryImpl
    ): CompressRepository
}
