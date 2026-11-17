package com.famlog.core.network

import com.famlog.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** 재발급 전용 클라이언트 (Authenticator 없음). 재발급 요청이 다시 재발급을 부르지 않게 분리한다 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RefreshClient

/** 빌드 타입별로 추가하는 인터셉터 (디버그: 로그). src/debug, src/release 의 모듈이 제공한다 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BuildTypeInterceptors

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideAppHeadersInterceptor(): AppHeadersInterceptor =
        AppHeadersInterceptor(appVersion = BuildConfig.VERSION_NAME)

    @Provides
    @Singleton
    @RefreshClient
    fun provideRefreshOkHttpClient(
        appHeaders: AppHeadersInterceptor,
        @BuildTypeInterceptors buildTypeInterceptors: Set<@JvmSuppressWildcards Interceptor>,
    ): OkHttpClient = baseClientBuilder()
        .addInterceptor(appHeaders)
        .apply { buildTypeInterceptors.forEach(::addInterceptor) }
        .build()

    @Provides
    @Singleton
    @RefreshClient
    fun provideRefreshAuthApi(@RefreshClient client: OkHttpClient, json: Json): AuthApi =
        retrofit(client, json).create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        appHeaders: AppHeadersInterceptor,
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
        @BuildTypeInterceptors buildTypeInterceptors: Set<@JvmSuppressWildcards Interceptor>,
    ): OkHttpClient = baseClientBuilder()
        .addInterceptor(appHeaders)
        .addInterceptor(authInterceptor)
        .apply { buildTypeInterceptors.forEach(::addInterceptor) }
        .authenticator(tokenAuthenticator)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = retrofit(client, json)

    @Provides
    @Singleton
    fun provideAccountApi(retrofit: Retrofit): AccountApi = retrofit.create(AccountApi::class.java)

    @Provides
    @Singleton
    fun provideGroupApi(retrofit: Retrofit): GroupApi = retrofit.create(GroupApi::class.java)

    private fun baseClientBuilder() = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)

    private fun retrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}
