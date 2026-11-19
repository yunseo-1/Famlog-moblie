package com.famlog.core.model

/**
 * 선택 동의 하나 (사양서 8.2). 지금 앱이 다루는 것은 위치기반서비스(LOCATION)뿐이다.
 * [currentVersion]이 null이면 아직 받지 않는 동의. [reconsentRequired]면 예전 버전에 동의한 상태라 다시 받아야 한다.
 */
data class Consent(
    val type: String,
    val agreed: Boolean,
    val docVersion: String?,
    val currentVersion: String?,
    val reconsentRequired: Boolean,
) {
    /** 지금 버전에 동의한 상태 */
    val valid: Boolean get() = agreed && !reconsentRequired

    companion object {
        const val LOCATION = "LOCATION"
    }
}
