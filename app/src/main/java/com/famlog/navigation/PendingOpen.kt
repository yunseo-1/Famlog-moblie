package com.famlog.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 알림을 눌러 열 화면 */
sealed interface OpenTarget {
    /** 채팅 알림 → 그 그룹 채팅방 (NOTI-01) */
    data class Chat(val groupId: Long) : OpenTarget

    /** 일정 리마인드 → 그 일정 상세 (SCH-03) */
    data class Schedule(val scheduleId: Long) : OpenTarget

    /** 내보내기 준비 알림 → 내 데이터 내보내기 화면 (ACC-02) */
    data object DataExport : OpenTarget
}

/**
 * 알림을 눌러 열 화면. MainActivity가 넣고, 시작 분기를 마치고 탭 화면에 오면 NavHost가 꺼내 간다.
 * 앱이 꺼져 있었으면 로그인 확인·그룹 목록을 먼저 거쳐야 해서 바로 이동하지 않는다.
 */
@Singleton
class PendingOpen @Inject constructor() {
    private val _target = MutableStateFlow<OpenTarget?>(null)
    val target: StateFlow<OpenTarget?> = _target.asStateFlow()

    fun open(target: OpenTarget) {
        _target.value = target
    }

    fun clear() {
        _target.value = null
    }
}
