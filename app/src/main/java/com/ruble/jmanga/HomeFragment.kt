package com.ruble.jmanga

import android.content.ContentValues.TAG
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.ruble.jmanga.adapter.MangaAdapter
import com.ruble.jmanga.api.RetrofitClient
import com.ruble.jmanga.cloudflare.CloudflareSolver
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
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private val updatesAdapter = MangaAdapter()
    private val hotUpdatesAdapter = MangaAdapter()
    private val popularMangaAdapter = MangaAdapter()
    private val newMangaAdapter = MangaAdapter()
    
    private val FRAGMENT_TAG = "HomeFragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化视图
        swipeRefresh = view.findViewById(R.id.swipe_refresh)
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
        
        // 设置点击事件
        val mangaClickListener = object : MangaAdapter.OnMangaClickListener {
            override fun onMangaClick(mangaId: String) {
                navigateToMangaDetail(mangaId)
            }
        }
        
        updatesAdapter.setOnMangaClickListener(mangaClickListener)
        hotUpdatesAdapter.setOnMangaClickListener(mangaClickListener)
        popularMangaAdapter.setOnMangaClickListener(mangaClickListener)
        newMangaAdapter.setOnMangaClickListener(mangaClickListener)

        // 设置下拉刷新
        swipeRefresh.setOnRefreshListener {
            loadData()
        }

        // 预处理 Cloudflare 挑战
        preSolveCloudfareChallenge()

        // 加载数据
        loadData()
    }
    
    // 跳转到漫画详情页面
    private fun navigateToMangaDetail(mangaId: String) {
        Log.d(FRAGMENT_TAG, "跳转到漫画详情页面: $mangaId")
        val detailFragment = MangaDetailFragment.newInstance(mangaId)
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, detailFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun preSolveCloudfareChallenge() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "开始预处理 Cloudflare 挑战...")
                val testUrl = "https://cncover.godamanga.online/"
                when (val result = CloudflareSolver.getInstance(requireContext()).solve(testUrl)) {
                    is CloudflareSolver.Result.Success -> {
                        Log.d(TAG, "Cloudflare 挑战预处理成功")
                    }
                    is CloudflareSolver.Result.Error -> {
                        Log.e(TAG, "Cloudflare 挑战预处理失败: ${result.exception.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cloudflare 挑战预处理出错", e)
            }
        }
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                swipeRefresh.isRefreshing = true
                Log.d(TAG, "开始加载漫画数据...")
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.mangaApi.getHomeData()
                }
                
                if (response.code == 200 && response.data != null) {
                    Log.d(TAG, "成功获取漫画数据")
                    
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
                    Log.e(TAG, "加载失败：服务器返回错误 code=${response.code}")
                    showError("加载失败：服务器返回错误 code=${response.code}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载失败", e)
                showError("加载失败：${e.message}")
            } finally {
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        fun newInstance() = HomeFragment()
    }
} 