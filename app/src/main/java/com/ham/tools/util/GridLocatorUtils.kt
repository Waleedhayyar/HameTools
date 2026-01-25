package com.ham.tools.util

import kotlin.math.floor

/**
 * 梅登海德网格定位工具类 (Maidenhead Grid Locator)
 * 
 * 网格系统说明：
 * - 2位 Field: A-R（经度）+ A-R（纬度），每格 20° x 10°
 * - 4位 Square: 00-99，每格 2° x 1°
 * - 6位 Sub-square: a-x（经度）+ a-x（纬度），每格 5' x 2.5'
 * 
 * 例如：OM88hf 表示北京附近的位置
 */
object GridLocatorUtils {
    
    private const val FIELD_CHARS = "ABCDEFGHIJKLMNOPQR"
    private const val SUBSQUARE_CHARS = "abcdefghijklmnopqrstuvwx"
    
    // 经度范围: -180 to 180
    // 纬度范围: -90 to 90
    
    /**
     * 将经纬度转换为6位梅登海德网格字符串
     * 
     * @param latitude 纬度 (-90 to 90)
     * @param longitude 经度 (-180 to 180)
     * @return 6位网格字符串，例如 "OM88hf"
     * @throws IllegalArgumentException 如果经纬度超出范围
     */
    fun toGrid(latitude: Double, longitude: Double): String {
        require(latitude in -90.0..90.0) { "纬度必须在 -90 到 90 之间" }
        require(longitude in -180.0..180.0) { "经度必须在 -180 到 180 之间" }
        
        // 标准化经纬度（加偏移使其从0开始）
        val lon = longitude + 180.0
        val lat = latitude + 90.0
        
        // Field (第1-2位): 每格 20° x 10°
        val fieldLon = floor(lon / 20.0).toInt()
        val fieldLat = floor(lat / 10.0).toInt()
        
        // Square (第3-4位): 每格 2° x 1°
        val squareLon = floor((lon % 20.0) / 2.0).toInt()
        val squareLat = floor((lat % 10.0) / 1.0).toInt()
        
        // Sub-square (第5-6位): 每格 5' x 2.5' (即 5/60° x 2.5/60°)
        val subLon = floor((lon % 2.0) / (5.0 / 60.0)).toInt()
        val subLat = floor((lat % 1.0) / (2.5 / 60.0)).toInt()
        
        return buildString {
            append(FIELD_CHARS[fieldLon])
            append(FIELD_CHARS[fieldLat])
            append(squareLon)
            append(squareLat)
            append(SUBSQUARE_CHARS[subLon])
            append(SUBSQUARE_CHARS[subLat])
        }
    }
    
    /**
     * 将网格字符串转换为中心点的经纬度
     * 
     * @param grid 4位或6位网格字符串
     * @return Pair<纬度, 经度>，代表网格的中心点
     * @throws IllegalArgumentException 如果网格格式无效
     */
    fun toLatLng(grid: String): Pair<Double, Double> {
        val upperGrid = grid.uppercase()
        
        require(upperGrid.length in listOf(4, 6)) { "网格必须是4位或6位" }
        require(upperGrid[0] in 'A'..'R' && upperGrid[1] in 'A'..'R') { "前两位必须是 A-R" }
        require(upperGrid[2].isDigit() && upperGrid[3].isDigit()) { "第3-4位必须是数字" }
        
        if (upperGrid.length == 6) {
            require(upperGrid[4] in 'A'..'X' && upperGrid[5] in 'A'..'X') { "第5-6位必须是 A-X" }
        }
        
        // Field
        var lon = (upperGrid[0] - 'A') * 20.0 - 180.0
        var lat = (upperGrid[1] - 'A') * 10.0 - 90.0
        
        // Square
        lon += (upperGrid[2] - '0') * 2.0
        lat += (upperGrid[3] - '0') * 1.0
        
        if (upperGrid.length == 6) {
            // Sub-square
            lon += (upperGrid[4] - 'A') * (5.0 / 60.0)
            lat += (upperGrid[5] - 'A') * (2.5 / 60.0)
            
            // 中心点偏移 (半个 sub-square)
            lon += 2.5 / 60.0
            lat += 1.25 / 60.0
        } else {
            // 4位网格的中心点偏移 (半个 square)
            lon += 1.0
            lat += 0.5
        }
        
        return Pair(lat, lon)
    }
    
    /**
     * 验证网格字符串格式是否有效
     * 
     * @param grid 要验证的网格字符串
     * @return true 如果格式有效
     */
    fun isValidGrid(grid: String): Boolean {
        if (grid.length !in listOf(4, 6)) return false
        
        val upper = grid.uppercase()
        
        // 检查 Field
        if (upper[0] !in 'A'..'R' || upper[1] !in 'A'..'R') return false
        
        // 检查 Square
        if (!upper[2].isDigit() || !upper[3].isDigit()) return false
        
        // 如果是6位，检查 Sub-square
        if (upper.length == 6) {
            if (upper[4] !in 'A'..'X' || upper[5] !in 'A'..'X') return false
        }
        
        return true
    }
    
    /**
     * 格式化网格字符串（大写 Field + 数字 + 小写 Sub-square）
     * 
     * @param grid 输入网格字符串
     * @return 格式化后的字符串，例如 "OM88hf"
     */
    fun formatGrid(grid: String): String {
        if (!isValidGrid(grid)) return grid
        
        val upper = grid.uppercase()
        return buildString {
            append(upper[0])
            append(upper[1])
            append(upper[2])
            append(upper[3])
            if (upper.length == 6) {
                append(upper[4].lowercaseChar())
                append(upper[5].lowercaseChar())
            }
        }
    }
    
    /**
     * 计算两个网格之间的大致距离（公里）
     * 使用 Haversine 公式
     * 
     * @param grid1 第一个网格
     * @param grid2 第二个网格
     * @return 距离（公里）
     */
    fun distanceBetween(grid1: String, grid2: String): Double {
        val (lat1, lon1) = toLatLng(grid1)
        val (lat2, lon2) = toLatLng(grid2)
        
        val r = 6371.0 // 地球半径（公里）
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        
        return r * c
    }
}
