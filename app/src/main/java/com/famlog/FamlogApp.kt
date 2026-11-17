package com.famlog

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.famlog.core.data.ChatSync
import com.kakao.sdk.common.KakaoSdk
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FamlogApp : Application() {

    @Inject lateinit var chatSync: ChatSync

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
}
