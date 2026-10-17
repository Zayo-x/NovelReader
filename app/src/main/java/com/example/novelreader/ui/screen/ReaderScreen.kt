package com.example.novelreader.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.novelreader.data.db.Bookmark
import com.example.novelreader.data.db.Chapter
import com.example.novelreader.ui.theme.readingBgs
import com.example.novelreader.ui.viewmodel.ReaderViewModel

/** 根据亮度档位调整文字颜色。
 * 调亮（level>1）：文字向远离背景色的方向偏移，对比更强更清晰；
 * 调暗（level<1）：文字向背景色靠拢，变淡。 */
private fun adjustBrightness(base: Color, bg: Color, level: Float): Color {
    if (level == 1f) return base
    return if (level > 1f) {
        // 背景偏亮 → 文字变深（向黑靠）；背景偏暗 → 文字变浅（向白靠）
        val target = if (bg.luminance() > 0.5f) Color.Black else Color.White
        lerp(base, target, (level - 1f).coerceIn(0f, 1f))
    } else {
        // 调暗：文字向背景色靠拢，变淡
        lerp(base, bg, (1f - level).coerceIn(0f, 1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    bookId: Long,
    onBack: () -> Unit
) {
    val chapters by viewModel.chapters.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val content by viewModel.currentContent.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()

    var showCatalog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var snackbarMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(bookId) { viewModel.loadBook(bookId) }

    val bg = readingBgs.getOrElse(settings.pageBackground) { readingBgs[0] }

    // 即时调节状态：拖动滑块时立即生效，松手才写入磁盘（避免受控 Slider 回跳/卡顿）
    var liveFontSize by remember { mutableStateOf(settings.fontSize) }
    var liveBrightness by remember { mutableStateOf(settings.textBrightness) }

    // 设置首次加载或外部变更时同步到即时状态
    LaunchedEffect(settings.fontSize) { liveFontSize = settings.fontSize }
    LaunchedEffect(settings.textBrightness) { liveBrightness = settings.textBrightness }

    val textColor = adjustBrightness(bg.text, bg.bg, liveBrightness)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg.bg)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        when {
                            // 单击页面顶部区域 → 打开目录
                            offset.y < height * 0.18f -> showCatalog = true
                            offset.x < width * 0.3 -> viewModel.prevChapter()
                            offset.x > width * 0.7 -> viewModel.nextChapter()
                            // 中间点击：无操作
                        }
                    },
                    onLongPress = { showMenu = true }
                )
            }
            .pointerInput(Unit) {
                // 从左向右滑动退出
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                    onDragEnd = {
                        if (totalDrag > size.width * 0.25f) onBack()
                    }
                )
            }
    ) {
        // 正文
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            Text(
                chapters.getOrNull(currentIndex)?.title ?: "",
                style = MaterialTheme.typography.titleMedium,
                color = bg.text.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                textAlign = TextAlign.Center
            )
            // 章节标题下方细分隔线
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .height(1.dp)
                    .background(bg.text.copy(alpha = 0.12f))
            )

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Text(
                    text = content,
                    fontSize = liveFontSize.sp,
                    lineHeight = (liveFontSize * settings.lineHeight).sp,
                    color = textColor,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                )
            }

            Text(
                text = "${currentIndex + 1} / ${chapters.size}",
                style = MaterialTheme.typography.bodySmall,
                color = bg.text.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                textAlign = TextAlign.Center
            )
        }

        error?.let {
            Box(Modifier.align(Alignment.Center)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(it, color = bg.text)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.openChapter(currentIndex) }) { Text("重试") }
                }
            }
        }

        // 提示消息
        snackbarMsg?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xCC000000), RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(msg, color = Color.White, fontSize = 14.sp)
            }
        }
    }

    if (showCatalog) {
        CatalogSheet(
            chapters = chapters,
            currentIndex = currentIndex,
            onSelect = { index ->
                viewModel.openChapter(index)
                showCatalog = false
            },
            onDismiss = { showCatalog = false }
        )
    }

    if (showBookmarkDialog) {
        BookmarkDialog(
            bookmarks = bookmarks,
            chapters = chapters,
            onJump = { b -> viewModel.openChapter(b.chapterIndex); showBookmarkDialog = false },
            onDelete = { b -> viewModel.deleteBookmark(b) },
            onDismiss = { showBookmarkDialog = false }
        )
    }

    if (showMenu) {
        ReaderMenuSheet(
            fontSize = liveFontSize,
            brightness = liveBrightness,
            onAddBookmark = {
                viewModel.addBookmark(null, 0)
                showMenu = false
                snackbarMsg = "已添加书签"
            },
            onOpenBookmarks = {
                showMenu = false
                showBookmarkDialog = true
            },
            onFontSize = { liveFontSize = it },
            onFontSizeCommit = { viewModel.setFontSize(it) },
            onBrightness = { liveBrightness = it },
            onBrightnessCommit = { viewModel.setTextBrightness(it) },
            onDismiss = { showMenu = false }
        )
    }

    // 提示自动消失
    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let {
            kotlinx.coroutines.delay(1500)
            snackbarMsg = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogSheet(
    chapters: List<Chapter>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                "目录",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyColumn(Modifier.height(400.dp)) {
                items(chapters.size) { i ->
                    val isCurrent = i == currentIndex
                    ListItem(
                        headlineContent = {
                            Text(
                                chapters[i].title,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        leadingContent = {
                            if (isCurrent) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(18.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(2.dp)
                                        )
                                )
                            }
                        },
                        modifier = Modifier.clickable { onSelect(i) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkDialog(
    bookmarks: List<Bookmark>,
    chapters: List<Chapter>,
    onJump: (Bookmark) -> Unit,
    onDelete: (Bookmark) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("书签") },
        text = {
            Column {
                if (bookmarks.isEmpty()) {
                    Text("暂无书签", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.height(300.dp)) {
                        items(bookmarks, key = { it.id }) { b ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        chapters.getOrNull(b.chapterIndex)?.title
                                            ?: "第 ${b.chapterIndex + 1} 章"
                                    )
                                },
                                supportingContent = {
                                    b.note?.let { Text(it) }
                                },
                                trailingContent = {
                                    Row {
                                        TextButton(onClick = { onJump(b) }) { Text("跳转") }
                                        TextButton(onClick = { onDelete(b) }) { Text("删除") }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderMenuSheet(
    fontSize: Float,
    brightness: Float,
    onAddBookmark: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onFontSize: (Float) -> Unit,
    onFontSizeCommit: (Float) -> Unit,
    onBrightness: (Float) -> Unit,
    onBrightnessCommit: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
            Text(
                "菜单",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // 添加书签
            ListItem(
                headlineContent = { Text("添加书签") },
                leadingContent = {
                    Icon(Icons.Filled.BookmarkBorder, null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier.clickable { onAddBookmark() }
            )
            // 跳转书签
            ListItem(
                headlineContent = { Text("书签列表") },
                leadingContent = {
                    Icon(Icons.Filled.Bookmark, null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier.clickable { onOpenBookmarks() }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // 文字亮度
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Brightness6, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("文字亮度", modifier = Modifier.width(80.dp))
                Slider(
                    value = brightness,
                    onValueChange = onBrightness,
                    onValueChangeFinished = { onBrightnessCommit(brightness) },
                    valueRange = 0.4f..1.3f,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${(brightness * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(44.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // 文字大小
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.FormatSize, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("文字大小", modifier = Modifier.width(80.dp))
                Slider(
                    value = fontSize,
                    onValueChange = onFontSize,
                    onValueChangeFinished = { onFontSizeCommit(fontSize) },
                    valueRange = 12f..32f,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${fontSize.toInt()}sp",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(44.dp)
                )
            }
        }
    }
}
