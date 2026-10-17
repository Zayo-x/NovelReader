package com.example.novelreader.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import com.example.novelreader.data.db.Book
import com.example.novelreader.data.db.BookDao
import com.example.novelreader.data.db.BookmarkDao
import com.example.novelreader.data.db.Chapter
import com.example.novelreader.data.db.ChapterDao
import com.example.novelreader.data.db.ComicPage
import com.example.novelreader.data.db.ComicPageDao
import com.example.novelreader.data.model.BookType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/** 自然序比较：page2 排在 page10 之前 */
private val naturalOrder = Comparator<String> { a, b ->
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val ca = a[i]
        val cb = b[j]
        if (ca.isDigit() && cb.isDigit()) {
            var x = 0L
            while (i < a.length && a[i].isDigit()) {
                x = x * 10 + (a[i] - '0')
                i++
            }
            var y = 0L
            while (j < b.length && b[j].isDigit()) {
                y = y * 10 + (b[j] - '0')
                j++
            }
            if (x != y) return@Comparator x.compareTo(y)
        } else {
            val d = ca.lowercaseChar().compareTo(cb.lowercaseChar())
            if (d != 0) return@Comparator d
            i++
            j++
        }
    }
    (a.length - i).compareTo(b.length - j)
}

/** 图片文件夹扫描结果 */
private data class FolderImage(val folder: String, val name: String, val uri: String)

/**
 * 漫画仓库：负责漫画导入（CBZ/ZIP、PDF、图片文件夹）与页面查询。
 *
 * 章节划分规则：
 * - 压缩包：按图片所在目录分组，同一目录 → 一话；全在根目录 → 单章「全书」
 * - 图片文件夹：按子文件夹分组，同上
 * - PDF：整份文档为一章，每页按 PDF 页序号渲染
 */
