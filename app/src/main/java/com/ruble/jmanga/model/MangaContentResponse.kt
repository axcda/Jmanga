package com.ruble.jmanga.model

data class MangaContentResponse(
    val code: Int,
    val message: String,
    val data: MangaContentData,
    val timestamp: Long
)

data class MangaContentData(
    val manga_id: String,
    val chapter_id: String,
    val title: String,
    val chapter_title: String,
    val prev_chapter: String?,
    val next_chapter: String?,
    val images: List<String>
) 