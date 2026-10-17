package com.example.novelreader.data

import android.content.Context
import android.net.Uri
import com.example.novelreader.data.db.Book
import com.example.novelreader.data.db.BookDao
import com.example.novelreader.data.db.Bookmark
import com.example.novelreader.data.db.BookmarkDao
import com.example.novelreader.data.db.Chapter
import com.example.novelreader.data.db.ChapterDao
import com.example.novelreader.data.model.BookType
import com.example.novelreader.data.parser.EpubParser
import com.example.novelreader.data.parser.TxtParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** 书籍仓库：管理本地书籍导入、书架、章节、书签 */
class BookRepository(
    private val context: Context,
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val bookmarkDao: BookmarkDao
) {

    // ---- 书架 ----
    fun observeBooks(): Flow<List<Book>> = bookDao.observeAllBooks()

    suspend fun getBook(id: Long): Book? = bookDao.getBook(id)

    suspend fun deleteBook(book: Book) {
        bookDao.delete(book)
        chapterDao.deleteByBook(book.id)
    }

    // ---- 本地书籍导入 ----
    suspend fun importLocalFile(uri: Uri): Result<Book> = withContext(Dispatchers.IO) {
        try {
            val fileName = queryName(uri) ?: "未命名.txt"
            val extension = fileName.substringAfterLast('.', "").lowercase()
            if (extension in setOf("pdf", "cbz", "zip")) {
                throw IllegalArgumentException("这是漫画格式，请到「漫画」标签页导入")
            }
            val file = copyToStorage(uri, fileName)

            val book = when (extension) {
                "epub" -> importEpub(file, fileName)
                else -> importTxt(file, fileName)
            }
            Result.success(book)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun importTxt(file: File, fileName: String): Book {
        val txtChapters = TxtParser.parse(file).second
        val book = Book(
            title = titleFromFileName(fileName),
            path = file.absolutePath,
            type = BookType.LOCAL_TXT,
            chapterCount = txtChapters.size
        )
        val bookId = bookDao.insert(book)
        val chapters = txtChapters.mapIndexed { i, c ->
            Chapter(bookId = bookId, title = c.title, index = i, content = c.content)
        }
        chapterDao.insertAll(chapters)
        return book.copy(id = bookId)
    }

    private suspend fun importEpub(file: File, fileName: String): Book {
        val epubChapters = EpubParser.parse(file).second
        val book = Book(
            title = titleFromFileName(fileName),
            path = file.absolutePath,
            type = BookType.LOCAL_EPUB,
            chapterCount = epubChapters.size
        )
        val bookId = bookDao.insert(book)
        val chapters = epubChapters.mapIndexed { i, c ->
            Chapter(bookId = bookId, title = c.title, index = i, content = c.content)
        }
        chapterDao.insertAll(chapters)
        return book.copy(id = bookId)
    }

    /** 从文件名取书名（去掉扩展名） */
    private fun titleFromFileName(fileName: String): String {
        val noExt = fileName.substringBeforeLast('.', fileName).trim()
        return noExt.ifBlank { fileName }
    }

    private fun queryName(uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx)
            }
        }
        return name
    }

    private fun copyToStorage(uri: Uri, fileName: String): File {
        // 保存到 filesDir，避免 cache 目录被系统清理导致文件丢失
        val dir = File(context.filesDir, "books")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "${System.currentTimeMillis()}_$fileName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        } ?: throw IllegalArgumentException("无法打开文件")
        return file
    }

    // ---- 章节 ----
    fun observeChapters(bookId: Long): Flow<List<Chapter>> = chapterDao.observeChapters(bookId)

    suspend fun getChapters(bookId: Long): List<Chapter> = chapterDao.getChapters(bookId)

    suspend fun getChapter(bookId: Long, index: Int): Chapter? = chapterDao.getChapter(bookId, index)

    // ---- 阅读进度 ----
    suspend fun updateProgress(bookId: Long, chapterIndex: Int, position: Int) {
        val book = bookDao.getBook(bookId) ?: return
        bookDao.update(
            book.copy(
                lastChapterIndex = chapterIndex,
                lastReadPosition = position,
                lastReadTime = System.currentTimeMillis()
            )
        )
    }

    // ---- 书签 ----
    fun observeBookmarks(bookId: Long): Flow<List<Bookmark>> = bookmarkDao.observeBookmarks(bookId)

    suspend fun addBookmark(bookId: Long, chapterIndex: Int, position: Int, note: String?) {
        bookmarkDao.insert(Bookmark(bookId = bookId, chapterIndex = chapterIndex, position = position, note = note))
    }

    suspend fun deleteBookmark(bookmark: Bookmark) = bookmarkDao.delete(bookmark)
}
