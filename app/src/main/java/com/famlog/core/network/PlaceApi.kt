package com.famlog.core.network

import com.famlog.core.model.NearbyPlace
import com.famlog.core.model.Place
import com.famlog.core.model.PlaceAlias
import com.famlog.core.model.PlaceCard
import com.famlog.core.model.PlaceCategory
import com.famlog.core.model.PlaceEdit
import com.famlog.core.model.PlaceMarks
import com.famlog.core.model.PlacePage
import com.famlog.core.model.PlaceSearchItem
import com.famlog.core.model.PlaceSearchPage
import com.famlog.core.model.PlaceSummary
import com.famlog.core.model.RegionCount
import com.famlog.core.model.RegionCounts
import com.famlog.core.model.SubRegionCount
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 장소 (PLC-01~05). 검색은 서버가 카카오 로컬을 대신 부른다(키는 서버에만).
 * 다른 그룹 장소 id는 404, 수정·삭제는 공유한 사람 또는 관리자 이상(403 GROUP_ROLE_REQUIRED).
 */
interface PlaceApi {
    /**
     * 한 쪽 15개, page 1~45. [x]=경도·[y]=위도는 함께 보내야 하고 보내면 가까운 순.
     * [groupId]를 주면 그 그룹에 이미 공유된 장소에 sharedPlaceId. 분당 30회 넘으면 429 RATE_LIMITED.
     */
    @GET("places/search")
    suspend fun search(
        @Query("query") query: String,
        @Query("page") page: Int,
        @Query("groupId") groupId: Long?,
        @Query("x") x: Double? = null,
        @Query("y") y: Double? = null,
    ): SearchResponse

    /** 앱은 kakaoPlaceId만 보낸다. 검색 후 1시간이 지나면 400, 같은 장소가 이미 있으면 409 PLACE_ALREADY_SHARED */
    @POST("groups/{groupId}/places")
    suspend fun share(@Path("groupId") groupId: Long, @Body body: SharePlaceRequest): PlaceResponse

    /** 최신 공유 순. 여러 값 필터는 같은 키를 반복한다 (?sharedBy=1&sharedBy=2) */
    @GET("groups/{groupId}/places")
    suspend fun list(
        @Path("groupId") groupId: Long,
        @Query("region1") region1: String?,
        @Query("region2") region2: String?,
        @Query("sharedBy") sharedBy: List<Long>,
        @Query("category") category: List<String>,
        @Query("cursor") cursor: String? = null,
        @Query("size") size: Int? = null,
        /** 지도 영역은 4개를 모두 보내야 한다 (일부만 400) */
        @Query("swLat") swLat: Double? = null,
        @Query("swLng") swLng: Double? = null,
        @Query("neLat") neLat: Double? = null,
        @Query("neLng") neLng: Double? = null,
    ): PlaceListResponse

    /** 지역별 개수(배지). 지역 필터는 받지 않는다 */
    @GET("groups/{groupId}/places/regions")
    suspend fun regions(
        @Path("groupId") groupId: Long,
        @Query("sharedBy") sharedBy: List<Long>,
        @Query("category") category: List<String>,
    ): RegionCountResponse

    /**
     * 내 주변 (PLC-04). 가까운 순 최대 50. 좌표는 서버도 저장하지 않는다.
     * 기능 플래그가 꺼지면 404, 위치 동의가 없으면 403 LOCATION_CONSENT_REQUIRED.
     */
    @GET("groups/{groupId}/places/nearby")
    suspend fun nearby(
        @Path("groupId") groupId: Long,
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radiusKm") radiusKm: Int,
    ): NearbyListResponse

    /** 별칭 장소 (PLC-06). 가나다 순 */
    @GET("groups/{groupId}/place-aliases")
    suspend fun aliases(@Path("groupId") groupId: Long): AliasListResponse

    /** 같은 별칭이 있으면 409 ALIAS_DUPLICATED, placeId가 우리 그룹 장소가 아니면 400 */
    @POST("groups/{groupId}/place-aliases")
    suspend fun createAlias(@Path("groupId") groupId: Long, @Body body: CreateAliasRequest): AliasResponse

    /** 만든 사람 또는 관리자 이상 */
    @DELETE("groups/{groupId}/place-aliases/{aliasId}")
    suspend fun deleteAlias(@Path("groupId") groupId: Long, @Path("aliasId") aliasId: Long)

    @GET("places/{placeId}")
    suspend fun detail(@Path("placeId") placeId: Long): PlaceResponse

    /** 보낸 키만 바뀐다. 별점·한줄평은 null을 보내면 지우고 키를 빼면 그대로라 [JsonObject]로 직접 만든다 */
    @PATCH("places/{placeId}")
    suspend fun update(@Path("placeId") placeId: Long, @Body body: JsonObject): PlaceResponse

    @DELETE("places/{placeId}")
    suspend fun delete(@Path("placeId") placeId: Long)

    /** 여러 번 해도 같다 */
    @PUT("places/{placeId}/marks/{kind}")
    suspend fun mark(@Path("placeId") placeId: Long, @Path("kind") kind: String): PlaceMarksResponse

    @DELETE("places/{placeId}/marks/{kind}")
    suspend fun unmark(@Path("placeId") placeId: Long, @Path("kind") kind: String): PlaceMarksResponse
}

