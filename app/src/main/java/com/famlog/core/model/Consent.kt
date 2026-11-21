package com.famlog.core.model

/**
 * 선택 동의 하나 (사양서 8.2). 앱이 다루는 것은 위치기반서비스(LOCATION)와 AI 분석 안내 확인(AI_ANALYSIS).
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

        /** AI 분석 안내 확인. 서버가 국외 이전 고지(OVERSEAS_TRANSFER)도 함께 기록한다 */
        const val AI_ANALYSIS = "AI_ANALYSIS"

        /** 국외 이전 고지. AI_ANALYSIS와 함께 기록·철회된다 (따로 바꿀 수 없다) */
        const val OVERSEAS_TRANSFER = "OVERSEAS_TRANSFER"
        const val TERMS = "TERMS"
        const val PRIVACY = "PRIVACY"
    }
}
