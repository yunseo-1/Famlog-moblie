package com.famlog.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Room SQL 확인 (단위 테스트의 FakeMessageDao와 같은 동작이어야 한다) */
@RunWith(AndroidJUnit4::class)
class MessageDaoTest {

    private lateinit var database: FamlogDatabase
    private lateinit var dao: MessageDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            FamlogDatabase::class.java,
        ).build()
        dao = database.messageDao()
    }

    @After
    fun tearDown() = database.close()

    private fun row(key: String, seq: Long?, sender: Long? = 8, unread: Int = 0, order: Long = 0, group: Long = 1) =
        MessageEntity(
            key = key, groupId = group, serverId = seq?.plus(100), seq = seq, senderId = sender,
            clientMessageId = key, type = "TEXT", body = "b", replyToId = null, replyJson = null,
            reactionsJson = "[]", unreadCount = unread, deleted = false, sentAtMillis = 0,
            sendState = if (seq == null) "SENDING" else "SENT", localOrder = order,
        )

    @Test
    fun seq_순서로_보내기_전_메시지는_맨_뒤에_만든_순서로() = runTest {
        dao.upsert(listOf(row("p2", null, order = 2), row("s3", 3), row("p1", null, order = 1), row("s1", 1)))

        assertEquals(listOf("s1", "s3", "p1", "p2"), dao.observe(1).first().map { it.key })
        assertEquals(3L, dao.maxSeq(1))
        assertEquals(1L, dao.minSeq(1))
        assertEquals(listOf("p1", "p2"), dao.pending().map { it.key })
    }

    @Test
    fun 같은_키로_다시_넣으면_한_줄이_바뀐다() = runTest {
        dao.upsert(listOf(row("c1", null)))
        dao.upsert(listOf(row("c1", 5)))

        val rows = dao.observe(1).first()
        assertEquals(1, rows.size)
        assertEquals(5L, rows.single().seq)
    }

    @Test
    fun 읽음은_범위_안_다른_사람_메시지만_줄이고_0_아래로_내려가지_않는다() = runTest {
        dao.upsert(listOf(row("a", 1, unread = 1), row("b", 2, sender = 9, unread = 1), row("c", 3, unread = 0), row("d", 4, unread = 2)))

        dao.decrementUnread(groupId = 1, fromSeq = 0, toSeq = 3, readerId = 9)

        assertEquals(listOf(0, 1, 0, 2), dao.observe(1).first().map { it.unreadCount })
    }

    @Test
    fun 삭제_표시는_본문과_공감을_지운다() = runTest {
        dao.upsert(listOf(row("a", 1).copy(reactionsJson = """[{"emoji":"HEART","userIds":[1]}]""")))

        dao.markDeleted(groupId = 1, id = 101)

        val deleted = dao.get("a")!!
        assertEquals(true, deleted.deleted)
        assertNull(deleted.body)
        assertEquals("[]", deleted.reactionsJson)
    }

    @Test
    fun 미디어_처리_결과와_업로드_진행을_저장한다() = runTest {
        dao.upsert(listOf(row("s1", 1), row("p1", null)))

        dao.updateMedia(id = 101, mediaJson = """[{"id":31,"mediaType":"IMAGE","status":"READY"}]""")
        dao.updateLocalMedia("p1", """[{"uri":"content://a","mime":"image/jpeg","size":1,"kind":"IMAGE","mediaId":31}]""")
        dao.updateLocalMedia("s1", "[]") // 서버에 저장된 메시지는 바꾸지 않는다

        assertEquals("""[{"id":31,"mediaType":"IMAGE","status":"READY"}]""", dao.getByServerId(101)?.mediaJson)
        assertEquals(true, dao.get("p1")?.localMediaJson?.contains("\"mediaId\":31"))
        assertNull(dao.get("s1")?.localMediaJson)
    }

    @Test
    fun clearSent는_보내기_전_메시지를_남긴다() = runTest {
        dao.upsert(listOf(row("s1", 1), row("p1", null), row("other", 1, group = 2)))

        dao.clearSent(1)

        assertEquals(listOf("p1"), dao.observe(1).first().map { it.key })
        assertEquals(listOf("other"), dao.observe(2).first().map { it.key })
    }
}
