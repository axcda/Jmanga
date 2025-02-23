package com.ruble.jmanga.cloudflare

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

class CloudflareSolver private constructor(private val context: Context) {
    companion object {
        private const val TAG = "CloudflareSolver"
        private const val DEFAULT_TIMEOUT = 30L
        private const val ENABLE_LOGGING = true

        // 请求头
        private val DEFAULT_HEADERS = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
            "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8"
        )
        
        @Volatile
        private var instance: CloudflareSolver? = null
        
        fun getInstance(context: Context): CloudflareSolver {
            return instance ?: synchronized(this) {
                instance ?: CloudflareSolver(context).also { instance = it }
            }
        }
    }

    // 持久化的OkHttpClient实例
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder().apply {
            connectTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
            readTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
            writeTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
            if (ENABLE_LOGGING) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
            }
            // 添加默认请求头
            addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder().apply {
                    DEFAULT_HEADERS.forEach { (key, value) ->
                        header(key, value)
                    }
                }.build()
                chain.proceed(request)
            }
        }.build()
    }

    // 结果类
    sealed class Result<out T> {
        data class Success<T>(val data: T) : Result<T>()
        data class Error(val exception: Exception) : Result<Nothing>()
    }

    suspend fun solve(url: String): Result<Response> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始解决 Cloudflare 挑战，目标 URL: $url")
            
            // 使用本地 TurnstileSolver 获取令牌
            val token = TurnstileSolver.getInstance(context).solve(url)
            Log.d(TAG, "获取到令牌: $token")

            // 使用令牌重新请求原始URL
            val finalRequest = Request.Builder()
                .url(url)
                .addHeader("cf-turnstile-response", token)
                .build()

            Log.d(TAG, "使用令牌发送最终请求")
            val finalResponse = client.newCall(finalRequest).execute()
            Log.d(TAG, "最终请求响应状态码: ${finalResponse.code}")
            
            Result.Success(finalResponse)
        } catch (e: Exception) {
            logError("Cloudflare解决失败", e)
            Result.Error(e)
        }
    }

    private fun logError(message: String, error: Throwable? = null) {
        Log.e(TAG, "$message: ${error?.message}", error)
    }
} 