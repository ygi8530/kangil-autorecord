package com.yoonki.watchrecorder.ui

import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yoonki.watchrecorder.Library
import com.yoonki.watchrecorder.Recording
import com.yoonki.watchrecorder.WatchLink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class View(val label: String) { Days("날짜별"), Months("월별"), Favorites("즐겨찾기") }

private sealed interface Dialog {
    data class Rename(val rec: Recording) : Dialog
    data class Note(val rec: Recording) : Dialog
    data class Delete(val recs: List<Recording>) : Dialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(resumeCount: Int, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val p = LocalPalette.current
    val player = remember { Player(context) }
    var recordings by remember { mutableStateOf(Library.load(context)) }
    var view by remember { mutableStateOf(View.Days) }
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    var watch by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    // Multi-select (long-press a row or tap "선택"): batch delete / favorite.
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()

    fun reload() {
        recordings = Library.load(context)
        selected = selected.filterTo(mutableSetOf()) { key -> recordings.any { it.key == key } }
    }

    fun refresh() {
        scope.launch {
            refreshing = true
            reload()
            WatchLink.connectedWatch(context) { watch = it }
            delay(600) // let the spinner be seen; the load itself is instant
            refreshing = false
        }
    }

    fun endSelection() {
        selecting = false
        selected = emptySet()
    }

    LaunchedEffect(resumeCount) { reload() }

    // New files from the watch land in MediaStore → refresh automatically.
    DisposableEffect(Unit) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = reload()
        }
        context.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
            player.release()
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            WatchLink.connectedWatch(context) { watch = it }
            delay(10_000)
        }
    }
    LaunchedEffect(player.playing) {
        while (player.playing) {
            player.tick()
            delay(200)
        }
    }
    BackHandler(enabled = selecting) { endSelection() }

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { reload() }

    fun delete(recs: List<Recording>) {
        recs.forEach { player.stopIf(it.uri) }
        try {
            Library.delete(context, recs)?.let { deleteLauncher.launch(IntentSenderRequest.Builder(it).build()) }
        } catch (e: Exception) {
            Toast.makeText(context, "삭제하지 못했어요: ${e.message}", Toast.LENGTH_LONG).show()
        }
        endSelection()
        reload()
    }

    val filtered = recordings.filter { r ->
        (view != View.Favorites || r.favorite) &&
            (query.isBlank() || listOf(r.title, r.note, r.fileName).any { it?.contains(query, ignoreCase = true) == true })
    }
    val sections: List<Pair<String, List<Recording>>> = when (view) {
        View.Days -> filtered.groupBy { Format.dayKey(it.recordedAt) }.values.map { Format.day(it.first().recordedAt) to it }
        View.Months, View.Favorites -> filtered.groupBy { Format.monthKey(it.recordedAt) }.values.map { Format.month(it.first().recordedAt) to it }
    }
    val selectedRecs = recordings.filter { it.key in selected }

    val todayKey = Format.dayKey(System.currentTimeMillis())

    Box(Modifier.fillMaxSize().background(p.background)) {
    PullToRefreshBox(refreshing, ::refresh, Modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = WindowInsets.systemBars.asPaddingValues(),
    ) {
        item {
            Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selecting) {
                    TextButton(::endSelection) { Text("취소", color = p.accent, fontSize = 17.sp) }
                    Text(
                        if (selected.isEmpty()) "항목 선택" else "${selected.size}개 선택됨",
                        color = p.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    )
                    val all = filtered.isNotEmpty() && filtered.all { it.key in selected }
                    TextButton({ selected = if (all) emptySet() else filtered.map { it.key }.toSet() }) {
                        Text(if (all) "선택 해제" else "전체 선택", color = p.accent, fontSize = 17.sp)
                    }
                } else {
                    WatchPill(watch)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = ::refresh) { Icon(Icons.Filled.Refresh, "새로고침", tint = p.accent) }
                    TextButton({ selecting = true; expanded = null }, enabled = recordings.isNotEmpty()) {
                        Text("선택", color = p.accent, fontSize = 17.sp)
                    }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "설정", tint = p.accent) }
                }
            }
            Text(
                "녹음", color = p.label, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 8.dp),
            )
            SearchField(query, { query = it })
            SegmentedControl(
                View.entries.map { it.label }, view.ordinal, { view = View.entries[it] },
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

        if (sections.isEmpty()) {
            item { EmptyState(view, query.isNotBlank()) }
        }

        sections.forEach { (header, recs) ->
            item(key = "h-$header") {
                SectionHeader(
                    header, "${recs.size}개 · ${Format.totalDuration(recs.sumOf { it.durationMs })}",
                    highlight = view == View.Days && Format.dayKey(recs.first().recordedAt) == todayKey,
                )
            }
            item(key = "c-$header") {
                GroupedCard {
                    recs.forEachIndexed { i, rec ->
                        if (i > 0) RowDivider()
                        SwipeToDelete(enabled = !selecting, onDelete = { dialog = Dialog.Delete(listOf(rec)) }) {
                            RecordingRow(
                                rec = rec,
                                today = Format.dayKey(rec.recordedAt) == todayKey,
                                showDate = view != View.Days,
                                expanded = !selecting && expanded == rec.key,
                                selecting = selecting,
                                selected = rec.key in selected,
                                player = player,
                                onClick = {
                                    if (selecting) selected = if (rec.key in selected) selected - rec.key else selected + rec.key
                                    else expanded = if (expanded == rec.key) null else rec.key
                                },
                                onLongClick = {
                                    if (!selecting) { selecting = true; expanded = null }
                                    selected = selected + rec.key
                                },
                                onFavorite = { Library.update(context, rec, favorite = !rec.favorite); reload() },
                                onRename = { dialog = Dialog.Rename(rec) },
                                onNote = { dialog = Dialog.Note(rec) },
                                onShare = {
                                    context.startActivity(Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).setType("audio/mp4").putExtra(Intent.EXTRA_STREAM, rec.uri)
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "공유"))
                                },
                                onOpen = {
                                    context.startActivity(Intent.createChooser(
                                        Intent(Intent.ACTION_VIEW).setDataAndType(rec.uri, "audio/*")
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "열기"))
                                },
                                onDelete = { dialog = Dialog.Delete(listOf(rec)) },
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(if (selecting) 96.dp else 24.dp)) }
    }
    }

    if (selecting) {
        SelectionBar(
            count = selected.size,
            allFavorite = selectedRecs.isNotEmpty() && selectedRecs.all { it.favorite },
            onFavorite = {
                val fav = !selectedRecs.all { it.favorite }
                selectedRecs.forEach { Library.update(context, it, favorite = fav) }
                endSelection(); reload()
            },
            onShare = {
                val uris = ArrayList(selectedRecs.map { it.uri })
                context.startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND_MULTIPLE).setType("audio/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "공유"))
            },
            onDelete = { dialog = Dialog.Delete(selectedRecs) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
    }

    when (val d = dialog) {
        is Dialog.Rename -> TextDialog("이름 바꾸기", d.rec.title ?: defaultTitle(d.rec), singleLine = true,
            onDismiss = { dialog = null }) { Library.update(context, d.rec, title = it.trim()); reload(); dialog = null }
        is Dialog.Note -> TextDialog("메모", d.rec.note.orEmpty(), singleLine = false,
            onDismiss = { dialog = null }) { Library.update(context, d.rec, note = it.trim()); reload(); dialog = null }
        is Dialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(if (d.recs.size == 1) "녹음 삭제" else "녹음 ${d.recs.size}개 삭제") },
            text = {
                Text(
                    if (d.recs.size == 1) "'${d.recs[0].title ?: defaultTitle(d.recs[0])}'을(를) 폰에서 삭제할까요? 되돌릴 수 없어요."
                    else "선택한 녹음 ${d.recs.size}개(${Format.size(d.recs.sumOf { it.size })})를 폰에서 삭제할까요? 되돌릴 수 없어요."
                )
            },
            confirmButton = { TextButton({ delete(d.recs); dialog = null }) { Text("삭제", color = IosColors.red) } },
            dismissButton = { TextButton({ dialog = null }) { Text("취소") } },
        )
        null -> Unit
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    allFavorite: Boolean,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val enabled = count > 0
    Column(modifier.fillMaxWidth().background(p.card)) {
        HorizontalDivider(thickness = 0.5.dp, color = p.separator)
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 24.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val tint = if (enabled) p.accent else p.secondary
            Action(if (allFavorite) Icons.Filled.Star else Icons.Filled.StarBorder, if (allFavorite) "즐겨찾기 해제" else "즐겨찾기",
                if (enabled && allFavorite) IosColors.yellow else tint) { if (enabled) onFavorite() }
            Action(Icons.Filled.Share, "공유", tint) { if (enabled) onShare() }
            Action(Icons.Filled.Delete, if (enabled) "삭제 ($count)" else "삭제", if (enabled) IosColors.red else p.secondary) { if (enabled) onDelete() }
        }
    }
}

