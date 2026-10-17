package com.example.novelreader.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.novelreader.data.ReaderSettings
import com.example.novelreader.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 设置页 ViewModel：管理深色/浅色模式等全局偏好 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<ReaderSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderSettings())

    fun setThemeMode(mode: Int) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setAccentIndex(index: Int) {
        viewModelScope.launch { settingsRepository.setAccentIndex(index) }
    }

    fun setPageBackground(index: Int) {
        viewModelScope.launch { settingsRepository.setPageBackground(index) }
    }
}
