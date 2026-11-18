package com.famlog.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 채팅 알림을 눌러 열 그룹 (NOTI-01). MainActivity가 넣고, 시작 분기를 마치고 탭 화면에 오면 NavHost가 꺼내 간다.
 * 앱이 꺼져 있었으면 로그인 확인·그룹 목록을 먼저 거쳐야 해서 바로 이동하지 않는다.
 */
@Singleton
class PendingChatOpen @Inject constructor() {
    private val _groupId = MutableStateFlow<Long?>(null)
    val groupId: StateFlow<Long?> = _groupId.asStateFlow()

    fun open(groupId: Long) {
        _groupId.value = groupId
    }

    fun clear() {
        _groupId.value = null
    }
}
