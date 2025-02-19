package com.ruble.jmanga.model

data class Manga(
    val title: String,
    val coverUrl: String,
    val updateTime: String = "",
    val latestChapter: String = "",
    val alt: String = ""
) 