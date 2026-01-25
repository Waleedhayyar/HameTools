package com.ham.tools.ui.screens.profile

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ham.tools.HamToolsApplication
import com.ham.tools.data.model.AppSettings
import com.ham.tools.data.model.LicenseClass
import com.ham.tools.data.model.QsoStatistics
import com.ham.tools.data.model.UserProfile
import com.ham.tools.data.repository.QsoLogRepository
import com.ham.tools.data.repository.UserPreferencesRepository
import com.ham.tools.util.AppLanguage
import com.ham.tools.util.DataExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Profile 页面 UI 状态
 */
data class ProfileUiState(
    val userProfile: UserProfile = UserProfile(),
    val appSettings: AppSettings = AppSettings(),
    val statistics: QsoStatistics = QsoStatistics(),
    val isLicenseFlipped: Boolean = false,  // 执照卡片是否翻转到背面
    val isLoading: Boolean = true,
    val exportResult: ExportResult? = null,
    val showEditProfileDialog: Boolean = false,
    val showSettingsSheet: Boolean = false
)

/**
 * 导出结果
 */
sealed class ExportResult {
    data class Success(val recordCount: Int, val fileName: String) : ExportResult()
    data class Error(val message: String) : ExportResult()
}

/**
 * Profile ViewModel
 * 
 * 管理用户配置、统计数据和设置
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val qsoLogRepository: QsoLogRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
    
    /**
     * 用户配置 Flow
     */
    val userProfile: StateFlow<UserProfile> = userPreferencesRepository.userProfile
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserProfile()
        )
    
    /**
     * 应用设置 Flow
     */
    val appSettings: StateFlow<AppSettings> = userPreferencesRepository.appSettings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )
    
    /**
     * 统计数据 Flow
     */
    val statistics: StateFlow<QsoStatistics> = qsoLogRepository.getStatistics()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = QsoStatistics()
        )
    
    init {
        // 监听数据变化并更新 UI 状态
        viewModelScope.launch {
            userProfile.collect { profile ->
                _uiState.value = _uiState.value.copy(
                    userProfile = profile,
                    isLoading = false
                )
            }
        }
        
        viewModelScope.launch {
            appSettings.collect { settings ->
                _uiState.value = _uiState.value.copy(appSettings = settings)
            }
        }
        
        viewModelScope.launch {
            statistics.collect { stats ->
                _uiState.value = _uiState.value.copy(statistics = stats)
            }
        }
    }
    
    /**
     * 切换执照卡片翻转状态
     */
    fun toggleLicenseFlip() {
        _uiState.value = _uiState.value.copy(
            isLicenseFlipped = !_uiState.value.isLicenseFlipped
        )
    }
    
    /**
     * 显示/隐藏编辑配置对话框
     */
    fun toggleEditProfileDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showEditProfileDialog = show)
    }
    
    /**
     * 显示/隐藏设置面板
     */
    fun toggleSettingsSheet(show: Boolean) {
        _uiState.value = _uiState.value.copy(showSettingsSheet = show)
    }
    
    /**
     * 更新用户配置
     */
    fun updateProfile(
        callsign: String,
        name: String,
        licenseClass: LicenseClass,
        licenseExpiryDate: Long?,
        gridLocator: String,
        qth: String
    ) {
        viewModelScope.launch {
            val currentProfile = _uiState.value.userProfile
            val updatedProfile = currentProfile.copy(
                callsign = callsign.uppercase().trim(),
                name = name.trim(),
                licenseClass = licenseClass,
                licenseExpiryDate = licenseExpiryDate,
                gridLocator = gridLocator.uppercase().trim(),
                qth = qth.trim(),
                isOnboardingComplete = true
            )
            userPreferencesRepository.updateProfile(updatedProfile)
            toggleEditProfileDialog(false)
        }
    }
    
    /**
     * 更新执照照片
     */
    fun updateLicensePhoto(uri: String) {
        viewModelScope.launch {
            userPreferencesRepository.updateLicensePhoto(uri)
        }
    }
    
    /**
     * 更新应用设置
     */
    fun updateSettings(settings: AppSettings) {
        viewModelScope.launch {
            userPreferencesRepository.updateSettings(settings)
        }
    }
    
    /**
     * 完成引导
     */
    fun completeOnboarding() {
        viewModelScope.launch {
            userPreferencesRepository.completeOnboarding()
        }
    }
    
    /**
     * 导出数据为 JSON
     */
    fun exportToJson(uri: Uri) {
        viewModelScope.launch {
            val logs = qsoLogRepository.getAllLogsForExport()
            val result = DataExporter.exportToJson(context, logs, uri)
            
            result.fold(
                onSuccess = { count ->
                    userPreferencesRepository.recordBackupTime()
                    _uiState.value = _uiState.value.copy(
                        exportResult = ExportResult.Success(count, "JSON")
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        exportResult = ExportResult.Error(error.message ?: "导出失败")
                    )
                }
            )
        }
    }
    
    /**
     * 导出数据为 CSV
     */
    fun exportToCsv(uri: Uri) {
        viewModelScope.launch {
            val logs = qsoLogRepository.getAllLogsForExport()
            val result = DataExporter.exportToCsv(context, logs, uri)
            
            result.fold(
                onSuccess = { count ->
                    userPreferencesRepository.recordBackupTime()
                    _uiState.value = _uiState.value.copy(
                        exportResult = ExportResult.Success(count, "CSV")
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        exportResult = ExportResult.Error(error.message ?: "导出失败")
                    )
                }
            )
        }
    }
    
    /**
     * 清除导出结果
     */
    fun clearExportResult() {
        _uiState.value = _uiState.value.copy(exportResult = null)
    }
    
    /**
     * 获取当前语言
     */
    fun getCurrentLanguage(): AppLanguage {
        return AppLanguage.fromCode(appSettings.value.languageCode)
    }
    
    /**
     * 更新应用语言
     * @param language 新的语言设置
     * @param activity 当前 Activity，用于重建以应用新语言
     */
    fun updateLanguage(language: AppLanguage, activity: Activity? = null) {
        viewModelScope.launch {
            // 保存到 DataStore
            userPreferencesRepository.updateLanguage(language.code)
            
            // 同时保存到 SharedPreferences (用于 Application 启动时读取)
            val prefs = context.getSharedPreferences("user_preferences", Context.MODE_PRIVATE)
            prefs.edit().putString("language_code", language.code).apply()
            
            // 更新全局语言状态
            HamToolsApplication.updateLanguage(language)
            
            // 重建 Activity 以应用新语言
            activity?.recreate()
        }
    }
}
