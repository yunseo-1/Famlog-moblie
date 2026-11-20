package com.famlog.core.model

import java.time.Instant

/** 장소 카테고리 (PLC-01). 서버 값 그대로. 모르는 값은 [ETC] */
enum class PlaceCategory {
    RESTAURANT, CAFE, TRAVEL, LIFE, ETC;

    companion object {
        fun of(value: String?): PlaceCategory = entries.firstOrNull { it.name == value } ?: ETC
    }
}

/** 다녀옴·가고 싶어요 (PLC-05) */
enum class PlaceMarkKind { VISITED, WANT }

/** 다녀옴·가고 싶어요를 누른 구성원 ID */
data class PlaceMarks(val visited: List<Long> = emptyList(), val want: List<Long> = emptyList()) {
    fun of(kind: PlaceMarkKind): List<Long> = if (kind == PlaceMarkKind.VISITED) visited else want
}

/**
 * 그룹에 공유된 장소 (PLC-01). [region2]는 시·군·구가 없는 곳(세종)이면 null.
 * 일반구가 있는 시는 "수원시 영통구"처럼 시와 구를 함께 쓴다.
 */
data class Place(
    val id: Long,
    val groupId: Long,
    val kakaoPlaceId: String,
    val name: String,
    val category: PlaceCategory,
    val categoryDetail: String?,
    val address: String?,
    val region1: String?,
    val region2: String?,
    val region3: String?,
    val lat: Double,
    val lng: Double,
    val phone: String?,
    val placeUrl: String?,
    val rating: Int?,
    val oneLineReview: String?,
    val sharedBy: Long?,
    val marks: PlaceMarks,
    val createdAt: Instant,
    /** 채팅에서 등록한 장소면 원문 (ARC-06) */
    val source: ItemSource? = null,
)

/** 장소 목록 한 페이지. [nextCursor]가 있으면 더 있다 */
data class PlacePage(val items: List<Place>, val nextCursor: String?)

/**
 * 장소 목록 필터 (PLC-02·03·04). 모두 함께 쓴다. [region2]는 [region1]이 있을 때만.
 * [bounds]는 지도 화면 영역 (지도 탭에서만)
 */
data class PlaceFilter(
    val region1: String? = null,
    val region2: String? = null,
    val sharedBy: Set<Long> = emptySet(),
    val categories: Set<PlaceCategory> = emptySet(),
    val bounds: MapBounds? = null,
)

/** 지도 화면 영역 (남서·북동 꼭짓점) */
data class MapBounds(val swLat: Double, val swLng: Double, val neLat: Double, val neLng: Double)

/**
 * 위도·경도. 내 위치일 때는 요청 처리에만 쓰고 저장·로그에 남기지 않는다 (사양서 8.6).
 * toString은 값을 숨긴다.
 */
data class Coordinates(val lat: Double, val lng: Double) {
    override fun toString(): String = "Coordinates(…)"
}

/** 내 주변 장소 하나와 거리 (PLC-04) */
data class NearbyPlace(val place: Place, val distanceM: Int)

/**
 * 그룹 별칭 장소 (PLC-06). "할머니 댁"처럼 검색이 안 되는 곳. 우리 그룹 장소([place])에 연결하거나
 * 주소 글([addressText])만 남길 수 있다. 연결한 장소가 지워지면 [place]는 null.
 */
data class PlaceAlias(
    val id: Long,
    val alias: String,
    val place: PlaceSummary?,
    val addressText: String?,
    val createdBy: Long?,
)

/** 카카오 검색 결과 하나. [sharedPlaceId]가 있으면 이미 우리 그룹에 공유된 장소 */
data class PlaceSearchItem(
    val kakaoPlaceId: String,
    val name: String,
    val categoryDetail: String?,
    val suggestedCategory: PlaceCategory,
    val address: String?,
    val region1: String?,
    val region2: String?,
    val distanceM: Int?,
    val sharedPlaceId: Long?,
)

data class PlaceSearchPage(val items: List<PlaceSearchItem>, val hasMore: Boolean)

/** 공유할 때 입력. 별점 1~5와 한줄평은 선택 */
data class PlaceShare(
    val kakaoPlaceId: String,
    val category: PlaceCategory,
    val rating: Int?,
    val oneLineReview: String?,
    val shareToChat: Boolean,
)

/** 카테고리·별점·한줄평 고치기. 별점·한줄평을 null로 보내면 지운다 */
data class PlaceEdit(val category: PlaceCategory, val rating: Int?, val oneLineReview: String?)

/** 시·도별 장소 수와 그 안의 시·군·구별 수 (PLC-02 배지). 세종처럼 시·군·구가 없으면 region2 null */
data class RegionCount(val region1: String, val count: Long, val children: List<SubRegionCount>)

data class SubRegionCount(val region2: String?, val count: Long)

data class RegionCounts(val total: Long, val items: List<RegionCount>)

/** 일정·별칭에 붙는 장소 요약 */
data class PlaceSummary(val id: Long, val name: String, val address: String?, val category: PlaceCategory)

/**
 * 채팅방 장소 카드(type=PLACE)의 내용. 공유한 순간의 값이라 지금 장소와 다를 수 있고,
 * 장소가 지워졌으면 상세에서 404가 난다.
 */
data class PlaceCard(
    val placeId: Long,
    val name: String,
    val category: PlaceCategory,
    val address: String?,
    val region1: String?,
    val region2: String?,
    val rating: Int?,
)
