package com.example.novelreader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.novelreader.NovelReaderApp
import com.example.novelreader.data.BookRepository
import com.example.novelreader.data.ComicRepository
import com.example.novelreader.data.SettingsRepository
import com.example.novelreader.ui.viewmodel.BookshelfViewModel
import com.example.novelreader.ui.viewmodel.ComicReaderViewModel
import com.example.novelreader.ui.viewmodel.ReaderViewModel
import com.example.novelreader.ui.viewmodel.SettingsViewModel

/** 简易 ViewModel 工厂：从 Application 取依赖 */
object ViewModelFactory : ViewModelProvider.Factory {

    private fun app(extras: CreationExtras): NovelReaderApp {
        val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
            as? NovelReaderApp
        return app ?: throw IllegalStateException("Application 未初始化")
    }

    fun repository(extras: CreationExtras): BookRepository = app(extras).repository
    fun comicRepository(extras: CreationExtras): ComicRepository = app(extras).comicRepository
    fun settings(extras: CreationExtras): SettingsRepository = app(extras).settingsRepository

    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val repository = repository(extras)
        val comicRepository = comicRepository(extras)
        val settings = settings(extras)
        val vm: ViewModel = when {
            modelClass.isAssignableFrom(BookshelfViewModel::class.java) ->
                BookshelfViewModel(repository, comicRepository, settings)
            modelClass.isAssignableFrom(ReaderViewModel::class.java) ->
                ReaderViewModel(repository, settings)
            modelClass.isAssignableFrom(ComicReaderViewModel::class.java) ->
                ComicReaderViewModel(repository, comicRepository)
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(settings)
            else -> throw IllegalArgumentException("未知 ViewModel: ${modelClass.name}")
        }
        @Suppress("UNCHECKED_CAST")
        return vm as T
    }
}
