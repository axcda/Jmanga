package com.ruble.jmanga

import android.content.ContentValues.TAG
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
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
    private lateinit var updatesRecycler: RecyclerView
    private lateinit var hotUpdatesRecycler: RecyclerView
    private lateinit var popularMangaRecycler: RecyclerView
    private lateinit var newMangaRecycler: RecyclerView
    private lateinit var progressBar: ProgressBar

    private val updatesAdapter = MangaAdapter()
    private val hotUpdatesAdapter = MangaAdapter()
    private val popularMangaAdapter = MangaAdapter()
    private val newMangaAdapter = MangaAdapter()

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
        updatesRecycler = view.findViewById(R.id.updates_recycler)
        hotUpdatesRecycler = view.findViewById(R.id.hot_updates_recycler)
        popularMangaRecycler = view.findViewById(R.id.popular_manga_recycler)
        newMangaRecycler = view.findViewById(R.id.new_manga_recycler)

        // 设置布局管理器为网格布局，两行显示
        context?.let { ctx ->
            val gridLayoutManager1 = GridLayoutManager(ctx, 2, GridLayoutManager.HORIZONTAL, false)
            val gridLayoutManager2 = GridLayoutManager(ctx, 2, GridLayoutManager.HORIZONTAL, false)
            val gridLayoutManager3 = GridLayoutManager(ctx, 2, GridLayoutManager.HORIZONTAL, false)
            val gridLayoutManager4 = GridLayoutManager(ctx, 2, GridLayoutManager.HORIZONTAL, false)

            updatesRecycler.layoutManager = gridLayoutManager1
            hotUpdatesRecycler.layoutManager = gridLayoutManager2
            popularMangaRecycler.layoutManager = gridLayoutManager3
            newMangaRecycler.layoutManager = gridLayoutManager4
        }

        // 设置适配器
        updatesRecycler.adapter = updatesAdapter
        hotUpdatesRecycler.adapter = hotUpdatesAdapter
        popularMangaRecycler.adapter = popularMangaAdapter
        newMangaRecycler.adapter = newMangaAdapter

        // 加载数据
        loadData()
    }

    private fun loadData() {
        showLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Log.d("HomeFragment", "开始加载漫画数据...")
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.mangaApi.getUpdates()
                }
                
                if (response.code == 200 && response.data != null) {
                    Log.d("HomeFragment", "成功获取漫画数据")
                    
                    // 更新界面
                    response.data.updates?.let { updates ->
                        updatesAdapter.submitList(updates)
                    }
                    
                    response.data.hot_updates?.let { hotUpdates ->
                        hotUpdatesAdapter.submitList(hotUpdates)
                    }
                    
                    response.data.popular_manga?.let { popularManga ->
                        popularMangaAdapter.submitList(popularManga)
                    }
                    
                    response.data.new_manga?.let { newManga ->
                        newMangaAdapter.submitList(newManga)
                    }
                } else {
                    Log.e("HomeFragment", "加载失败：服务器返回错误 code=${response.code}")
                    showError("加载失败：服务器返回错误 code=${response.code}")
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "加载失败", e)
                showError("加载失败：${e.message}")
                Log.e("HomeFragment", "错误详情: ${e.message}", e)
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    companion object {
        fun newInstance() = HomeFragment()
    }
} 