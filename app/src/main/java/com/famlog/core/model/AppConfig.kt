package com.famlog.core.model

/**
 * 앱 설정 (GET /app/config, SCR-00). 앱 시작 때 받는다.
 * [noticeUpdatedAt]은 공지 문구가 바뀔 때만 새 값이라, 이 값으로 이미 본 공지를 다시 띄우지 않는다.
 */
data class AppConfig(
    val minSupportedVersion: String?,
    val latestVersion: String?,
    val notice: String?,
    val noticeUpdatedAt: String?,
    val features: AppFeatures = AppFeatures(),
)

/** 서버 기능 스위치. 꺼진 기능은 서버가 막고(내 주변 404) 앱은 화면에서 숨긴다 */
data class AppFeatures(
    val nearby: Boolean = true,
    val photoLocation: Boolean = false,
    val aiAnalysis: Boolean = true,
)

/** 앱 버전 비교 (서버와 같은 규칙): 점으로 나눈 숫자를 자리별로, 빠진 자리는 0, 꼬리(-beta, +3)는 무시, 1~4자리 */
object AppVersions {
    private val pattern = Regex("""^(\d+(?:\.\d+){0,3})(?:[-+].*)?$""")

    /** [a]가 [b]보다 낮으면 음수, 같으면 0, 높으면 양수. 숫자 형식이 아니면 null (비교하지 않는다) */
    fun compare(a: String, b: String): Int? {
        val left = parse(a) ?: return null
        val right = parse(b) ?: return null
        for (i in 0 until maxOf(left.size, right.size)) {
            val diff = left.getOrElse(i) { 0 }.compareTo(right.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    fun isLower(version: String, than: String?): Boolean = than != null && (compare(version, than) ?: 0) < 0

    private fun parse(value: String): List<Long>? =
        pattern.matchEntire(value.trim())?.groupValues?.get(1)?.split('.')?.map { it.toLongOrNull() ?: return null }
}
