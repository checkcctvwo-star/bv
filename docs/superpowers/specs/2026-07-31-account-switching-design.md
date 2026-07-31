# 账号切换功能设计 (A + B)

- **日期**: 2026-07-31
- **项目**: BV-news（`Frost819/bv` fork，Kotlin / Jetpack Compose / Android TV）
- **范围**: 子系统 A（按场景账号切换）+ 子系统 B（视频连播）。子系统 C（大屏自动播放推荐首页）推迟到后续规格。
- **参考实现**: PiliPlus（Flutter）的 `AccountType` + `accountMode` + per-request account 机制。借鉴设计，不照搬代码。

---

## 1. 概述

为 BV TV 客户端增加"按场景账号切换"：4 个方向（主账号 / 记录观看 / 推荐 / 取流）各自可指定不同的 B站账号或匿名。同时增加"刷视频"连播：播放时按遥控器键或播完自动切到推荐流的下一个视频。

参考 PiliPlus 的 `AccountType` 枚举（`main / heartbeat / recommend / video`）与 `accountMode` 数组设计，移植到 BV 的 Kotlin 代码库。

---

## 2. 需求决策摘要

| 决策点 | 选择 |
|---|---|
| v1 范围 | A（账号切换）+ B（视频连播）；C 推迟 |
| 账号模型 | PiliPlus 式：4 方向各指派任意已登录账号或匿名；记录观看匿名→回退主账号；取流匿名→游客 |
| 主账号覆盖 | 所有未细分操作（点赞 / 投币 / 收藏 / 关注 / 稍后再看 / 动态 / 搜索 / 用户信息） |
| 记录观看 | 读取 + 上报都用 heartbeat 账号（匿名回退主账号） |
| TV UI | 全在设置页：账号池 + 快速/详细模式开关 + 四宫格（详细模式） |
| 模式 | 快速 = 全局单账号（=现有行为）；详细 = 四宫格按方向指派 |
| B 来源 | 推荐流（用 recommend 账号） |
| B 触发 | 遥控器键切上/下一个 + 播完自动接下一个（开关，集成进现有"播放完成动作"） |
| B 入口 | 点击视频进全屏即可刷（BV 已默认全屏） |
| 接口路径 | Web 优先 v1；gRPC/App 按场景切换推迟 |

**假设（实现时确认）**：B 支持回到上一个；刷视频模式受现有"是否显示视频详情页"设置控制。

---

## 3. 系统架构

### 3.1 现有架构要点（来自代码探索）

- **多账号已存在**：Room 表 `user`（`UserDB`：id, uid, username, avatar, auth(JSON), lock），`UserDao` 提供 CRUD。`UserRepository` 用 Compose `mutableStateOf` 持有当前账号，`setUser()` 切换当前账号（触发 `BVApp.initRepository()` 重建 channel/代理）。
- **认证三处存储**：Room（多账号）、`Prefs`（DataStore + 内存 `MutableStateFlow`，当前账号）、`AuthRepository`（`@Single`，运行时：sessionData, biliJct, accessToken, mid, buvid3, buvid）。
- **HTTP 已是 per-request 参数**：`BiliHttpApi`（Ktor+OkHttp）每个方法显式接收 `sessData / accessKey / csrf`，方法内 `header("Cookie", ...)` 或 `parameter("access_key", ...)`。拦截器只有 `injectBuvid3Cookie`（设备标识）和 `encApiSign`（WBI/App 签名）。**无全局认证拦截器**。
- **gRPC accessKey 写死 channel**：`Channel.generateChannel(accessKey, buvid)` 的 `MetadataInterceptor` 把 accessKey 写入 channel metadata；`ChannelRepository.defaultChannel` 单例，所有 stub 共享。→ gRPC 路径无法 per-request 切账号。
- **现有播放器连播基础**：`VideoPlayerV3Activity`（默认全屏）、`VideoPlayerV3ViewModel`（`uploadHistory` 调 `sendHeartbeat`）；设置项"播放完成动作""连续播放相关视频""是否显示视频详情页""播放器自定义快捷键""隐身模式(`incognitoMode`)"。
- **DI**：Koin（`@Module @ComponentScan`、`@Single`、`@KoinViewModel`）。`ApiType`（Web/App）是 per-call 参数。

### 3.2 新增组件

