# 账号切换 + 视频连播 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 为 BV TV 客户端增加"按场景账号切换"（4 方向：主账号/记录观看/推荐/取流，快速/详细模式，四宫格设置页）和"刷视频"连播（推荐流，按键/自动切下一个），Web 优先 v1。

**架构：** 新增 `AccountResolver`（bili-api 模块，`@Single`）按 `AccountType` 解析出 `ResolvedAuth`（sessData/biliJct/accessToken/mid/buvid3），通过两个接口（`AccountModeProvider` 读 Prefs、`AuthDataFetcher` 读 Room）获取数据。Repositories 用 `AccountResolver` 替代直接读 `AuthRepository`；详细模式下非主账号方向强制 `ApiType.Web`。主账号 = 当前账号（`authRepository`）。B：`RecommendFeedQueue` 维护推荐流缓冲，接入 `checkAndPlayNext` 新增的 `PlayRecommend` 动作 + 遥控器上/下一个键。

**技术栈：** Kotlin、Jetpack Compose (TV Material3)、Koin（`@Single`/`@KoinViewModel`/`@ComponentScan`）、kotlinx.serialization、Room、Ktor、kotlin.test（JUnit Platform）。

**设计文档：** `docs/superpowers/specs/2026-07-31-account-switching-design.md`

**分支：** `feat/account-switching`（已含设计文档 commit）。

---

## 文件结构

### 新建（bili-api 模块）
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/account/AccountType.kt` — 4 方向枚举。
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/account/ResolvedAuth.kt` — 解析后的原始凭证数据类。
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/account/AccountModeProvider.kt` — 读 accountMode 的接口。
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/account/AuthDataFetcher.kt` — 按 uid 取凭证的接口。
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/account/AccountResolver.kt` — 核心解析逻辑（`@Single`）。
- `bili-api/src/test/kotlin/dev/aaa1115910/biliapi/account/AccountResolverTest.kt` — 解析逻辑单测。

### 新建（app 模块）
- `app/src/main/kotlin/dev/aaa1115910/bv/account/PrefsAccountModeProvider.kt` — `AccountModeProvider` 的 Prefs 实现。
- `app/src/main/kotlin/dev/aaa1115910/bv/account/RoomAuthDataFetcher.kt` — `AuthDataFetcher` 的 Room 实现。
- `app/src/main/kotlin/dev/aaa1115910/bv/player/RecommendFeedQueue.kt` — 推荐流缓冲队列。
- `app/src/test/kotlin/dev/aaa1115910/bv/player/RecommendFeedQueueTest.kt` — 队列单测。
- `app/src/main/kotlin/dev/aaa1115910/bv/screen/settings/content/AccountSetting.kt` — 账号方向设置页（账号池 + 模式开关 + 四宫格）。
- `.github/workflows/test.yml` — CI 测试工作流。

### 修改
- `bili-api/build.gradle.kts` — 确保 `testImplementation(libs.kotlin.test)`。
- `bili-api/.../repositories/RecommendVideoRepository.kt` — 注入 `AccountResolver`，推荐/热门用 RECOMMEND 账号。
- `bili-api/.../repositories/VideoPlayRepository.kt` — 取流用 VIDEO 账号，心跳用 HEARTBEAT 账号。
- `bili-api/.../repositories/HistoryRepository.kt` — 历史读取用 HEARTBEAT 账号。
- `app/.../util/Prefs.kt` — 新增 accountMode 相关 key。
- `app/.../screen/settings/SettingsScreen.kt` — 新增 `Account` 导航项 + `when` 分支。
- `app/.../screen/settings/content/AudioVideoSetting.kt` — `ActionAfterPlayItems` 新增 `PlayRecommend`。
- `app/.../viewmodel/player/VideoPlayerV3ViewModel.kt` — 接入 `RecommendFeedQueue` + next/prev。
- `app/src/main/res/values/strings.xml` — 新增字符串资源（如需）。

---

## 任务 1：AccountType + ResolvedAuth + 接口（bili-api）

**文件：**
- 创建：`bili-api/src/main/kotlin/dev/aaa1115910/biliapi/account/AccountType.kt`
- 创建：`bili-api/src/main/kotlin/dev/aaa1115910/biliapi/account/ResolvedAuth.kt`
- 创建：`bili-api/src/main/kotlin/dev/aaa1115910/biliapi/account/AccountModeProvider.kt`
- 创建：`bili-api/src/main/kotlin/dev/aaa1115910/biliapi/account/AuthDataFetcher.kt`
- 测试：`bili-api/src/test/kotlin/dev/aaa1115910/biliapi/account/TypesTest.kt`

- [ ] **步骤 1：编写失败的测试**

```kotlin
// bili-api/src/test/kotlin/dev/aaa1115910/biliapi/account/TypesTest.kt
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
```

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew :bili-api:test`
预期：FAIL，报错 "Unresolved reference: AccountType"（类型未定义）。

> 若 bili-api 模块无 `testImplementation(libs.kotlin.test)`，先在 `bili-api/build.gradle.kts` 的 `dependencies` 块加：
> ```kotlin
> testImplementation(libs.kotlin.test)
> ```
> 并确认 `tasks.withType<Test> { useJUnitPlatform() }` 存在（若无则加）。

