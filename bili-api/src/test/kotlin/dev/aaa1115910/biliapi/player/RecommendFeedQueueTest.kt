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
        // buffer empty, should have prefetched next page
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

    @Test
    fun `prev then next returns the same video that was current`() = runBlocking {
        val queue = RecommendFeedQueue(FakeSource(mapOf(0 to listOf(ref(1, 10), ref(2, 20), ref(3, 30)))), threshold = 5)
        queue.next() // vid 1 -> history
        queue.next() // vid 2 -> history (current)
        queue.next() // vid 3 -> history (current)
        // now history = [1, 2, 3], current = 3
        val prev = queue.prev() // should return vid 2, put vid 3 back in buffer
        assertEquals(2L, prev?.aid)
        // next() should return vid 3 again (the previously current video), not replay vid 2
        assertEquals(3L, queue.next()?.aid)
    }

    @Test
    fun `prev returns null when history has less than 2 items`() = runBlocking {
        val queue = RecommendFeedQueue(FakeSource(mapOf(0 to listOf(ref(1, 10)))), threshold = 5)
        assertNull(queue.prev()) // history is empty

        queue.next() // history now has 1 item
        assertNull(queue.prev()) // still < 2
    }

    @Test
    fun `multiple prev and next sequences preserve all videos`() = runBlocking {
        val queue = RecommendFeedQueue(FakeSource(mapOf(0 to listOf(ref(1, 10), ref(2, 20), ref(3, 30)))), threshold = 5)
        queue.next() // 1
        queue.next() // 2
        queue.next() // 3 (current)
        queue.prev() // back to 2, 3 in buffer
        queue.prev() // back to 1, 2 in buffer
        // now next() should return 2, then 3
        assertEquals(2L, queue.next()?.aid)
        assertEquals(3L, queue.next()?.aid)
    }
}