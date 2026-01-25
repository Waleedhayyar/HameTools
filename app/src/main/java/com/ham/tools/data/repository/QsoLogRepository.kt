package com.ham.tools.data.repository

import com.ham.tools.data.local.QsoLogDao
import com.ham.tools.data.model.FrequencyStat
import com.ham.tools.data.model.ModeStat
import com.ham.tools.data.model.QsoLog
import com.ham.tools.data.model.QsoStatistics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用于中间合并计数统计的数据类
 */
private data class CountStats(
    val total: Int,
    val month: Int,
    val today: Int,
    val year: Int
)

/**
 * Repository for QSO log data operations
 * 
 * Provides a clean API for data access to the rest of the application
 */
@Singleton
class QsoLogRepository @Inject constructor(
    private val qsoLogDao: QsoLogDao
) {
    /**
     * Get all QSO logs as a Flow (reactive updates)
     */
    fun getAllLogs(): Flow<List<QsoLog>> = qsoLogDao.getAllLogs()
    
    /**
     * Get a single QSO log by ID
     */
    suspend fun getLogById(id: Long): QsoLog? = qsoLogDao.getLogById(id)
    
    /**
     * Insert a new QSO log
     * @return The ID of the inserted log
     */
    suspend fun insertLog(log: QsoLog): Long = qsoLogDao.insert(log)
    
    /**
     * Update an existing QSO log
     */
    suspend fun updateLog(log: QsoLog) = qsoLogDao.update(log)
    
    /**
     * Delete a QSO log
     */
    suspend fun deleteLog(log: QsoLog) = qsoLogDao.delete(log)
    
    /**
     * Get the total count of QSO logs
     */
    fun getLogCount(): Flow<Int> = qsoLogDao.getLogCount()
    
    /**
     * Search logs by callsign
     */
    fun searchByCallsign(query: String): Flow<List<QsoLog>> = qsoLogDao.searchByCallsign(query)
    
    // ===== 统计相关 =====
    
    /**
     * 获取综合统计数据
     */
    fun getStatistics(): Flow<QsoStatistics> {
        // 今日开始时间 (00:00:00)
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        // 本月开始时间
        val monthStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
        
        // 本年开始时间
        val yearStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
        
        // 明年开始时间
        val yearEnd = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.YEAR, 1)
        }.timeInMillis
        
        // 先合并前4个计数流
        val countsFlow = combine(
            qsoLogDao.getLogCount(),
            qsoLogDao.getLogCountThisMonth(monthStart),
            qsoLogDao.getLogCountToday(todayStart),
            qsoLogDao.getLogCountInYear(yearStart, yearEnd)
        ) { total, month, today, year ->
            CountStats(total, month, today, year)
        }
        
        // 再合并统计流
        return combine(
            countsFlow,
            qsoLogDao.getFrequencyStats(),
            qsoLogDao.getModeStats()
        ) { counts, freqStats, modeStats ->
            QsoStatistics(
                totalCount = counts.total,
                thisMonthCount = counts.month,
                todayCount = counts.today,
                thisYearCount = counts.year,
                frequencyStats = freqStats,
                modeStats = modeStats
            )
        }
    }
    
    /**
     * 获取频率/波段统计
     */
    fun getFrequencyStats(): Flow<List<FrequencyStat>> = qsoLogDao.getFrequencyStats()
    
    /**
     * 获取模式统计
     */
    fun getModeStats(): Flow<List<ModeStat>> = qsoLogDao.getModeStats()
    
    /**
     * 获取所有记录用于导出
     */
    suspend fun getAllLogsForExport(): List<QsoLog> = qsoLogDao.getAllLogsForExport()
}
