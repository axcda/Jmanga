package com.ruble.jmanga.model

data class SearchResponse(
    val code: Int,
    val message: String,
    val data: SearchData,
    val timestamp: Long
)

data class SearchData(
    val manga_list: List<Manga>,
    val pagination: Pagination,
    val keyword: String
) 