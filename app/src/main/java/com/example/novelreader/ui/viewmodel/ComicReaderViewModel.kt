package com.example.novelreader.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.novelreader.data.BookRepository
import com.example.novelreader.data.ComicRepository
import com.example.novelreader.data.db.Book
import com.example.novelreader.data.db.Chapter
import com.example.novelreader.data.db.ComicPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 漫画阅读器 ViewModel：管理页面列表、章节与阅读进度 */
class ComicReaderViewModel(
    private val repository: BookRepository,
    private val comicRepository: ComicRepository
) : ViewModel() {

    private val _book = MutableStateFlow<Book?>(null)
    val book: StateFlow<Book?> = _book.asStateFlow()

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters.asStateFlow()

    /** 打平后的全部页面（按 章节 → 页码 排序） */
    private val _pages = MutableStateFlow<List<ComicPage>>(emptyList())
    val pages: StateFlow<List<ComicPage>> = _pages.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** 打开时应该定位到的页下标 */
    private val _startIndex = MutableStateFlow(0)
    val startIndex: StateFlow<Int> = _startIndex.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadBook(bookId: Long) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val book = repository.getBook(bookId)
            if (book == null) {
                _error.value = "书籍不存在"
                _loading.value = false
                return@launch
            }
            _book.value = book
            _chapters.value = repository.getChapters(bookId)
            val pages = comicRepository.getPages(bookId)
            _pages.value = pages
            if (pages.isEmpty()) {
                _error.value = "没有可显示的页面"
            }
            // 还原上次阅读到的页
            _startIndex.value = pages.indexOfFirst {
                it.chapterIndex == book.lastChapterIndex && it.pageIndex == book.lastReadPosition
            }.coerceAtLeast(0)
            _loading.value = false
        }
    }

    /** 解码一页图片（供界面调用） */
    suspend fun loadPage(page: ComicPage, reqWidth: Int, reqHeight: Int): Bitmap? {
        val book = _book.value ?: return null
        return comicRepository.loadPage(book, page, reqWidth, reqHeight)
    }

    /** 翻到某一页后记录进度 */
    fun onPageSettled(flatIndex: Int) {
        val page = _pages.value.getOrNull(flatIndex) ?: return
        val book = _book.value ?: return
        viewModelScope.launch {
            repository.updateProgress(book.id, page.chapterIndex, page.pageIndex)
        }
    }

    /** 某章节的第一页在打平列表中的下标 */
    fun firstPageOfChapter(chapterIndex: Int): Int =
        _pages.value.indexOfFirst { it.chapterIndex == chapterIndex }.coerceAtLeast(0)

    override fun onCleared() {
        _book.value?.let { comicRepository.closeBook(it.id) }
    }
}
