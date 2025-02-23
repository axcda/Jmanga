package com.ruble.jmanga.model

data class Pagination(
    val current_page: Int,
    val page_links: List<PageLink>
)

data class PageLink(
    val text: String,
    val link: String
) 