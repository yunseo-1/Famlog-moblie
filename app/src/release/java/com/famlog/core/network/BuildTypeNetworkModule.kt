package com.famlog.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import okhttp3.Interceptor

/** 릴리스 빌드는 네트워크 로그를 남기지 않는다 */
@Module
@InstallIn(SingletonComponent::class)
object BuildTypeNetworkModule {
    @Provides
    @ElementsIntoSet
    @BuildTypeInterceptors
    fun provideInterceptors(): Set<Interceptor> = emptySet()
}
