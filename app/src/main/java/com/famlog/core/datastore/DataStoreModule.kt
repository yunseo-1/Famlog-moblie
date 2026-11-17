package com.famlog.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthDataStore

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

@Module
@InstallIn(SingletonComponent::class)
abstract class DataStoreModule {

    @Binds
    abstract fun bindTokenStore(impl: DataStoreTokenStore): TokenStore

    @Binds
    abstract fun bindTokenCipher(impl: KeystoreTokenCipher): TokenCipher

    companion object {
        @Provides
        @Singleton
        @AuthDataStore
        fun provideAuthDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            context.authDataStore
    }
}
