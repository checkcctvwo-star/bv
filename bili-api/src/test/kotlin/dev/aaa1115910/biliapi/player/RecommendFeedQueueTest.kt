package dev.aaa1115910.biliapi.player

import dev.aaa1115910.biliapi.player.RecommendFeedQueue.VideoRef
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecommendFeedQueueTest {

    private class FakeSource(private val pages: Map<Int, List<VideoRef>>) : RecommendFeedQueue.Source {
        var fetchCount = 0
        override suspend fun fetchPage(pageIdx: Int): List<VideoRef> {
            fetchCount++
            return pages[pageIdx] ?: emptyList()
        }
    }

    private fun ref(aid: Long, cid: Long) = VideoRef(aid = aid, cid = cid, title = "v$aid")

    @Test
    fun `next returns items in order and prefetches when buffer low`() = runBlocking {
        val source = FakeSource(mapOf(0 to listOf(ref(1, 10), ref(2, 20)), 1 to listOf(ref(3, 30))))
        val queue = RecommendFeedQueue(source, threshold = 2)
        assertEquals(1L, queue.next()?.aid)
        assertEquals(2L, queue.next()?.aid)
        // buffer 空，应预取下一页
        assertEquals(3L, queue.next()?.aid)
        assertTrue(source.fetchCount >= 2)
    }

    @Test
    fun `next returns null when feed exhausted`() = runBlocking {
        val queue = RecommendFeedQueue(FakeSource(mapOf(0 to listOf(ref(1, 10)))), threshold = 5)
        assertEquals(1L, queue.next()?.aid)
        assertNull(queue.next())
    }

    @Test
    fun `prev returns previously played video`() = runBlocking {
        val queue = RecommendFeedQueue(FakeSource(mapOf(0 to listOf(ref(1, 10), ref(2, 20)))), threshold = 5)
        queue.next()
        queue.next()
        assertEquals(1L, queue.prev()?.aid)
    }
}