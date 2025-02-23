package com.ruble.jmanga

import android.os.Bundle
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
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import android.graphics.Rect

class ExploreFragment : Fragment() {
    private lateinit var searchEditText: EditText
    private lateinit var searchButton: ImageButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private val mangaAdapter = MangaAdapter()

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

        // 设置RecyclerView
        val spanCount = 3 // 每行显示3个
        val layoutManager = GridLayoutManager(context, spanCount)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = mangaAdapter

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
    }

    private fun performSearch() {
        val keyword = searchEditText.text.toString().trim()
        if (keyword.isEmpty()) {
            Toast.makeText(context, "请输入搜索关键词", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.mangaApi.search(keyword)
                if (response.code == 200 && response.data.manga_list.isNotEmpty()) {
                    mangaAdapter.submitList(response.data.manga_list)
                } else {
                    Toast.makeText(context, "未找到相关漫画", Toast.LENGTH_SHORT).show()
                    mangaAdapter.submitList(emptyList())
                }
            } catch (e: Exception) {
                Toast.makeText(context, "搜索失败：${e.message}", Toast.LENGTH_SHORT).show()
                mangaAdapter.submitList(emptyList())
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    companion object {
        fun newInstance() = ExploreFragment()
    }
} 