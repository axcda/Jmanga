package com.ruble.jmanga.api

import com.ruble.jmanga.model.ApiResponse
import retrofit2.http.GET

interface MangaApi {
    @GET("api/manga/updates")
    suspend fun getMangaList(): ApiResponse

//    @GET("api/test")
//    suspend fun testConnection(): String
//
//    @GET("/")
//    suspend fun getRoot(): String
} 