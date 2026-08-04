package dev.aaa1115910.bv.account

import dev.aaa1115910.biliapi.account.AuthDataFetcher
import dev.aaa1115910.biliapi.account.ResolvedAuth
import dev.aaa1115910.bv.entity.AuthData
import dev.aaa1115910.bv.repository.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.annotation.Single

@Single
class RoomAuthDataFetcher(
    private val userRepository: UserRepository
) : AuthDataFetcher {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    override suspend fun fetch(uid: Long): ResolvedAuth? {
        val user = userRepository.findUserByUid(uid) ?: run {
            logger.info { "AuthDataFetcher: uid $uid not found in account pool, will fall back" }
            return null
        }
        return runCatching { AuthData.fromJson(user.auth) }
            .onFailure {
                logger.info { "AuthDataFetcher: parse auth json failed for uid $uid: ${it.stackTraceToString()}" }
            }
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