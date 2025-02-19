package com.ruble.jmanga.model

data class ApiResponse(
    val code: Int,
    val data: ResponseData,
    val message: String? = null,
    val timestamp: Long? = null
)

data class ResponseData(
    val code: Int,
    val data: MangaData
)

data class MangaData(
    val hot_updates: List<MangaUpdate>,
    val recent_updates: List<MangaUpdate>? = null,
    val popular_updates: List<MangaUpdate>? = null
)

data class MangaUpdate(
    val chapter: String,
    val detail: MangaDetail,
    val title: String? = null,
    val cover_url: String? = null,
    val image_url: String? = null,
    val update_time: String? = null
)

data class MangaDetail(
    val chapters: List<Chapter>,
    val cover_url: String? = null,
    val image_url: String? = null,
    val title: String? = null
)

data class Chapter(
    val link: String,
    val title: String
) 