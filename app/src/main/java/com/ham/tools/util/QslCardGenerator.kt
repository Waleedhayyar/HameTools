package com.ham.tools.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.ham.tools.data.model.QslPlaceholder
import com.ham.tools.data.model.QslTemplate
import com.ham.tools.data.model.QsoLog
import com.ham.tools.data.model.TextElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility class for generating QSL card images
 * 
 * Uses Android Canvas and Bitmap APIs to composite text onto background images
 */
@Singleton
class QslCardGenerator @Inject constructor() {
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    
    /**
     * Generate a QSL card image from a template and QSO log
     * 
     * @param context Android context
     * @param template The QSL template to use
     * @param qsoLog The QSO log data to fill in
     * @param myCallsign The user's own callsign
     * @return Generated Bitmap
     */
    suspend fun generateCard(
        context: Context,
        template: QslTemplate,
        qsoLog: QsoLog,
        myCallsign: String = "MY CALL"
    ): Bitmap = withContext(Dispatchers.Default) {
        // Create bitmap with template dimensions
        val bitmap = Bitmap.createBitmap(
            template.canvasWidth,
            template.canvasHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        
        // Draw background
        drawBackground(context, canvas, template)
        
        // Parse text elements from JSON
        val textElements = parseTextElements(template.textElementsJson)
        
        // Draw each text element
        textElements.forEach { element ->
            val text = resolvePlaceholder(element.placeholder, qsoLog, myCallsign)
            drawTextElement(canvas, element, text, template.canvasWidth, template.canvasHeight)
        }
        
        bitmap
    }
    
    /**
     * Draw background (image or solid color)
     */
    private fun drawBackground(context: Context, canvas: Canvas, template: QslTemplate) {
        if (template.backgroundUri != null) {
            try {
                val uri = Uri.parse(template.backgroundUri)
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bgBitmap = BitmapFactory.decodeStream(inputStream)
                    if (bgBitmap != null) {
                        val scaledBitmap = Bitmap.createScaledBitmap(
                            bgBitmap,
                            template.canvasWidth,
                            template.canvasHeight,
                            true
                        )
                        canvas.drawBitmap(scaledBitmap, 0f, 0f, null)
                        if (scaledBitmap != bgBitmap) {
                            scaledBitmap.recycle()
                        }
                        bgBitmap.recycle()
                        return
                    }
                }
            } catch (e: Exception) {
                // Fall through to draw solid color
            }
        }
        
        // Draw solid color background
        canvas.drawColor(template.backgroundColor)
    }
    
    /**
     * Draw a single text element on the canvas
     */
    private fun drawTextElement(
        canvas: Canvas,
        element: TextElement,
        text: String,
        canvasWidth: Int,
        canvasHeight: Int
    ) {
        val paint = Paint().apply {
            color = element.color
            textSize = element.fontSize * (canvasWidth / 400f) // Scale based on canvas size
            isAntiAlias = true
            typeface = when {
                element.fontWeight >= 700 -> Typeface.DEFAULT_BOLD
                else -> Typeface.DEFAULT
            }
            // Add shadow for better readability
            setShadowLayer(4f, 2f, 2f, Color.argb(128, 0, 0, 0))
        }
        
        // Convert percentage position to actual pixels
        val x = element.x * canvasWidth
        val y = element.y * canvasHeight
        
        canvas.drawText(text, x, y, paint)
    }
    
    /**
     * Resolve a placeholder to actual text from QSO log
     */
    private fun resolvePlaceholder(
        placeholder: QslPlaceholder,
        qsoLog: QsoLog,
        myCallsign: String
    ): String {
        return when (placeholder) {
            QslPlaceholder.MY_CALLSIGN -> myCallsign
            QslPlaceholder.THEIR_CALLSIGN -> qsoLog.callsign
            QslPlaceholder.DATE -> dateFormat.format(Date(qsoLog.timestamp))
            QslPlaceholder.TIME -> timeFormat.format(Date(qsoLog.timestamp))
            QslPlaceholder.FREQUENCY -> qsoLog.frequency
            QslPlaceholder.MODE -> qsoLog.mode.displayName
            QslPlaceholder.RST_SENT -> qsoLog.rstSent
            QslPlaceholder.RST_RCVD -> qsoLog.rstRcvd
            QslPlaceholder.QTH -> qsoLog.qth ?: ""
            QslPlaceholder.GRID -> qsoLog.gridLocator ?: ""
            QslPlaceholder.POWER -> qsoLog.txPower ?: ""
            QslPlaceholder.MESSAGE -> "73!"
        }
    }
    
