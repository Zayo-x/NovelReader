package com.example.novelreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.novelreader.data.THEME_MODE_LIGHT
import com.example.novelreader.data.THEME_MODE_SYSTEM
import com.example.novelreader.ui.ViewModelFactory
import com.example.novelreader.ui.screen.AboutScreen
import com.example.novelreader.ui.screen.BookshelfScreen
import com.example.novelreader.ui.screen.ComicReaderScreen
import com.example.novelreader.ui.screen.ReaderScreen
import com.example.novelreader.ui.screen.SettingsScreen
import com.example.novelreader.ui.theme.NovelReaderTheme
import com.example.novelreader.ui.viewmodel.BookshelfViewModel
import com.example.novelreader.ui.viewmodel.ComicReaderViewModel
import com.example.novelreader.ui.viewmodel.ReaderViewModel
import com.example.novelreader.ui.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsRepository = (application as NovelReaderApp).settingsRepository
        setContent {
            val settings by settingsRepository.settings.collectAsState(
                initial = com.example.novelreader.data.ReaderSettings()
            )
            // 根据主题模式决定是否深色：跟随系统 / 浅色 / 深色
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (settings.themeMode) {
                THEME_MODE_SYSTEM -> systemDark
                THEME_MODE_LIGHT -> false
                else -> true
            }
            NovelReaderTheme(
                darkTheme = darkTheme,
                accentIndex = settings.accentIndex
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost()
                }
            }
        }
    }
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = "bookshelf"
    ) {
        composable("bookshelf") {
            val vm: BookshelfViewModel = viewModel(factory = ViewModelFactory)
            BookshelfScreen(
                viewModel = vm,
                onOpenBook = { book ->
                    // 漫画与小说走不同阅读器
                    val route = if (book.type.isComic) "comic/${book.id}" else "reader/${book.id}"
                    navController.navigate(route)
                },
                onOpenSettings = { navController.navigate("settings") }
            )
        }
        composable("reader/{bookId}") { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId")?.toLongOrNull() ?: 0L
            val vm: ReaderViewModel = viewModel(factory = ViewModelFactory)
            ReaderScreen(
                viewModel = vm,
                bookId = bookId,
                onBack = { navController.popBackStack() }
            )
        }
        composable("comic/{bookId}") { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId")?.toLongOrNull() ?: 0L
            val vm: ComicReaderViewModel = viewModel(factory = ViewModelFactory)
            ComicReaderScreen(
                viewModel = vm,
                bookId = bookId,
                onBack = { navController.popBackStack() }
            )
        }
        composable("settings") {
            val vm: SettingsViewModel = viewModel(factory = ViewModelFactory)
            SettingsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenAbout = { navController.navigate("about") }
            )
        }
        composable("about") {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
