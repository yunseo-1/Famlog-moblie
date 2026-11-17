package com.famlog

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.famlog.core.data.ChatSync
import com.kakao.sdk.common.KakaoSdk
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class FamlogApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject lateinit var chatSync: ChatSync

    @Inject lateinit var workerFactory: HiltWorkerFactory

    /** 인증 헤더·토큰 재발급이 붙은 앱 클라이언트. 사진은 GET /media/{id}/content 로 받는다 */
    @Inject lateinit var okHttpClient: OkHttpClient

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()) {
            KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        }
        // 실시간 채팅은 앱이 화면에 보이는 동안만 연결한다. 백그라운드 알림은 FCM(S11)
        chatSync.start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = chatSync.setForeground(true)
            override fun onStop(owner: LifecycleOwner) = chatSync.setForeground(false)
        })
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient })) }
        .crossfade(true)
        .build()
}
