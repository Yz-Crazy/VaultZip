package com.vaultzip.di

import com.vaultzip.session.InMemoryPasswordSessionStore
import com.vaultzip.session.PasswordSessionStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionModule {

    @Binds
    @Singleton
    abstract fun bindPasswordSessionStore(
        impl: InMemoryPasswordSessionStore
    ): PasswordSessionStore
}
