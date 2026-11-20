package com.famlog.core.network

import com.famlog.core.model.AiNotice
import com.famlog.core.model.GroupAiSettings
import com.famlog.testing.testJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** AI 분석 안내·그룹 설정·구성원 분석 제외 (SET-01) */
class GroupSettingsParsingTest {

    @Test
    fun 안내_내용과_그룹_설정을_읽는다() {
        val notice = testJson.decodeFromString<AiNoticeResponse>(
            """{"version":"1.0","recipient":"Anthropic","country":"미국","sentItems":["채팅 본문","보낸 시각"],
               "notSentItems":["사진"],"purpose":"등록 후보 만들기","retention":"30일","refusal":"설정에서 제외"}""",
        ).toModel()
        val settings = testJson.decodeFromString<GroupSettingsResponse>(
            """{"aiAnalysisEnabled":true,"aiEnabledAt":"2026-11-20T10:00:00+09:00","aiNoticeVersion":"1.0","aiNoticePending":true}""",
        ).toModel()

        assertEquals(
            AiNotice("1.0", "Anthropic", "미국", listOf("채팅 본문", "보낸 시각"), listOf("사진"), "등록 후보 만들기", "30일", "설정에서 제외"),
            notice,
        )
        assertEquals(GroupAiSettings(enabled = true, noticeVersion = "1.0", noticePending = true), settings)
    }

    @Test
    fun 구성원_분석_제외는_관리자에게만_값이_오고_아니면_null() {
        val admin = testJson.decodeFromString<MemberResponse>("""{"userId":2,"nickname":"엄마","role":"MEMBER","aiOptOut":true}""").toModel()
        val member = testJson.decodeFromString<MemberResponse>("""{"userId":2,"nickname":"엄마","role":"MEMBER","aiOptOut":null}""").toModel()

        assertEquals(true, admin.aiOptOut)
        assertNull(member.aiOptOut)
    }

    @Test
    fun 끌_때는_안내_버전을_보내지_않는다() {
        assertEquals("""{"aiAnalysisEnabled":false}""", testJson.encodeToString(UpdateGroupSettingsRequest.serializer(), UpdateGroupSettingsRequest(false)))
        assertEquals(
            """{"aiAnalysisEnabled":true,"aiNoticeVersion":"1.0"}""",
            testJson.encodeToString(UpdateGroupSettingsRequest.serializer(), UpdateGroupSettingsRequest(true, "1.0")),
        )
        assertEquals("""{"aiOptOut":true}""", testJson.encodeToString(GroupPreferencesRequest.serializer(), GroupPreferencesRequest(aiOptOut = true)))
    }
}
