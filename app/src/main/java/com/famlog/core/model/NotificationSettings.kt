package com.famlog.core.model

import java.time.LocalTime

/** 알림 설정 (SET-02). 모든 그룹에 적용. 방해 금지 시각은 서울 시각이고 자정을 넘겨도 된다 */
data class NotificationSettings(
    val previewHidden: Boolean = false,
    val quietHoursEnabled: Boolean = false,
    val quietStart: LocalTime = LocalTime.of(22, 0),
    val quietEnd: LocalTime = LocalTime.of(7, 0),
)
