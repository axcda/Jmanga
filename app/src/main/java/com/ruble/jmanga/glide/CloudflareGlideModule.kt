package com.ruble.jmanga.glide

import android.content.Context
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import okhttp3.OkHttpClient
import java.io.InputStream
import java.util.concurrent.TimeUnit

@GlideModule
class CloudflareGlideModule : AppGlideModule() {
    companion object {
        private const val TAG = "CloudflareGlideModule"
    }
    
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        Log.d(TAG, "注册 Cloudflare 图片加载组件")
        
        // 创建 OkHttpClient
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val newRequest = originalRequest.newBuilder().apply {
                    // 添加必要的请求头
                    addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                    addHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                    addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    addHeader("Accept-Encoding", "gzip, deflate, br")
                    addHeader("Referer", "https://godamanga.online/")
                }.build()

                chain.proceed(newRequest)
            }
            .build()

        // 直接使用 CloudflareDataFetcherFactory 处理所有图片请求
        registry.replace(
            GlideUrl::class.java,
            InputStream::class.java,
            CloudflareDataFetcherFactory(client)
        )
        
        Log.d(TAG, "Cloudflare 图片加载组件注册完成")
    }

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        builder.setLogLevel(Log.VERBOSE)
    }

    override fun isManifestParsingEnabled(): Boolean {
        return false
    }
} 