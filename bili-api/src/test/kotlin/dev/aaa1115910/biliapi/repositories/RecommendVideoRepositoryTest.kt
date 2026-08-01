package dev.aaa1115910.biliapi.repositories

import dev.aaa1115910.biliapi.account.AccountModeProvider
import dev.aaa1115910.biliapi.account.AccountResolver
import dev.aaa1115910.biliapi.account.AccountType
import dev.aaa1115910.biliapi.account.AuthDataFetcher
import dev.aaa1115910.biliapi.account.ResolvedAuth
import dev.aaa1115910.biliapi.entity.ApiType
import dev.aaa1115910.biliapi.entity.home.RecommendPage
import dev.aaa1115910.biliapi.entity.rank.PopularVideoPage
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Paths
import java.util.Properties

class RecommendVideoRepositoryTest {
    companion object {
        private val localProperties = Properties().apply {
            val path = Paths.get("../local.properties").toAbsolutePath().toString()
            load(File(path).bufferedReader())
        }
        val SESSDATA: String =
            runCatching { localProperties.getProperty("test.sessdata") }.getOrNull() ?: ""
        val BILI_JCT: String =
            runCatching { localProperties.getProperty("test.bili_jct") }.getOrNull() ?: ""
        val UID: Long =
            runCatching { localProperties.getProperty("test.uid") }.getOrNull()?.toLongOrNull() ?: 2
        val ACCESS_TOKEN: String =
            runCatching { localProperties.getProperty("test.access_token") }.getOrNull() ?: ""
        val BUVID: String =
            runCatching { localProperties.getProperty("test.buvid") }.getOrNull() ?: ""
    }

    private val authRepository = AuthRepository()
    private val channelRepository = ChannelRepository()
    private val accountResolver = AccountResolver(
        authRepository = authRepository,
        modeProvider = object : AccountModeProvider {
            override val isDetailed = false
            override val mainUid = 0L
            override val anonymousBuvid3 = ""
            override fun uidFor(type: AccountType) = null
        },
        fetcher = object : AuthDataFetcher {
            override suspend fun fetch(uid: Long): ResolvedAuth? = null
        }
    )
    private val recommendVideoRepository =
        RecommendVideoRepository(channelRepository, accountResolver)

    init {
        channelRepository.initDefaultChannel(
            FavoriteRepositoryTest.ACCESS_TOKEN,
            FavoriteRepositoryTest.BUVID
        )

        authRepository.sessionData = SeasonRepositoryTest.SESSDATA
        authRepository.accessToken = SeasonRepositoryTest.ACCESS_TOKEN
        authRepository.biliJct = SeasonRepositoryTest.BILI_JCT
        authRepository.mid = SeasonRepositoryTest.UID
    }

    @Test
    fun getPopularVideos() = runBlocking {
        val result = recommendVideoRepository.getPopularVideos(
            page = PopularVideoPage(),
            preferApiType = ApiType.App
        )
        println(result)
    }

    @Test
    fun `get popular videos with web api`() = runBlocking {
        `get popular videos`(5, ApiType.Web)
    }

    @Test
    fun `get popular videos with app api`() = runBlocking {
        `get popular videos`(5, ApiType.App)
    }

    private suspend fun `get popular videos`(
        pageCount: Int,
        preferApiType: ApiType
    ) = runBlocking {
        var nextPage = PopularVideoPage()
        for (i in 0..pageCount) {
            val result = recommendVideoRepository.getPopularVideos(
                page = nextPage,
                preferApiType = preferApiType
            )
            nextPage = result.nextPage
            println(result.list.map { it.title })
        }
    }

    @Test
    fun `get recommend videos with web api`() = runBlocking {
        `get recommend videos`(5, ApiType.Web)
    }

    @Test
    fun `get recommend videos with app api`() = runBlocking {
        `get recommend videos`(5, ApiType.App)
    }

    private suspend fun `get recommend videos`(
        pageCount: Int,
        preferApiType: ApiType
    ) = runBlocking {
        var nextPage = RecommendPage()
        for (i in 0..pageCount) {
            println("page: $nextPage")
            val result = recommendVideoRepository.getRecommendVideos(
                page = nextPage,
                preferApiType = preferApiType
            )
            nextPage = result.nextPage
            println(result.items.map { it.title })
        }
    }
}