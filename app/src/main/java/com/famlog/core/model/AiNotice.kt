package com.famlog.core.model

/**
 * AI 분석 안내 (SET-01, 사양서 8.3 국외 이전 고지). 서버 설정이라 문구가 바뀔 수 있고, 바뀌면 [version]이 오른다.
 * 관리자가 켤 때와 구성원이 처음 볼 때 같은 내용을 보여 준다.
 */
data class AiNotice(
    val version: String,
    val recipient: String,
    val country: String,
    val sentItems: List<String>,
    val notSentItems: List<String>,
    val purpose: String,
    val retention: String,
    val refusal: String,
)

/**
 * 그룹 AI 분석 설정.
 * [noticePending]: 켜져 있는데 내가 현재 버전 안내를 확인하지 않았다 (사람 단위. 이 그룹에서 분석 제외했으면 서버가 false로 준다)
 */
data class GroupAiSettings(
    val enabled: Boolean,
    val noticeVersion: String?,
    val noticePending: Boolean,
)
