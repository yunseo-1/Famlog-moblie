package com.famlog.core.data

import com.famlog.core.model.ApiResult
import com.famlog.core.model.Coordinates
import com.famlog.core.model.NearbyPlace
import com.famlog.core.model.Place
import com.famlog.core.model.PlaceAlias
import com.famlog.core.model.PlaceEdit
import com.famlog.core.model.PlaceFilter
import com.famlog.core.model.PlaceMarkKind
import com.famlog.core.model.PlaceMarks
import com.famlog.core.model.PlacePage
import com.famlog.core.model.PlaceSearchPage
import com.famlog.core.model.PlaceShare
import com.famlog.core.model.RegionCounts
import com.famlog.core.network.ApiCaller
import com.famlog.core.network.CreateAliasRequest
import com.famlog.core.network.PlaceApi
import com.famlog.core.network.SharePlaceRequest
import com.famlog.core.network.toModel
import com.famlog.core.network.toPatch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 장소 (PLC-01~05). 일정처럼 로컬 캐시 없이 화면에 들어올 때 받는다.
 * 공유·수정·삭제·표시가 끝나면 [changes]로 알려 목록이 다시 받게 한다.
 */
@Singleton
class PlaceRepository @Inject constructor(
    private val api: PlaceApi,
    private val caller: ApiCaller,
) {
    private val _changes = MutableSharedFlow<Long>(extraBufferCapacity = 8)

    /** 바뀐 장소의 그룹 ID */
    val changes: SharedFlow<Long> = _changes.asSharedFlow()

    suspend fun search(query: String, page: Int, groupId: Long): ApiResult<PlaceSearchPage> =
        caller.call { api.search(query, page, groupId).toModel() }

    suspend fun share(groupId: Long, share: PlaceShare): ApiResult<Place> = caller.call {
        api.share(
            groupId,
            SharePlaceRequest(
                kakaoPlaceId = share.kakaoPlaceId,
                category = share.category.name,
                oneLineReview = share.oneLineReview?.trim()?.takeIf { it.isNotEmpty() },
                rating = share.rating,
                shareToChat = share.shareToChat,
            ),
        ).toModel()
    }.also { notifyIfSuccess(it, groupId) }

    suspend fun page(groupId: Long, filter: PlaceFilter, cursor: String? = null): ApiResult<PlacePage> = caller.call {
        api.list(
            groupId = groupId,
            region1 = filter.region1,
            region2 = filter.region2?.takeIf { filter.region1 != null },
            sharedBy = filter.sharedBy.sorted(),
            category = filter.categories.sortedBy { it.ordinal }.map { it.name },
            cursor = cursor,
            size = PAGE_SIZE,
            swLat = filter.bounds?.swLat,
            swLng = filter.bounds?.swLng,
            neLat = filter.bounds?.neLat,
            neLng = filter.bounds?.neLng,
        ).toModel()
    }

    /** 내 주변 [radiusKm] km 안 (가까운 순). [at]은 요청에만 쓰고 저장·로그하지 않는다 */
    suspend fun nearby(groupId: Long, at: Coordinates, radiusKm: Int = NEARBY_RADIUS_KM): ApiResult<List<NearbyPlace>> =
        caller.call { api.nearby(groupId, at.lat, at.lng, radiusKm).items.map { it.toModel() } }

    suspend fun aliases(groupId: Long): ApiResult<List<PlaceAlias>> =
        caller.call { api.aliases(groupId).items.map { it.toModel() } }

    /** [placeId]와 [addressText]는 둘 다 선택. 빈 주소는 보내지 않는다 */
    suspend fun createAlias(groupId: Long, alias: String, placeId: Long?, addressText: String?): ApiResult<PlaceAlias> =
        caller.call {
            api.createAlias(groupId, CreateAliasRequest(alias.trim(), placeId, addressText?.trim()?.takeIf { it.isNotEmpty() })).toModel()
        }.also { notifyIfSuccess(it, groupId) }

    suspend fun deleteAlias(groupId: Long, alias: PlaceAlias): ApiResult<Unit> =
        caller.call { api.deleteAlias(groupId, alias.id) }.also { notifyIfSuccess(it, groupId) }

    /** 지역 배지는 구성원·카테고리 필터만 반영한다 */
    suspend fun regions(groupId: Long, filter: PlaceFilter): ApiResult<RegionCounts> = caller.call {
        api.regions(groupId, filter.sharedBy.sorted(), filter.categories.sortedBy { it.ordinal }.map { it.name }).toModel()
    }

    suspend fun detail(placeId: Long): ApiResult<Place> = caller.call { api.detail(placeId).toModel() }

    suspend fun edit(place: Place, edit: PlaceEdit): ApiResult<Place> =
        caller.call { api.update(place.id, edit.toPatch()).toModel() }.also { notifyIfSuccess(it, place.groupId) }

    suspend fun delete(place: Place): ApiResult<Unit> =
        caller.call { api.delete(place.id) }.also { notifyIfSuccess(it, place.groupId) }

    suspend fun setMark(place: Place, kind: PlaceMarkKind, on: Boolean): ApiResult<PlaceMarks> = caller.call {
        (if (on) api.mark(place.id, kind.name) else api.unmark(place.id, kind.name)).toModel()
    }

    private fun notifyIfSuccess(result: ApiResult<*>, groupId: Long) {
        if (result is ApiResult.Success) _changes.tryEmit(groupId)
    }

    companion object {
        /** 서버 기본 50, 최대 100 */
        private const val PAGE_SIZE = 50

        /** 사양 PLC-04 "내 주변 N km". 서버 기본값과 같다 */
        const val NEARBY_RADIUS_KM = 3
    }
}
