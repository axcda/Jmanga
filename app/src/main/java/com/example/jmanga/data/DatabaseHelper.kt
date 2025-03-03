package com.example.jmanga.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "manga_db"
        private const val DATABASE_VERSION = 1

        // 历史记录表
        private const val TABLE_HISTORY = "history"
        private const val COLUMN_ID = "id"
        private const val COLUMN_MANGA_ID = "manga_id"
        private const val COLUMN_TITLE = "title"
        private const val COLUMN_COVER_URL = "cover_url"
        private const val COLUMN_LAST_READ = "last_read"
        private const val COLUMN_CHAPTER_ID = "chapter_id"
        private const val COLUMN_CHAPTER_TITLE = "chapter_title"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // 创建历史记录表
        val createHistoryTable = """
            CREATE TABLE $TABLE_HISTORY (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_MANGA_ID TEXT NOT NULL,
                $COLUMN_TITLE TEXT NOT NULL,
                $COLUMN_COVER_URL TEXT,
                $COLUMN_LAST_READ INTEGER NOT NULL,
                $COLUMN_CHAPTER_ID TEXT,
                $COLUMN_CHAPTER_TITLE TEXT
            )
        """.trimIndent()
        db.execSQL(createHistoryTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 升级数据库时删除旧表并重新创建
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        onCreate(db)
    }

    // 添加历史记录
    fun addHistory(mangaId: String, title: String, coverUrl: String?, chapterId: String?, chapterTitle: String?) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_MANGA_ID, mangaId)
            put(COLUMN_TITLE, title)
            put(COLUMN_COVER_URL, coverUrl)
            put(COLUMN_LAST_READ, System.currentTimeMillis())
            put(COLUMN_CHAPTER_ID, chapterId)
            put(COLUMN_CHAPTER_TITLE, chapterTitle)
        }

        // 如果已存在相同的漫画记录，则更新
        val selection = "$COLUMN_MANGA_ID = ?"
        val selectionArgs = arrayOf(mangaId)
        val count = db.update(TABLE_HISTORY, values, selection, selectionArgs)

        if (count == 0) {
            // 不存在则插入新记录
            db.insert(TABLE_HISTORY, null, values)
        }
        db.close()
    }

    // 获取所有历史记录
    fun getAllHistory(): List<HistoryItem> {
        val historyList = mutableListOf<HistoryItem>()
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_HISTORY,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_LAST_READ DESC"
        )

        with(cursor) {
            while (moveToNext()) {
                val historyItem = HistoryItem(
                    id = getInt(getColumnIndexOrThrow(COLUMN_ID)),
                    mangaId = getString(getColumnIndexOrThrow(COLUMN_MANGA_ID)),
                    title = getString(getColumnIndexOrThrow(COLUMN_TITLE)),
                    coverUrl = getString(getColumnIndexOrThrow(COLUMN_COVER_URL)),
                    lastRead = getLong(getColumnIndexOrThrow(COLUMN_LAST_READ)),
                    chapterId = getString(getColumnIndexOrThrow(COLUMN_CHAPTER_ID)),
                    chapterTitle = getString(getColumnIndexOrThrow(COLUMN_CHAPTER_TITLE))
                )
                historyList.add(historyItem)
            }
        }
        cursor.close()
        db.close()
        return historyList
    }

    // 清除所有历史记录
    fun clearHistory() {
        val db = this.writableDatabase
        db.delete(TABLE_HISTORY, null, null)
        db.close()
    }
}

// 历史记录数据类
data class HistoryItem(
    val id: Int,
    val mangaId: String,
    val title: String,
    val coverUrl: String?,
    val lastRead: Long,
    val chapterId: String?,
    val chapterTitle: String?
) 