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
}