package com.famlog.debug

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** 빌드 타입별 Activity 준비. 디버그: 로컬 서버 접속에 필요한 로컬 네트워크 권한을 요청한다 */
object BuildTypeSetup {
    /** Android 17(API 37) 로컬 네트워크 보호. 없으면 10.0.2.2·사설 IP 연결이 시간 초과로 끝난다 */
    private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
    private const val REQUEST_CODE = 9137

    fun onCreate(activity: Activity) {
        if (Build.VERSION.SDK_INT < 37) return
        if (ContextCompat.checkSelfPermission(activity, ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        ActivityCompat.requestPermissions(activity, arrayOf(ACCESS_LOCAL_NETWORK), REQUEST_CODE)
    }
}
