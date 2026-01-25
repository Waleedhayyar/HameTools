package com.ham.tools.data.remote

import android.util.Xml
import com.ham.tools.data.model.BandCondition
import com.ham.tools.data.model.SolarData
import com.ham.tools.data.model.VhfCondition
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream

/**
 * HamQSL XML 数据解析器
 * 
 * 使用 Android 内置的 XmlPullParser 解析太阳/地磁数据
 * 这比第三方库更轻量且兼容性更好
 */
object SolarXmlParser {
    
    // XML 命名空间（无）
    private val ns: String? = null
    
    /**
     * 解析 XML 输入流为 SolarData 对象
     * 
     * @param inputStream XML 数据输入流
     * @return 解析后的 SolarData 对象
     * @throws XmlPullParserException XML 解析错误
     * @throws IOException 读取错误
     */
    @Throws(XmlPullParserException::class, IOException::class)
    fun parse(inputStream: InputStream): SolarData {
        inputStream.use { stream ->
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(stream, "UTF-8")
            parser.nextTag()
            return readSolarData(parser)
        }
    }
    
    /**
     * 读取 <solar> 根元素下的 <solardata>
     */
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readSolarData(parser: XmlPullParser): SolarData {
        // 默认值
        var updated = ""
        var solarFlux = 0
        var aIndex = 0
        var kIndex = 0
        var geomagField = "UNKNOWN"
        var signalNoise = "S0"
        var solarWind: Int? = null
        var magneticBz: Double? = null
        var sunspots: Int? = null
        var xRay: String? = null
        var protonFlux: String? = null
        var electronFlux: String? = null
        val bandConditions = mutableListOf<BandCondition>()
        val vhfConditions = mutableListOf<VhfCondition>()
        
        // 跳过 <solar> 根元素，找到 <solardata>
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "solardata") {
                break
            }
            parser.next()
        }
        
        // 解析 <solardata> 内的元素
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            
            when (parser.name) {
                "updated" -> updated = readText(parser)
                "solarflux" -> solarFlux = readText(parser).toIntOrNull() ?: 0
                "aindex" -> aIndex = readText(parser).toIntOrNull() ?: 0
                "kindex" -> kIndex = readText(parser).toIntOrNull() ?: 0
                "kindexnt" -> {
                    // kindex 可能有两个版本，取第一个非零值
                    if (kIndex == 0) {
                        kIndex = readText(parser).toIntOrNull() ?: 0
                    } else {
                        skip(parser)
                    }
                }
                "geomagfield" -> geomagField = readText(parser)
                "signalnoise" -> signalNoise = readText(parser)
                "solarwind" -> solarWind = readText(parser).toIntOrNull()
                "magneticfield" -> magneticBz = readText(parser).toDoubleOrNull()
                "sunspots" -> sunspots = readText(parser).toIntOrNull()
                "xray" -> xRay = readText(parser)
                "protonflux" -> protonFlux = readText(parser)
                "electonflux", "electronflux" -> electronFlux = readText(parser)
                "calculatedconditions" -> {
                    bandConditions.addAll(readBandConditions(parser))
                }
                "calculatedvhfconditions" -> {
                    vhfConditions.addAll(readVhfConditions(parser))
                }
                else -> skip(parser)
            }
        }
        
        return SolarData(
            updated = updated,
            solarFlux = solarFlux,
            aIndex = aIndex,
            kIndex = kIndex,
            geomagField = geomagField,
            signalNoise = signalNoise,
            solarWind = solarWind,
            magneticBz = magneticBz,
            sunspots = sunspots,
            xRay = xRay,
            protonFlux = protonFlux,
            electronFlux = electronFlux,
            bandConditions = bandConditions,
            vhfConditions = vhfConditions,
            fetchedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * 读取 <calculatedconditions> 下的所有 <band> 元素
     * 
     * 格式：<band name="80m-40m" time="day">Good</band>
     */
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readBandConditions(parser: XmlPullParser): List<BandCondition> {
        val conditions = mutableListOf<BandCondition>()
        
        while (parser.next() != XmlPullParser.END_TAG || parser.name != "calculatedconditions") {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            
            if (parser.name == "band") {
                val name = parser.getAttributeValue(ns, "name") ?: "unknown"
                val time = parser.getAttributeValue(ns, "time") ?: "day"
                val condition = readText(parser)
                conditions.add(BandCondition(name, time, condition))
            } else {
                skip(parser)
            }
        }
        
        return conditions
    }
    
    /**
     * 读取 <calculatedvhfconditions> 下的元素
     */
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readVhfConditions(parser: XmlPullParser): List<VhfCondition> {
        val conditions = mutableListOf<VhfCondition>()
        
        while (parser.next() != XmlPullParser.END_TAG || parser.name != "calculatedvhfconditions") {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            
            val phenomenon = parser.name
            val location = parser.getAttributeValue(ns, "location")
            val text = readText(parser)
            
            // 只添加有意义的 VHF 条件
            if (text.isNotBlank() && text.lowercase() != "band closed") {
                conditions.add(VhfCondition(phenomenon, location))
            }
        }
        
        return conditions
    }
    
    /**
     * 读取元素的文本内容
     */
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text.trim()
            parser.nextTag()
        }
        return result
    }
    
    /**
     * 跳过当前元素及其子元素
     */
    @Throws(XmlPullParserException::class, IOException::class)
    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) {
            throw IllegalStateException("Expected START_TAG")
        }
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }
}
