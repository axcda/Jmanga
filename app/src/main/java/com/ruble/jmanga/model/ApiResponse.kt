package com.ruble.jmanga.model

data class ApiResponse(
    val code: Int,
    val message: String,
    val data: HomeData?,
    val timestamp: Long
)

data class HomeData(
    val updates: List<Manga>?,
    val hot_updates: List<Manga>?,
    val popular_manga: List<Manga>?,
    val new_manga: List<Manga>?
)

data class MangaData(
    val hot_updates: List<MangaItem>,
    val new_manga: List<MangaItem>,
    val popular_manga: List<MangaItem>,
    val updates: List<MangaItem>
)

data class MangaItem(
    val cover: String?,
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