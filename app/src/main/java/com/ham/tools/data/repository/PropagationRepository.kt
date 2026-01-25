package com.ham.tools.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ham.tools.data.model.BandCondition
import com.ham.tools.data.model.SolarData
import com.ham.tools.data.model.VhfCondition
import com.ham.tools.data.remote.HamQslApi
import com.ham.tools.data.remote.SolarXmlParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

// DataStore 实例（扩展属性）
private val Context.propagationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "propagation_cache"
)

/**
 * 传播预测数据仓库
 * 
 * 负责从 HamQSL 获取太阳/地磁数据，并管理本地缓存
 * 
 * 缓存策略：
 * - 15分钟内的数据直接使用缓存
 * - 超过15分钟自动刷新
 * - 网络错误时 fallback 到缓存
 */
@Singleton
class PropagationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hamQslApi: HamQslApi
) {
    
    companion object {
        // 缓存键
        private val KEY_CACHED_DATA = stringPreferencesKey("cached_solar_data")
        private val KEY_CACHE_TIMESTAMP = longPreferencesKey("cache_timestamp")
        
        // 缓存有效期（毫秒）
        private const val CACHE_MAX_AGE_MS = 15 * 60 * 1000L  // 15分钟
    }
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * 获取太阳/地磁数据
     * 
     * @param forceRefresh 是否强制刷新（忽略缓存）
     * @return Result<SolarData> 成功返回数据，失败返回异常
     */
    suspend fun getSolarData(forceRefresh: Boolean = false): Result<SolarData> {
        return withContext(Dispatchers.IO) {
            try {
                // 检查缓存
                if (!forceRefresh) {
                    val cachedData = getCachedData()
                    if (cachedData != null && !cachedData.isCacheExpired()) {
                        return@withContext Result.success(cachedData)
                    }
                }
                
                // 从网络获取
                val response = hamQslApi.getSolarXml()
                
                if (response.isSuccessful && response.body() != null) {
                    val inputStream = response.body()!!.byteStream()
                    val solarData = SolarXmlParser.parse(inputStream)
                    
                    // 保存到缓存
                    saveToCache(solarData)
                    
                    Result.success(solarData)
                } else {
                    // 网络请求失败，尝试使用缓存
                    val cachedData = getCachedData()
                    if (cachedData != null) {
                        Result.success(cachedData.copy(
                            fetchedAt = cachedData.fetchedAt // 保持原来的时间戳以表明是缓存
                        ))
                    } else {
                        Result.failure(Exception("无法获取传播数据: ${response.code()}"))
                    }
                }
            } catch (e: Exception) {
                // 网络异常，尝试使用缓存
                val cachedData = getCachedData()
                if (cachedData != null) {
                    Result.success(cachedData)
                } else {
                    Result.failure(Exception("网络错误: ${e.message}"))
                }
            }
        }
    }
    
    /**
     * 从 DataStore 获取缓存的数据
     */
    private suspend fun getCachedData(): SolarData? {
        return try {
            val preferences = context.propagationDataStore.data.first()
            val jsonString = preferences[KEY_CACHED_DATA] ?: return null
            json.decodeFromString<SolarData>(jsonString)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 保存数据到 DataStore 缓存
     */
    private suspend fun saveToCache(data: SolarData) {
        try {
            context.propagationDataStore.edit { preferences ->
                preferences[KEY_CACHED_DATA] = json.encodeToString(data)
                preferences[KEY_CACHE_TIMESTAMP] = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            // 缓存保存失败不影响正常流程
        }
    }
    
    /**
     * 清除缓存
     */
    suspend fun clearCache() {
        context.propagationDataStore.edit { preferences ->
            preferences.clear()
        }
    }
    
    /**
     * 检查是否有有效缓存
     */
    suspend fun hasCachedData(): Boolean {
        val cachedData = getCachedData()
        return cachedData != null && !cachedData.isCacheExpired()
    }
}
