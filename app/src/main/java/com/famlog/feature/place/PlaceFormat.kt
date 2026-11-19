package com.famlog.feature.place

import androidx.annotation.StringRes
import com.famlog.R
import com.famlog.core.model.PlaceCategory
import com.famlog.feature.chat.SeoulZone
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@StringRes
fun PlaceCategory.labelRes(): Int = when (this) {
    PlaceCategory.RESTAURANT -> R.string.place_category_restaurant
    PlaceCategory.CAFE -> R.string.place_category_cafe
    PlaceCategory.TRAVEL -> R.string.place_category_travel
    PlaceCategory.LIFE -> R.string.place_category_life
    PlaceCategory.ETC -> R.string.place_category_etc
}

/** "서울 송파구", "세종"처럼 시·도와 시·군·구를 붙인다. 둘 다 없으면 null */
fun regionText(region1: String?, region2: String?): String? =
    listOfNotNull(region1, region2).joinToString(" ").takeIf { it.isNotEmpty() }

/** 1km 미만은 m, 이상은 소수 한 자리 km ("850m", "1.2km") */
fun formatDistance(meters: Int): String =
    if (meters < 1_000) "${meters}m" else String.format(Locale.US, "%.1fkm", meters / 1_000.0)

private val sharedDateFormatter = DateTimeFormatter.ofPattern("M/d")
private val sharedDateWithYearFormatter = DateTimeFormatter.ofPattern("yy/M/d")

/** 공유한 날: 올해는 "11/12", 그 전은 "25/11/12" */
fun formatSharedDate(at: Instant, today: LocalDate): String {
    val date = at.atZone(SeoulZone).toLocalDate()
    return if (date.year == today.year) sharedDateFormatter.format(date) else sharedDateWithYearFormatter.format(date)
}
