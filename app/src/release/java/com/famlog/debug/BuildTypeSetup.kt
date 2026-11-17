package com.famlog.debug

import android.app.Activity

/** 릴리스는 공인 서버만 쓰므로 할 일이 없다 (디버그 구현은 src/debug) */
object BuildTypeSetup {
    @Suppress("UNUSED_PARAMETER")
    fun onCreate(activity: Activity) = Unit
}
