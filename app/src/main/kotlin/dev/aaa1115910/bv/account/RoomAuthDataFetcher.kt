package dev.aaa1115910.bv.account

import dev.aaa1115910.biliapi.account.AuthDataFetcher
import dev.aaa1115910.biliapi.account.ResolvedAuth
import dev.aaa1115910.bv.entity.AuthData
import dev.aaa1115910.bv.repository.UserRepository
import org.koin.core.annotation.Single

@Single
class RoomAuthDataFetcher(
    private val userRepository: UserRepository
) : AuthDataFetcher {
    override suspend fun fetch(uid: Long): ResolvedAuth? {
        val user = userRepository.findUserByUid(uid) ?: return null
        return runCatching { AuthData.fromJson(user.auth) }
            .getOrNull()
            ?.toResolvedAuth()
    }

    private fun AuthData.toResolvedAuth() = ResolvedAuth(
        sessData = sessData,
        biliJct = biliJct,
        accessToken = accessToken,
        mid = uid,
        buvid3 = ""
    )
}