- [ ] **步骤 3：编写实现代码**

```kotlin
// bili-api/src/main/kotlin/dev/aaa1115910/biliapi/account/AccountType.kt
package dev.aaa1115910.biliapi.account

enum class AccountType {
    MAIN, HEARTBEAT, RECOMMEND, VIDEO
}
```

```kotlin
// bili-api/src/main/kotlin/dev/aaa1115910/biliapi/account/ResolvedAuth.kt
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
```

```kotlin
// bili-api/src/main/kotlin/dev/aaa1115910/biliapi/account/AccountModeProvider.kt
package dev.aaa1115910.biliapi.account

/** 提供 accountMode 状态：模式开关、每方向的 uid、主账号 uid、匿名 buvid3。 */
interface AccountModeProvider {
    val isDetailed: Boolean
    val mainUid: Long
    val anonymousBuvid3: String
    fun uidFor(type: AccountType): Long?
}
```

```kotlin
// bili-api/src/main/kotlin/dev/aaa1115910/biliapi/account/AuthDataFetcher.kt
package dev.aaa1115910.biliapi.account

/** 按 uid 从账号池（Room）取凭证。返回 null 表示账号已删除。 */
interface AuthDataFetcher {
    suspend fun fetch(uid: Long): ResolvedAuth?
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`./gradlew :bili-api:test`
预期：PASS（2 个测试通过）。

- [ ] **步骤 5：Commit**

```bash
git add bili-api/src/main/kotlin/dev/aaa1115910/biliapi/account/ bili-api/src/test/kotlin/dev/aaa1115910/biliapi/account/ bili-api/build.gradle.kts
git commit -m "feat(account): 新增 AccountType/ResolvedAuth/接口定义"
```

---

## 任务 2：AccountResolver 核心逻辑（TDD）

**文件：**
- 创建：`bili-api/src/main/kotlin/dev/aaa1115910/biliapi/account/AccountResolver.kt`
- 测试：`bili-api/src/test/kotlin/dev/aaa1115910/biliapi/account/AccountResolverTest.kt`

- [ ] **步骤 1：编写失败的测试**

```kotlin
// bili-api/src/test/kotlin/dev/aaa1115910/biliapi/account/AccountResolverTest.kt
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
```

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew :bili-api:test`
预期：FAIL，"Unresolved reference: AccountResolver"。

- [ ] **步骤 3：编写实现代码**

```kotlin
// bili-api/src/main/kotlin/dev/aaa1115910/biliapi/account/AccountResolver.kt
package dev.aaa1115910.biliapi.account

import dev.aaa1115910.biliapi.entity.ApiType
import dev.aaa1115910.biliapi.repositories.AuthRepository
import org.koin.core.annotation.Single

@Single
class AccountResolver(
    private val authRepository: AuthRepository,
    private val modeProvider: AccountModeProvider,
    private val fetcher: AuthDataFetcher
) {
    suspend fun resolve(type: AccountType): ResolvedAuth {
        // 快速模式 或 MAIN 方向：用当前账号（authRepository），保持现有行为
        if (!modeProvider.isDetailed || type == AccountType.MAIN) return fromAuthRepository()
        val uid = modeProvider.uidFor(type)
        return when {
            uid != null && uid != 0L -> fetcher.fetch(uid) ?: fallback(type)
            type == AccountType.HEARTBEAT -> fromAuthRepository()  // 匿名回退主账号
            else -> anonymous()  // RECOMMEND/VIDEO 匿名 -> 游客
        }
    }

    /** 详细模式下非主账号方向强制 Web（v1：gRPC 不支持按场景切）。 */
    fun effectiveApiType(type: AccountType, preferApiType: ApiType): ApiType =
        if (modeProvider.isDetailed && type != AccountType.MAIN) ApiType.Web else preferApiType

    private fun fallback(type: AccountType): ResolvedAuth =
        if (type == AccountType.HEARTBEAT) fromAuthRepository() else anonymous()

    private fun fromAuthRepository() = ResolvedAuth(
        sessData = authRepository.sessionData ?: "",
        biliJct = authRepository.biliJct ?: "",
        accessToken = authRepository.accessToken ?: "",
        mid = authRepository.mid ?: 0,
        buvid3 = authRepository.buvid3 ?: ""
    )

    private fun anonymous() = ResolvedAuth(buvid3 = modeProvider.anonymousBuvid3)
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`./gradlew :bili-api:test`
预期：PASS（8 个测试通过）。

- [ ] **步骤 5：Commit**

```bash
git add bili-api/src/main/kotlin/dev/aaa1115910/biliapi/account/AccountResolver.kt bili-api/src/test/kotlin/dev/aaa1115910/biliapi/account/AccountResolverTest.kt
git commit -m "feat(account): AccountResolver 核心解析逻辑 + 单测"
```

---

## 任务 3：Prefs 新增 accountMode 字段 + Provider 实现

**文件：**
- 修改：`app/src/main/kotlin/dev/aaa1115910/bv/util/Prefs.kt`（账号区追加 + PrefKeys 追加）
- 创建：`app/src/main/kotlin/dev/aaa1115910/bv/account/PrefsAccountModeProvider.kt`

- [ ] **步骤 1：在 Prefs 账号区追加字段**

在 `Prefs.kt` 的 `buvid3 by pref(...)` 行之后追加：

```kotlin
    // 账号方向（按场景切换）
    var accountModeEnabled by pref(PrefKeys.prefAccountModeEnabledKey, false)
    var accountHeartbeatUid by pref(PrefKeys.prefAccountHeartbeatUidKey, 0L)
    var accountRecommendUid by pref(PrefKeys.prefAccountRecommendUidKey, 0L)
    var accountVideoUid by pref(PrefKeys.prefAccountVideoUidKey, 0L)
