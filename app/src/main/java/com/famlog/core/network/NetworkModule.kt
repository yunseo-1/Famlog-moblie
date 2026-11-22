package com.famlog.core.network

import com.famlog.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.HttpUrl.Companion.toHttpUrl
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

/** 채팅 웹소켓 전용 클라이언트. 토큰은 STOMP CONNECT 헤더로 보내므로 Authenticator·AuthInterceptor가 없다 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ChatSocketClient

/** 저장소(S3) Presigned URL로 파일을 올리는 클라이언트. 인증 헤더·로그 없음 (URL에 서명이 들어 있다) */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UploadClient

/** 서버 API 주소 (…/api/v1/) */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiBaseUrl

/** 앱 버전 이름 (X-App-Version, 푸시 토큰 등록) */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppVersion

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
    fun provideAppHeadersInterceptor(@AppVersion appVersion: String): AppHeadersInterceptor =
        AppHeadersInterceptor(appVersion = appVersion)

    @Provides
    @Singleton
    fun provideUpgradeRequiredInterceptor(sessionEvents: SessionEvents): UpgradeRequiredInterceptor =
        UpgradeRequiredInterceptor(sessionEvents)

    @Provides
    @AppVersion
    fun provideAppVersion(): String = BuildConfig.VERSION_NAME

    @Provides
    @Singleton
    @RefreshClient
    fun provideRefreshOkHttpClient(
        appHeaders: AppHeadersInterceptor,
        upgradeRequired: UpgradeRequiredInterceptor,
        @BuildTypeInterceptors buildTypeInterceptors: Set<@JvmSuppressWildcards Interceptor>,
    ): OkHttpClient = baseClientBuilder()
        .addInterceptor(appHeaders)
        .addInterceptor(upgradeRequired)
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
        upgradeRequired: UpgradeRequiredInterceptor,
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
        @BuildTypeInterceptors buildTypeInterceptors: Set<@JvmSuppressWildcards Interceptor>,
    ): OkHttpClient = baseClientBuilder()
        .addInterceptor(appHeaders)
        .addInterceptor(upgradeRequired)
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

    @Provides
    @Singleton
    fun provideChatApi(retrofit: Retrofit): ChatApi = retrofit.create(ChatApi::class.java)

    @Provides
    @Singleton
    fun provideMediaApi(retrofit: Retrofit): MediaApi = retrofit.create(MediaApi::class.java)

    @Provides
    @Singleton
    fun provideDeviceApi(retrofit: Retrofit): DeviceApi = retrofit.create(DeviceApi::class.java)

    @Provides
    @Singleton
    fun provideScheduleApi(retrofit: Retrofit): ScheduleApi = retrofit.create(ScheduleApi::class.java)

    @Provides
    @Singleton
    fun provideTodoApi(retrofit: Retrofit): TodoApi = retrofit.create(TodoApi::class.java)

    @Provides
    @Singleton
    fun providePlaceApi(retrofit: Retrofit): PlaceApi = retrofit.create(PlaceApi::class.java)

    @Provides
    @Singleton
    fun provideConsentApi(retrofit: Retrofit): ConsentApi = retrofit.create(ConsentApi::class.java)

    @Provides
    @Singleton
    fun provideGroupSettingsApi(retrofit: Retrofit): GroupSettingsApi = retrofit.create(GroupSettingsApi::class.java)

    @Provides
    @Singleton
    fun provideReportApi(retrofit: Retrofit): ReportApi = retrofit.create(ReportApi::class.java)

    @Provides
    @Singleton
    fun provideAppConfigApi(retrofit: Retrofit): AppConfigApi = retrofit.create(AppConfigApi::class.java)

    @Provides
    @Singleton
    fun provideDataExportApi(retrofit: Retrofit): DataExportApi = retrofit.create(DataExportApi::class.java)

    @Provides
    @Singleton
    fun provideCandidateApi(retrofit: Retrofit): CandidateApi = retrofit.create(CandidateApi::class.java)

    @Provides
    @ApiBaseUrl
    fun provideApiBaseUrl(): String = BuildConfig.API_BASE_URL

    /** 동영상 500MB까지 올리므로 쓰기 시간 제한을 넉넉히 둔다 */
    @Provides
    @Singleton
    @UploadClient
    fun provideUploadClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .build()

    /** 끊긴 연결은 웹소켓 ping으로 알아챈다. 읽기 시간 제한은 메시지가 뜸한 방에서 끊기지 않게 끈다 */
    @Provides
    @Singleton
    @ChatSocketClient
    fun provideChatSocketClient(@RefreshClient client: OkHttpClient): OkHttpClient = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    /** API 주소(…/api/v1/)와 같은 서버의 /ws. OkHttp는 http(s) 주소로 웹소켓을 연다 */
    @Provides
    @ChatSocketUrl
    fun provideChatSocketUrl(): String =
        BuildConfig.API_BASE_URL.toHttpUrl().newBuilder().encodedPath("/ws").build().toString()

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
