package com.ham.tools.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ham.tools.data.model.FrequencyStat
import com.ham.tools.data.model.ModeStat
import com.ham.tools.data.model.QsoLog
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for QSO log operations
 */
@Dao
interface QsoLogDao {
    
    /**
     * Get all QSO logs ordered by timestamp (newest first)
     */
    @Query("SELECT * FROM qso_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<QsoLog>>
    
    /**
     * Get a single QSO log by ID
     */
    @Query("SELECT * FROM qso_logs WHERE id = :id")
    suspend fun getLogById(id: Long): QsoLog?
    
    /**
     * Insert a new QSO log
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: QsoLog): Long
    
    /**
     * Update an existing QSO log
     */
    @Update
    suspend fun update(log: QsoLog)
    
    /**
     * Delete a QSO log
     */
    @Delete
    suspend fun delete(log: QsoLog)
    
    /**
     * Get total count of QSO logs
     */
    @Query("SELECT COUNT(*) FROM qso_logs")
    fun getLogCount(): Flow<Int>
    
    /**
     * Search logs by callsign
     */
    @Query("SELECT * FROM qso_logs WHERE callsign LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchByCallsign(query: String): Flow<List<QsoLog>>
    
    // ===== 统计查询 =====
    
    /**
     * 获取指定时间范围内的通联数量
     */
    @Query("SELECT COUNT(*) FROM qso_logs WHERE timestamp >= :startTime AND timestamp <= :endTime")
    fun getLogCountInRange(startTime: Long, endTime: Long): Flow<Int>
    
    /**
     * 获取本月通联数量
     */
    @Query("SELECT COUNT(*) FROM qso_logs WHERE timestamp >= :monthStart")
    fun getLogCountThisMonth(monthStart: Long): Flow<Int>
    
    /**
     * 获取今日通联数量
     */
    @Query("SELECT COUNT(*) FROM qso_logs WHERE timestamp >= :dayStart")
    fun getLogCountToday(dayStart: Long): Flow<Int>
    
    /**
     * 获取指定年份的通联数量
     */
    @Query("SELECT COUNT(*) FROM qso_logs WHERE timestamp >= :yearStart AND timestamp < :yearEnd")
    fun getLogCountInYear(yearStart: Long, yearEnd: Long): Flow<Int>
    
    /**
     * 按频率/波段统计通联数量（返回频率字符串和计数）
     */
    @Query("SELECT frequency, COUNT(*) as count FROM qso_logs GROUP BY frequency ORDER BY count DESC LIMIT 10")
    fun getFrequencyStats(): Flow<List<FrequencyStat>>
    
    /**
     * 按模式统计通联数量
     */
    @Query("SELECT mode, COUNT(*) as count FROM qso_logs GROUP BY mode ORDER BY count DESC")
    fun getModeStats(): Flow<List<ModeStat>>
    
    /**
     * 获取所有记录用于导出
     */
    @Query("SELECT * FROM qso_logs ORDER BY timestamp DESC")
    suspend fun getAllLogsForExport(): List<QsoLog>
}
