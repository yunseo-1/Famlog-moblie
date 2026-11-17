package com.famlog.core.data

import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 만 14세 이상 확인 (사양서 4.1 AUTH-02, 8.5). 서버 AgePolicy와 같은 규칙:
 * 생일 날짜를 받지 않으므로 현재 연월이 "출생연월 + 14년"보다 뒤일 때만 허용한다 (14번째 생일이 있는 달은 불가).
 * 앱 판정은 안내용이고, 최종 판정은 서버(403 AGE_RESTRICTED)가 한다.
 */
object AgePolicy {
    const val MINIMUM_AGE = 14
    private val YYYYMM = DateTimeFormatter.ofPattern("uuuuMM")

    /** YYYYMM을 읽는다. 형식이 틀리거나 미래·1900년 이전이면 null */
    fun parse(birthYm: String, now: YearMonth): YearMonth? {
        if (birthYm.length != 6 || !birthYm.all(Char::isDigit)) return null
        val value = try {
            YearMonth.parse(birthYm, YYYYMM)
        } catch (e: DateTimeParseException) {
            return null
        }
        return value.takeIf { it.year >= 1900 && !it.isAfter(now) }
    }

    fun isOldEnough(birth: YearMonth, now: YearMonth): Boolean = now.isAfter(birth.plusYears(MINIMUM_AGE.toLong()))
}
