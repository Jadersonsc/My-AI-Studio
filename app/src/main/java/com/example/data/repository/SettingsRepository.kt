package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.AppLanguage
import com.example.data.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("apk_extractor_settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _biometricEnabled = MutableStateFlow(prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false))
    val biometricEnabled: StateFlow<Boolean> = _biometricEnabled.asStateFlow()

    private val _autoCloudSync = MutableStateFlow(prefs.getBoolean(KEY_AUTO_CLOUD_SYNC, true))
    val autoCloudSync: StateFlow<Boolean> = _autoCloudSync.asStateFlow()

    private val _language = MutableStateFlow(loadLanguage())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _externalAnalytics = MutableStateFlow(prefs.getBoolean(KEY_EXTERNAL_ANALYTICS, true))
    val externalAnalytics: StateFlow<Boolean> = _externalAnalytics.asStateFlow()

    private val _cloudProvider = MutableStateFlow(
        prefs.getString(KEY_CLOUD_PROVIDER, "Google Drive (Cloud Sync)") ?: "Google Drive (Cloud Sync)"
    )
    val cloudProvider: StateFlow<String> = _cloudProvider.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
        _biometricEnabled.value = enabled
    }

    fun setAutoCloudSync(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CLOUD_SYNC, enabled).apply()
        _autoCloudSync.value = enabled
    }

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.code).apply()
        _language.value = language
    }

    fun setExternalAnalytics(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EXTERNAL_ANALYTICS, enabled).apply()
        _externalAnalytics.value = enabled
    }

    fun setCloudProvider(provider: String) {
        prefs.edit().putString(KEY_CLOUD_PROVIDER, provider).apply()
        _cloudProvider.value = provider
    }

    private fun loadThemeMode(): ThemeMode {
        val name = prefs.getString(KEY_THEME_MODE, ThemeMode.DARK.name)
        return try {
            ThemeMode.valueOf(name ?: ThemeMode.DARK.name)
        } catch (_: Exception) {
            ThemeMode.DARK
        }
    }

    private fun loadLanguage(): AppLanguage {
        val code = prefs.getString(KEY_LANGUAGE, "pt")
        return AppLanguage.entries.find { it.code == code } ?: AppLanguage.PORTUGUESE
    }

    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_AUTO_CLOUD_SYNC = "auto_cloud_sync"
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_EXTERNAL_ANALYTICS = "external_analytics"
        private const val KEY_CLOUD_PROVIDER = "cloud_provider"
    }
}