```

在 `PrefKeys` 对象内追加（与现有 `prefBuvid3Key` 同区）：

```kotlin
    val prefAccountModeEnabledKey = booleanPreferencesKey("account_mode_enabled")
    val prefAccountHeartbeatUidKey = longPreferencesKey("account_hb_uid")
    val prefAccountRecommendUidKey = longPreferencesKey("account_rc_uid")
    val prefAccountVideoUidKey = longPreferencesKey("account_vi_uid")
```

- [ ] **步骤 2：编写 Provider 实现**

```kotlin
// app/src/main/kotlin/dev/aaa1115910/bv/account/PrefsAccountModeProvider.kt
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
```

- [ ] **步骤 3：编译验证**

运行：`./gradlew :app:compileDefaultDebugKotlin`
预期：BUILD SUCCESSFUL（Prefs 新字段与 Provider 编译通过；`@Single` 由 `AppModule` 的 `@ComponentScan` 自动注册）。

- [ ] **步骤 4：Commit**

```bash
git add app/src/main/kotlin/dev/aaa1115910/bv/util/Prefs.kt app/src/main/kotlin/dev/aaa1115910/bv/account/PrefsAccountModeProvider.kt
git commit -m "feat(account): Prefs 新增 accountMode 字段 + PrefsAccountModeProvider"
```

---

## 任务 4：RoomAuthDataFetcher 实现

**文件：**
- 创建：`app/src/main/kotlin/dev/aaa1115910/bv/account/RoomAuthDataFetcher.kt`

- [ ] **步骤 1：编写实现**

```kotlin
// app/src/main/kotlin/dev/aaa1115910/bv/account/RoomAuthDataFetcher.kt
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
```

- [ ] **步骤 2：编译验证**

运行：`./gradlew :app:compileDefaultDebugKotlin`
预期：BUILD SUCCESSFUL。`UserRepository.findUserByUid` 已存在；`AuthData.fromJson` 已存在。

- [ ] **步骤 3：Commit**

```bash
git add app/src/main/kotlin/dev/aaa1115910/bv/account/RoomAuthDataFetcher.kt
git commit -m "feat(account): RoomAuthDataFetcher 从 Room 取凭证"
```

---

## 任务 5：RecommendVideoRepository 接入 AccountResolver

**文件：**
- 修改：`bili-api/src/main/kotlin/dev/aaa1115910/biliapi/repositories/RecommendVideoRepository.kt`

- [ ] **步骤 1：修改类，注入 AccountResolver 并用它取凭证**

将类签名改为：

```kotlin
@Single
class RecommendVideoRepository(
    private val authRepository: AuthRepository,
    private val channelRepository: ChannelRepository,
    private val accountResolver: AccountResolver
) {
```

在文件顶部 import 区追加：
```kotlin
import dev.aaa1115910.biliapi.account.AccountResolver
import dev.aaa1115910.biliapi.account.AccountType
```

将 `getRecommendVideos` 内的凭证与 apiType 改为：

```kotlin
    suspend fun getRecommendVideos(
        page: RecommendPage = RecommendPage(),
        preferApiType: ApiType = ApiType.Web
    ): RecommendData {
        val auth = accountResolver.resolve(AccountType.RECOMMEND)
        val apiType = accountResolver.effectiveApiType(AccountType.RECOMMEND, preferApiType)
        val items = when (apiType) {
            ApiType.Web -> BiliHttpApi.getFeedRcmd(
                idx = page.nextWebIdx,
                sessData = auth.sessData.ifEmpty { null }
            ).getResponseData().item.map { UgcItem.fromRcmdItem(it) }

            ApiType.App -> BiliHttpApi.getFeedIndex(
                idx = page.nextAppIdx,
                accessKey = auth.accessToken.ifEmpty { null }
            ).getResponseData().items.filter { it.cardGoto == "av" }
                .map { UgcItem.fromRcmdItem(it) }
        }
        val nextPage = when (apiType) {
            ApiType.Web -> RecommendPage(nextWebIdx = page.nextWebIdx + 1)
            ApiType.App -> RecommendPage(nextAppIdx = items.first().idx + 1)
        }
        return RecommendData(items = items, nextPage = nextPage)
    }
```

对 `getPopularVideos` 做同样处理（用 `AccountType.RECOMMEND`）：把 `authRepository.sessionData ?: ""` 替换为 `accountResolver.resolve(AccountType.RECOMMEND).sessData`，并把 `when (preferApiType)` 的 `preferApiType` 替换为 `accountResolver.effectiveApiType(AccountType.RECOMMEND, preferApiType)`。App 分支的 gRPC stub 调用保持不变（仅在快速模式/主账号时命中，沿用 `channelRepository.defaultChannel`）。

- [ ] **步骤 2：编译 + 既有冒烟测试验证**

运行：`./gradlew :bili-api:compileKotlin :app:compileDefaultDebugKotlin`
预期：BUILD SUCCESSFUL。Koin 的 `@ComponentScan` 自动把新构造参数 `AccountResolver` 注入。

- [ ] **步骤 3：代码审查（code-review 技能）**

对 RecommendVideoRepository 改动做双轴线审查，确认：快速模式行为不变（resolve 返回 authRepository 值）；详细模式非主账号走 Web。

- [ ] **步骤 4：Commit**

```bash
git add bili-api/src/main/kotlin/dev/aaa1115910/biliapi/repositories/RecommendVideoRepository.kt
git commit -m "feat(account): 推荐流接入 AccountResolver（RECOMMEND 方向）"
```

---

## 任务 6：VideoPlayRepository 接入（取流 + 心跳）

**文件：**
- 修改：`bili-api/src/main/kotlin/dev/aaa1115910/biliapi/repositories/VideoPlayRepository.kt`

- [ ] **步骤 1：注入 AccountResolver**

类签名追加参数与 import（同任务 5）：
```kotlin
import dev.aaa1115910.biliapi.account.AccountResolver
import dev.aaa1115910.biliapi.account.AccountType

@Single
class VideoPlayRepository(
    private val authRepository: AuthRepository,
    private val channelRepository: ChannelRepository,
    private val accountResolver: AccountResolver
) {
```

- [ ] **步骤 2：getPlayData 改用 VIDEO 方向**

```kotlin
    suspend fun getPlayData(
        aid: Long,
        cid: Long,
        preferApiType: ApiType = ApiType.Web
    ): PlayData {
        val auth = accountResolver.resolve(AccountType.VIDEO)
        val apiType = accountResolver.effectiveApiType(AccountType.VIDEO, preferApiType)
        return when (apiType) {
            ApiType.Web -> {
                val playUrlData = BiliHttpApi.getVideoPlayUrl(
                    av = aid, cid = cid, fnval = 4048, qn = 127, fnver = 0, fourk = 1,
                    sessData = auth.sessData.ifEmpty { null },
                    dedeUserID = auth.mid.takeIf { it != 0L }
                ).getResponseData()
                PlayData.fromPlayUrlData(playUrlData)
            }
            ApiType.App -> { /* 保持现有 gRPC playerStub 逻辑不变 */ }
        }
    }
```

> App 分支原样保留（仅在快速模式命中，走 `channelRepository.defaultChannel`，主账号）。把原 App 分支代码整体移入 `ApiType.App ->` 块。

- [ ] **步骤 3：getPgcPlayData 改用 VIDEO 方向**

在方法开头加：
```kotlin
        val auth = accountResolver.resolve(AccountType.VIDEO)
        val apiType = accountResolver.effectiveApiType(AccountType.VIDEO, preferApiType)
```
Web 分支把 `sessData = authRepository.sessionData` 改为 `sessData = auth.sessData.ifEmpty { null }`，并把 `when (preferApiType)` 改为 `when (apiType)`。App 分支保留原 gRPC 逻辑。代理分支（`enableProxy`）的 `BiliHttpProxyApi.getPgcVideoPlayUrlV2` 同样用 `auth.sessData`。

- [ ] **步骤 4：sendHeartbeat 改用 HEARTBEAT 方向**

```kotlin
    suspend fun sendHeartbeat(
        aid: Long, cid: Long, time: Int,
        type: HeartbeatVideoType = HeartbeatVideoType.Video,
        subType: Int? = null, epid: Int? = null, seasonId: Int? = null,
        preferApiType: ApiType = ApiType.Web
    ) {
        val auth = accountResolver.resolve(AccountType.HEARTBEAT)
        val apiType = accountResolver.effectiveApiType(AccountType.HEARTBEAT, preferApiType)
        val result = when (apiType) {
            ApiType.Web -> BiliHttpApi.sendHeartbeat(
                avid = aid, cid = cid, playedTime = time, type = type.value,
                subType = subType, epid = epid, sid = seasonId,
                csrf = auth.biliJct.ifEmpty { null },
                sessData = auth.sessData
            )
            ApiType.App -> BiliHttpApi.sendHeartbeat(
                avid = aid, cid = cid, playedTime = time, type = type.value,
                subType = subType, epid = epid, sid = seasonId,
                accessKey = auth.accessToken.ifEmpty { null }
            )
        }
        println("send heartbeat result: $result")
    }
```

- [ ] **步骤 5：编译验证**

运行：`./gradlew :bili-api:compileKotlin`
预期：BUILD SUCCESSFUL。

- [ ] **步骤 6：Commit**

```bash
git add bili-api/src/main/kotlin/dev/aaa1115910/biliapi/repositories/VideoPlayRepository.kt
git commit -m "feat(account): 取流用 VIDEO、心跳用 HEARTBEAT 方向"
```

---

## 任务 7：HistoryRepository 接入

**文件：**
- 修改：`bili-api/src/main/kotlin/dev/aaa1115910/biliapi/repositories/HistoryRepository.kt`

- [ ] **步骤 1：注入 AccountResolver 并改用 HEARTBEAT 方向**

```kotlin
import dev.aaa1115910.biliapi.account.AccountResolver
import dev.aaa1115910.biliapi.account.AccountType

@Single
class HistoryRepository(
    private val authRepository: AuthRepository,
    private val channelRepository: ChannelRepository,
    private val accountResolver: AccountResolver
) {
    private val historyStub
        get() = runCatching {
            HistoryGrpcKt.HistoryCoroutineStub(channelRepository.defaultChannel!!)
        }.getOrNull()

    suspend fun getHistories(
        cursor: Long,
        preferApiType: ApiType = ApiType.Web
    ): HistoryData {
        val auth = accountResolver.resolve(AccountType.HEARTBEAT)
        val apiType = accountResolver.effectiveApiType(AccountType.HEARTBEAT, preferApiType)
        return when (apiType) {
            ApiType.Web -> {
                val data = BiliHttpApi.getHistories(
                    viewAt = cursor,
                    sessData = auth.sessData
                ).getResponseData()
                HistoryData.fromHistoryResponse(data)
            }
            ApiType.App -> {
                val reply = historyStub?.cursorV2(cursorV2Req {
                    this.cursor = cursor { max = cursor }
                    business = "archive"
                })
                HistoryData.fromHistoryResponse(reply!!)
            }
        }
    }
}
```

- [ ] **步骤 2：编译验证**

运行：`./gradlew :bili-api:compileKotlin`
预期：BUILD SUCCESSFUL。

- [ ] **步骤 3：Commit**

```bash
git add bili-api/src/main/kotlin/dev/aaa1115910/biliapi/repositories/HistoryRepository.kt
git commit -m "feat(account): 历史记录读取用 HEARTBEAT 方向"
```

---

## 任务 8：设置页"账号与方向"UI

**文件：**
- 修改：`app/src/main/kotlin/dev/aaa1115910/bv/screen/settings/SettingsScreen.kt`
- 创建：`app/src/main/kotlin/dev/aaa1115910/bv/screen/settings/content/AccountSetting.kt`

- [ ] **步骤 1：在 SettingsMenuNavItem 新增 Account 项**

在 `SettingsMenuNavItem` 枚举追加（放在 `Network` 附近）：
```kotlin
    Account(R.string.settings_item_account),
```
并在 `strings.xml` 新增：
```xml
<string name="settings_item_account">账号与方向</string>
```
在 `SettingContent` 的 `when (currentMenu)` 追加分支：
```kotlin
                SettingsMenuNavItem.Account -> AccountSetting()
```

- [ ] **步骤 2：编写 AccountSetting Composable**

```kotlin
// app/src/main/kotlin/dev/aaa1115910/bv/screen/settings/content/AccountSetting.kt
package dev.aaa1115910.bv.screen.settings.content

import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ListItem
import dev.aaa1115910.bv.activities.LoginActivity
import dev.aaa1115910.bv.dao.AppDatabase
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.entity.db.UserDB
import dev.aaa1115910.bv.screen.settings.SettingsMenuNavItem
import dev.aaa1115910.bv.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class DirectionCard(
    val title: String,
    val currentUid: Long,
    val currentName: String,
    val isAnonymous: Boolean
)

@Composable
fun AccountSetting(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var accountModeEnabled by remember { mutableStateOf(Prefs.accountModeEnabled) }
    var users by remember { mutableStateOf<List<UserDB>>(emptyList()) }
    var pickingFor by remember { mutableStateOf<String?>(null) }  // title of direction being assigned

    val refreshUsers = {
        scope.launch(Dispatchers.IO) {
            val list = BVApp.getAppDatabase().userDao().getAll()
            withContext(Dispatchers.Main) { users = list }
        }
    }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = SettingsMenuNavItem.Account.getDisplayName(context),
                style = MaterialTheme.typography.displaySmall
            )
            // 账号池入口（扫码添加 / 管理）
            ListItem(
                headlineContent = { Text("账号池：${users.size} 个账号") },
                supportingContent = { Text("扫码添加 / 管理已登录账号") },
                modifier = Modifier.padding(horizontal = 12.dp),
                onClick = {
                    context.startActivity(Intent(context, LoginActivity::class.java))
                    refreshUsers()
                }
            )
            // 快速 / 详细 模式开关
            SettingSwitchListItem(
                title = "详细模式",
                supportText = if (accountModeEnabled) "四宫格按方向指派账号" else "全局单账号（快速）",
                checked = accountModeEnabled,
                onCheckedChange = {
                    accountModeEnabled = it
                    Prefs.accountModeEnabled = it
                }
            )
            if (accountModeEnabled) {
                DirectionGrid(
                    users = users,
                    onPick = { pickingFor = it }
                )
            }
        }
    }

    if (pickingFor != null) {
        AccountPickerDialog(
            directionTitle = pickingFor!!,
            users = users,
            onPick = { uid ->
                when (pickingFor) {
                    "记录观看" -> Prefs.accountHeartbeatUid = uid
                    "推荐" -> Prefs.accountRecommendUid = uid
                    "取流" -> Prefs.accountVideoUid = uid
                }
                pickingFor = null
            },
            onDismiss = { pickingFor = null }
        )
    }
}

