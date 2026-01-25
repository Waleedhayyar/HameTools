package com.ham.tools.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET

/**
 * HamQSL API 接口
 * 
 * 从 hamqsl.com 获取太阳和地磁数据
 * 返回原始 XML 响应，由 SolarXmlParser 解析
 */
interface HamQslApi {
    
    companion object {
        const val BASE_URL = "https://www.hamqsl.com/"
        const val USER_AGENT = "HamRadioPropagationApp/1.0 (contact@hamtools.app)"
    }
    
    /**
     * 获取太阳/地磁数据 XML
     * 
     * 返回包含以下信息的 XML：
     * - 太阳通量指数 (SFI)
     * - A/K 指数
     * - 地磁场状态
     * - HF 波段传播条件
     * - VHF 现象（可选）
     */
    @GET("solarxml.php")
    suspend fun getSolarXml(): Response<ResponseBody>
}
