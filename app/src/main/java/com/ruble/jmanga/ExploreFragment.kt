package com.ruble.jmanga

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ruble.jmanga.adapter.MangaAdapter
import com.ruble.jmanga.api.RetrofitClient
import kotlinx.coroutines.launch
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import android.graphics.Rect
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.ruble.jmanga.model.Manga

class ExploreFragment : Fragment() {
    private lateinit var searchEditText: EditText
    private lateinit var searchButton: ImageButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    
    private val mangaAdapter = MangaAdapter()
    private var currentKeyword = ""
    private var currentPage = 1
    private var totalPages = 1
    private var isLoading = false
    private val mangaList = mutableListOf<Manga>()
    
    private val TAG = "ExploreFragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_explore, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化视图
        searchEditText = view.findViewById(R.id.search_edit_text)
        searchButton = view.findViewById(R.id.search_button)
        recyclerView = view.findViewById(R.id.search_results_recycler)
        progressBar = view.findViewById(R.id.progress_bar)
        swipeRefresh = view.findViewById(R.id.swipe_refresh)

        // 设置RecyclerView
        val spanCount = 3 // 每行显示3个
        val layoutManager = GridLayoutManager(context, spanCount)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = mangaAdapter
        
        // 设置漫画点击事件
        mangaAdapter.setOnMangaClickListener(object : MangaAdapter.OnMangaClickListener {
            override fun onMangaClick(mangaId: String) {
                navigateToMangaDetail(mangaId)
            }
        })

        // 添加item间距
        recyclerView.addItemDecoration(object : ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                val spacing = resources.getDimensionPixelSize(R.dimen.grid_spacing)
                val position = parent.getChildAdapterPosition(view)
                val column = position % spanCount

                // 设置水平间距
                outRect.left = if (column == 0) spacing else spacing / 2
                outRect.right = if (column == spanCount - 1) spacing else spacing / 2

                // 设置垂直间距
                if (position < spanCount) {
                    outRect.top = spacing
                }
                outRect.bottom = spacing
            }
        })
        
        // 添加滚动监听器，实现分页加载
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                
                if (!isLoading && currentKeyword.isNotEmpty()) {
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
        })

        // 设置搜索按钮点击事件
        searchButton.setOnClickListener {
            performSearch()
        }

        // 设置输入法搜索按钮点击事件
        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }
        
        // 设置下拉刷新监听
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
    
    // 跳转到漫画详情页面
    private fun navigateToMangaDetail(mangaId: String) {
        Log.d(TAG, "跳转到漫画详情页面: $mangaId")
        val detailFragment = MangaDetailFragment.newInstance(mangaId)
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, detailFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun performSearch() {
        val keyword = searchEditText.text.toString().trim()
        if (keyword.isEmpty()) {
            Toast.makeText(context, "请输入搜索关键词", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 隐藏键盘
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
        
        // 清空之前的结果
        currentKeyword = keyword
        currentPage = 1
        mangaList.clear()
        mangaAdapter.submitList(null)
        
        // 执行搜索
        search(keyword, 1, true)
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
        
        if (isNewSearch) {
            progressBar.visibility = View.VISIBLE
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
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
                        
                        if (mangaList.isEmpty()) {
                            Toast.makeText(context, "未找到相关漫画", Toast.LENGTH_SHORT).show()
                        }
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
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
                Log.d(TAG, "搜索完成: isLoading=false")
            }
        }
    }

    companion object {
        fun newInstance() = ExploreFragment()
    }
} 