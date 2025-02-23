package com.ruble.jmanga.glide

import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import okhttp3.OkHttpClient
import java.io.InputStream

class CloudflareDataFetcherFactory(private val client: OkHttpClient) : ModelLoaderFactory<GlideUrl, InputStream> {
    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<GlideUrl, InputStream> {
        return CloudflareModelLoader(client)
    }

    override fun teardown() {
        // 不需要清理资源
    }
}

class CloudflareModelLoader(private val client: OkHttpClient) : ModelLoader<GlideUrl, InputStream> {
    override fun buildLoadData(
        model: GlideUrl,
        width: Int,
        height: Int,
        options: com.bumptech.glide.load.Options
    ): ModelLoader.LoadData<InputStream> {
        return ModelLoader.LoadData(model, CloudflareDataFetcher(client, model))
    }

    override fun handles(model: GlideUrl): Boolean {
        return true
    }
} 