package com.ham.tools.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * 支持的应用语言
 */
enum class AppLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String
) {
    /** 跟随系统语言 */
    SYSTEM("system", "System Default", "跟随系统"),
    
    /** 简体中文 */
    CHINESE("zh", "Chinese", "简体中文"),
    
    /** 英语 */
    ENGLISH("en", "English", "English"),
    
    /** 日语 */
    JAPANESE("ja", "Japanese", "日本語");
    
    companion object {
        /**
         * 根据语言代码获取 AppLanguage
         */
        fun fromCode(code: String): AppLanguage {
            return entries.find { it.code == code } ?: SYSTEM
        }
        
        /**
         * 获取系统默认语言对应的 AppLanguage
         */
        fun getSystemLanguage(): AppLanguage {
            val systemLocale = Locale.getDefault()
            return when (systemLocale.language) {
                "zh" -> CHINESE
                "en" -> ENGLISH
                "ja" -> JAPANESE
                else -> ENGLISH // 默认使用英语
            }
        }
    }
}

/**
 * 语言管理器
 * 
 * 用于管理应用内语言设置
 */
object LocaleManager {
    
    /**
     * 获取实际使用的语言（如果是 SYSTEM 则返回系统语言）
     */
    fun getEffectiveLanguage(language: AppLanguage): AppLanguage {
        return if (language == AppLanguage.SYSTEM) {
            AppLanguage.getSystemLanguage()
        } else {
            language
        }
    }
    
    /**
     * 根据 AppLanguage 获取 Locale
     */
    fun getLocale(language: AppLanguage): Locale {
        val effectiveLanguage = getEffectiveLanguage(language)
        return when (effectiveLanguage) {
            AppLanguage.CHINESE -> Locale.SIMPLIFIED_CHINESE
            AppLanguage.ENGLISH -> Locale.ENGLISH
            AppLanguage.JAPANESE -> Locale.JAPANESE
            AppLanguage.SYSTEM -> Locale.getDefault()
        }
    }
    
    /**
     * 应用语言设置到 Context
     * 
     * @param context 原始 Context
     * @param language 目标语言
     * @return 应用了新语言的 Context
     */
    fun applyLanguage(context: Context, language: AppLanguage): Context {
        val locale = getLocale(language)
        Locale.setDefault(locale)
        
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        
        return context.createConfigurationContext(config)
    }
    
    /**
     * 更新 Context 的语言配置
     * 
     * @param context 要更新的 Context
     * @param language 目标语言
     */
    fun updateContextLocale(context: Context, language: AppLanguage): Context {
        return applyLanguage(context, language)
    }
}
