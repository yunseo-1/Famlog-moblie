package com.famlog.core.common

import android.os.Build
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId
import javax.inject.Qualifier

/** 로그인 요청 device.deviceName. 기기 관리 화면(SCR-80)에 보인다 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DeviceName

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /** 날짜 판단(만 14세 확인 등)은 서울 기준 (CLAUDE.md 3장) */
    @Provides
    fun provideClock(): Clock = Clock.system(ZoneId.of("Asia/Seoul"))

    @Provides
    @DeviceName
    fun provideDeviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".take(100)
}