@Composable
private fun DirectionGrid(users: List<UserDB>, onPick: (String) -> Unit) {
    val cards = listOf(
        DirectionCard("主账号", Prefs.uid, users.firstOrNull { it.uid == Prefs.uid }?.username ?: "未登录", Prefs.uid == 0L),
        DirectionCard("记录观看", Prefs.accountHeartbeatUid, users.firstOrNull { it.uid == Prefs.accountHeartbeatUid }?.username ?: "匿名", Prefs.accountHeartbeatUid == 0L),
        DirectionCard("推荐", Prefs.accountRecommendUid, users.firstOrNull { it.uid == Prefs.accountRecommendUid }?.username ?: "匿名", Prefs.accountRecommendUid == 0L),
        DirectionCard("取流", Prefs.accountVideoUid, users.firstOrNull { it.uid == Prefs.accountVideoUid }?.username ?: "匿名", Prefs.accountVideoUid == 0L)
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 48.dp).fillMaxSize()
    ) {
        items(cards) { card ->
            ListItem(
                headlineContent = { Text(card.title) },
                supportingContent = { Text(if (card.isAnonymous) "匿名" else card.currentName) },
                modifier = Modifier
                    .padding(8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
                onClick = { onPick(card.title) }
            )
        }
    }
}

@Composable
private fun AccountPickerDialog(
    directionTitle: String,
    users: List<UserDB>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("为「$directionTitle」选择账号") },
        text = {
            Column {
                TextButton(onClick = { onPick(0L) }) { Text("匿名") }
                users.forEach { user ->
                    TextButton(onClick = { onPick(user.uid) }) { Text("${user.username}（${user.uid}）") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
```

> 复用现有 `SettingSwitchListItem`（`screen/settings/components/`）。主账号卡片点击当前跳账号池（切换主账号 = 切换当前账号，沿用 `LoginActivity`/`UserSwitchActivity`）；如项目有 `UserSwitchActivity`，把主账号 onClick 改为启动它。

- [ ] **步骤 3：编译验证**

运行：`./gradlew :app:compileDefaultDebugKotlin`
预期：BUILD SUCCESSFUL。若 `SettingSwitchListItem` 的 import 路径不同，按实际包名修正。

- [ ] **步骤 4：Commit**

```bash
git add app/src/main/kotlin/dev/aaa1115910/bv/screen/settings/ app/src/main/res/values/strings.xml
git commit -m "feat(account): 设置页账号与方向（账号池+模式开关+四宫格）"
```

---

## 任务 9：RecommendFeedQueue（推荐流缓冲队列，TDD）

**文件：**
- 创建：`app/src/main/kotlin/dev/aaa1115910/bv/player/RecommendFeedQueue.kt`
- 测试：`app/src/test/kotlin/dev/aaa1115910/bv/player/RecommendFeedQueueTest.kt`

- [ ] **步骤 1：编写失败的测试**

```kotlin
// app/src/test/kotlin/dev/aaa1115910/bv/player/RecommendFeedQueueTest.kt
package dev.aaa1115910.bv.player

import dev.aaa1115910.bv.player.RecommendFeedQueue.VideoRef
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecommendFeedQueueTest {

    private class FakeSource(private val pages: Map<Int, List<VideoRef>>) : RecommendFeedQueue.Source {
        var fetchCount = 0
        override suspend fun fetchPage(pageIdx: Int): List<VideoRef> {
            fetchCount++
            return pages[pageIdx] ?: emptyList()
        }
    }

    private fun ref(aid: Long, cid: Long) = VideoRef(aid = aid, cid = cid, title = "v$aid")

    @Test
    fun `next returns items in order and prefetches when buffer low`() = runBlocking {
        val source = FakeSource(mapOf(0 to listOf(ref(1, 10), ref(2, 20)), 1 to listOf(ref(3, 30))))
        val queue = RecommendFeedQueue(source, threshold = 2)
        assertEquals(1L, queue.next()?.aid)
        assertEquals(2L, queue.next()?.aid)
        // buffer 空，应预取下一页
        assertEquals(3L, queue.next()?.aid)
        assertTrue(source.fetchCount >= 2)
    }

    @Test
    fun `next returns null when feed exhausted`() = runBlocking {
        val queue = RecommendFeedQueue(FakeSource(mapOf(0 to listOf(ref(1, 10)))), threshold = 5)
        assertEquals(1L, queue.next()?.aid)
        assertNull(queue.next())
    }

    @Test
    fun `prev returns previously played video`() = runBlocking {
        val queue = RecommendFeedQueue(FakeSource(mapOf(0 to listOf(ref(1, 10), ref(2, 20)))), threshold = 5)
        queue.next()
        queue.next()
        assertEquals(1L, queue.prev()?.aid)
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew :app:testDebugUnitTest`
预期：FAIL，"Unresolved reference: RecommendFeedQueue"。

- [ ] **步骤 3：编写实现代码**

```kotlin
// app/src/main/kotlin/dev/aaa1115910/bv/player/RecommendFeedQueue.kt
package dev.aaa1115910.bv.player

class RecommendFeedQueue(
    private val source: Source,
    private val threshold: Int = 5,
    private val pageSize: Int = 30
) {
    data class VideoRef(val aid: Long, val cid: Long, val title: String)

    interface Source {
        suspend fun fetchPage(pageIdx: Int): List<VideoRef>
    }

    private val buffer = ArrayDeque<VideoRef>()
    private val history = ArrayDeque<VideoRef>()
    private var pageIdx = 0
    private var exhausted = false

    suspend fun next(): VideoRef? {
        if (buffer.isEmpty() && !fetchMore()) return null
        val item = buffer.removeFirst()
        history.addLast(item)
        if (buffer.size < threshold) fetchMore()
        return item
    }

    fun prev(): VideoRef? {
        if (history.size < 2) return null
        history.removeLast()  // 移除当前
        val prev = history.removeLast()
        buffer.addFirst(prev)  // 重新放回缓冲头部避免丢失
        return prev
    }

    private suspend fun fetchMore(): Boolean {
        if (exhausted) return false
        val page = source.fetchPage(pageIdx)
        if (page.isEmpty()) {
            exhausted = true
            return false
        }
        buffer.addAll(page)
        pageIdx++
        return true
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`./gradlew :app:testDebugUnitTest`
预期：PASS（3 个测试通过）。

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/kotlin/dev/aaa1115910/bv/player/RecommendFeedQueue.kt app/src/test/kotlin/dev/aaa1115910/bv/player/RecommendFeedQueueTest.kt
git commit -m "feat(player): RecommendFeedQueue 推荐流缓冲队列 + 单测"
```

---

## 任务 10：播放器接入刷视频（PlayRecommend + 上/下一个键）

**文件：**
- 修改：`app/src/main/kotlin/dev/aaa1115910/bv/screen/settings/content/AudioVideoSetting.kt`（`ActionAfterPlayItems` 枚举）
- 修改：`app/src/main/kotlin/dev/aaa1115910/bv/viewmodel/player/VideoPlayerV3ViewModel.kt`

- [ ] **步骤 1：ActionAfterPlayItems 新增 PlayRecommend**

在 `AudioVideoSetting.kt` 的枚举追加：
```kotlin
    PlayRecommend(4, "播放推荐流下一个"),
```

- [ ] **步骤 2：VideoPlayerV3ViewModel 接入队列与 next/prev**

在类中新增字段与依赖（`@KoinViewModel` 构造参数追加 `recommendVideoRepository`，用于构造队列 Source）：
```kotlin
import dev.aaa1115910.biliapi.account.AccountType
import dev.aaa1115910.bv.player.RecommendFeedQueue
import dev.aaa1115910.biliapi.repositories.RecommendVideoRepository

@KoinViewModel
class VideoPlayerV3ViewModel(
    private val videoInfoRepository: VideoInfoRepository,
    private val videoPlayRepository: VideoPlayRepository,
    private val recommendVideoRepository: RecommendVideoRepository
) : ViewModel() {
    // ... 既有字段 ...

    private var feedQueue: RecommendFeedQueue? = null

    private fun ensureQueue() {
        if (feedQueue != null) return
        feedQueue = RecommendFeedQueue(object : RecommendFeedQueue.Source {
            override suspend fun fetchPage(pageIdx: Int): List<RecommendFeedQueue.VideoRef> {
                val data = runCatching {
                    recommendVideoRepository.getRecommendVideos(
                        page = dev.aaa1115910.biliapi.entity.home.RecommendPage(nextWebIdx = pageIdx),
                        preferApiType = dev.aaa1115910.biliapi.entity.ApiType.Web
                    )
                }.getOrNull() ?: return emptyList()
                return data.items.map { RecommendFeedQueue.VideoRef(it.avid, it.cid, it.title) }
            }
        })
    }

    fun playNextRecommend() {
        viewModelScope.launch(Dispatchers.IO) {
            ensureQueue()
            val ref = feedQueue?.next() ?: return@launch
            playNewVideo(dev.aaa1115910.bv.entity.VideoListItem(aid = ref.aid, cid = ref.cid, title = ref.title))
        }
    }

    fun playPrevRecommend() {
        viewModelScope.launch(Dispatchers.IO) {
            val ref = feedQueue?.prev() ?: return@launch
            playNewVideo(dev.aaa1115910.bv.entity.VideoListItem(aid = ref.aid, cid = ref.cid, title = ref.title))
        }
    }
}
```

> `VideoListItem` 构造参数以代码库实际为准（已知含 aid/cid/title/epid/seasonId）。`UgcItem.avid/cid/title` 字段名以实际为准。

- [ ] **步骤 3：checkAndPlayNext 增加 PlayRecommend 分支**

在 `checkAndPlayNext` 的 `when (Prefs.actionAfterPlay)` 追加（放在 `PlayRelated` 之后）：
```kotlin
            ActionAfterPlayItems.PlayRecommend -> {
                playNextRecommend()
                return
            }
```

- [ ] **步骤 4：遥控器键绑定 next/prev（在 VideoPlayerV3Screen 或 Activity 按键处理）**

在播放器按键处理（`VideoPlayerV3Screen` 的 `onPreviewKeyEvent` 或现有快捷键分发处）追加：
```kotlin
// 下一个推荐（例如 KEYCODE_MEDIA_NEXT 或自定义快捷键）
if (it.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_MEDIA_NEXT && it.nativeKeyEvent.action == android.view.KeyEvent.ACTION_UP) {
    playerViewModel.playNextRecommend()
    return@onPreviewKeyEvent true
}
if (it.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS && it.nativeKeyEvent.action == android.view.KeyEvent.ACTION_UP) {
    playerViewModel.playPrevRecommend()
    return@onPreviewKeyEvent true
}
```

- [ ] **步骤 5：编译验证**

运行：`./gradlew :app:compileDefaultDebugKotlin`
预期：BUILD SUCCESSFUL。若 `VideoListItem` 或 `UgcItem` 字段名不符，按实际修正。

- [ ] **步骤 6：Commit**

```bash
git add app/src/main/kotlin/dev/aaa1115910/bv/screen/settings/content/AudioVideoSetting.kt app/src/main/kotlin/dev/aaa1115910/bv/viewmodel/player/VideoPlayerV3ViewModel.kt app/src/main/kotlin/dev/aaa1115910/bv/activities/video/
git commit -m "feat(player): 刷视频接入推荐流（PlayRecommend 动作 + 上/下一个键）"
```

---

## 任务 11：GitHub Actions 测试工作流

**文件：**
- 创建：`.github/workflows/test.yml`

- [ ] **步骤 1：编写测试工作流**

```yaml
# .github/workflows/test.yml
name: Test

on:
  push:
    branches: [ feat/account-switching, main, master ]
  pull_request:

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
          submodules: 'true'
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v4
      - name: Run unit tests
        run: ./gradlew testDebugUnitTest :bili-api:test
```

> APK 打包沿用现有 `release.yml`（tag 推送）/ `alpha.yml`（手动或 alpha 分支），已配置 `submodules: true`。无需新建打包 workflow。

- [ ] **步骤 2：本地运行全部测试验证**

运行：`./gradlew testDebugUnitTest :bili-api:test`
预期：所有测试 PASS（AccountResolverTest 8 个 + RecommendFeedQueueTest 3 个 + 既有 GithubApiTest）。

- [ ] **步骤 3：Commit**

```bash
git add .github/workflows/test.yml
git commit -m "ci: 新增 test 工作流运行 JVM 单测"
```

---

## 自检结果

**1. 规格覆盖度：** 对照设计文档逐章核对：
- 数据模型与存储（§4.1）→ 任务 1（类型）+ 任务 3（Prefs）。覆盖。
- 认证路由（§4.2）→ 任务 2（Resolver）+ 任务 5/6/7（4 类 Repository）+ 任务 3/4（Provider/Fetcher）。覆盖。LikeRepository/CoinRepository 设计中归主账号，保持用 `authRepository`（=主账号）不改，已说明。
- TV 设置页 UI（§4.3）→ 任务 8。覆盖。
- B 视频连播（§4.4）→ 任务 9（队列）+ 任务 10（PlayRecommend + 键）。覆盖。
- 错误处理与边界（§5）→ 账号删除回退（任务 2 `fetch ?: fallback`）、匿名兜底（任务 2）、推荐流空不卡死（任务 9 `next()` 返回 null，任务 10 `?: return`）。Token 过期 UI 提示标记为实现时细节（Web 返 -101 已有处理路径），不阻塞。
- 测试策略（§6）→ 任务 2/9 纯 JVM 单测 + 任务 11 CI。UI/播放器交互靠编译+代码审查+真机（任务 8/10 已含编译验证步骤）。
- v1 范围与后续（§7）→ gRPC 按场景切换未做（任务 2 `effectiveApiType` 强制 Web 规避），子系统 C 未做。符合 v1。

**2. 占位符扫描：** 无"TODO/待定/补充细节"。任务 8/10 中对 `VideoListItem`、`UgcItem` 字段名标注"以实际为准"并给出验证步骤（编译），非占位符而是已知需对齐的签名。

**3. 类型一致性：** `AccountType` 四值（MAIN/HEARTBEAT/RECOMMEND/VIDEO）全计划一致；`ResolvedAuth` 字段（sessData/biliJct/accessToken/mid/buvid3）一致；`AccountModeProvider`/`AuthDataFetcher` 方法签名一致；`RecommendFeedQueue.VideoRef`/`Source` 一致；`ActionAfterPlayItems.PlayRecommend` code=4 不与既有（0/1/2/3）冲突。

---

## 执行交接

计划已完成并保存到 `docs/superpowers/plans/2026-07-31-account-switching.md`。两种执行方式：

**1. 子代理驱动（推荐）** - 每个任务调度一个新的子代理，任务间进行审查，快速迭代。

**2. 内联执行** - 在当前会话中使用 executing-plans 执行任务，批量执行并设有检查点。

**选哪种方式？**
