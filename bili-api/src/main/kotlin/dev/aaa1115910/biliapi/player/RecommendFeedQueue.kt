package dev.aaa1115910.biliapi.player

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RecommendFeedQueue(
    private val source: Source,
    private val threshold: Int = 5,
    private val pageSize: Int = 30
) {
    data class VideoRef(val aid: Long, val cid: Long, val title: String)

    interface Source {
        suspend fun fetchPage(pageIdx: Int): List<VideoRef>
    }

    private val buffer = ArrayDeque<VideoRef>()
    private val history = ArrayDeque<VideoRef>()
    private val mutex = Mutex()
    private var pageIdx = 0
    private var exhausted = false

    suspend fun next(): VideoRef? = mutex.withLock {
        if (buffer.isEmpty() && !fetchMore()) return null
        val item = buffer.removeFirst()
        history.addLast(item)
        if (buffer.size < threshold) fetchMore()
        item
    }

    suspend fun prev(): VideoRef? = mutex.withLock {
        if (history.size < 2) return null
        val current = history.removeLast()
        val prev = history.removeLast()
        history.addLast(prev)       // prev becomes current
        buffer.addFirst(current)    // current goes back to buffer so next() can return to it
        return prev
    }

    private suspend fun fetchMore(): Boolean {
        if (exhausted) return false
        val page = source.fetchPage(pageIdx)
        if (page.isEmpty()) {
            exhausted = true
            return false
        }
        buffer.addAll(page)
        pageIdx++
        return true
    }
}