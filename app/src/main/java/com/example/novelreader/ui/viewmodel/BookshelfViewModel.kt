package com.example.novelreader.ui.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.novelreader.data.BookRepository
import com.example.novelreader.data.ComicRepository
import com.example.novelreader.data.ReaderSettings
import com.example.novelreader.data.SettingsRepository
import com.example.novelreader.data.db.Book
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 书架页 ViewModel（小说 + 漫画） */
class BookshelfViewModel(
    private val repository: BookRepository,
    private val comicRepository: ComicRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val books: StateFlow<List<Book>> = repository.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<ReaderSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderSettings())

    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage: StateFlow<String?> = _importMessage.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    /** 导入小说（TXT / EPUB） */
    fun importFile(uri: Uri) {
        viewModelScope.launch {
            _importing.value = true
            repository.importLocalFile(uri)
                .onSuccess { _importMessage.value = "导入成功：${it.title}" }
                .onFailure { _importMessage.value = "导入失败：${it.message}" }
            _importing.value = false
        }
    }

    /** 导入漫画压缩包 / PDF */
    fun importComicFile(uri: Uri) {
        viewModelScope.launch {
            _importing.value = true
            comicRepository.importComicFile(uri)
                .onSuccess { _importMessage.value = "导入成功：${it.title}" }
                .onFailure { _importMessage.value = "导入失败：${it.message}" }
            _importing.value = false
        }
    }

    /** 导入漫画图片文件夹 */
    fun importComicFolder(treeUri: Uri) {
        viewModelScope.launch {
            _importing.value = true
            comicRepository.importImageFolder(treeUri)
                .onSuccess { _importMessage.value = "导入成功：${it.title}" }
                .onFailure { _importMessage.value = "导入失败：${it.message}" }
            _importing.value = false
        }
    }

    /** 漫画封面（取第一页） */
    suspend fun loadComicCover(book: Book, reqWidth: Int): Bitmap? =
        comicRepository.loadCover(book, reqWidth)

    fun clearImportMessage() {
        _importMessage.value = null
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch {
            if (book.type.isComic) comicRepository.deleteComic(book) else repository.deleteBook(book)
        }
    }
}
