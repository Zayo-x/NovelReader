package com.example.novelreader.ui.screen

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.novelreader.data.db.Chapter
import com.example.novelreader.data.db.ComicPage
import com.example.novelreader.ui.viewmodel.ComicReaderViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 漫画阅读器：左右翻页，点击左右两侧翻页，点击中间显隐工具栏 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicReaderScreen(
    viewModel: ComicReaderViewModel,
    bookId: Long,
    onBack: () -> Unit
) {
    val book by viewModel.book.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val pages by viewModel.pages.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val startIndex by viewModel.startIndex.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(bookId) { viewModel.loadBook(bookId) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )

            pages.isEmpty() -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(error ?: "没有可显示的页面", color = Color.White)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onBack) { Text("返回") }
            }

            else -> ComicPager(
                viewModel = viewModel,
                pages = pages,
                chapters = chapters,
                startIndex = startIndex,
                title = book?.title ?: "",
                onBack = onBack
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ComicPager(
    viewModel: ComicReaderViewModel,
    pages: List<ComicPage>,
    chapters: List<Chapter>,
    startIndex: Int,
    title: String,
    onBack: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
        pageCount = { pages.size }
    )
    val scope = rememberCoroutineScope()
    var uiVisible by remember { mutableStateOf(true) }
    var showCatalog by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }

    // 停稳后记录进度
    LaunchedEffect(pagerState.settledPage) {
        viewModel.onPageSettled(pagerState.settledPage)
    }
    LaunchedEffect(pagerState.currentPage) {
        sliderValue = pagerState.currentPage.toFloat()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(pages.size) {
                detectTapGestures(
                    onTap = { offset ->
                        val width = size.width.toFloat()
                        when {
                            offset.x < width * 0.3f -> scope.launch {
                                pagerState.animateScrollToPage(
                                    (pagerState.currentPage - 1).coerceAtLeast(0)
                                )
                            }

                            offset.x > width * 0.7f -> scope.launch {
                                pagerState.animateScrollToPage(
                                    (pagerState.currentPage + 1).coerceAtMost(pages.size - 1)
                                )
                            }

                            else -> uiVisible = !uiVisible
                        }
                    }
                )
            }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondBoundsPageCount = 1,
            key = { pages[it].id }
        ) { index ->
            ComicPageImage(page = pages[index]) { page, reqW, reqH ->
                viewModel.loadPage(page, reqW, reqH)
            }
        }

        if (uiVisible) {
            // 顶部信息栏
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color(0xCC000000))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "第 ${pagerState.currentPage + 1} / ${pages.size} 页",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }

            // 底部翻页栏
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xCC000000))
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { showCatalog = true }) {
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.width(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("目录", color = Color.White)
                }
                if (pages.size > 1) {
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = {
                            scope.launch {
                                pagerState.scrollToPage(
                                    sliderValue.roundToInt().coerceIn(0, pages.size - 1)
                                )
                            }
                        },
                        valueRange = 0f..(pages.size - 1).toFloat(),
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Text(
                    "${pagerState.currentPage + 1}",
                    color = Color.White,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(40.dp)
                )
            }
        }

        if (showCatalog) {
            ModalBottomSheet(onDismissRequest = { showCatalog = false }) {
                Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                    Text(
                        "目录",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    LazyColumn(Modifier.height(400.dp)) {
                        itemsIndexed(chapters, key = { _, c -> c.id }) { _, chapter ->
                            val pageCount = pages.count { it.chapterIndex == chapter.index }
                            ListItem(
                                headlineContent = { Text(chapter.title) },
                                supportingContent = { Text("$pageCount 页") },
                                modifier = Modifier.clickable {
                                    showCatalog = false
                                    scope.launch {
                                        pagerState.scrollToPage(
                                            viewModel.firstPageOfChapter(chapter.index)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 单页解码状态 */
private data class PageLoadState(val finished: Boolean, val bitmap: Bitmap?)

@Composable
private fun ComicPageImage(
    page: ComicPage,
    load: suspend (ComicPage, Int, Int) -> Bitmap?
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val reqWidth = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val reqHeight = with(density) { configuration.screenHeightDp.dp.roundToPx() }

    val state by produceState(PageLoadState(false, null), page.id, reqWidth, reqHeight) {
        value = PageLoadState(true, load(page, reqWidth, reqHeight))
    }

    val bitmap = state.bitmap
    when {
        bitmap != null && !bitmap.isRecycled -> Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        state.finished -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("无法显示这一页", color = Color.White.copy(alpha = 0.6f))
        }

        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
        }
    }
}
