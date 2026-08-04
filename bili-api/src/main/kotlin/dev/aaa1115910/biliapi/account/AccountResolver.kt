package dev.aaa1115910.biliapi.account

import dev.aaa1115910.biliapi.entity.ApiType
import dev.aaa1115910.biliapi.repositories.AuthRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.annotation.Single

@Single
class AccountResolver(
    private val authRepository: AuthRepository,
    private val modeProvider: AccountModeProvider,
    private val fetcher: AuthDataFetcher
) {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    suspend fun resolve(type: AccountType): ResolvedAuth {
        // 快速模式 或 MAIN 方向：用当前账号（authRepository），保持现有行为
        if (!modeProvider.isDetailed || type == AccountType.MAIN) return fromAuthRepository()
        val uid = modeProvider.uidFor(type)
        return when {
            uid != null && uid != 0L -> fetcher.fetch(uid) ?: fallback(type)
            type == AccountType.HEARTBEAT -> {
                logger.info { "AccountResolver: HEARTBEAT anonymous -> main account" }
                fromAuthRepository()  // 匿名回退主账号
            }
            else -> {
                logger.info { "AccountResolver: $type anonymous -> guest" }
                anonymous()  // RECOMMEND/VIDEO 匿名 -> 游客
            }
        }
    }

    /** 详细模式下非主账号方向强制 Web（v1：gRPC 不支持按场景切）。 */
    fun effectiveApiType(type: AccountType, preferApiType: ApiType): ApiType =
        if (modeProvider.isDetailed && type != AccountType.MAIN) ApiType.Web else preferApiType

    private fun fallback(type: AccountType): ResolvedAuth {
        logger.info { "AccountResolver: fetch failed for $type, falling back" }
        return if (type == AccountType.HEARTBEAT) fromAuthRepository() else anonymous()
    }

    private fun fromAuthRepository() = ResolvedAuth(
        sessData = authRepository.sessionData ?: "",
        biliJct = authRepository.biliJct ?: "",
        accessToken = authRepository.accessToken ?: "",
        mid = authRepository.mid ?: 0,
        buvid3 = authRepository.buvid3 ?: ""
    )

    private fun anonymous() = ResolvedAuth(buvid3 = modeProvider.anonymousBuvid3)
}