fun defaultTitle(rec: Recording) = "${Format.time(rec.recordedAt)} 녹음"

@Composable
private fun WatchPill(name: String?) {
    val p = LocalPalette.current
    val color = if (name != null) IosColors.green else IosColors.gray
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(p.fill).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Filled.Watch, null, tint = p.secondary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(if (name != null) "워치 연결됨" else "워치 연결 안 됨", color = p.secondary, fontSize = 13.sp)
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.fill)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = p.secondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("검색 (이름, 메모)", color = p.secondary, fontSize = 17.sp)
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = TextStyle(color = p.label, fontSize = 17.sp),
                cursorBrush = SolidColor(p.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EmptyState(view: Any, searching: Boolean) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            when {
                searching -> "검색 결과 없음"
                view == View.Favorites -> "즐겨찾기한 녹음이 없어요"
                else -> "아직 녹음이 없어요"
            },
            color = p.label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (view == View.Favorites) "녹음을 펼쳐 ★을 누르면 여기에 모여요" else "워치에서 홈 키를 두 번 눌러 녹음하면 자동으로 들어와요",
            color = p.secondary, fontSize = 15.sp,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(enabled: Boolean, onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it == SwipeToDismissBoxValue.EndToStart) onDelete()
        false // the confirmation dialog decides; snap back
    })
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = enabled,
        gesturesEnabled = enabled,
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(IosColors.red).padding(end = 20.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(Icons.Filled.Delete, "삭제", tint = Color.White)
            }
        },
    ) { content() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingRow(
    rec: Recording,
    today: Boolean,
    showDate: Boolean,
    expanded: Boolean,
    selecting: Boolean,
    selected: Boolean,
    player: Player,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavorite: () -> Unit,
    onRename: () -> Unit,
    onNote: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val p = LocalPalette.current
    val active = player.uri == rec.uri
    // Today's recordings: faint accent tint + dot (like iOS "unread"), so they stand out in any view.
    val rowBg = when {
        selected -> p.accent.copy(alpha = 0.18f).compositeOver(p.card)
        today -> p.accent.copy(alpha = 0.10f).compositeOver(p.card)
        else -> p.card
    }
    Column(
        Modifier.fillMaxWidth().background(rowBg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selecting) {
                Icon(
                    if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    if (selected) "선택됨" else "선택 안 됨",
                    tint = if (selected) p.accent else p.secondary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
            } else if (today) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(p.accent))
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (rec.favorite) {
                        Icon(Icons.Filled.Star, null, tint = IosColors.yellow, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        rec.title ?: defaultTitle(rec), color = p.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    (if (showDate) Format.full(rec.recordedAt) else Format.time(rec.recordedAt)) + " · " + Format.size(rec.size),
                    color = p.secondary, fontSize = 15.sp,
                )
                if (rec.note != null && !expanded) {
                    Text(rec.note, color = p.secondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(Format.duration(rec.durationMs), color = p.secondary, fontSize = 15.sp)
        }

        AnimatedVisibility(expanded) {
            Column(Modifier.padding(top = 10.dp)) {
                if (rec.note != null) {
                    Text(rec.note, color = p.label, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
                val total = if (active && player.duration > 0) player.duration else rec.durationMs.toInt().coerceAtLeast(1)
                val pos = if (active) player.position else 0
                Slider(
                    value = pos.toFloat() / total,
                    onValueChange = { if (active) player.seekTo((it * total).toInt()) else { player.toggle(rec.uri); player.seekTo((it * total).toInt()) } },
                    colors = SliderDefaults.colors(thumbColor = p.label, activeTrackColor = p.label, inactiveTrackColor = p.separator),
                )
                Row {
                    Text(Format.duration(pos.toLong()), color = p.secondary, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text("-" + Format.duration((total - pos).toLong()), color = p.secondary, fontSize = 12.sp)
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ player.cycleSpeed() }) { Text("${formatSpeed(player.speed)}x", color = p.accent, fontSize = 15.sp) }
                    IconButton({ if (active) player.skip(-10_000) }) { Icon(Icons.Filled.Replay10, "10초 뒤로", tint = p.label, modifier = Modifier.size(30.dp)) }
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(p.label).clickable { player.toggle(rec.uri) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (active && player.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "재생", tint = p.card, modifier = Modifier.size(34.dp))
                    }
                    IconButton({ if (active) player.skip(10_000) }) { Icon(Icons.Filled.Forward10, "10초 앞으로", tint = p.label, modifier = Modifier.size(30.dp)) }
                    Spacer(Modifier.width(48.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Action(if (rec.favorite) Icons.Filled.Star else Icons.Filled.StarBorder, "즐겨찾기", if (rec.favorite) IosColors.yellow else p.accent, onFavorite)
                    Action(Icons.Filled.Edit, "이름", p.accent, onRename)
                    Action(Icons.Filled.StickyNote2, "메모", p.accent, onNote)
                    Action(Icons.Filled.Share, "공유", p.accent, onShare)
                    Action(Icons.AutoMirrored.Filled.OpenInNew, "열기", p.accent, onOpen)
                    Action(Icons.Filled.Delete, "삭제", IosColors.red, onDelete)
                }
            }
        }
    }
}

private fun formatSpeed(s: Float) = if (s == s.toInt().toFloat()) s.toInt().toString() else s.toString()

@Composable
private fun Action(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(24.dp))
        Text(label, color = tint, fontSize = 11.sp)
    }
}

@Composable
private fun TextDialog(title: String, initial: String, singleLine: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = singleLine, minLines = if (singleLine) 1 else 3, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton({ onSave(text) }) { Text("저장") } },
        dismissButton = { TextButton(onDismiss) { Text("취소") } },
    )
}
