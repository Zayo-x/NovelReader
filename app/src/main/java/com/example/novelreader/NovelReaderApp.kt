package com.example.novelreader

import android.app.Application
import com.example.novelreader.data.BookRepository
import com.example.novelreader.data.ComicPageLoader
import com.example.novelreader.data.ComicRepository
import com.example.novelreader.data.SettingsRepository
import com.example.novelreader.data.db.AppDatabase

/** 应用入口：构建依赖容器 */
class NovelReaderApp : Application() {

    lateinit var repository: BookRepository
        private set
    lateinit var comicRepository: ComicRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getInstance(this)
        repository = BookRepository(
            context = this,
            bookDao = db.bookDao(),
            chapterDao = db.chapterDao(),
            bookmarkDao = db.bookmarkDao()
        )
        comicRepository = ComicRepository(
            context = this,
            bookDao = db.bookDao(),
            chapterDao = db.chapterDao(),
            comicPageDao = db.comicPageDao(),
            bookmarkDao = db.bookmarkDao(),
            loader = ComicPageLoader(this)
        )
        settingsRepository = SettingsRepository(this)
    }
}
