package com.ham.tools.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ham.tools.data.model.UserProfile
import com.ham.tools.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Main Activity ViewModel
 * 
 * 管理应用级别的状态，如用户配置和引导状态
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {
    
    /**
     * 是否正在加载用户配置
     */
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    /**
     * 用户配置 Flow
     */
    val userProfile: StateFlow<UserProfile> = userPreferencesRepository.userProfile
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserProfile()
        )
    
    init {
        // 等待配置加载完成
        viewModelScope.launch {
            userPreferencesRepository.userProfile.first()
            _isLoading.value = false
        }
    }
    
    /**
     * 完成引导并保存呼号
     */
    suspend fun completeOnboarding(callsign: String) {
        val currentProfile = userProfile.value
        val updatedProfile = currentProfile.copy(
            callsign = callsign.uppercase().trim(),
            isOnboardingComplete = true
        )
        userPreferencesRepository.updateProfile(updatedProfile)
    }
}
