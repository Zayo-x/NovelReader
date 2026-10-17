package com.example.novelreader.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.novelreader.data.model.BookType

/** 书籍（书架项） */
@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String = "未知作者",
    /** 本地文件路径（图片文件夹时为 SAF 目录 uri） */
    val path: String? = null,
    val type: BookType,
    /** 最后阅读章节下标 */
    val lastChapterIndex: Int = 0,
    /** 最后阅读位置（小说：章节内字符偏移；漫画：页码） */
    val lastReadPosition: Int = 0,
    /** 最后阅读时间戳 */
    val lastReadTime: Long = 0,
    /** 总章节数（本地解析后缓存，-1 表示未知） */
    val chapterCount: Int = -1,
    /** 内容简介 */
    val intro: String? = null,
    /** 加入书架时间 */
    val addedTime: Long = System.currentTimeMillis()
)

/** 章节 */
@Entity(
    tableName = "chapters",
    indices = [Index(value = ["bookId", "chapter_index"])]
)
data class Chapter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val title: String,
    /** 章节在书中的序号（从 0 开始） */
    @ColumnInfo(name = "chapter_index")
    val index: Int,
    /** 章节正文内容（漫画章节为 null，页面见 comic_pages） */
    val content: String? = null
)

/** 漫画页面 */
@Entity(
    tableName = "comic_pages",
    indices = [Index(value = ["bookId", "chapter_index", "page_index"])]
)
data class ComicPage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    /** 所属章节序号 */
    @ColumnInfo(name = "chapter_index")
    val chapterIndex: Int,
    /** 章节内页码（从 0 开始；PDF 为 PDF 页序号） */
    @ColumnInfo(name = "page_index")
    val pageIndex: Int,
    /**
     * 图片来源：
     * - CBZ/ZIP：压缩包内条目名
     * - 图片文件夹：SAF document uri
     * - PDF：空字符串（按 pageIndex 渲染）
     */
    val source: String,
    val width: Int = 0,
    val height: Int = 0
)

/** 书签 */
@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val chapterIndex: Int,
    /** 章节内字符偏移（漫画为页码） */
    val position: Int,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
