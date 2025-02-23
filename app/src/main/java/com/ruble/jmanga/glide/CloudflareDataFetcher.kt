package com.ruble.jmanga.glide

import android.util.Log
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.GlideUrl
import com.ruble.jmanga.App
import com.ruble.jmanga.cloudflare.CloudflareSolver
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream

class CloudflareDataFetcher(
    private val client: OkHttpClient,
    private val url: GlideUrl
) : DataFetcher<InputStream> {

    private var stream: InputStream? = null
    private var isCancelled = false

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
        if (isCancelled) {
            return
        }

        try {
            val request = Request.Builder().url(url.toStringUrl()).build()
            var response = client.newCall(request).execute()

            // 如果遇到Cloudflare挑战，尝试解决
            if (response.code == 403) {
                response.close()
                Log.d("CloudflareDataFetcher", "遇到Cloudflare挑战，尝试解决...")
                
                runBlocking {
                    when (val result = CloudflareSolver.getInstance(App.instance).solve(url.toStringUrl())) {
                        is CloudflareSolver.Result.Success -> {
                            // 使用获取到的响应
                            stream = result.data.body?.byteStream()
                            if (stream != null) {
                                callback.onDataReady(stream)
                                return@runBlocking
                            }
                        }
                        is CloudflareSolver.Result.Error -> {
                            Log.e("CloudflareDataFetcher", "Cloudflare 解决失败: ${result.exception.message}")
                            callback.onLoadFailed(result.exception)
                            return@runBlocking
                        }
                    }
                }
                
                callback.onLoadFailed(IOException("无法绕过Cloudflare保护"))
                return
            }

            stream = response.body?.byteStream()
            if (stream != null) {
                callback.onDataReady(stream)
            } else {
                callback.onLoadFailed(IOException("无法获取图片数据"))
            }
        } catch (e: Exception) {
            if (!isCancelled) {
                callback.onLoadFailed(e)
            }
        }
    }

    override fun cleanup() {
        try {
            stream?.close()
        } catch (e: Exception) {
            // 忽略清理错误
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