package dev.aaa1115910.biliapi.account

/** 解析后的账号凭证。空字符串/0 表示匿名（游客）。 */
data class ResolvedAuth(
    val sessData: String = "",
    val biliJct: String = "",
    val accessToken: String = "",
    val mid: Long = 0,
    val buvid3: String = ""
) {
    val isAnonymous: Boolean get() = sessData.isEmpty() && accessToken.isEmpty()
}