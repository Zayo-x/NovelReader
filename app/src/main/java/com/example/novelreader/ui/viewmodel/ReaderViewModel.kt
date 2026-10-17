package com.example.novelreader.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.novelreader.data.BookRepository
import com.example.novelreader.data.ReaderSettings
import com.example.novelreader.data.SettingsRepository
import com.example.novelreader.data.db.Book
import com.example.novelreader.data.db.Bookmark
import com.example.novelreader.data.db.Chapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 阅读器 ViewModel：管理章节、进度、书签、设置 */
class ReaderViewModel(
    private val repository: BookRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _book = MutableStateFlow<Book?>(null)
    val book: StateFlow<Book?> = _book.asStateFlow()

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _currentContent = MutableStateFlow("")
    val currentContent: StateFlow<String> = _currentContent.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    val settings: StateFlow<ReaderSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderSettings())

    fun loadBook(bookId: Long) {
        viewModelScope.launch {
            val b = repository.getBook(bookId) ?: return@launch
            _book.value = b
            val chapters = repository.getChapters(bookId)
            _chapters.value = chapters
            val targetIndex = b.lastChapterIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
            _currentIndex.value = targetIndex
            // 关键：打开书籍时立即加载当前章节正文
            loadContent(targetIndex)
        }
        // 书签单独观察，避免阻塞 loadBook 主体
        viewModelScope.launch {
            repository.observeBookmarks(bookId).collect { _bookmarks.value = it }
        }
    }

    fun openChapter(index: Int) {
        if (index !in _chapters.value.indices) return
        _currentIndex.value = index
        loadContent(index)
        // 切换章节时保存进度（位置记为新章节开头）
        val book = _book.value ?: return
        viewModelScope.launch { repository.updateProgress(book.id, index, 0) }
    }

    fun nextChapter() = openChapter(_currentIndex.value + 1)

    fun prevChapter() = openChapter(_currentIndex.value - 1)

    private fun loadContent(index: Int) {
        val chapter = _chapters.value.getOrNull(index) ?: return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                // 本地书籍：正文已入库，直接读取
                _currentContent.value = chapter.content ?: ""
            } finally {
                _loading.value = false
            }
        }
    }

    /** 记录阅读进度（翻页/退出时调用） */
    fun saveProgress(position: Int) {
        val book = _book.value ?: return
        viewModelScope.launch {
            repository.updateProgress(book.id, _currentIndex.value, position)
        }
    }

    fun addBookmark(note: String? = null, position: Int) {
        val book = _book.value ?: return
        viewModelScope.launch {
            repository.addBookmark(book.id, _currentIndex.value, position, note)
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch { repository.deleteBookmark(bookmark) }
    }

    fun setFontSize(size: Float) {
        viewModelScope.launch { settingsRepository.setFontSize(size) }
    }

    fun setLineHeight(height: Float) {
        viewModelScope.launch { settingsRepository.setLineHeight(height) }
    }

    fun toggleNightMode() {
        viewModelScope.launch { settingsRepository.setNightMode(!settings.value.isNightMode) }
    }

    fun setPageBackground(bg: Int) {
        viewModelScope.launch { settingsRepository.setPageBackground(bg) }
    }

    fun setTextBrightness(brightness: Float) {
        viewModelScope.launch { settingsRepository.setTextBrightness(brightness) }
    }
}
