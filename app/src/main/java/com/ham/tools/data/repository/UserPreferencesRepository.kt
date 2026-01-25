package com.ham.tools.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ham.tools.data.model.AppSettings
import com.ham.tools.data.model.LicenseClass
import com.ham.tools.data.model.UserProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// DataStore 实例
private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences"
)

/**
 * 用户偏好设置仓库
 * 
 * 管理用户配置和应用设置的持久化
 */
@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    // ===== UserProfile Keys =====
    private object ProfileKeys {
        val CALLSIGN = stringPreferencesKey("callsign")
        val NAME = stringPreferencesKey("name")
        val LICENSE_CLASS = stringPreferencesKey("license_class")
        val LICENSE_EXPIRY = longPreferencesKey("license_expiry")
        val LICENSE_PHOTO_URI = stringPreferencesKey("license_photo_uri")
        val GRID_LOCATOR = stringPreferencesKey("grid_locator")
        val QTH = stringPreferencesKey("qth")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val FIRST_LAUNCH_TIME = longPreferencesKey("first_launch_time")
    }
    
    // ===== AppSettings Keys =====
    private object SettingsKeys {
        val LLM_API_KEY = stringPreferencesKey("llm_api_key")
        val LLM_ENDPOINT = stringPreferencesKey("llm_endpoint")
        val DARK_MODE = stringPreferencesKey("dark_mode")  // "true", "false", or "system"
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val LAST_BACKUP_TIME = longPreferencesKey("last_backup_time")
        val AUTO_BACKUP_INTERVAL = intPreferencesKey("auto_backup_interval")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")  // "system", "zh", "en", "ja"
    }
    
    /**
     * 获取用户配置 Flow
     */
    val userProfile: Flow<UserProfile> = context.userPreferencesDataStore.data.map { prefs ->
        UserProfile(
            callsign = prefs[ProfileKeys.CALLSIGN] ?: "",
            name = prefs[ProfileKeys.NAME] ?: "",
            licenseClass = prefs[ProfileKeys.LICENSE_CLASS]?.let { 
                try { LicenseClass.valueOf(it) } catch (e: Exception) { LicenseClass.NONE }
            } ?: LicenseClass.NONE,
            licenseExpiryDate = prefs[ProfileKeys.LICENSE_EXPIRY],
            licensePhotoUri = prefs[ProfileKeys.LICENSE_PHOTO_URI],
            gridLocator = prefs[ProfileKeys.GRID_LOCATOR] ?: "",
            qth = prefs[ProfileKeys.QTH] ?: "",
            isOnboardingComplete = prefs[ProfileKeys.ONBOARDING_COMPLETE] ?: false,
            firstLaunchTime = prefs[ProfileKeys.FIRST_LAUNCH_TIME] ?: System.currentTimeMillis()
        )
    }
    
    /**
     * 获取应用设置 Flow
     */
    val appSettings: Flow<AppSettings> = context.userPreferencesDataStore.data.map { prefs ->
        AppSettings(
            llmApiKey = prefs[SettingsKeys.LLM_API_KEY] ?: "",
            llmEndpoint = prefs[SettingsKeys.LLM_ENDPOINT] ?: "https://api.openai.com/v1",
            darkMode = when (prefs[SettingsKeys.DARK_MODE]) {
                "true" -> true
                "false" -> false
                else -> null
            },
            dynamicColor = prefs[SettingsKeys.DYNAMIC_COLOR] ?: true,
            lastBackupTime = prefs[SettingsKeys.LAST_BACKUP_TIME],
            autoBackupIntervalDays = prefs[SettingsKeys.AUTO_BACKUP_INTERVAL] ?: 0,
            languageCode = prefs[SettingsKeys.LANGUAGE_CODE] ?: "system"
        )
    }
    
    /**
     * 更新用户配置
     */
    suspend fun updateProfile(profile: UserProfile) {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[ProfileKeys.CALLSIGN] = profile.callsign
            prefs[ProfileKeys.NAME] = profile.name
            prefs[ProfileKeys.LICENSE_CLASS] = profile.licenseClass.name
            profile.licenseExpiryDate?.let { prefs[ProfileKeys.LICENSE_EXPIRY] = it }
            profile.licensePhotoUri?.let { prefs[ProfileKeys.LICENSE_PHOTO_URI] = it }
            prefs[ProfileKeys.GRID_LOCATOR] = profile.gridLocator
            prefs[ProfileKeys.QTH] = profile.qth
            prefs[ProfileKeys.ONBOARDING_COMPLETE] = profile.isOnboardingComplete
            prefs[ProfileKeys.FIRST_LAUNCH_TIME] = profile.firstLaunchTime
        }
    }
    
    /**
     * 更新应用设置
     */
    suspend fun updateSettings(settings: AppSettings) {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[SettingsKeys.LLM_API_KEY] = settings.llmApiKey
            prefs[SettingsKeys.LLM_ENDPOINT] = settings.llmEndpoint
            prefs[SettingsKeys.DARK_MODE] = when (settings.darkMode) {
                true -> "true"
                false -> "false"
                null -> "system"
            }
            prefs[SettingsKeys.DYNAMIC_COLOR] = settings.dynamicColor
            settings.lastBackupTime?.let { prefs[SettingsKeys.LAST_BACKUP_TIME] = it }
            prefs[SettingsKeys.AUTO_BACKUP_INTERVAL] = settings.autoBackupIntervalDays
            prefs[SettingsKeys.LANGUAGE_CODE] = settings.languageCode
        }
    }
    
    /**
     * 更新语言设置
     */
    suspend fun updateLanguage(languageCode: String) {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[SettingsKeys.LANGUAGE_CODE] = languageCode
        }
    }
    
    /**
     * 同步获取当前语言设置（阻塞式，仅用于初始化）
     */
    suspend fun getLanguageCode(): String {
        return context.userPreferencesDataStore.data.map { prefs ->
            prefs[SettingsKeys.LANGUAGE_CODE] ?: "system"
        }.first()
    }
    
    /**
     * 快速更新呼号
     */
    suspend fun updateCallsign(callsign: String) {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[ProfileKeys.CALLSIGN] = callsign.uppercase()
        }
    }
    
    /**
     * 标记引导完成
     */
    suspend fun completeOnboarding() {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[ProfileKeys.ONBOARDING_COMPLETE] = true
        }
    }
    
    /**
     * 更新 LLM API Key
     */
    suspend fun updateLlmApiKey(apiKey: String) {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[SettingsKeys.LLM_API_KEY] = apiKey
        }
    }
    
    /**
     * 更新执照照片 URI
     */
    suspend fun updateLicensePhoto(uri: String) {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[ProfileKeys.LICENSE_PHOTO_URI] = uri
        }
    }
    
    /**
     * 记录备份时间
     */
    suspend fun recordBackupTime() {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[SettingsKeys.LAST_BACKUP_TIME] = System.currentTimeMillis()
        }
    }
}
