package com.ruble.jmanga.api

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    // 10.0.2.2 是 Android 模拟器中访问主机 localhost 的特殊 IP 地址
    private const val BASE_URL = "http://10.0.2.2:7056/"
    private const val TAG = "RetrofitClient"

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d(TAG, message)
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor { chain ->
            val request = chain.request()
            Log.d(TAG, "发起请求: ${request.url}")
            Log.d(TAG, "请求头: ${request.headers}")
            
            val response = chain.proceed(request)
            Log.d(TAG, "收到响应: ${response.code} ${response.message}")
            Log.d(TAG, "响应头: ${response.headers}")
            
            response
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val mangaApi: MangaApi = retrofit.create(MangaApi::class.java)
} 