@Serializable
data class SearchItemResponse(
    val kakaoPlaceId: String,
    val name: String,
    val categoryDetail: String? = null,
    val suggestedCategory: String? = null,
    val address: String? = null,
    val region1: String? = null,
    val region2: String? = null,
    val region3: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val phone: String? = null,
    val placeUrl: String? = null,
    val distanceM: Int? = null,
    val sharedPlaceId: Long? = null,
)

@Serializable
data class SearchResponse(val items: List<SearchItemResponse>, val hasMore: Boolean = false)

@Serializable
data class SharePlaceRequest(
    val kakaoPlaceId: String,
    val category: String,
    val oneLineReview: String? = null,
    val rating: Int? = null,
    val shareToChat: Boolean,
)

@Serializable
data class PlaceMarksResponse(val visited: List<Long> = emptyList(), val want: List<Long> = emptyList())

@Serializable
data class PlaceResponse(
    val id: Long,
    val groupId: Long,
    val kakaoPlaceId: String,
    val name: String,
    val category: String,
    val categoryDetail: String? = null,
    val address: String? = null,
    val region1: String? = null,
    val region2: String? = null,
    val region3: String? = null,
    val lat: Double,
    val lng: Double,
    val phone: String? = null,
    val placeUrl: String? = null,
    val rating: Int? = null,
    val oneLineReview: String? = null,
    val sharedBy: Long? = null,
    val marks: PlaceMarksResponse = PlaceMarksResponse(),
    val createdAt: String,
)

@Serializable
data class PlaceListResponse(val items: List<PlaceResponse>, val nextCursor: String? = null)

@Serializable
data class Region2CountResponse(val region2: String? = null, val count: Long)

@Serializable
data class Region1CountResponse(val region1: String, val count: Long, val children: List<Region2CountResponse> = emptyList())

@Serializable
data class RegionCountResponse(val total: Long, val items: List<Region1CountResponse>)

@Serializable
data class NearbyPlaceResponse(val place: PlaceResponse, val distanceM: Int)

@Serializable
data class NearbyListResponse(val items: List<NearbyPlaceResponse>)

@Serializable
data class CreateAliasRequest(val alias: String, val placeId: Long? = null, val addressText: String? = null)

@Serializable
data class AliasResponse(
    val id: Long,
    val alias: String,
    val place: PlaceSummaryResponse? = null,
    val addressText: String? = null,
    val createdBy: Long? = null,
)

@Serializable
data class AliasListResponse(val items: List<AliasResponse>)

@Serializable
data class PlaceSummaryResponse(val id: Long, val name: String, val address: String? = null, val category: String? = null)

fun SearchItemResponse.toModel() = PlaceSearchItem(
    kakaoPlaceId = kakaoPlaceId,
    name = name,
    categoryDetail = categoryDetail,
    suggestedCategory = PlaceCategory.of(suggestedCategory),
    address = address,
    region1 = region1,
    region2 = region2,
    distanceM = distanceM,
    sharedPlaceId = sharedPlaceId,
)

fun SearchResponse.toModel() = PlaceSearchPage(items.map { it.toModel() }, hasMore)

fun PlaceMarksResponse.toModel() = PlaceMarks(visited, want)

fun PlaceResponse.toModel() = Place(
    id = id,
    groupId = groupId,
    kakaoPlaceId = kakaoPlaceId,
    name = name,
    category = PlaceCategory.of(category),
    categoryDetail = categoryDetail,
    address = address,
    region1 = region1,
    region2 = region2,
    region3 = region3,
    lat = lat,
    lng = lng,
    phone = phone,
    placeUrl = placeUrl,
    rating = rating,
    oneLineReview = oneLineReview,
    sharedBy = sharedBy,
    marks = marks.toModel(),
    createdAt = parseServerTime(createdAt),
)

fun PlaceListResponse.toModel() = PlacePage(items.map { it.toModel() }, nextCursor)

fun RegionCountResponse.toModel() = RegionCounts(
    total = total,
    items = items.map { region -> RegionCount(region.region1, region.count, region.children.map { SubRegionCount(it.region2, it.count) }) },
)

fun PlaceSummaryResponse.toModel() = PlaceSummary(id, name, address, PlaceCategory.of(category))

fun NearbyPlaceResponse.toModel() = NearbyPlace(place.toModel(), distanceM)

fun AliasResponse.toModel() = PlaceAlias(id, alias, place?.toModel(), addressText, createdBy)

/** 카테고리·별점·한줄평 전체를 보낸다 (지운 별점·한줄평은 null) */
fun PlaceEdit.toPatch(): JsonObject = buildJsonObject {
    put("category", category.name)
    put("rating", rating?.let(::JsonPrimitive) ?: JsonNull)
    put("oneLineReview", oneLineReview?.let(::JsonPrimitive) ?: JsonNull)
}

/** 채팅 PLACE 메시지 payload {placeId, name, category, address, region1, region2, rating}. 형식이 다르면 null */
fun JsonObject.toPlaceCard(): PlaceCard? {
    fun text(key: String) = (get(key) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
    val placeId = (get("placeId") as? JsonPrimitive)?.longOrNull ?: return null
    val name = text("name") ?: return null
    return PlaceCard(
        placeId = placeId,
        name = name,
        category = PlaceCategory.of(text("category")),
        address = text("address"),
        region1 = text("region1"),
        region2 = text("region2"),
        rating = (get("rating") as? JsonPrimitive)?.intOrNull,
    )
}
