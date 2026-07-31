package dev.aaa1115910.bv.account

import dev.aaa1115910.biliapi.account.AccountModeProvider
import dev.aaa1115910.biliapi.account.AccountType
import dev.aaa1115910.bv.util.Prefs
import org.koin.core.annotation.Single

@Single
class PrefsAccountModeProvider : AccountModeProvider {
    override val isDetailed: Boolean get() = Prefs.accountModeEnabled
    override val mainUid: Long get() = Prefs.uid
    override val anonymousBuvid3: String get() = Prefs.buvid3
    override fun uidFor(type: AccountType): Long? = when (type) {
        AccountType.MAIN -> Prefs.uid.takeIf { it != 0L }
        AccountType.HEARTBEAT -> Prefs.accountHeartbeatUid.takeIf { it != 0L }
        AccountType.RECOMMEND -> Prefs.accountRecommendUid.takeIf { it != 0L }
        AccountType.VIDEO -> Prefs.accountVideoUid.takeIf { it != 0L }
    }
}