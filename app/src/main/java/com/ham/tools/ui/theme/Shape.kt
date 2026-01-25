package com.ham.tools.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 Shapes - Android 16 风格大圆角
 * 
 * 遵循 Android 16 设计语言，使用更大的圆角：
 * - extraSmall: 8dp (小型元素，如 Chip)
 * - small: 12dp (一般小卡片)
 * - medium: 20dp (普通卡片、对话框)
 * - large: 28dp (大型卡片、底部面板)
 * - extraLarge: 32dp (全屏面板)
 */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/**
 * 常用圆角尺寸
 */
object CornerRadius {
    /** 超小圆角 - 用于小型 Chip、Badge */
    val ExtraSmall = 8.dp
    
    /** 小圆角 - 用于列表项、小按钮 */
    val Small = 12.dp
    
    /** 中等圆角 - 用于输入框、普通卡片 */
    val Medium = 20.dp
    
    /** 大圆角 - 用于大型卡片、底部面板 */
    val Large = 28.dp
    
    /** 超大圆角 - 用于全屏面板、特殊展示卡片 */
    val ExtraLarge = 32.dp
    
    /** 全圆角 - 用于圆形按钮、头像 */
    val Full = 100.dp
}
