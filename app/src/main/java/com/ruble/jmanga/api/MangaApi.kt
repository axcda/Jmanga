package com.ruble.jmanga.api

import com.ruble.jmanga.model.ApiResponse
import com.ruble.jmanga.model.MangaContentResponse
import com.ruble.jmanga.model.MangaDetailResponse
import com.ruble.jmanga.model.SearchResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MangaApi {
    @GET("/api/manga/home")
    suspend fun getHomeData(): ApiResponse

    @GET("/api/manga/search/{keyword}")
    suspend fun search(
        @Path("keyword") keyword: String,
        @Query("page") page: Int = 1
    ): SearchResponse
    
    @GET("/api/manga/chapter/{mangaId}")
    suspend fun getChapterList(
        @Path("mangaId") mangaId: String
    ): MangaDetailResponse
    
    @GET("/api/manga/content/{mangaId}/{chapterId}")
    suspend fun getChapterContent(
        @Path("mangaId") mangaId: String,
        @Path("chapterId") chapterId: String
    ): MangaContentResponse

//    @GET("api/test")
//    suspend fun testConnection(): String
//
//    @GET("/")
//    suspend fun getRoot(): String
} 