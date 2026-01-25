package com.ham.tools.util

import android.content.Context
import android.net.Uri
import com.ham.tools.data.model.QsoLog
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 数据导出工具
 * 
 * 支持将 QSO 日志导出为 JSON 或 CSV 格式
 */
object DataExporter {
    
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    /**
     * 可序列化的 QSO 数据
     */
    @Serializable
    data class ExportableQso(
        val id: Long,
        val callsign: String,
        val frequency: String,
        val mode: String,
        val rstSent: String,
        val rstRcvd: String,
        val timestamp: Long,
        val timestampFormatted: String,
        val opName: String?,
        val qth: String?,
        val gridLocator: String?,
        val qslInfo: String?,
        val txPower: String?,
        val rig: String?,
        val myGridLocator: String?,
        val propagation: String,
        val qslStatus: String,
        val remarks: String?
    )
    
    /**
     * 导出数据包装
     */
    @Serializable
    data class ExportData(
        val appVersion: String = "1.0.0",
        val exportTime: String,
        val totalRecords: Int,
        val qsoLogs: List<ExportableQso>
    )
    
    /**
     * 将 QsoLog 转换为可导出格式
     */
    private fun QsoLog.toExportable(): ExportableQso {
        return ExportableQso(
            id = id,
            callsign = callsign,
            frequency = frequency,
            mode = mode.name,
            rstSent = rstSent,
            rstRcvd = rstRcvd,
            timestamp = timestamp,
            timestampFormatted = dateFormat.format(Date(timestamp)),
            opName = opName,
            qth = qth,
            gridLocator = gridLocator,
            qslInfo = qslInfo,
            txPower = txPower,
            rig = rig,
            myGridLocator = myGridLocator,
            propagation = propagation.name,
            qslStatus = qslStatus.name,
            remarks = remarks
        )
    }
    
    /**
     * 导出为 JSON 格式
     * 
     * @param context Context
     * @param logs QSO 日志列表
     * @param uri 目标文件 URI
     * @return 是否成功
     */
    fun exportToJson(context: Context, logs: List<QsoLog>, uri: Uri): Result<Int> {
        return try {
            val exportData = ExportData(
                exportTime = dateFormat.format(Date()),
                totalRecords = logs.size,
                qsoLogs = logs.map { it.toExportable() }
            )
            
            val jsonString = json.encodeToString(exportData)
            
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(jsonString)
                }
            }
            
            Result.success(logs.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 导出为 CSV 格式 (ADIF 兼容)
     * 
     * @param context Context
     * @param logs QSO 日志列表
     * @param uri 目标文件 URI
     * @return 是否成功
     */
    fun exportToCsv(context: Context, logs: List<QsoLog>, uri: Uri): Result<Int> {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    // CSV 头部
                    writer.write("ID,Callsign,Frequency,Mode,RST_Sent,RST_Rcvd,Timestamp,Date,Time,")
                    writer.write("Op_Name,QTH,Grid_Locator,QSL_Info,TX_Power,Rig,My_Grid,Propagation,QSL_Status,Remarks\n")
                    
                    // 数据行
                    logs.forEach { log ->
                        val date = dateFormat.format(Date(log.timestamp))
                        val datePart = date.substringBefore(" ")
                        val timePart = date.substringAfter(" ")
                        
                        writer.write(buildString {
                            append("${log.id},")
                            append("\"${log.callsign}\",")
                            append("\"${log.frequency}\",")
                            append("${log.mode.name},")
                            append("${log.rstSent},")
                            append("${log.rstRcvd},")
                            append("${log.timestamp},")
                            append("$datePart,")
                            append("$timePart,")
                            append("\"${log.opName ?: ""}\",")
                            append("\"${log.qth ?: ""}\",")
                            append("\"${log.gridLocator ?: ""}\",")
                            append("\"${log.qslInfo ?: ""}\",")
                            append("\"${log.txPower ?: ""}\",")
                            append("\"${log.rig ?: ""}\",")
                            append("\"${log.myGridLocator ?: ""}\",")
                            append("${log.propagation.name},")
                            append("${log.qslStatus.name},")
                            append("\"${log.remarks?.replace("\"", "\"\"") ?: ""}\"\n")
                        })
                    }
                }
            }
            
            Result.success(logs.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 生成导出文件名
     */
    fun generateFileName(format: String): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "hamtools_backup_$timestamp.$format"
    }
}
