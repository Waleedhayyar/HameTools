package com.ham.tools.ui.screens.tools.qsl

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ham.tools.data.model.QslPlaceholder
import com.ham.tools.data.model.QslTemplate
import com.ham.tools.data.model.QsoLog
import com.ham.tools.data.model.TextElement
import com.ham.tools.data.repository.QslTemplateRepository
import com.ham.tools.data.repository.QsoLogRepository
import com.ham.tools.data.repository.UserPreferencesRepository
import com.ham.tools.util.QslCardGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject

/**
 * Material 3 颜色配置，用于创建动态取色模板
 */
data class M3Colors(
    val primary: Int,
    val onPrimary: Int,
    val secondary: Int,
    val tertiary: Int,
    val surfaceContainer: Int
)

/**
 * UI State for QSL Editor
 */
data class QslEditorState(
    val templateName: String = "新模板",
    val backgroundUri: String? = null,
    val backgroundColor: Int = 0xFF1C1B1F.toInt(),
    val textElements: List<TextElement> = emptyList(),
    val selectedElementId: String? = null,
    val isSaving: Boolean = false,
    val savedMessage: String? = null,
    val showAddElementDialog: Boolean = false,
    val showColorPicker: Boolean = false,
    val isInitialized: Boolean = false,
    val userCallsign: String = "",
    val qsoLog: QsoLog? = null,  // 当前关联的通联记录
    val isGenerateMode: Boolean = false,  // 是否为生成卡片模式（从通联记录进入）
    val showExportDialog: Boolean = false,  // 是否显示导出对话框
    val isExporting: Boolean = false  // 是否正在导出
)

/**
 * ViewModel for QSL Card Editor
 */
