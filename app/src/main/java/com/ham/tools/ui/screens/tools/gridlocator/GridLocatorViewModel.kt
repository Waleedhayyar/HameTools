package com.ham.tools.ui.screens.tools.gridlocator

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.ham.tools.util.GridLocatorUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * GPS 位置信息数据类
 */
data class LocationInfo(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val accuracy: Float?,
    val grid: String
)

/**
 * GPS 定位状态
 */
sealed class LocationState {
    data object Initial : LocationState()
    data object Loading : LocationState()
    data object PermissionDenied : LocationState()
    data class Success(val location: LocationInfo) : LocationState()
    data class Error(val message: String) : LocationState()
}

/**
 * 手动计算状态
 */
data class CalculatorState(
    val latitude: String = "",
    val longitude: String = "",
    val gridInput: String = "",
    val calculatedGrid: String? = null,
    val calculatedLatLng: Pair<Double, Double>? = null,
    val error: String? = null
)

/**
 * UI 状态
 */
data class GridLocatorUiState(
    val locationState: LocationState = LocationState.Initial,
    val calculatorState: CalculatorState = CalculatorState(),
    val isManualMode: Boolean = false
)

/**
 * 网格定位 ViewModel
 */
@HiltViewModel
class GridLocatorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(GridLocatorUiState())
    val uiState: StateFlow<GridLocatorUiState> = _uiState.asStateFlow()
    
    private var locationCallback: LocationCallback? = null
    
    /**
     * 重置定位状态，用于每次进入屏幕时重新获取定位
     */
    fun resetLocationState() {
        _uiState.update { it.copy(locationState = LocationState.Initial) }
    }
    
    /**
     * 检查位置权限
     */
    fun hasLocationPermission(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val coarseLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        return fineLocation || coarseLocation
    }
    
    /**
     * 处理权限结果
     */
    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            startLocationUpdates()
        } else {
            _uiState.update { it.copy(locationState = LocationState.PermissionDenied) }
        }
    }
    
    /**
     * 开始获取位置更新
     */
    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            _uiState.update { it.copy(locationState = LocationState.PermissionDenied) }
            return
        }
        
        _uiState.update { it.copy(locationState = LocationState.Loading) }
        
        viewModelScope.launch {
            try {
                // 首先尝试获取最后已知位置
                fusedLocationClient.lastLocation.addOnSuccessListener { lastLocation ->
                    if (lastLocation != null) {
                        updateLocation(lastLocation)
                    }
                }
                
                // 然后请求实时位置更新
                val locationRequest = LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    5000L // 5秒更新一次
                ).apply {
                    setMinUpdateIntervalMillis(2000L)
                    setMaxUpdateDelayMillis(10000L)
                }.build()
                
                locationCallback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        result.lastLocation?.let { location ->
                            updateLocation(location)
                        }
                    }
                }
                
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback!!,
                    context.mainLooper
                )
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(locationState = LocationState.Error("获取位置失败: ${e.message}"))
                }
            }
        }
    }
    
    /**
     * 更新位置信息
     */
    private fun updateLocation(location: Location) {
        val grid = GridLocatorUtils.toGrid(location.latitude, location.longitude)
        val locationInfo = LocationInfo(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = if (location.hasAltitude()) location.altitude else null,
            accuracy = if (location.hasAccuracy()) location.accuracy else null,
            grid = grid
        )
        _uiState.update { it.copy(locationState = LocationState.Success(locationInfo)) }
    }
    
    /**
     * 停止位置更新
     */
    fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }
    }
    
    /**
     * 切换模式
     */
    fun toggleMode() {
        _uiState.update { it.copy(isManualMode = !it.isManualMode) }
    }
    
    /**
     * 更新纬度输入
     */
    fun updateLatitude(value: String) {
        _uiState.update { 
            it.copy(calculatorState = it.calculatorState.copy(
                latitude = value,
                calculatedGrid = null,
                error = null
            ))
        }
    }
    
    /**
     * 更新经度输入
     */
    fun updateLongitude(value: String) {
        _uiState.update { 
            it.copy(calculatorState = it.calculatorState.copy(
                longitude = value,
                calculatedGrid = null,
                error = null
            ))
        }
    }
    
    /**
     * 更新网格输入
     */
    fun updateGridInput(value: String) {
        _uiState.update { 
            it.copy(calculatorState = it.calculatorState.copy(
                gridInput = value,
                calculatedLatLng = null,
                error = null
            ))
        }
    }
    
    /**
     * 正向计算：经纬度 -> 网格
     */
    fun calculateGrid() {
        val state = _uiState.value.calculatorState
        
        try {
            val lat = state.latitude.toDoubleOrNull()
            val lon = state.longitude.toDoubleOrNull()
            
            if (lat == null || lon == null) {
                _uiState.update {
                    it.copy(calculatorState = it.calculatorState.copy(error = "请输入有效的经纬度"))
                }
                return
            }
            
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                _uiState.update {
                    it.copy(calculatorState = it.calculatorState.copy(error = "经纬度超出有效范围"))
                }
                return
            }
            
            val grid = GridLocatorUtils.toGrid(lat, lon)
            _uiState.update {
                it.copy(calculatorState = it.calculatorState.copy(
                    calculatedGrid = grid,
                    error = null
                ))
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(calculatorState = it.calculatorState.copy(error = e.message))
            }
        }
    }
    
    /**
     * 反向计算：网格 -> 经纬度
     */
    fun calculateLatLng() {
        val state = _uiState.value.calculatorState
        val grid = state.gridInput.trim()
        
        try {
            if (!GridLocatorUtils.isValidGrid(grid)) {
                _uiState.update {
                    it.copy(calculatorState = it.calculatorState.copy(error = "请输入有效的网格代码（4位或6位）"))
                }
                return
            }
            
            val (lat, lon) = GridLocatorUtils.toLatLng(grid)
            _uiState.update {
                it.copy(calculatorState = it.calculatorState.copy(
                    calculatedLatLng = Pair(lat, lon),
                    error = null
                ))
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(calculatorState = it.calculatorState.copy(error = e.message))
            }
        }
    }
    
    /**
     * 清除计算结果
     */
    fun clearCalculator() {
        _uiState.update {
            it.copy(calculatorState = CalculatorState())
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        stopLocationUpdates()
    }
}
