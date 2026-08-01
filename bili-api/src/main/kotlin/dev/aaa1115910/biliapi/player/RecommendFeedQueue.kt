package dev.aaa1115910.biliapi.player

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
    private var pageIdx = 0
    private var exhausted = false

    suspend fun next(): VideoRef? {
        if (buffer.isEmpty() && !fetchMore()) return null
        val item = buffer.removeFirst()
        history.addLast(item)
        if (buffer.size < threshold) fetchMore()
        return item
    }

    fun prev(): VideoRef? {
        if (history.size < 2) return null
        history.removeLast()  // 移除当前
        val prev = history.removeLast()
        buffer.addFirst(prev)  // 重新放回缓冲头部避免丢失
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