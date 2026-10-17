package com.example.novelreader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.example.novelreader.data.db.Book
import com.example.novelreader.data.db.ComicPage
import com.example.novelreader.data.model.BookType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/**
 * 漫画页面解码器：
 * - CBZ / ZIP：按条目名读取图片
 * - PDF：用系统 PdfRenderer 渲染页面
 * - 图片文件夹：按 SAF document uri 读取
 *
 * 内部维护位图 LRU 缓存，并按书籍缓存 ZipFile / PdfRenderer
 * （用完请调用 [closeBook]，否则文件句柄会一直占用）。
 */
class ComicPageLoader(private val context: Context) {

    private val cache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(
        // 最多占用 1/6 可用堆内存
        (Runtime.getRuntime().maxMemory() / 1024 / 6).toInt().coerceAtLeast(8 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private val zipCache = HashMap<Long, ZipFile>()
    private val pdfCache = HashMap<Long, PdfRenderer>()
    private val pdfLock = Any()
    private val zipLock = Any()

    /** 解码一页；解码失败返回 null */
    suspend fun load(book: Book, page: ComicPage, reqWidth: Int, reqHeight: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            val key = "${book.id}#${page.chapterIndex}#${page.pageIndex}#$reqWidth"
            cache.get(key)?.let { return@withContext it }
            val bitmap = try {
                when (book.type) {
                    BookType.LOCAL_CBZ -> decodeZipEntry(book, page, reqWidth, reqHeight)
                    BookType.LOCAL_PDF -> renderPdfPage(book, page, reqWidth, reqHeight)
                    BookType.LOCAL_IMAGE_DIR -> decodeUri(page.source, reqWidth, reqHeight)
                    else -> null
                }
            } catch (e: Throwable) {
                null
            }
            if (bitmap != null && !bitmap.isRecycled) cache.put(key, bitmap)
            bitmap
        }

    /** 释放某本书占用的 ZipFile / PdfRenderer */
    fun closeBook(bookId: Long) {
        synchronized(zipLock) {
            zipCache.remove(bookId)?.let { runCatching { it.close() } }
        }
        synchronized(pdfLock) {
            pdfCache.remove(bookId)?.let { runCatching { it.close() } }
        }
    }

    // ---- CBZ / ZIP ----

    private fun zipOf(book: Book): ZipFile? {
        val path = book.path ?: return null
        synchronized(zipLock) {
            zipCache[book.id]?.let { return it }
            return runCatching { ZipFile(File(path)) }.getOrNull()?.also { zipCache[book.id] = it }
        }
    }

    private fun decodeZipEntry(book: Book, page: ComicPage, reqW: Int, reqH: Int): Bitmap? {
        val zip = zipOf(book) ?: return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openZipStream(zip, page.source)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, reqW, reqH)
        }
        return openZipStream(zip, page.source)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private fun openZipStream(zip: ZipFile, entryName: String): java.io.InputStream? =
        synchronized(zipLock) {
            val entry = zip.getEntry(entryName) ?: return@synchronized null
            zip.getInputStream(entry)
        }

    // ---- PDF ----

    private fun pdfOf(book: Book): PdfRenderer? {
        val path = book.path ?: return null
        synchronized(pdfLock) {
            pdfCache[book.id]?.let { return it }
            return runCatching {
                val pfd = ParcelFileDescriptor.open(
                    File(path),
                    ParcelFileDescriptor.MODE_READ_ONLY
                )
                PdfRenderer(pfd)
            }.getOrNull()?.also { pdfCache[book.id] = it }
        }
    }

    private fun renderPdfPage(book: Book, page: ComicPage, reqW: Int, reqH: Int): Bitmap? {
        val renderer = pdfOf(book) ?: return null
        return synchronized(pdfLock) {
            if (page.pageIndex !in 0 until renderer.pageCount) return null
            renderer.openPage(page.pageIndex).use { pdfPage ->
                // PDF 页面尺寸单位是 point(1/72 inch)，按屏幕尺寸放大渲染保证清晰度
                val target = maxOf(reqW, reqH).toFloat().coerceAtLeast(720f)
                val pageMax = maxOf(pdfPage.width, pdfPage.height).toFloat().coerceAtLeast(1f)
                val scale = (target / pageMax).coerceIn(1f, 3f)
                val width = (pdfPage.width * scale).toInt().coerceAtLeast(1)
                val height = (pdfPage.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    // ---- SAF 图片 ----

    private fun decodeUri(uriString: String, reqW: Int, reqH: Int): Bitmap? {
        if (uriString.isBlank()) return null
        val uri = Uri.parse(uriString)
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, reqW, reqH)
        }
        return resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    // ---- 工具 ----

    /** 标准两段式采样：保证解码结果不小于请求尺寸 */
    private fun calculateSampleSize(width: Int, height: Int, reqW: Int, reqH: Int): Int {
        if (reqW <= 0 || reqH <= 0) return 1
        var sample = 1
        val halfW = width / 2
        val halfH = height / 2
        while (halfW / sample >= reqW && halfH / sample >= reqH) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }
}