| 组件 | 类型 | 职责 |
|---|---|---|
| `AccountType` | enum | `MAIN, HEARTBEAT, RECOMMEND, VIDEO`（对齐 PiliPlus） |
| `accountMode` | Prefs 持久化 | `AccountType -> uid?` 映射（null=匿名）+ `accountModeEnabled`（快速/详细） |
| `AccountResolver` | Koin `@Single` | 按 `AccountType` 返回对应 `AuthData`（查 accountMode→uid→`UserRepository` 取 AuthData；null=匿名） |

### 3.3 认证数据流

```
设置页(四宫格指派) → accountMode (Prefs/DataStore)
   → AccountResolver.resolve(type) → AuthData
   → Repository 方法(recommend/heartbeat/playurl/history/主账号类)
   → BiliHttpApi.xxx(sessData=该账号.sessData, csrf=该账号.biliJct)  [Web per-request]
   → B站 Web API
```

gRPC/App 路径 v1 仅服务主账号/快速模式（沿用现有 `defaultChannel`）。非主账号方向强制 `ApiType.Web`。

---

## 4. 详细设计

### 4.1 数据模型与存储

- 新增 `AccountType` 枚举：`MAIN, HEARTBEAT, RECOMMEND, VIDEO`（主账号 / 记录观看 / 推荐 / 取流）。
- 新增 `accountMode: AccountType -> uid?` 映射（uid=null = 匿名），存于 `Prefs`（`PrefDelegate`，DataStore + 内存 `MutableStateFlow`，与现有设置项一致）。
  - 存储形式：4 个可空 uid 字段（`mainUid, heartbeatUid, recommendUid, videoUid`），用 `PrefDelegate` 持久化（与现有 `uid`/`sessData` 等 key 一致的逐字段模式）。
- 新增 `accountModeEnabled: Boolean`（false=快速，true=详细）。
  - 快速模式语义：4 方向都跟随 `mainUid`（=当前单账号行为）。
- 复用 `UserDB` / `UserDao` 作为账号池。`UserRepository` 增加 `getAuthDataByUid(uid): AuthData?`（从 Room 取该账号 auth JSON 反序列化）。
- 匿名账号：复用 `Prefs.buvid3`，sessData 为空。
- 模式切换迁移：
  - 快速→详细：保留 `mainUid`，其余方向默认 null（匿名），让用户显式指派。
  - 详细→快速：全部方向归一到 `mainUid`。

### 4.2 认证路由

- 新增 `AccountResolver`（Koin `@Single`）：
  - `resolve(type: AccountType): AuthData`：查 `accountMode[type]` 得 uid；uid 非空→`UserRepository.getAuthDataByUid(uid)`；uid 空→匿名 AuthData（仅 buvid3）。
  - 兜底：`HEARTBEAT` 匿名→回退 `MAIN`；`VIDEO` 匿名→游客（空 sessData）；快速模式下所有方向→`MAIN`。
- 改造 4 类调用方，从 `AccountResolver` 取对应方向 AuthData，传给 `BiliHttpApi` 的 `sessData/csrf` 参数：
  - 推荐：`RecommendVideoRepository.getRecommendVideos` / `getPopularVideos`
  - 记录观看：`VideoPlayRepository.sendHeartbeat` + `HistoryRepository.getHistories`（读 + 写）
  - 取流：`VideoPlayRepository.getPlayData` / `getPgcPlayData`
  - 主账号：`LikeRepository` / `CoinRepository` / 收藏 / 关注 / 稍后再看 / 动态 / 搜索 / 用户信息
- **Web 优先**：非主账号方向的调用强制 `ApiType.Web`（即使全局设了 App）；主账号方向尊重现有 ApiType 设置。
  - 即：`resolve(type)` 返回非 main 账号时，该次调用 `preferApiType = ApiType.Web`。
- `LikeRepository` / `CoinRepository` 现仅 Web（无 preferApiType），天然兼容。

### 4.3 TV 设置页 UI

- 新建设置入口"账号与方向"（或在现有账号区扩展），含三部分：
  1. **账号池**：复用 `UserSwitchScreen` / `UserSwitchViewModel` 逻辑（扫码添加、列表、删除、账号锁）。
  2. **模式开关**：快速 / 详细（Compose Switch，遥控器可切）。
  3. **四宫格**（仅详细模式显示）：2×2 卡片（主账号 / 记录观看 / 推荐 / 取流），每张显示当前账号头像 + 名称或"匿名"；OK 打开账号选择器（账号池列表 + "匿名"选项）。
