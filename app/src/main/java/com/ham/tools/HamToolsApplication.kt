package com.ham.tools

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.ham.tools.util.AppLanguage
import com.ham.tools.util.LocaleManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * HamTools Application class
 * 
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection
 * throughout the application.
 */
@HiltAndroidApp
class HamToolsApplication : Application() {
    
    companion object {
        private var _currentLanguage = MutableStateFlow(AppLanguage.SYSTEM)
        
        /**
         * 当前应用语言设置 (可观察)
         */
        val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()
        
        /**
         * 更新应用语言
         */
        fun updateLanguage(language: AppLanguage) {
            _currentLanguage.value = language
        }
    }
    
    override fun attachBaseContext(base: Context) {
        // 从 SharedPreferences 同步读取语言设置
        val prefs = base.getSharedPreferences("user_preferences", Context.MODE_PRIVATE)
        val languageCode = prefs.getString("language_code", "system") ?: "system"
        val language = AppLanguage.fromCode(languageCode)
        _currentLanguage.value = language
        
        val context = LocaleManager.applyLanguage(base, language)
        super.attachBaseContext(context)
    }
    
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 重新应用语言设置
        LocaleManager.applyLanguage(this, _currentLanguage.value)
    }
}
