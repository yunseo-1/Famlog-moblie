package com.famlog.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor

/**
 * 디버그 빌드 전용 요청 로그. BASIC 수준이라 메서드·URL·응답 코드만 남고
 * 헤더(토큰)와 본문(메시지 등 개인정보)은 남기지 않는다 (사양서 9.8).
 */
@Module
@InstallIn(SingletonComponent::class)
object BuildTypeNetworkModule {
    @Provides
    @ElementsIntoSet
    @BuildTypeInterceptors
    fun provideInterceptors(): Set<Interceptor> = setOf(
        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
    )
}