    /**
     * Parse text elements from JSON string
     */
    fun parseTextElements(json: String): List<TextElement> {
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                TextElement(
                    id = obj.optString("id", "element_$i"),
                    placeholder = QslPlaceholder.valueOf(obj.getString("placeholder")),
                    x = obj.getDouble("x").toFloat(),
                    y = obj.getDouble("y").toFloat(),
                    fontSize = obj.optDouble("fontSize", 24.0).toFloat(),
                    color = obj.optInt("color", Color.WHITE),
                    fontWeight = obj.optInt("fontWeight", 400)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Serialize text elements to JSON string
     */
    fun serializeTextElements(elements: List<TextElement>): String {
        val jsonArray = JSONArray()
        elements.forEach { element ->
            val obj = org.json.JSONObject().apply {
                put("id", element.id)
                put("placeholder", element.placeholder.name)
                put("x", element.x.toDouble())
                put("y", element.y.toDouble())
                put("fontSize", element.fontSize.toDouble())
                put("color", element.color)
                put("fontWeight", element.fontWeight)
            }
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }
    
    /**
     * Save bitmap to cache for sharing
     * 
     * @param context Android context
     * @param bitmap The bitmap to share
     * @param filename Filename without extension
     * @return Uri of the cached image for sharing, or null if failed
     */
    suspend fun saveToCacheForSharing(
        context: Context,
        bitmap: Bitmap,
        filename: String = "QSL_${System.currentTimeMillis()}"
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            // 保存到应用缓存目录
            val cacheDir = File(context.cacheDir, "shared_qsl")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            
            val file = File(cacheDir, "$filename.png")
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            
            // 使用 FileProvider 获取可分享的 Uri
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Save bitmap to device gallery
     * 
     * @param context Android context
     * @param bitmap The bitmap to save
     * @param filename Filename without extension
     * @return Uri of the saved image, or null if failed
     */
    suspend fun saveToGallery(
        context: Context,
        bitmap: Bitmap,
        filename: String = "QSL_${System.currentTimeMillis()}"
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ use MediaStore
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "$filename.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/HamTools")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                
                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    }
                    
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                
                uri
            } else {
                // Legacy approach for older Android versions
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val hamToolsDir = File(picturesDir, "HamTools")
                if (!hamToolsDir.exists()) {
                    hamToolsDir.mkdirs()
                }
                
                val file = File(hamToolsDir, "$filename.png")
                FileOutputStream(file).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                
                // Notify gallery
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DATA, file.absolutePath)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                }
                context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Create a default template with common elements
     * 使用固定颜色作为默认值（用于非动态场景）
     */
    fun createDefaultTemplate(): QslTemplate {
        return createDefaultTemplate(
            primaryColor = 0xFF6750A4.toInt(),      // Material 3 Primary
            onPrimaryColor = 0xFFFFFFFF.toInt(),    // White
            secondaryColor = 0xFF625B71.toInt(),    // Material 3 Secondary
            tertiaryColor = 0xFF7D5260.toInt(),     // Material 3 Tertiary
            backgroundColor = 0xFF1C1B1F.toInt()    // Material 3 Dark surface
        )
    }
    
    /**
     * Create a default template with Material 3 dynamic colors
     * 
     * @param primaryColor 主色调
     * @param onPrimaryColor 主色调上的文字颜色
     * @param secondaryColor 次要颜色
     * @param tertiaryColor 第三颜色
     * @param backgroundColor 背景颜色
     */
    fun createDefaultTemplate(
        primaryColor: Int,
        onPrimaryColor: Int,
        secondaryColor: Int,
        tertiaryColor: Int,
        backgroundColor: Int
    ): QslTemplate {
        val defaultElements = listOf(
            TextElement(
                id = "my_call",
                placeholder = QslPlaceholder.MY_CALLSIGN,
                x = 0.05f,
                y = 0.18f,
                fontSize = 52f,
                color = onPrimaryColor,
                fontWeight = 700
            ),
            TextElement(
                id = "to_radio",
                placeholder = QslPlaceholder.THEIR_CALLSIGN,
                x = 0.05f,
                y = 0.38f,
                fontSize = 36f,
                color = primaryColor,
                fontWeight = 700
            ),
            TextElement(
                id = "date",
                placeholder = QslPlaceholder.DATE,
                x = 0.05f,
                y = 0.52f,
                fontSize = 22f,
                color = onPrimaryColor,
                fontWeight = 400
            ),
            TextElement(
                id = "time",
                placeholder = QslPlaceholder.TIME,
                x = 0.32f,
                y = 0.52f,
                fontSize = 22f,
                color = secondaryColor,
                fontWeight = 400
            ),
            TextElement(
                id = "freq",
                placeholder = QslPlaceholder.FREQUENCY,
                x = 0.05f,
                y = 0.66f,
                fontSize = 26f,
                color = tertiaryColor,
                fontWeight = 500
            ),
            TextElement(
                id = "mode",
                placeholder = QslPlaceholder.MODE,
                x = 0.40f,
                y = 0.66f,
                fontSize = 26f,
                color = tertiaryColor,
                fontWeight = 500
            ),
            TextElement(
                id = "rst",
                placeholder = QslPlaceholder.RST_SENT,
                x = 0.05f,
                y = 0.80f,
                fontSize = 22f,
                color = secondaryColor,
                fontWeight = 400
            ),
            TextElement(
                id = "msg",
                placeholder = QslPlaceholder.MESSAGE,
                x = 0.65f,
                y = 0.92f,
                fontSize = 30f,
                color = primaryColor,
                fontWeight = 700
            )
        )
        
        return QslTemplate(
            name = "默认模板",
            backgroundColor = backgroundColor,
            textElementsJson = serializeTextElements(defaultElements),
            isDefault = true
        )
    }
}
