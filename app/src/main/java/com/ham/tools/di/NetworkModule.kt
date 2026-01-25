package com.ham.tools.di

import com.ham.tools.data.remote.HamQslApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt 模块 - 网络依赖注入
 * 
 * 提供 OkHttp、Retrofit 和 API 接口实例
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    /**
     * 提供 OkHttpClient 实例
     * 
     * - 设置超时时间
     * - 添加 User-Agent 头
     * - 添加日志拦截器（Debug 模式）
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", HamQslApi.USER_AGENT)
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(loggingInterceptor)
            .build()
    }
    
    /**
     * 提供 Retrofit 实例
     */
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(HamQslApi.BASE_URL)
            .client(okHttpClient)
            .build()
    }
    
    /**
     * 提供 HamQslApi 接口实例
     */
    @Provides
    @Singleton
    fun provideHamQslApi(retrofit: Retrofit): HamQslApi {
        return retrofit.create(HamQslApi::class.java)
    }
}
