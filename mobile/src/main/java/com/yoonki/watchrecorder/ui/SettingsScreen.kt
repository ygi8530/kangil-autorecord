package com.yoonki.watchrecorder.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yoonki.watchrecorder.Library
import com.yoonki.watchrecorder.PhoneListenerService
import com.yoonki.watchrecorder.ReceivedStore.deleteAfterSync
import com.yoonki.watchrecorder.WatchLink
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val p = LocalPalette.current
    var deleteOnWatch by remember { mutableStateOf(context.deleteAfterSync) }
    var watch by remember { mutableStateOf<String?>(null) }
    val recordings = remember { Library.load(context) }
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }

    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) {
        while (true) {
            WatchLink.connectedWatch(context) { watch = it }
            delay(5_000)
        }
    }

    LazyColumn(Modifier.fillMaxSize().background(p.background), contentPadding = WindowInsets.systemBars.asPaddingValues()) {
        item {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onBack).padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBackIos, null, tint = p.accent)
                Text("녹음", color = p.accent, fontSize = 17.sp)
            }
            Text("설정", color = p.label, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))

            SectionHeader("워치")
            GroupedCard {
                SettingsRow("연결 상태", Icons.Filled.Watch, if (watch != null) IosColors.green else IosColors.gray, value = watch ?: "연결 안 됨")
                RowDivider(58)
                SettingsRow("폰에 저장되면 워치에서 삭제", Icons.Filled.DeleteSweep, IosColors.red, trailing = {
                    Switch(
                        checked = deleteOnWatch,
                        onCheckedChange = { deleteOnWatch = it; WatchLink.setDeleteAfterSync(context, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = IosColors.green, checkedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent),
                    )
                })
            }
            SectionFooter(
                "켜면 폰에 저장되고 크기까지 확인된 녹음만 워치에서 지워요. 워치가 연결돼 있지 않으면 다음에 연결될 때 적용돼요. " +
                    "워치와 끊겨 있을 때 녹음한 파일은 워치에 보관됐다가 연결되는 즉시 자동으로 넘어와요."
            )

            SectionHeader("보관함")
            GroupedCard {
                SettingsRow("녹음", Icons.Filled.LibraryMusic, IosColors.orange, value = "${recordings.size}개")
                RowDivider(58)
                SettingsRow("즐겨찾기", Icons.Filled.Star, IosColors.yellow, value = "${recordings.count { it.favorite }}개")
                RowDivider(58)
                SettingsRow("총 길이", Icons.Filled.Storage, IosColors.purple, value = Format.totalDuration(recordings.sumOf { it.durationMs }))
                RowDivider(58)
                SettingsRow("사용 용량", Icons.Filled.Storage, IosColors.indigo, value = Format.size(recordings.sumOf { it.size }))
                RowDivider(58)
                SettingsRow("저장 위치", Icons.Filled.Folder, IosColors.blue, value = PhoneListenerService.RELATIVE_PATH.trimEnd('/'))
            }

            SectionHeader("정보")
            GroupedCard {
                SettingsRow("버전", Icons.Filled.Info, IosColors.gray, value = version)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
