package com.ruble.jmanga.api

import android.util.Log
import com.ruble.jmanga.App
import com.ruble.jmanga.cloudflare.CloudflareSolver
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    // 10.0.2.2 是 Android 模拟器中访问主机 localhost 的特殊 IP 地址
    private const val BASE_URL = "http://10.0.2.2:7056"
    private const val TAG = "RetrofitClient"

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d(TAG, message)
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor { chain ->
            val originalRequest = chain.request()
            val newRequestBuilder = originalRequest.newBuilder()

            // 添加绕过Cloudflare的请求头
            if (originalRequest.url.host.contains("godamanga.online")) {
                newRequestBuilder.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                newRequestBuilder.addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                newRequestBuilder.addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                newRequestBuilder.addHeader("Accept-Encoding", "gzip, deflate, br")
                newRequestBuilder.addHeader("Connection", "keep-alive")
                newRequestBuilder.addHeader("Cache-Control", "max-age=0")
                newRequestBuilder.addHeader("Sec-Ch-Ua", "\"Not A(Brand\";v=\"99\", \"Google Chrome\";v=\"121\", \"Chromium\";v=\"121\"")
                newRequestBuilder.addHeader("Sec-Ch-Ua-Mobile", "?0")
                newRequestBuilder.addHeader("Sec-Ch-Ua-Platform", "\"Windows\"")
                newRequestBuilder.addHeader("Sec-Fetch-Dest", "document")
                newRequestBuilder.addHeader("Sec-Fetch-Mode", "navigate")
                newRequestBuilder.addHeader("Sec-Fetch-Site", "none")
                newRequestBuilder.addHeader("Sec-Fetch-User", "?1")
                newRequestBuilder.addHeader("Upgrade-Insecure-Requests", "1")

                val request = newRequestBuilder.build()
                val response = chain.proceed(request)

                // 如果遇到Cloudflare挑战，尝试解决
                if (response.code == 403) {
                    response.close()
                    runBlocking {
                        when (val result = CloudflareSolver.getInstance(App.instance).solve(request.url.toString())) {
                            is CloudflareSolver.Result.Success -> {
                                return@runBlocking result.data
                            }
                            is CloudflareSolver.Result.Error -> {
                                Log.e(TAG, "Cloudflare 解决失败: ${result.exception.message}")
                                return@runBlocking response
                            }
                        }
                    }
                }

                response
            } else {
                chain.proceed(newRequestBuilder.build())
            }
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("$BASE_URL/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val mangaApi: MangaApi = retrofit.create(MangaApi::class.java)
} 