- 快速模式下隐藏四宫格，只显示账号池 + 当前账号（= 现有单账号行为）。
- 全部在设置页内，无播放中浮层。

### 4.4 B 视频连播

- **来源**：推荐流（用 `RECOMMEND` 账号，经 `AccountResolver`）。维护推荐视频队列，快见底时预取下一页（`RecommendVideoRepository` 分页）。
- **入口**：点击视频进全屏（BV 已默认全屏），即进入可刷模式。
- **触发**：
  - 遥控器键切下一个 / 上一个（复用"播放器自定义快捷键"机制）。
  - 播完自动接下一个：在现有"播放完成动作"设置中新增选项"播放推荐流下一个"。
- **上一个**：保留本会话已看列表，支持回看。
- **与现有连续播放整合**：复用 `VideoPlayerV3ViewModel` 的连播基础设施（现有"连续播放相关视频"），把来源从"相关视频"换成"推荐流"。
- **详情页**：刷视频模式直接进播放器（受现有"是否显示视频详情页"设置控制）。

---

## 5. 错误处理与边界

- **匿名兜底**：取流匿名→游客看免费；推荐匿名→buvid 推荐；记录观看匿名→回退主账号。
- **Token 过期**：Web SESSDATA 过期→请求返 -101，UI 提示"该方向账号需重新登录"，不影响其他方向。
- **账号被删除**：`accountMode` 指向已删除 uid→`getAuthDataByUid` 返回 null→视为匿名，走兜底。
- **模式切换**：见 4.1 迁移规则，保证不丢主账号。
- **ApiType 强制 Web**：非主账号方向强制 Web；若该接口无 Web 版本（罕见），回退主账号并提示。
- **推荐流空 / 失败（B）**：刷视频时推荐流获取失败→提示并停留当前视频，不卡死；队列见底预取失败→重试一次再提示。

---

## 6. 测试策略

落实"测试全包"要求（见 memory `thorough-self-testing`）。

- **TDD**：核心逻辑先写测试再实现（`tdd` 技能）。
- **JVM 单测**（不依赖设备，本地可跑可验证）：
  - `AccountResolver`：4 方向取账号、匿名兜底、heartbeat 回退 main、video 匿名游客、快速模式全→main。
  - `accountMode`：快速/详细切换、映射持久化、删账号后回退。
  - ApiType 强制：非主账号→Web，主账号→尊重设置。
  - 推荐队列：预取、见底补页、空列表处理。
- **代码审查**：`code-review` 技能（双轴线，并行子代理）。
- **完成前验证**：`verification-before-completion`，交付前跑 `./gradlew testDebugUnitTest` 确认通过。
- **APK 打包**：GitHub Actions（注意 `libs` git submodule 需初始化；见 memory `apk-build-github-actions`）。
- 真机不便的部分：UI / 播放器交互写 instrumented 测试供 CI 跑；核心逻辑靠 JVM 单测本地验证。

---

## 7. v1 范围与后续

- **v1**：A（账号切换，Web 优先）+ B（视频连播，推荐流）。
- **后续 1**：gRPC/App 路径按场景切换（把 accessKey 从 channel metadata 改为 per-call，或为每账号建独立 channel）。
- **后续 2**：子系统 C（默认大屏自动播放推荐首页，横屏自动连播、自动下滑）。依赖 B 的连播机制 + A 的 recommend 账号。

---

## 8. 参考实现（PiliPlus）

- `lib/models/common/account_type.dart`：`enum AccountType { main, heartbeat, recommend, video }`
- `lib/utils/accounts/api_type.dart`：`ApiType.apiTypeSet` 把每个 API 归类到 AccountType。
- `lib/utils/accounts.dart`：`Accounts.accountMode`（List<Account>，长度 4）、`set/get/refresh/clear`。
- `lib/utils/accounts/account.dart`：`sealed class Account`、`LoginAccount`（cookieJar + accessKey + grpcHeaders）、`AnonymousAccount`、`NoAccount`。
- `lib/http/init.dart`：`extra: {'account': account}` per-request。
- 关键差异：PiliPlus gRPC accessKey 按调用传（grpcHeaders），BV 写死 channel。v1 用 Web 路径规避此差异。
