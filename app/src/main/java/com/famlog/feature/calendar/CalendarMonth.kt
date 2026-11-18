package com.famlog.feature.calendar

import com.famlog.core.model.Member
import com.famlog.core.model.Occurrence
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** 월 격자: 일요일 시작, 그 달이 걸친 주만큼 (4~6행 × 7칸). Figma 03 캘린더 */
fun monthGrid(month: YearMonth): List<LocalDate> {
    val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val last = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
    return generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
}

/** [전체]면 모든 일정, 구성원을 고르면 그 사람이 참여자인 일정만 (SCH-02, 고른 사람끼리는 합집합) */
fun List<Occurrence>.filterByMembers(selected: Set<Long>): List<Occurrence> =
    if (selected.isEmpty()) this else filter { occurrence -> occurrence.participantIds.any { it in selected } }

/** 그날의 일정: 종일 먼저, 그다음 시작 순 */
fun List<Occurrence>.on(date: LocalDate): List<Occurrence> =
    filter { it.covers(date) }.sortedWith(compareBy({ !it.allDay }, { it.startsAt }, { it.title }))

/**
 * 날짜 칸에 찍을 구성원 점. 그날 일정의 참여자를 구성원 순서대로, 많으면 [max]명까지.
 * 구성원을 골랐으면 고른 사람의 점만 보인다.
 */
fun List<Occurrence>.dotMembers(date: LocalDate, members: List<Member>, selected: Set<Long>, max: Int = 4): List<Member> {
    val ids = on(date).flatMap { it.participantIds }.toSet()
    return members.filter { it.userId in ids && (selected.isEmpty() || it.userId in selected) }.take(max)
}

/** 목록 보기: 그 달의 날짜별 일정 (여러 날 일정은 걸친 날마다). 일정이 없는 날은 뺀다 */
fun List<Occurrence>.sectionsIn(month: YearMonth): List<Pair<LocalDate, List<Occurrence>>> =
    generateSequence(month.atDay(1)) { it.plusDays(1) }
        .takeWhile { YearMonth.from(it) == month }
        .map { it to on(it) }
        .filter { it.second.isNotEmpty() }
        .toList()

/**
 * 받침이 있으면 "은", 없으면 "는" ("아빠는", "동생은"). 한글이 아니면 "은(는)".
 * 겹침 안내 "아빠는 이 시간에 ○○ 일정이 있어요" (SCH-05)
 */
fun topicParticle(word: String): String {
    val last = word.lastOrNull() ?: return "은(는)"
    if (last !in '가'..'힣') return "은(는)"
    return if ((last - '가') % 28 == 0) "는" else "은"
}
