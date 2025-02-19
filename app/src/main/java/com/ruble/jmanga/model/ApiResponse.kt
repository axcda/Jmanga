package com.ruble.jmanga.model

data class ApiResponse(
    val code: Int,
    val data: MangaData,
    val message: String? = null,
    val timestamp: Long? = null
)

data class MangaData(
    val hot_updates: List<MangaItem>,
    val new_manga: List<MangaItem>,
    val popular_manga: List<MangaItem>,
    val updates: List<MangaItem>
)

data class MangaItem(
    val image_url: String?,
    val link: String,
    val title: String
)

data class ResponseData(
    val code: Int,
    val data: MangaData
)

data class MangaDetail(
    val chapters: List<Chapter>,
    val image_url: String? = null,
    val title: String? = null
)

data class Chapter(
    val link: String,
    val title: String
) 