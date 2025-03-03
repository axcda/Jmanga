package com.ruble.jmanga.glide

import android.util.Log
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.GlideUrl
import com.ruble.jmanga.App
import com.ruble.jmanga.cloudflare.CloudflareSolver
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

class CloudflareDataFetcher(
    private val client: OkHttpClient,
    private val url: GlideUrl
) : DataFetcher<InputStream> {

    private var stream: InputStream? = null
    private var isCancelled = false
    private val TAG = "CloudflareDataFetcher"

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
        if (isCancelled) {
            return
        }

        try {
            Log.d(TAG, "开始加载图片: ${url.toStringUrl()}")
            
            // 构建带有完整头信息的请求
            val requestBuilder = Request.Builder()
                .url(url.toStringUrl())
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                .addHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .addHeader("Accept-Encoding", "gzip, deflate, br")
                .addHeader("Connection", "keep-alive")
                .addHeader("Cache-Control", "no-cache")
                .addHeader("Pragma", "no-cache")
                .addHeader("Sec-Fetch-Dest", "image")
                .addHeader("Sec-Fetch-Mode", "no-cors")
                .addHeader("Sec-Fetch-Site", "cross-site")
                .addHeader("Referer", "https://g-mh.org/")
            
            // 添加Glide提供的自定义头
            val headers = url.headers
            for ((key, value) in headers) {
                requestBuilder.addHeader(key, value)
            }
            
            // 创建请求并执行
            val request = requestBuilder.build()
            var response = client.newCall(request).execute()

            // 如果遇到Cloudflare挑战或者其他错误，尝试解决
            if (!response.isSuccessful) {
                response.close()
                Log.d(TAG, "初始请求未成功: ${response.code}, 尝试通过CloudflareSolver解决...")
                
                runBlocking {
                    when (val result = CloudflareSolver.getInstance(App.instance).solve(url.toStringUrl())) {
                        is CloudflareSolver.Result.Success -> {
                            // 使用获取到的cookies重新构建请求
                            val cookies = result.data.headers("Set-Cookie")
                            if (cookies.isNotEmpty()) {
                                Log.d(TAG, "获取到Cloudflare cookies: $cookies")
                                
                                // 创建新的请求，带上cookie
                                val cookieHeader = cookies.joinToString("; ")
                                val newRequest = requestBuilder
                                    .removeHeader("Cookie") // 移除可能存在的旧cookie
                                    .addHeader("Cookie", cookieHeader)
                                    .build()
                                
                                // 关闭旧响应，执行新请求
                                response.close()
                                response = client.newCall(newRequest).execute()
                                
                                if (response.isSuccessful) {
                                    Log.d(TAG, "使用CloudflareSolver的cookies成功获取图片")
                                } else {
                                    Log.e(TAG, "即使使用CloudflareSolver的cookies仍然请求失败: ${response.code}")
                                }
                            } else {
                                // 如果没有获取到cookie，直接使用原始响应体
                                Log.d(TAG, "CloudflareSolver成功但没有返回cookies，使用原始响应")
                                response.close()
                                response = result.data
                            }
                        }
                        is CloudflareSolver.Result.Error -> {
                            Log.e(TAG, "CloudflareSolver解决失败: ${result.exception.message}")
                            callback.onLoadFailed(result.exception)
                            return@runBlocking
                        }
                    }
                }
            }

            // 检查最终响应
            if (!response.isSuccessful) {
                Log.e(TAG, "最终图片加载失败，状态码: ${response.code}")
                callback.onLoadFailed(IOException("图片加载失败，HTTP错误码: ${response.code}"))
                return
            }

            // 提取响应体流
            stream = response.body?.byteStream()
            if (stream != null) {
                callback.onDataReady(stream)
                Log.d(TAG, "成功获取图片数据流")
            } else {
                callback.onLoadFailed(IOException("图片响应体为空"))
                Log.e(TAG, "图片响应体为空")
            }
        } catch (e: Exception) {
            if (!isCancelled) {
                Log.e(TAG, "加载图片时发生异常", e)
                callback.onLoadFailed(e)
            }
        }
    }

    override fun cleanup() {
        try {
            stream?.close()
        } catch (e: Exception) {
            // 忽略清理错误
            Log.w(TAG, "清理资源时发生错误", e)
        }
    }

    override fun cancel() {
        isCancelled = true
    }

    override fun getDataClass(): Class<InputStream> {
        return InputStream::class.java
    }

    override fun getDataSource(): DataSource {
        return DataSource.REMOTE
    }
} 