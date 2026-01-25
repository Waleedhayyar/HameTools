package com.ham.tools.ui.screens.tools.propagation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ham.tools.data.model.PropagationUiState
import com.ham.tools.data.repository.PropagationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 传播预测 ViewModel
 * 
 * 管理太阳/地磁数据的获取、缓存和 UI 状态
 * 支持下拉刷新和自动定时刷新
 */
@HiltViewModel
class PropagationViewModel @Inject constructor(
    private val repository: PropagationRepository
) : ViewModel() {
    
    companion object {
        // 自动刷新间隔（毫秒）
        private const val AUTO_REFRESH_INTERVAL_MS = 30 * 60 * 1000L  // 30分钟
    }
    
    // UI 状态
    private val _uiState = MutableStateFlow<PropagationUiState>(PropagationUiState.Loading)
    val uiState: StateFlow<PropagationUiState> = _uiState.asStateFlow()
    
    // 是否正在刷新
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    
    init {
        // 首次加载数据
        loadData(forceRefresh = false)
        
        // 启动自动刷新
        startAutoRefresh()
    }
    
    /**
     * 加载传播数据
     * 
     * @param forceRefresh 是否强制刷新（忽略缓存）
     */
    fun loadData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (_uiState.value !is PropagationUiState.Success) {
                _uiState.value = PropagationUiState.Loading
            }
            
            val result = repository.getSolarData(forceRefresh)
            
            result.fold(
                onSuccess = { data ->
                    val isFromCache = !forceRefresh && data.isCacheExpired(1) // 1分钟内视为新数据
                    _uiState.value = PropagationUiState.Success(
                        data = data,
                        isFromCache = isFromCache
                    )
                },
                onFailure = { error ->
                    val currentState = _uiState.value
                    val cachedData = if (currentState is PropagationUiState.Success) {
                        currentState.data
                    } else null
                    
                    _uiState.value = PropagationUiState.Error(
                        message = error.message ?: "未知错误",
                        cachedData = cachedData
                    )
                }
            )
        }
    }
    
    /**
     * 下拉刷新
     */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            loadData(forceRefresh = true)
            _isRefreshing.value = false
        }
    }
    
    /**
     * 重试加载
     */
    fun retry() {
        loadData(forceRefresh = true)
    }
    
    /**
     * 启动自动刷新
     */
    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(AUTO_REFRESH_INTERVAL_MS)
                // 只在成功状态下自动刷新
                if (_uiState.value is PropagationUiState.Success) {
                    loadData(forceRefresh = true)
                }
            }
        }
    }
}