class ComicRepository(
    private val context: Context,
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val comicPageDao: ComicPageDao,
    private val bookmarkDao: BookmarkDao,
    private val loader: ComicPageLoader
) {

    companion object {
        private val IMAGE_EXT = setOf(
            "jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif", "avif"
        )
        private const val ROOT_CHAPTER = "全书"
        private const val TAG = "ComicImport"
    }

    // ---- 查询 ----

    fun observePages(bookId: Long): Flow<List<ComicPage>> = comicPageDao.observePages(bookId)

    suspend fun getPages(bookId: Long): List<ComicPage> = comicPageDao.getPages(bookId)

    suspend fun pageCount(bookId: Long): Int = comicPageDao.countPages(bookId)

    /** 解码指定页 */
    suspend fun loadPage(book: Book, page: ComicPage, reqWidth: Int, reqHeight: Int): Bitmap? =
        loader.load(book, page, reqWidth, reqHeight)

    /** 取第一页作为书架封面 */
    suspend fun loadCover(book: Book, reqWidth: Int): Bitmap? {
        val first = comicPageDao.getPages(book.id).firstOrNull() ?: return null
        return loader.load(book, first, reqWidth, reqWidth * 2)
    }

    fun closeBook(bookId: Long) = loader.closeBook(bookId)

    // ---- 导入 ----

    /** 导入漫画文件（按扩展名识别 CBZ/ZIP 与 PDF） */
    suspend fun importComicFile(uri: Uri): Result<Book> = withContext(Dispatchers.IO) {
        runCatching {
            val name = queryName(uri) ?: "未命名"
            when (name.substringAfterLast('.', "").lowercase()) {
                "pdf" -> importPdf(uri, name)
                "cbz", "zip" -> importArchive(uri, name)
                else -> throw IllegalArgumentException("不支持的格式，请选择 CBZ / ZIP 压缩包或 PDF")
            }
        }
    }

    /** 导入图片文件夹 */
    suspend fun importImageFolder(treeUri: Uri): Result<Book> = withContext(Dispatchers.IO) {
        runCatching {
            // 申请持久化读取权限，避免重启后失去访问
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
            // 注意：树 URI 不能直接查询，必须转成文档 URI，否则 DocumentsProvider 抛 Invalid URI
            val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
            val rootName = runCatching { queryDisplayName(rootDocUri) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: "漫画"
            android.util.Log.d(TAG, "导入图片文件夹 treeUri=$treeUri rootId=$rootId name=$rootName")
            val found = ArrayList<FolderImage>()
            walkImages(
                treeUri,
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootId),
                "",
                found
            )
            android.util.Log.d(TAG, "扫描到 ${found.size} 张图片")
            if (found.isEmpty()) throw IllegalArgumentException("文件夹里没有找到图片")
            buildFolderBook(treeUri, rootName, found)
        }.onFailure {
            android.util.Log.e(TAG, "图片文件夹导入失败", it)
        }
    }

    private suspend fun importArchive(uri: Uri, fileName: String): Book {
        val file = copyToComicDir(uri, fileName)
        val entries = try {
            ZipFile(file).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory && isImageName(it.name) }
                    .map { it.name }
                    .toList()
            }
        } catch (e: Throwable) {
            file.delete()
            throw IllegalArgumentException("无法读取压缩包：${e.message}")
        }
        if (entries.isEmpty()) {
            file.delete()
            throw IllegalArgumentException("压缩包里没有找到图片")
        }

        val groups = groupArchiveEntries(entries)
        val book = Book(
            title = titleFromFileName(fileName),
            path = file.absolutePath,
            type = BookType.LOCAL_CBZ,
            chapterCount = groups.size
        )
        val bookId = bookDao.insert(book)
        saveChaptersAndPages(bookId, groups)
        return book.copy(id = bookId)
    }

    private suspend fun importPdf(uri: Uri, fileName: String): Book {
        val file = copyToComicDir(uri, fileName)
        val pageCount = try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { it.pageCount }
            }
        } catch (e: Throwable) {
            file.delete()
            throw IllegalArgumentException("无法读取 PDF：${e.message}")
        }
        if (pageCount <= 0) {
            file.delete()
            throw IllegalArgumentException("PDF 没有可用页面")
        }

        val book = Book(
            title = titleFromFileName(fileName),
            path = file.absolutePath,
            type = BookType.LOCAL_PDF,
            chapterCount = 1
        )
        val bookId = bookDao.insert(book)
        chapterDao.insertAll(
            listOf(Chapter(bookId = bookId, title = ROOT_CHAPTER, index = 0, content = null))
        )
        comicPageDao.insertAll(
            (0 until pageCount).map {
                ComicPage(bookId = bookId, chapterIndex = 0, pageIndex = it, source = "")
            }
        )
        return book.copy(id = bookId)
    }

    private suspend fun buildFolderBook(
        treeUri: Uri,
        rootName: String,
        found: List<FolderImage>
    ): Book {
        val byFolder = found.groupBy { it.folder }
        val folders = byFolder.keys.sortedWith(naturalOrder)
        val book = Book(
            title = rootName,
            path = treeUri.toString(),
            type = BookType.LOCAL_IMAGE_DIR,
            chapterCount = folders.size
        )
        val bookId = bookDao.insert(book)

        val chapters = ArrayList<Chapter>(folders.size)
        val pages = ArrayList<ComicPage>()
        folders.forEachIndexed { chapterIndex, folder ->
            val title = when {
                folder.isNotEmpty() -> folder.substringAfterLast('/')
                folders.size == 1 -> rootName
                else -> ROOT_CHAPTER
            }
            chapters += Chapter(bookId = bookId, title = title, index = chapterIndex, content = null)
            val images = byFolder.getValue(folder).sortedWith(compareBy(naturalOrder) { it.name })
            images.forEachIndexed { pageIndex, image ->
                pages += ComicPage(
                    bookId = bookId,
                    chapterIndex = chapterIndex,
                    pageIndex = pageIndex,
                    source = image.uri
                )
            }
        }
        chapterDao.insertAll(chapters)
        comicPageDao.insertAll(pages)
        return book.copy(id = bookId)
    }

    // ---- 删除 ----

    /** 删除漫画：数据、文件与目录授权一并清理 */
    suspend fun deleteComic(book: Book) = withContext(Dispatchers.IO) {
        loader.closeBook(book.id)
        comicPageDao.deleteByBook(book.id)
        chapterDao.deleteByBook(book.id)
        bookmarkDao.deleteByBook(book.id)
        bookDao.delete(book)
        when (book.type) {
            BookType.LOCAL_CBZ, BookType.LOCAL_PDF -> {
                book.path?.let { runCatching { File(it).delete() } }
            }
            BookType.LOCAL_IMAGE_DIR -> {
                book.path?.let { path ->
                    runCatching {
                        context.contentResolver.releasePersistableUriPermission(
                            Uri.parse(path),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                }
            }
            else -> Unit
        }
    }

    // ---- 内部工具 ----

    private suspend fun saveChaptersAndPages(
        bookId: Long,
        groups: List<Pair<String, List<String>>>
    ) {
        val chapters = ArrayList<Chapter>(groups.size)
        val pages = ArrayList<ComicPage>()
        groups.forEachIndexed { chapterIndex, (title, items) ->
            chapters += Chapter(bookId = bookId, title = title, index = chapterIndex, content = null)
            items.forEachIndexed { pageIndex, source ->
                pages += ComicPage(
                    bookId = bookId,
                    chapterIndex = chapterIndex,
                    pageIndex = pageIndex,
                    source = source
                )
            }
        }
        chapterDao.insertAll(chapters)
        comicPageDao.insertAll(pages)
    }

    /** 按所在目录把压缩包条目分组成章节 */
    private fun groupArchiveEntries(entries: List<String>): List<Pair<String, List<String>>> {
        val byParent = entries.groupBy { it.substringBeforeLast('/', "") }
        return byParent.keys.sortedWith(naturalOrder).map { parent ->
            val title = if (parent.isEmpty()) ROOT_CHAPTER else parent.substringAfterLast('/')
            title to byParent.getValue(parent).sortedWith(naturalOrder)
        }
    }

    /** 递归扫描图片文件夹 */
    private fun walkImages(
        treeUri: Uri,
        childrenUri: Uri,
        folder: String,
        out: MutableList<FolderImage>
    ) {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val docId = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                val mime = cursor.getString(2) ?: ""
                val childFolder = if (folder.isEmpty()) name else "$folder/$name"
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    runCatching {
                        walkImages(
                            treeUri,
                            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId),
                            childFolder,
                            out
                        )
                    }
                } else if (mime.startsWith("image/") || isImageName(name)) {
                    out += FolderImage(
                        folder = folder,
                        name = name,
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId).toString()
                    )
                }
            }
        }
    }

    private fun isImageName(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in IMAGE_EXT

    /** 从文件名取书名（去掉扩展名） */
    private fun titleFromFileName(fileName: String): String {
        val noExt = fileName.substringBeforeLast('.', fileName).trim()
        return noExt.ifBlank { fileName }
    }

    private fun copyToComicDir(uri: Uri, fileName: String): File {
        val dir = File(context.filesDir, "comics")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "${System.currentTimeMillis()}_$fileName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        } ?: throw IllegalArgumentException("无法打开文件")
        return file
    }

    private fun queryName(uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return null
    }

    private fun queryDisplayName(uri: Uri): String? {
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }
}
