package com.example.novelreader.ui.screen

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.novelreader.data.db.Book
import com.example.novelreader.ui.viewmodel.BookshelfViewModel
import kotlin.math.abs

// 书卷封面配色
private val coverPalette = listOf(
    Color(0xFF5D737E), // 黛青
    Color(0xFF8E6F4E), // 棕褐
    Color(0xFF6B705C), // 橄榄
    Color(0xFF9C6644), // 朱陶
    Color(0xFF4A5D6C), // 蓝灰
    Color(0xFF557A67), // 墨绿
    Color(0xFF8C5E58), // 赭红
    Color(0xFF5B6B8C)  // 群青
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BookshelfScreen(
    viewModel: BookshelfViewModel,
    onOpenBook: (Book) -> Unit,
    onOpenSettings: () -> Unit
) {
    val books by viewModel.books.collectAsState()
    val importing by viewModel.importing.collectAsState()
    val importMessage by viewModel.importMessage.collectAsState()
    var bookToDelete by remember { mutableStateOf<Book?>(null) }
    // 用 rememberSaveable：从阅读器返回后仍停留在原来的标签页
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var showComicImport by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val novelBooks = remember(books) { books.filter { !it.type.isComic } }
    val comicBooks = remember(books) { books.filter { it.type.isComic } }
    val currentList = if (selectedTab == 0) novelBooks else comicBooks

    // 小说：TXT / EPUB
    val novelPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { viewModel.importFile(it) } }

    // 漫画压缩包 / PDF
    val comicFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { viewModel.importComicFile(it) } }

    // 漫画图片文件夹
    val comicFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? -> uri?.let { viewModel.importComicFolder(it) } }

    LaunchedEffect(importMessage) {
        importMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearImportMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    modifier = Modifier.clickable(onClick = onOpenSettings),
                    title = {
                        Text(
                            "翰墨",
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 2.sp
                        )
                    }
                )
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("小说") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("漫画") }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedTab == 0) {
                        novelPicker.launch(arrayOf("*/*"))
                    } else {
                        showComicImport = true
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "导入")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (currentList.isEmpty() && !importing) {
                if (selectedTab == 0) {
                    EmptyShelf(onImport = { novelPicker.launch(arrayOf("*/*")) })
                } else {
                    EmptyComicShelf(onImport = { showComicImport = true })
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(currentList, key = { it.id }) { book ->
                        BookCard(
                            book = book,
                            loadCover = { b, w -> viewModel.loadComicCover(b, w) },
                            onClick = { onOpenBook(book) },
                            onLongClick = { bookToDelete = book }
                        )
                    }
                }
            }
            if (importing) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    // 漫画导入方式选择
    if (showComicImport) {
        ModalBottomSheet(onDismissRequest = { showComicImport = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    "导入漫画",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                ListItem(
                    headlineContent = { Text("压缩包（CBZ / ZIP）") },
                    supportingContent = { Text("按压缩包内文件夹自动分话") },
                    leadingContent = {
                        Icon(Icons.Filled.FolderZip, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier.clickable {
                        showComicImport = false
                        comicFilePicker.launch(arrayOf("*/*"))
                    }
                )
                ListItem(
                    headlineContent = { Text("PDF 文档") },
                    supportingContent = { Text("按 PDF 页翻页阅读") },
                    leadingContent = {
                        Icon(Icons.Filled.PictureAsPdf, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier.clickable {
                        showComicImport = false
                        comicFilePicker.launch(arrayOf("application/pdf"))
                    }
                )
                ListItem(
                    headlineContent = { Text("图片文件夹") },
                    supportingContent = { Text("按子文件夹自动分话") },
                    leadingContent = {
                        Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier.clickable {
                        showComicImport = false
                        comicFolderPicker.launch(null)
                    }
                )
            }
        }
    }

    bookToDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { bookToDelete = null },
            title = { Text("删除内容") },
            text = {
                Text(
                    if (book.type.isComic) "确定要从书架删除《${book.title}》吗？本地漫画文件也会一并删除。"
                    else "确定要从书架删除《${book.title}》吗？阅读进度和章节也会一并删除。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBook(book)
                    bookToDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { bookToDelete = null }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCard(
    book: Book,
    loadCover: suspend (Book, Int) -> Bitmap?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val cover = coverPalette[abs(book.title.hashCode()) % coverPalette.size]
    val darker = cover.copy(
        red = cover.red * 0.62f,
        green = cover.green * 0.62f,
        blue = cover.blue * 0.62f
    )
    val firstChar = book.title.firstOrNull()?.toString() ?: "书"

    // 漫画封面：异步取第一页
    val coverBitmap by produceState<Bitmap?>(initialValue = null, book.id, book.type) {
        value = if (book.type.isComic) loadCover(book, 400) else null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // 封面
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.verticalGradient(listOf(cover, darker))),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = coverBitmap
                if (bitmap != null && !bitmap.isRecycled) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            firstChar,
                            fontSize = 46.sp,
                            color = Color.White.copy(alpha = 0.92f),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .size(width = 36.dp, height = 2.dp)
                                .background(Color.White.copy(alpha = 0.35f))
                        )
                    }
                }
                // 漫画角标
                if (book.type.isComic) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("漫", color = Color.White, fontSize = 10.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // 书名（文件名，不带后缀）
            Text(
                book.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().height(40.dp)
            )
        }
    }
}

@Composable
private fun EmptyShelf(onImport: () -> Unit) {
    EmptyState(
        icon = {
            Icon(
                Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = "书架空空如也",
        description = "点击下方按钮导入本地小说\n支持 TXT 与 EPUB 格式",
        buttonText = "导入小说",
        onImport = onImport
    )
}

@Composable
private fun EmptyComicShelf(onImport: () -> Unit) {
    EmptyState(
        icon = {
            Icon(
                Icons.Filled.CollectionsBookmark,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = "还没有漫画",
        description = "支持 CBZ / ZIP 压缩包、PDF 文档\n以及图片文件夹",
        buttonText = "导入漫画",
        onImport = onImport
    )
}

@Composable
private fun EmptyState(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    buttonText: String,
    onImport: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(Modifier.height(24.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onImport) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(buttonText)
        }
    }
}
