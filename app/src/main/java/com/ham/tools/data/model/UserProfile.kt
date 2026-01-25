package com.ham.tools.data.model

import kotlinx.serialization.Serializable

/**
 * 用户执照等级
 */
@Serializable
enum class LicenseClass(val displayName: String, val displayNameCn: String) {
    A("Class A", "A类"),
    B("Class B", "B类"),
    C("Class C", "C类"),
    NONE("Not Set", "未设置")
}

/**
 * 用户配置数据模型
 * 
 * 存储用户的呼号、执照信息等
 */
@Serializable
data class UserProfile(
    /** 用户呼号 */
    val callsign: String = "",
    
    /** 用户姓名 */
    val name: String = "",
    
    /** 执照等级 */
    val licenseClass: LicenseClass = LicenseClass.NONE,
    
    /** 执照到期日 (毫秒时间戳) */
    val licenseExpiryDate: Long? = null,
    
    /** 执照照片 URI (可选) */
    val licensePhotoUri: String? = null,
    
    /** 我的网格定位 */
    val gridLocator: String = "",
    
    /** 我的 QTH */
    val qth: String = "",
    
    /** 是否已完成初始设置 */
    val isOnboardingComplete: Boolean = false,
    
    /** 首次打开时间 */
    val firstLaunchTime: Long = System.currentTimeMillis()
) {
    /**
     * 检查是否已设置呼号
     */
    val hasCallsign: Boolean get() = callsign.isNotBlank()
    
    /**
     * 检查执照是否即将过期（30天内）
     */
    val isLicenseExpiringSoon: Boolean
        get() {
            if (licenseExpiryDate == null) return false
            val daysUntilExpiry = (licenseExpiryDate - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)
            return daysUntilExpiry in 0..30
        }
    
    /**
     * 检查执照是否已过期
     */
    val isLicenseExpired: Boolean
        get() {
            if (licenseExpiryDate == null) return false
            return licenseExpiryDate < System.currentTimeMillis()
        }
}

/**
 * 应用设置
 */
@Serializable
data class AppSettings(
    /** LLM API Key (如 OpenAI) */
    val llmApiKey: String = "",
    
    /** LLM 服务端点 */
    val llmEndpoint: String = "https://api.openai.com/v1",
    
    /** 是否启用深色模式 (null = 跟随系统) */
    val darkMode: Boolean? = null,
    
    /** 是否启用动态取色 */
    val dynamicColor: Boolean = true,
    
    /** 最后一次数据备份时间 */
    val lastBackupTime: Long? = null,
    
    /** 自动备份间隔天数 (0 = 禁用) */
    val autoBackupIntervalDays: Int = 0,
    
    /** 应用语言设置 (system = 跟随系统, zh = 中文, en = 英文, ja = 日文) */
    val languageCode: String = "system"
)