@HiltViewModel
class QslEditorViewModel @Inject constructor(
    private val repository: QslTemplateRepository,
    private val qsoLogRepository: QsoLogRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val generator: QslCardGenerator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val _state = MutableStateFlow(QslEditorState())
    val state: StateFlow<QslEditorState> = _state.asStateFlow()
    
    val templates = repository.getAllTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    private var currentTemplateId: Long? = null
    private var themeColors: M3Colors? = null
    
    // 从导航参数获取 QSO Log ID
    private val qsoLogId: Long? = savedStateHandle.get<Long>("qsoLogId")
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    
    init {
        // Load user callsign
        viewModelScope.launch {
            val profile = userPreferencesRepository.userProfile.first()
            _state.value = _state.value.copy(userCallsign = profile.callsign)
            
            // 如果有 QSO Log ID，加载通联记录
            qsoLogId?.let { id ->
                val qsoLog = qsoLogRepository.getLogById(id)
                if (qsoLog != null) {
                    _state.value = _state.value.copy(
                        qsoLog = qsoLog,
                        isGenerateMode = true
                    )
                }
            }
        }
    }
    
    /**
     * 根据占位符类型获取实际显示文本
     * 如果有 QSO 数据则使用实际数据，否则使用默认文本
     */
    fun getTextForPlaceholder(placeholder: QslPlaceholder): String {
        val qsoLog = _state.value.qsoLog
        val userCallsign = _state.value.userCallsign
        
        return when (placeholder) {
            QslPlaceholder.MY_CALLSIGN -> userCallsign.ifBlank { placeholder.defaultText }
            QslPlaceholder.THEIR_CALLSIGN -> qsoLog?.callsign ?: placeholder.defaultText
            QslPlaceholder.DATE -> qsoLog?.let { dateFormat.format(Date(it.timestamp)) } ?: placeholder.defaultText
            QslPlaceholder.TIME -> qsoLog?.let { timeFormat.format(Date(it.timestamp)) } ?: placeholder.defaultText
            QslPlaceholder.FREQUENCY -> qsoLog?.frequency ?: placeholder.defaultText
            QslPlaceholder.MODE -> qsoLog?.mode?.displayName ?: placeholder.defaultText
            QslPlaceholder.RST_SENT -> qsoLog?.rstSent ?: placeholder.defaultText
            QslPlaceholder.RST_RCVD -> qsoLog?.rstRcvd ?: placeholder.defaultText
            QslPlaceholder.QTH -> qsoLog?.qth ?: placeholder.defaultText
            QslPlaceholder.GRID -> qsoLog?.gridLocator ?: placeholder.defaultText
            QslPlaceholder.POWER -> qsoLog?.txPower ?: placeholder.defaultText
            QslPlaceholder.MESSAGE -> placeholder.defaultText
        }
    }
    
    /**
     * Initialize with Material 3 theme colors
     * 在 Composable 中调用，传入当前主题颜色
     */
    fun initializeWithColors(colors: M3Colors) {
        if (_state.value.isInitialized) return
        
        themeColors = colors
        viewModelScope.launch {
            val defaultTemplate = repository.getDefaultTemplate()
            if (defaultTemplate != null) {
                loadTemplate(defaultTemplate)
            } else {
                // Create and save default template with dynamic colors
                val template = generator.createDefaultTemplate(
                    primaryColor = colors.primary,
                    onPrimaryColor = colors.onPrimary,
                    secondaryColor = colors.secondary,
                    tertiaryColor = colors.tertiary,
                    backgroundColor = colors.surfaceContainer
                )
                val id = repository.insertTemplate(template)
                repository.setAsDefault(id)
                loadTemplate(template.copy(id = id))
            }
            _state.value = _state.value.copy(isInitialized = true)
        }
    }
    
    /**
     * Load a template into the editor
     */
    fun loadTemplate(template: QslTemplate) {
        currentTemplateId = template.id
        _state.value = _state.value.copy(
            templateName = template.name,
            backgroundUri = template.backgroundUri,
            backgroundColor = template.backgroundColor,
            textElements = generator.parseTextElements(template.textElementsJson),
            selectedElementId = null
        )
    }
    
    /**
     * Set background image from URI
     * 上传新背景时清空所有文本元素，用户需要自己添加
     */
    fun setBackgroundImage(uri: Uri) {
        _state.value = _state.value.copy(
            backgroundUri = uri.toString(),
            textElements = emptyList(),  // 清空所有文本元素
            selectedElementId = null
        )
    }
    
    /**
     * Set solid background color
     */
    fun setBackgroundColor(color: Int) {
        _state.value = _state.value.copy(
            backgroundColor = color,
            backgroundUri = null // Clear image when using solid color
        )
    }
    
    /**
     * Add a new text element
     * 如果是"我的呼号"类型且用户已设置呼号，自动关联
     */
    fun addTextElement(placeholder: QslPlaceholder) {
        // 使用主题颜色来设定默认元素颜色
        val defaultColor = themeColors?.onPrimary ?: 0xFFFFFFFF.toInt()
        
        val newElement = TextElement(
            id = UUID.randomUUID().toString(),
            placeholder = placeholder,
            x = 0.1f,
            y = 0.5f,
            fontSize = if (placeholder == QslPlaceholder.MY_CALLSIGN) 48f else 24f,
            color = defaultColor,
            fontWeight = if (placeholder == QslPlaceholder.MY_CALLSIGN) 700 else 400
        )
        _state.value = _state.value.copy(
            textElements = _state.value.textElements + newElement,
            selectedElementId = newElement.id,
            showAddElementDialog = false
        )
    }
    
    /**
     * 获取用户呼号（用于在生成卡片时自动填入）
     */
    fun getUserCallsign(): String = _state.value.userCallsign
    
    /**
     * Select an element for editing
     */
    fun selectElement(elementId: String?) {
        _state.value = _state.value.copy(selectedElementId = elementId)
    }
    
    /**
     * Update element position by delta (增量移动)
     */
    fun updateElementPosition(elementId: String, deltaX: Float, deltaY: Float) {
        val elements = _state.value.textElements
        val index = elements.indexOfFirst { it.id == elementId }
        if (index == -1) return
        
        val element = elements[index]
        val newX = (element.x + deltaX).coerceIn(0f, 1f)
        val newY = (element.y + deltaY).coerceIn(0f, 1f)
        
        // 只有位置变化时才更新
        if (newX != element.x || newY != element.y) {
            val newElements = elements.toMutableList()
            newElements[index] = element.copy(x = newX, y = newY)
            _state.value = _state.value.copy(textElements = newElements)
        }
    }
    
    /**
     * Update element font size (called during pinch zoom)
     */
    fun updateElementFontSize(elementId: String, scaleFactor: Float) {
        val elements = _state.value.textElements
        val index = elements.indexOfFirst { it.id == elementId }
        if (index == -1) return
        
        val element = elements[index]
        val newSize = (element.fontSize * scaleFactor).coerceIn(12f, 120f)
        
        // 只有大小变化时才更新
        if (newSize != element.fontSize) {
            val newElements = elements.toMutableList()
            newElements[index] = element.copy(fontSize = newSize)
            _state.value = _state.value.copy(textElements = newElements)
        }
    }
    
    /**
     * Update element color
     */
    fun updateElementColor(elementId: String, color: Int) {
        _state.value = _state.value.copy(
            textElements = _state.value.textElements.map { element ->
                if (element.id == elementId) {
                    element.copy(color = color)
                } else element
            },
            showColorPicker = false
        )
    }
    
    /**
     * Toggle element bold
     */
    fun toggleElementBold(elementId: String) {
        _state.value = _state.value.copy(
            textElements = _state.value.textElements.map { element ->
                if (element.id == elementId) {
                    element.copy(fontWeight = if (element.fontWeight >= 700) 400 else 700)
                } else element
            }
        )
    }
    
    /**
     * Delete selected element
     */
    fun deleteSelectedElement() {
        val selectedId = _state.value.selectedElementId ?: return
        _state.value = _state.value.copy(
            textElements = _state.value.textElements.filter { it.id != selectedId },
            selectedElementId = null
        )
    }
    
    /**
     * Show add element dialog
     */
    fun showAddElementDialog() {
        _state.value = _state.value.copy(showAddElementDialog = true)
    }
    
    /**
     * Hide add element dialog
     */
    fun hideAddElementDialog() {
        _state.value = _state.value.copy(showAddElementDialog = false)
    }
    
    /**
     * Show color picker
     */
    fun showColorPicker() {
        _state.value = _state.value.copy(showColorPicker = true)
    }
    
    /**
     * Hide color picker
     */
    fun hideColorPicker() {
        _state.value = _state.value.copy(showColorPicker = false)
    }
    
    /**
     * Update template name
     */
    fun updateTemplateName(name: String) {
        _state.value = _state.value.copy(templateName = name)
    }
    
    /**
     * Save template to database
     */
    fun saveTemplate() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            
            val state = _state.value
            val template = QslTemplate(
                id = currentTemplateId ?: 0,
                name = state.templateName,
                backgroundUri = state.backgroundUri,
                backgroundColor = state.backgroundColor,
                textElementsJson = generator.serializeTextElements(state.textElements),
                isDefault = currentTemplateId?.let { 
                    repository.getTemplateById(it)?.isDefault 
                } ?: false,
                updatedAt = System.currentTimeMillis()
            )
            
            if (currentTemplateId != null && currentTemplateId != 0L) {
                repository.updateTemplate(template)
            } else {
                val id = repository.insertTemplate(template)
                currentTemplateId = id
            }
            
            _state.value = _state.value.copy(
                isSaving = false,
                savedMessage = "模板已保存"
            )
        }
    }
    
    /**
     * Create new template
     * 创建空白模板，不包含任何文本元素
     */
    fun createNewTemplate() {
        currentTemplateId = null
        val backgroundColor = themeColors?.surfaceContainer ?: 0xFF1C1B1F.toInt()
        _state.value = _state.value.copy(
            templateName = "新模板",
            backgroundUri = null,
            backgroundColor = backgroundColor,
            textElements = emptyList(),  // 空白模板
            selectedElementId = null,
            showAddElementDialog = false,
            showColorPicker = false
        )
    }
    
    /**
     * Delete a template by ID
     */
    fun deleteTemplate(templateId: Long) {
        viewModelScope.launch {
            val template = repository.getTemplateById(templateId)
            if (template != null) {
                repository.deleteTemplate(template)
                // 如果删除的是当前模板，加载默认模板
                if (currentTemplateId == templateId) {
                    val defaultTemplate = repository.getDefaultTemplate()
                    if (defaultTemplate != null) {
                        loadTemplate(defaultTemplate)
                    } else {
                        createNewTemplate()
                    }
                }
            }
        }
    }
    
    /**
     * Set as default template
     */
    fun setAsDefault() {
        currentTemplateId?.let { id ->
            viewModelScope.launch {
                repository.setAsDefault(id)
            }
        }
    }
    
    /**
     * Clear saved message
     */
    fun clearSavedMessage() {
        _state.value = _state.value.copy(savedMessage = null)
    }
    
    /**
     * Build current template for preview/export
     */
    fun buildCurrentTemplate(): QslTemplate {
        val state = _state.value
        return QslTemplate(
            id = currentTemplateId ?: 0,
            name = state.templateName,
            backgroundUri = state.backgroundUri,
            backgroundColor = state.backgroundColor,
            textElementsJson = generator.serializeTextElements(state.textElements)
        )
    }
    
    // ===== 导出功能 =====
    
    /**
     * 显示导出对话框
     */
    fun showExportDialog() {
        _state.value = _state.value.copy(showExportDialog = true)
    }
    
    /**
     * 隐藏导出对话框
     */
    fun hideExportDialog() {
        _state.value = _state.value.copy(showExportDialog = false)
    }
    
    /**
     * 生成 QSL 卡片 Bitmap
     */
    suspend fun generateCardBitmap(context: android.content.Context): android.graphics.Bitmap? {
        val state = _state.value
        val template = buildCurrentTemplate()
        
        // 如果有 QSO 数据，使用实际数据生成
        val qsoLog = state.qsoLog
        return if (qsoLog != null) {
            generator.generateCard(
                context = context,
                template = template,
                qsoLog = qsoLog,
                myCallsign = state.userCallsign
            )
        } else {
            // 使用默认数据预览
            val dummyQsoLog = QsoLog(
                callsign = "PREVIEW",
                frequency = "14.200 MHz",
                mode = com.ham.tools.data.model.Mode.SSB
            )
            generator.generateCard(
                context = context,
                template = template,
                qsoLog = dummyQsoLog,
                myCallsign = state.userCallsign.ifBlank { "MY CALL" }
            )
        }
    }
    
    /**
     * 保存卡片到相册
     */
    fun saveToGallery(context: android.content.Context) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isExporting = true)
            
            try {
                val bitmap = generateCardBitmap(context)
                if (bitmap != null) {
                    val filename = generateFilename()
                    val uri = generator.saveToGallery(context, bitmap, filename)
                    
                    if (uri != null) {
                        _state.value = _state.value.copy(
                            isExporting = false,
                            showExportDialog = false,
                            savedMessage = "已保存到相册"
                        )
                    } else {
                        _state.value = _state.value.copy(
                            isExporting = false,
                            savedMessage = "保存失败"
                        )
                    }
                    bitmap.recycle()
                } else {
                    _state.value = _state.value.copy(
                        isExporting = false,
                        savedMessage = "生成卡片失败"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isExporting = false,
                    savedMessage = "保存失败: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 分享卡片到第三方应用
     */
    fun shareCard(context: android.content.Context, onShare: (android.net.Uri) -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isExporting = true)
            
            try {
                val bitmap = generateCardBitmap(context)
                if (bitmap != null) {
                    val filename = generateFilename()
                    val uri = generator.saveToCacheForSharing(context, bitmap, filename)
                    
                    if (uri != null) {
                        _state.value = _state.value.copy(
                            isExporting = false,
                            showExportDialog = false
                        )
                        onShare(uri)
                    } else {
                        _state.value = _state.value.copy(
                            isExporting = false,
                            savedMessage = "准备分享失败"
                        )
                    }
                    bitmap.recycle()
                } else {
                    _state.value = _state.value.copy(
                        isExporting = false,
                        savedMessage = "生成卡片失败"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isExporting = false,
                    savedMessage = "分享失败: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 生成文件名
     */
    private fun generateFilename(): String {
        val state = _state.value
        val callsign = state.qsoLog?.callsign ?: "QSL"
        val timestamp = System.currentTimeMillis()
        return "QSL_${callsign}_$timestamp"
    }
}
