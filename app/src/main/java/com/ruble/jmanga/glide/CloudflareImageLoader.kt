package com.ruble.jmanga.glide

import android.util.Log
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.ruble.jmanga.cloudflare.CloudflareSolver
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.concurrent.TimeUnit

class CloudflareImageLoader(private val client: OkHttpClient) : ModelLoader<GlideUrl, InputStream> {
    override fun buildLoadData(
        model: GlideUrl,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<InputStream>? {
        return ModelLoader.LoadData(model, CloudflareDataFetcher(client, model))
    }

    override fun handles(model: GlideUrl): Boolean {
        return model.toStringUrl().contains("godamanga.online")
    }

    class Factory : ModelLoaderFactory<GlideUrl, InputStream> {
        private val client: OkHttpClient = OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val newRequestBuilder = originalRequest.newBuilder()

                if (originalRequest.url.host.contains("godamanga.online")) {
                    newRequestBuilder.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                    newRequestBuilder.addHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                    newRequestBuilder.addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    newRequestBuilder.addHeader("Accept-Encoding", "gzip, deflate, br")
                    newRequestBuilder.addHeader("Connection", "keep-alive")
                    newRequestBuilder.addHeader("Cache-Control", "no-cache")
                    newRequestBuilder.addHeader("Pragma", "no-cache")
                    newRequestBuilder.addHeader("Sec-Ch-Ua", "\"Not A(Brand\";v=\"99\", \"Google Chrome\";v=\"121\", \"Chromium\";v=\"121\"")
                    newRequestBuilder.addHeader("Sec-Ch-Ua-Mobile", "?0")
                    newRequestBuilder.addHeader("Sec-Ch-Ua-Platform", "\"Windows\"")
                    newRequestBuilder.addHeader("Sec-Fetch-Dest", "image")
                    newRequestBuilder.addHeader("Sec-Fetch-Mode", "no-cors")
                    newRequestBuilder.addHeader("Sec-Fetch-Site", "cross-site")
                    newRequestBuilder.addHeader("Referer", "https://g-mh.org/")
                }

                chain.proceed(newRequestBuilder.build())
            }
            .build()

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<GlideUrl, InputStream> {
            return CloudflareImageLoader(client)
        }

        override fun teardown() {
            // Do nothing
        }
    }
} 