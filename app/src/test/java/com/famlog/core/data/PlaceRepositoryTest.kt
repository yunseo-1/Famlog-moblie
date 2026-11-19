package com.famlog.core.data

import com.famlog.core.model.ApiResult
import com.famlog.core.model.ErrorCodes
import com.famlog.core.model.Place
import com.famlog.core.model.PlaceCategory
import com.famlog.core.model.PlaceEdit
import com.famlog.core.model.PlaceFilter
import com.famlog.core.model.PlaceMarkKind
import com.famlog.core.model.PlaceMarks
import com.famlog.core.model.PlaceShare
import com.famlog.core.model.errorCode
import com.famlog.core.network.PlaceApi
import com.famlog.testing.api
import com.famlog.testing.errorResponse
import com.famlog.testing.jsonResponse
import com.famlog.testing.testApiCaller
import com.famlog.testing.testJson
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder
import java.time.Instant

class PlaceRepositoryTest {

    private val server = MockWebServer()
    private lateinit var repository: PlaceRepository

    @Before
    fun setUp() {
        server.start()
        repository = PlaceRepository(server.api<PlaceApi>(), testApiCaller())
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun 인수_기준_서울_송파구_아빠는_지역과_공유한_사람을_함께_보낸다() = runTest {
        server.enqueue(jsonResponse("""{"items":[${placeJson(1)}],"nextCursor":"c2"}"""))

        val page = repository.page(GROUP, PlaceFilter(region1 = "서울", region2 = "송파구", sharedBy = setOf(DAD))) as ApiResult.Success

        val path = server.takeRequest().path!!
        assertEquals(
            "/api/v1/groups/$GROUP/places?region1=서울&region2=송파구&sharedBy=$DAD&size=50",
            URLDecoder.decode(path, "UTF-8"),
        )
        assertEquals("c2", page.value.nextCursor)
        val place = page.value.items.single()
        assertEquals(PlaceCategory.RESTAURANT, place.category)
        assertEquals(PlaceMarks(visited = listOf(DAD), want = emptyList()), place.marks)
    }

    @Test
    fun 여러_구성원과_카테고리는_같은_키를_반복하고_시도_없는_시군구는_보내지_않는다() = runTest {
        server.enqueue(jsonResponse("""{"items":[]}"""))
        server.enqueue(jsonResponse("""{"total":0,"items":[]}"""))

        val filter = PlaceFilter(
            region2 = "송파구",
            sharedBy = setOf(9, DAD),
            categories = setOf(PlaceCategory.CAFE, PlaceCategory.RESTAURANT),
        )
        repository.page(GROUP, filter, cursor = "abc")
        repository.regions(GROUP, filter)

        assertEquals(
            "/api/v1/groups/$GROUP/places?sharedBy=2&sharedBy=9&category=RESTAURANT&category=CAFE&cursor=abc&size=50",
            server.takeRequest().path,
        )
        // 지역 배지는 지역 필터를 받지 않는다
        assertEquals("/api/v1/groups/$GROUP/places/regions?sharedBy=2&sharedBy=9&category=RESTAURANT&category=CAFE", server.takeRequest().path)
    }

    @Test
    fun 지역별_개수에서_세종은_시군구가_null이다() = runTest {
        server.enqueue(
            jsonResponse(
                """{"total":3,"items":[
                    {"region1":"서울","count":2,"children":[{"region2":"송파구","count":2}]},
                    {"region1":"세종","count":1,"children":[{"region2":null,"count":1}]}]}""",
            ),
        )

        val counts = (repository.regions(GROUP, PlaceFilter()) as ApiResult.Success).value

        assertEquals(3L, counts.total)
        assertEquals(listOf("서울", "세종"), counts.items.map { it.region1 })
        assertNull(counts.items[1].children.single().region2)
    }

    @Test
    fun 검색은_그룹을_함께_보내_이미_공유한_장소를_알려_준다() = runTest {
        server.enqueue(
            jsonResponse(
                """{"items":[{"kakaoPlaceId":"k1","name":"해주냉면","categoryDetail":"냉면","suggestedCategory":"RESTAURANT",
                   "address":"서울 송파구","region1":"서울","region2":"송파구","lat":37.5,"lng":127.1,"sharedPlaceId":1},
                   {"kakaoPlaceId":"k2","name":"어떤 곳","suggestedCategory":"SOMETHING_NEW"}],"hasMore":true}""",
            ),
        )

        val page = (repository.search("냉면", 2, GROUP) as ApiResult.Success).value

        assertEquals("/api/v1/places/search?query=%EB%83%89%EB%A9%B4&page=2&groupId=$GROUP", server.takeRequest().path)
        assertEquals(true, page.hasMore)
        assertEquals(1L, page.items[0].sharedPlaceId)
        assertEquals(PlaceCategory.ETC, page.items[1].suggestedCategory) // 모르는 값은 기타
    }

    @Test
    fun 공유는_kakaoPlaceId만_보내고_빈_한줄평은_빼고_목록에_바뀜을_알린다() = runTest {
        server.enqueue(jsonResponse(placeJson(1), code = 201))

        repository.changes.test {
            repository.share(GROUP, PlaceShare("k1", PlaceCategory.CAFE, rating = 4, oneLineReview = "  ", shareToChat = false))
            assertEquals(GROUP, awaitItem())
        }

        val body = body()
        assertEquals("k1", body["kakaoPlaceId"]!!.jsonPrimitive.content)
        assertEquals("CAFE", body["category"]!!.jsonPrimitive.content)
        assertEquals("4", body["rating"]!!.jsonPrimitive.content)
        assertEquals("false", body["shareToChat"]!!.jsonPrimitive.content)
        assertFalse(body.containsKey("oneLineReview"))
        assertFalse(body.containsKey("name")) // 이름·좌표는 서버가 검색 결과에서 채운다
    }

    @Test
    fun 같은_장소를_다시_공유하면_409_코드를_돌려준다() = runTest {
        server.enqueue(errorResponse(409, ErrorCodes.PLACE_ALREADY_SHARED))

        val result = repository.share(GROUP, PlaceShare("k1", PlaceCategory.CAFE, null, null, true))

        assertEquals(ErrorCodes.PLACE_ALREADY_SHARED, result.errorCode)
    }

    @Test
    fun 수정은_별점과_한줄평을_지우면_null을_보낸다() = runTest {
        server.enqueue(jsonResponse(placeJson(1)))

        repository.edit(place(), PlaceEdit(PlaceCategory.TRAVEL, rating = null, oneLineReview = null))

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/v1/places/1", request.path)
        val body = testJson.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("TRAVEL", body["category"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, body["rating"])
        assertEquals(JsonNull, body["oneLineReview"])
    }

    @Test
    fun 다녀옴은_PUT_취소는_DELETE이고_서버의_표시_목록을_돌려준다() = runTest {
        server.enqueue(jsonResponse("""{"visited":[$ME],"want":[]}"""))
        server.enqueue(jsonResponse("""{"visited":[],"want":[]}"""))

        val on = (repository.setMark(place(), PlaceMarkKind.VISITED, on = true) as ApiResult.Success).value
        repository.setMark(place(), PlaceMarkKind.WANT, on = false)

        assertEquals(listOf(ME), on.visited)
        server.takeRequest().let {
            assertEquals("PUT", it.method)
            assertEquals("/api/v1/places/1/marks/VISITED", it.path)
        }
        server.takeRequest().let {
            assertEquals("DELETE", it.method)
            assertEquals("/api/v1/places/1/marks/WANT", it.path)
        }
    }

    @Test
    fun 삭제가_성공하면_바뀜을_알리고_실패하면_알리지_않는다() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(errorResponse(403, ErrorCodes.GROUP_ROLE_REQUIRED))

        repository.changes.test {
            assertEquals(ApiResult.Success(Unit), repository.delete(place()))
            assertEquals(GROUP, awaitItem())
            repository.delete(place())
            expectNoEvents()
        }
        assertEquals("DELETE", server.takeRequest().method)
    }

    private fun body(): JsonObject = testJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject

    private fun place() = Place(
        id = 1, groupId = GROUP, kakaoPlaceId = "k1", name = "해주냉면", category = PlaceCategory.RESTAURANT,
        categoryDetail = "냉면", address = null, region1 = "서울", region2 = "송파구", region3 = null, lat = 37.5, lng = 127.1,
        phone = null, placeUrl = null, rating = 5, oneLineReview = "맛있어요", sharedBy = DAD, marks = PlaceMarks(),
        createdAt = Instant.parse("2026-11-12T00:00:00Z"),
    )

    private fun placeJson(id: Long) = """
        {"id":$id,"groupId":$GROUP,"kakaoPlaceId":"k$id","name":"해주냉면","category":"RESTAURANT","categoryDetail":"냉면",
         "address":"서울 송파구 백제고분로 1","region1":"서울","region2":"송파구","region3":"석촌동","lat":37.5,"lng":127.1,
         "rating":5,"oneLineReview":"물냉면","sharedBy":$DAD,"marks":{"visited":[$DAD],"want":[]},
         "createdAt":"2026-11-12T10:00:00+09:00","updatedAt":"2026-11-12T10:00:00+09:00"}
    """.trimIndent()

    private companion object {
        const val GROUP = 1L
        const val ME = 1L
        const val DAD = 2L
    }
}
