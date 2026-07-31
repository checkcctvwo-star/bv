package dev.aaa1115910.biliapi.account

import dev.aaa1115910.biliapi.entity.ApiType
import dev.aaa1115910.biliapi.repositories.AuthRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountResolverTest {

    private fun authRepo(sessData: String = "mainSess", mid: Long = 100L) = AuthRepository().apply {
        sessionData = sessData
        biliJct = "mainJct"
        accessToken = "mainToken"
        this.mid = mid
        buvid3 = "buvid3Main"
    }

    private class FakeMode(
        override val isDetailed: Boolean = false,
        override val mainUid: Long = 100L,
        override val anonymousBuvid3: String = "anonBuvid",
        private val uids: Map<AccountType, Long> = emptyMap()
    ) : AccountModeProvider {
        override fun uidFor(type: AccountType): Long? = uids[type]
    }

    private class FakeFetcher(private val map: Map<Long, ResolvedAuth>) : AuthDataFetcher {
        override suspend fun fetch(uid: Long): ResolvedAuth? = map[uid]
    }

    @Test
    fun `quick mode returns main account for all types`() = runBlocking {
        val resolver = AccountResolver(authRepo(), FakeMode(isDetailed = false), FakeFetcher(emptyMap()))
        val r = resolver.resolve(AccountType.RECOMMEND)
        assertEquals("mainSess", r.sessData)
        assertEquals(100L, r.mid)
    }

    @Test
    fun `detailed recommend with uid returns that account`() = runBlocking {
        val fetcher = FakeFetcher(mapOf(200L to ResolvedAuth(sessData = "recSess", mid = 200L)))
        val resolver = AccountResolver(authRepo(), FakeMode(isDetailed = true, uids = mapOf(AccountType.RECOMMEND to 200L)), fetcher)
        assertEquals("recSess", resolver.resolve(AccountType.RECOMMEND).sessData)
    }

    @Test
    fun `detailed recommend anonymous returns anonymous`() = runBlocking {
        val resolver = AccountResolver(authRepo(), FakeMode(isDetailed = true, uids = emptyMap()), FakeFetcher(emptyMap()))
        val r = resolver.resolve(AccountType.RECOMMEND)
        assertTrue(r.isAnonymous)
        assertEquals("anonBuvid", r.buvid3)
    }

    @Test
    fun `detailed heartbeat anonymous falls back to main`() = runBlocking {
        val resolver = AccountResolver(authRepo(), FakeMode(isDetailed = true, uids = emptyMap()), FakeFetcher(emptyMap()))
        val r = resolver.resolve(AccountType.HEARTBEAT)
        assertEquals("mainSess", r.sessData)
    }

    @Test
    fun `detailed video uid not found falls back to anonymous`() = runBlocking {
        val resolver = AccountResolver(authRepo(), FakeMode(isDetailed = true, uids = mapOf(AccountType.VIDEO to 999L)), FakeFetcher(emptyMap()))
        assertTrue(resolver.resolve(AccountType.VIDEO).isAnonymous)
    }

    @Test
    fun `detailed main returns main account`() = runBlocking {
        val resolver = AccountResolver(authRepo(), FakeMode(isDetailed = true), FakeFetcher(emptyMap()))
        assertEquals("mainSess", resolver.resolve(AccountType.MAIN).sessData)
    }

    @Test
    fun `effectiveApiType forces Web for non-main in detailed mode`() {
        val resolver = AccountResolver(authRepo(), FakeMode(isDetailed = true), FakeFetcher(emptyMap()))
        assertEquals(ApiType.Web, resolver.effectiveApiType(AccountType.RECOMMEND, ApiType.App))
    }

    @Test
    fun `effectiveApiType respects preferApiType for main or quick mode`() {
        val resolverQuick = AccountResolver(authRepo(), FakeMode(isDetailed = false), FakeFetcher(emptyMap()))
        assertEquals(ApiType.App, resolverQuick.effectiveApiType(AccountType.RECOMMEND, ApiType.App))
        val resolverDetailed = AccountResolver(authRepo(), FakeMode(isDetailed = true), FakeFetcher(emptyMap()))
        assertEquals(ApiType.App, resolverDetailed.effectiveApiType(AccountType.MAIN, ApiType.App))
    }
}