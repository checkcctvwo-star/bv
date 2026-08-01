package dev.aaa1115910.biliapi.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TypesTest {
    @Test
    fun `AccountType has exactly four directions`() {
        assertEquals(4, AccountType.entries.size)
        assertTrue(AccountType.entries.contains(AccountType.MAIN))
        assertTrue(AccountType.entries.contains(AccountType.HEARTBEAT))
        assertTrue(AccountType.entries.contains(AccountType.RECOMMEND))
        assertTrue(AccountType.entries.contains(AccountType.VIDEO))
    }

    @Test
    fun `ResolvedAuth defaults represent anonymous`() {
        val anon = ResolvedAuth()
        assertEquals("", anon.sessData)
        assertEquals("", anon.biliJct)
        assertEquals("", anon.accessToken)
        assertEquals(0L, anon.mid)
        assertEquals("", anon.buvid3)
    }

    @Test
    fun `ResolvedAuth isAnonymous is true when both sessData and accessToken are empty`() {
        assertTrue(ResolvedAuth(sessData = "", accessToken = "").isAnonymous)
    }

    @Test
    fun `ResolvedAuth isAnonymous is false when sessData is present but accessToken is empty`() {
        assertEquals(false, ResolvedAuth(sessData = "abc", accessToken = "").isAnonymous)
    }

    @Test
    fun `ResolvedAuth isAnonymous is false when accessToken is present but sessData is empty`() {
        assertEquals(false, ResolvedAuth(sessData = "", accessToken = "xyz").isAnonymous)
    }

    @Test
    fun `ResolvedAuth isAnonymous is false when both sessData and accessToken are present`() {
        assertEquals(false, ResolvedAuth(sessData = "abc", accessToken = "xyz").isAnonymous)
    }
}