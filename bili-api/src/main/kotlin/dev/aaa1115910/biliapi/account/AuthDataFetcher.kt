package dev.aaa1115910.biliapi.account

/** 按 uid 从账号池（Room）取凭证。返回 null 表示账号已删除。 */
interface AuthDataFetcher {
    suspend fun fetch(uid: Long): ResolvedAuth?
}