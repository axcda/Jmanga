package com.example.jmanga.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ruble.jmanga.R
import com.example.jmanga.data.DatabaseHelper
import com.ruble.jmanga.MangaDetailFragment
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class SettingsFragment : Fragment() {

    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var cacheSizeText: TextView
    private lateinit var clearCacheButton: Button
    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var historyAdapter: HistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化视图
        historyRecyclerView = view.findViewById(R.id.history_recycler_view)
        cacheSizeText = view.findViewById(R.id.cache_size_text)
        clearCacheButton = view.findViewById(R.id.clear_cache_button)

        // 初始化数据库
        databaseHelper = DatabaseHelper(requireContext())

        // 设置历史记录列表
        setupHistoryRecyclerView()

        // 更新缓存大小显示
        updateCacheSize()

        // 设置清理缓存按钮点击事件
        clearCacheButton.setOnClickListener {
            clearCache()
        }
    }

    private fun setupHistoryRecyclerView() {
        historyAdapter = HistoryAdapter(databaseHelper.getAllHistory())
        historyAdapter.setOnHistoryClickListener(object : HistoryAdapter.OnHistoryClickListener {
            override fun onHistoryClick(mangaId: String) {
                navigateToMangaDetail(mangaId)
            }
        })
        historyRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = historyAdapter
        }
    }

    private fun updateCacheSize() {
        val cacheSize = calculateCacheSize()
        cacheSizeText.text = "当前缓存大小: ${formatFileSize(cacheSize)}"
    }

    private fun clearCache() {
        context?.cacheDir?.deleteRecursively()
        updateCacheSize()
    }

    private fun calculateCacheSize(): Long {
        var size = 0L
        context?.cacheDir?.walkTopDown()?.forEach { file ->
            if (file.isFile) {
                size += file.length()
            }
        }
        return size
    }

    private fun formatFileSize(size: Long): String {
        val kb = size / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1 -> String.format("%.2f MB", mb)
            kb >= 1 -> String.format("%.2f KB", kb)
            else -> String.format("%d B", size)
        }
    }

    private fun navigateToMangaDetail(mangaId: String) {
        val detailFragment = MangaDetailFragment.newInstance(mangaId)
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, detailFragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroy() {
        super.onDestroy()
        databaseHelper.close()
    }
}

class HistoryAdapter(private val historyItems: List<com.example.jmanga.data.HistoryItem>) :
    RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    // 定义点击事件接口
    interface OnHistoryClickListener {
        fun onHistoryClick(mangaId: String)
    }
    
    // 点击事件监听器
    private var historyClickListener: OnHistoryClickListener? = null
    
    // 设置点击监听器的方法
    fun setOnHistoryClickListener(listener: OnHistoryClickListener) {
        this.historyClickListener = listener
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById(R.id.history_title)
        val dateText: TextView = view.findViewById(R.id.history_date)
        val chapterText: TextView = view.findViewById(R.id.history_chapter)
        val container: View = view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = historyItems[position]
        holder.titleText.text = item.title
        holder.dateText.text = formatDate(item.lastRead)
        holder.chapterText.text = item.chapterTitle ?: "未知章节"
        
        // 设置点击事件
        holder.container.setOnClickListener {
            historyClickListener?.onHistoryClick(item.mangaId)
        }
    }

    override fun getItemCount() = historyItems.size

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
} 