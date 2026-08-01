package dev.aaa1115910.bv.screen.settings.content

import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.activities.user.UserSwitchActivity
import dev.aaa1115910.bv.component.settings.SettingSwitchListItem
import dev.aaa1115910.bv.entity.db.UserDB
import dev.aaa1115910.bv.screen.settings.SettingsMenuNavItem
import dev.aaa1115910.bv.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class DirectionCard(
    val title: String,
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
                selected = false,
                onClick = {
                    context.startActivity(Intent(context, UserSwitchActivity::class.java))
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
    val context = LocalContext.current
    val cards = listOf(
        DirectionCard("主账号", users.firstOrNull { it.uid == Prefs.uid }?.username ?: "未登录", Prefs.uid == 0L),
        DirectionCard("记录观看", users.firstOrNull { it.uid == Prefs.accountHeartbeatUid }?.username ?: "匿名", Prefs.accountHeartbeatUid == 0L),
        DirectionCard("推荐", users.firstOrNull { it.uid == Prefs.accountRecommendUid }?.username ?: "匿名", Prefs.accountRecommendUid == 0L),
        DirectionCard("取流", users.firstOrNull { it.uid == Prefs.accountVideoUid }?.username ?: "匿名", Prefs.accountVideoUid == 0L)
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
                selected = false,
                onClick = {
                    if (card.title == "主账号") {
                        context.startActivity(Intent(context, UserSwitchActivity::class.java))
                    } else {
                        onPick(card.title)
                    }
                }
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