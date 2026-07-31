package dev.aaa1115910.biliapi.account

/** 提供 accountMode 状态：模式开关、每方向的 uid、主账号 uid、匿名 buvid3。 */
interface AccountModeProvider {
    val isDetailed: Boolean
    val mainUid: Long
    val anonymousBuvid3: String
    fun uidFor(type: AccountType): Long?
}