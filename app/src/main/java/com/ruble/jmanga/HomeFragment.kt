package com.ruble.jmanga

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ruble.jmanga.adapter.MangaAdapter
import com.ruble.jmanga.api.RetrofitClient
import com.ruble.jmanga.model.Manga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class HomeFragment : Fragment() {
    private lateinit var recentUpdatesRecycler: RecyclerView
    private lateinit var hotUpdatesRecycler: RecyclerView
    private lateinit var popularRankingRecycler: RecyclerView
    private lateinit var progressBar: ProgressBar

    private val recentAdapter = MangaAdapter()
    private val hotAdapter = MangaAdapter()
    private val popularAdapter = MangaAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        progressBar = view.findViewById(R.id.progress_bar)

        // 初始化RecyclerViews
        recentUpdatesRecycler = view.findViewById(R.id.recent_updates_recycler)
        hotUpdatesRecycler = view.findViewById(R.id.hot_updates_recycler)
        popularRankingRecycler = view.findViewById(R.id.popular_ranking_recycler)

        // 设置布局管理器为水平滚动
        context?.let { ctx ->
            recentUpdatesRecycler.layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
            hotUpdatesRecycler.layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
            popularRankingRecycler.layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
        }

        // 设置适配器
        recentUpdatesRecycler.adapter = recentAdapter
        hotUpdatesRecycler.adapter = hotAdapter
        popularRankingRecycler.adapter = popularAdapter

        // 加载数据
        loadData()
    }

    private fun loadData() {
        showLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Log.d("HomeFragment", "开始加载漫画数据...")
                
                // 在IO线程中执行网络请求
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.mangaApi.getMangaList()
                }
                
                if (response.code == 200) {
                    Log.d("HomeFragment", "成功获取漫画数据")
                    Log.d("HomeFragment", "响应数据: $response")
                    
                    // 在IO线程中处理数据
                    val mangaList = withContext(Dispatchers.Default) {
                        val updates = mutableListOf<Manga>()
                        
                        // 处理热门更新
                        response.data.data.hot_updates.forEach { update ->
                            Log.d("HomeFragment", "处理漫画: ${update.title ?: update.chapter}")
                            Log.d("HomeFragment", "漫画详情: $update")
                            
                            // 尝试从多个位置获取封面URL
                            val coverUrl = when {
                                !update.image_url.isNullOrEmpty() -> {
                                    Log.d("HomeFragment", "使用update.image_url: ${update.image_url}")
                                    update.image_url
                                }
                                !update.detail.image_url.isNullOrEmpty() -> {
                                    Log.d("HomeFragment", "使用update.detail.image_url: ${update.detail.image_url}")
                                    update.detail.image_url
                                }
                                else -> {
                                    Log.w("HomeFragment", "未找到封面URL")
                                    ""
                                }
                            }
                            
                            Log.d("HomeFragment", "封面URL: $coverUrl")
                            
                            updates.add(
                                Manga(
                                    title = update.title ?: update.chapter,
                                    coverUrl = coverUrl,
                                    updateTime = update.update_time ?: "",
                                    latestChapter = update.chapter,
                                    alt = update.detail.chapters.firstOrNull()?.title ?: ""
                                )
                            )
                        }
                        
                        // 处理最近更新
                        response.data.data.recent_updates?.forEach { update ->
                            val coverUrl = when {
                                !update.image_url.isNullOrEmpty() -> update.image_url
                                !update.detail.image_url.isNullOrEmpty() -> update.detail.image_url
                                else -> ""
                            }

                            updates.add(
                                Manga(
                                    title = update.title ?: update.chapter,
                                    coverUrl = coverUrl,
                                    updateTime = update.update_time ?: "",
                                    latestChapter = update.chapter,
                                    alt = update.detail.chapters.firstOrNull()?.title ?: ""
                                )
                            )
                        }
                        
                        // 处理人气排行
                        response.data.data.popular_updates?.forEach { update ->
                            val coverUrl = when {
                                !update.image_url.isNullOrEmpty() -> update.image_url
                                !update.detail.image_url.isNullOrEmpty() -> update.detail.image_url
                                else -> ""
                            }

                            updates.add(
                                Manga(
                                    title = update.title ?: update.chapter,
                                    coverUrl = coverUrl,
                                    updateTime = update.update_time ?: "",
                                    latestChapter = update.chapter,
                                    alt = update.detail.chapters.firstOrNull()?.title ?: ""
                                )
                            )
                        }
                        
                        updates
                    }

                    Log.d("HomeFragment", "处理完成，漫画数量: ${mangaList.size}")

                    // 获取最近更新（取前5个）
                    val recent = mangaList.take(5)
                    
                    // 获取热门更新（随机选择5个）
                    val hot = mangaList.shuffled().take(5)
                    
                    // 获取人气排行（随机选择5个）
                    val popular = mangaList.shuffled().take(5)

                    // 在主线程中更新UI
                    withContext(Dispatchers.Main) {
                        recentAdapter.updateData(recent)
                        hotAdapter.updateData(hot)
                        popularAdapter.updateData(popular)
                        showLoading(false)
                    }
                    
                    Log.d("HomeFragment", "数据加载完成并更新UI")
                } else {
                    throw Exception("API返回错误：${response.message}")
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "加载失败", e)
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    val errorMessage = when (e) {
                        is UnknownHostException -> "无法连接到服务器，请检查网络连接"
                        is SocketTimeoutException -> "连接服务器超时，请稍后重试"
                        is retrofit2.HttpException -> {
                            val code = e.code()
                            when (code) {
                                404 -> "找不到数据接口，请确认服务器配置"
                                500 -> "服务器内部错误"
                                else -> "HTTP错误: $code"
                            }
                        }
                        else -> "加载失败：${e.message}"
                    }
                    Log.e("HomeFragment", "错误详情: $errorMessage", e)
                    context?.let {
                        Toast.makeText(it, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    companion object {
        fun newInstance() = HomeFragment()
    }
} 