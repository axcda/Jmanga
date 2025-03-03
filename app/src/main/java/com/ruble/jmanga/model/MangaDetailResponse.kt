package com.ruble.jmanga.model

data class MangaDetailResponse(
    val code: Int,
    val message: String,
    val data: MangaDetailData,
    val timestamp: Long
)

data class MangaDetailData(
    val manga_info: MangaInfo,
    val chapters: List<Chapter>
)

data class MangaInfo(
    val manga_id: String,
    val title: String,
    val description: String,
    val status: String,
    val author: Author,
    val type: Type,
    val cover: String,
    val created_at: String,
    val updated_at: String
)

data class Author(
    val names: List<String>,
    val links: List<String>
)

data class Type(
    val names: List<String>,
    val links: List<String>
) 