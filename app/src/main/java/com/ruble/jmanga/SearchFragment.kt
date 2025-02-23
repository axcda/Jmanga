package com.ruble.jmanga

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.ruble.jmanga.adapter.MangaAdapter
import com.ruble.jmanga.api.RetrofitClient
import com.ruble.jmanga.model.Manga
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {
    private lateinit var searchView: SearchView
    private lateinit var mangaRecycler: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    
    private val mangaAdapter = MangaAdapter()
    private var currentKeyword = ""
    private var currentPage = 1
    private var totalPages = 1
    private var isLoading = false
    private val mangaList = mutableListOf<Manga>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化视图
        searchView = view.findViewById(R.id.search_view)
        mangaRecycler = view.findViewById(R.id.manga_recycler)
        swipeRefresh = view.findViewById(R.id.swipe_refresh)
        Log.d("aaaaaaaaaaa","开始加载")
        // 设置RecyclerView
        mangaRecycler.apply {
            layoutManager = GridLayoutManager(context, 3)
            adapter = mangaAdapter
            
            // 添加滚动监听器
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                private var lastDy = 0

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    
                    // 记录滚动方向变化
                    if (dy != lastDy) {
                        lastDy = dy
                        Log.d(TAG, "滚动方向改变: dy = $dy")
                    }

                    if (!isLoading) {
                        val layoutManager = recyclerView.layoutManager as GridLayoutManager
                        val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                        val totalItemCount = layoutManager.itemCount

                        Log.d(TAG, "滚动状态: lastVisibleItem=$lastVisibleItem, totalItemCount=$totalItemCount, currentPage=$currentPage, totalPages=$totalPages")
                        
                        // 检查是否需要加载更多
                        if (lastVisibleItem >= totalItemCount - 5 && currentPage < totalPages) {
                            Log.d(TAG, "触发加载下一页")
                            loadNextPage()
                        }
                    }
                }

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    when (newState) {
                        RecyclerView.SCROLL_STATE_IDLE -> Log.d(TAG, "滚动停止")
                        RecyclerView.SCROLL_STATE_DRAGGING -> Log.d(TAG, "开始拖动")
                        RecyclerView.SCROLL_STATE_SETTLING -> Log.d(TAG, "惯性滚动")
                    }
                }
            })
        }

        // 设置搜索监听
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let {
                    Log.d(TAG, "开始搜索: $it")
                    currentKeyword = it
                    currentPage = 1
                    mangaList.clear()
                    mangaAdapter.submitList(null)
                    search(it, 1, true)
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })

        // 设置刷新监听
        swipeRefresh.setOnRefreshListener {
            Log.d(TAG, "触发下拉刷新")
            if (currentKeyword.isNotEmpty()) {
                currentPage = 1
                mangaList.clear()
                mangaAdapter.submitList(null)
                search(currentKeyword, 1, true)
            } else {
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun loadNextPage() {
        if (isLoading || currentPage >= totalPages) {
            Log.d(TAG, "跳过加载下一页: isLoading=$isLoading, currentPage=$currentPage, totalPages=$totalPages")
            return
        }
        
        Log.d(TAG, "加载下一页: ${currentPage + 1}")
        search(currentKeyword, currentPage + 1, false)
    }

    private fun search(keyword: String, page: Int, isNewSearch: Boolean) {
        if (isLoading) {
            Log.d(TAG, "搜索被跳过：正在加载中")
            return
        }
        
        Log.d(TAG, "开始搜索: keyword=$keyword, page=$page, isNewSearch=$isNewSearch")
        isLoading = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (isNewSearch) {
                    swipeRefresh.isRefreshing = true
                }
                val response = RetrofitClient.mangaApi.search(keyword, page)
                
                if (response.code == 200) {
                    // 更新总页数
                    totalPages = response.data.pagination.page_links.findLast { 
                        it.text.toIntOrNull() != null 
                    }?.text?.toInt() ?: 1
                    
                    Log.d(TAG, "搜索成功: 总页数=$totalPages")
                    
                    // 更新漫画列表
                    response.data.manga_list.let { newList ->
                        if (isNewSearch) {
                            mangaList.clear()
                        }
                        mangaList.addAll(newList)
                        mangaAdapter.submitList(mangaList.toList())
                        // 更新当前页码
                        currentPage = page
                        Log.d(TAG, "更新列表: 当前页=$currentPage, 列表大小=${mangaList.size}")
                    }
                } else {
                    Log.e(TAG, "搜索失败: ${response.message}")
                    Toast.makeText(context, "搜索失败：${response.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "搜索异常", e)
                Toast.makeText(context, "搜索失败：${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
                swipeRefresh.isRefreshing = false
                Log.d(TAG, "搜索完成: isLoading=false")
            }
        }
    }

    companion object {
        private const val TAG = "SearchFragment"
        fun newInstance() = SearchFragment()
    }
}