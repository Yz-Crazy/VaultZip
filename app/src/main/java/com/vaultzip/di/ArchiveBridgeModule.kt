package com.vaultzip.di

import com.vaultzip.archive.bridge.NativeArchiveBridge
import com.vaultzip.archive.bridge.NativeArchiveBridgeImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ArchiveBridgeModule {

    @Binds
    @Singleton
    abstract fun bindNativeArchiveBridge(
        impl: NativeArchiveBridgeImpl
    ): NativeArchiveBridge
}
