package com.ruble.jmanga

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ruble.jmanga.adapter.MangaImageAdapter
import com.ruble.jmanga.api.RetrofitClient
import com.ruble.jmanga.cloudflare.CloudflareSolver
import com.example.jmanga.data.DatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MangaReaderFragment : Fragment() {
    private lateinit var imagesRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var databaseHelper: DatabaseHelper
    
    private val imageAdapter = MangaImageAdapter()
    private val TAG = "MangaReaderFragment"
    
    private var mangaId: String = ""
    private var chapterId: String = ""
    private var mangaTitle: String = ""
    private var chapterTitle: String = ""
    private var coverUrl: String? = null

    companion object {
        private const val ARG_MANGA_ID = "manga_id"
        private const val ARG_CHAPTER_ID = "chapter_id"
        private const val ARG_MANGA_TITLE = "manga_title"
        private const val ARG_CHAPTER_TITLE = "chapter_title"
        private const val ARG_COVER_URL = "cover_url"
        
        fun newInstance(
            mangaId: String,
            chapterId: String,
            mangaTitle: String,
            chapterTitle: String,
            coverUrl: String?
        ): MangaReaderFragment {
            val fragment = MangaReaderFragment()
            val args = Bundle()
            args.putString(ARG_MANGA_ID, mangaId)
            args.putString(ARG_CHAPTER_ID, chapterId)
            args.putString(ARG_MANGA_TITLE, mangaTitle)
            args.putString(ARG_CHAPTER_TITLE, chapterTitle)
            args.putString(ARG_COVER_URL, coverUrl)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            mangaId = it.getString(ARG_MANGA_ID, "")
            chapterId = it.getString(ARG_CHAPTER_ID, "")
            mangaTitle = it.getString(ARG_MANGA_TITLE, "")
            chapterTitle = it.getString(ARG_CHAPTER_TITLE, "")
            coverUrl = it.getString(ARG_COVER_URL)
        }
        
        // 初始化数据库
        databaseHelper = DatabaseHelper(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_manga_reader, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 初始化视图
        imagesRecyclerView = view.findViewById(R.id.images_recycler_view)
        progressBar = view.findViewById(R.id.progress_bar)
        
        // 设置RecyclerView
        imagesRecyclerView.layoutManager = LinearLayoutManager(context).apply {
            // 防止布局过程中的重叠
            isItemPrefetchEnabled = true
        }
        
        // 设置固定大小，提高性能
        imagesRecyclerView.setHasFixedSize(true)
        
        imagesRecyclerView.adapter = imageAdapter
        
        // 添加图片加载监听器，处理加载事件
        imageAdapter.setOnImageLoadListener(object : MangaImageAdapter.OnImageLoadListener {
            override fun onImageLoaded(position: Int, success: Boolean) {
                if (position == 0) {
                    Log.d(TAG, "第一张图片加载${if (success) "成功" else "失败"}")
                    if (success) {
                        // 第一张图片加载成功后，可以在这里添加任何附加的UI更新
                        // 例如滚动到顶部确保完整显示
                        imagesRecyclerView.scrollToPosition(0)
                    }
                }
            }
        })
        
        // 预处理 Cloudflare 挑战
        preSolveCloudfareChallenge()
        
        // 加载漫画内容
        if (mangaId.isNotEmpty() && chapterId.isNotEmpty()) {
            loadChapterContent(mangaId, chapterId)
            // 记录阅读历史
            saveReadingHistory()
        } else {
            Toast.makeText(context, "漫画ID或章节ID为空", Toast.LENGTH_SHORT).show()
            requireActivity().supportFragmentManager.popBackStack()
        }
    }
    
    private fun saveReadingHistory() {
        if (mangaId.isNotEmpty() && mangaTitle.isNotEmpty()) {
            databaseHelper.addHistory(
                mangaId = mangaId,
                title = mangaTitle,
                coverUrl = coverUrl,
                chapterId = chapterId,
                chapterTitle = chapterTitle
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        databaseHelper.close()
    }
    
    /**
     * 预处理 Cloudflare 挑战，提前获取必要的 cookie
     */
    private fun preSolveCloudfareChallenge() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "开始预处理 Cloudflare 挑战...")
                
                // 预先处理图片服务器域名
                val imageHosts = listOf(
                    "https://f40-1-4.g-mh.online/",
                    "https://f40-1-3.g-mh.online/",
                    "https://f40-1-2.g-mh.online/",
                    "https://f40-1-1.g-mh.online/"
                )
                
                // 为每个可能的图片服务器域名预先解决Cloudflare挑战
                for (host in imageHosts) {
                    try {
                        Log.d(TAG, "尝试解决域名: $host 的Cloudflare挑战")
                        val result = CloudflareSolver.getInstance(requireContext()).solve(host)
                        
                        when (result) {
                            is CloudflareSolver.Result.Success -> {
                                Log.d(TAG, "成功解决 $host 的Cloudflare挑战")
                                
                                // 保存获取到的Cookie
                                val cookies = result.data.headers("Set-Cookie")
                                if (cookies.isNotEmpty()) {
                                    withContext(Dispatchers.Main) {
                                        val cookieManager = CookieManager.getInstance()
                                        for (cookie in cookies) {
                                            cookieManager.setCookie(host, cookie)
                                        }
                                        cookieManager.flush()
                                    }
                                    Log.d(TAG, "已保存 $host 的Cookie: $cookies")
                                }
                            }
                            is CloudflareSolver.Result.Error -> {
                                Log.e(TAG, "$host 的Cloudflare挑战解决失败: ${result.exception.message}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "处理 $host 时出错", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cloudflare 挑战预处理出错", e)
            }
        }
    }
    
    private fun loadChapterContent(mangaId: String, chapterId: String) {
        progressBar.visibility = View.VISIBLE
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 在IO线程执行网络请求
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.mangaApi.getChapterContent(mangaId, chapterId)
                }
                
                if (response.code == 200) {
                    // 确保images列表不为空
                    val imageList = response.data.images
                    if (imageList != null && imageList.isNotEmpty()) {
                        // 更新UI
                        imageAdapter.submitList(imageList)
                        
                        // 设置标题 (如果响应中有标题)
                        response.data.chapter_title?.let { title ->
                            activity?.title = title
                        } ?: run {
                            activity?.title = "第 $chapterId 章"
                        }
                        
                        Log.d(TAG, "加载漫画内容成功: ${response.data.title ?: "未知标题"}, 图片数量: ${imageList.size}")
                    } else {
                        Log.e(TAG, "加载漫画内容失败: 图片列表为空")
                        Toast.makeText(context, "加载失败: 图片列表为空", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e(TAG, "加载漫画内容失败: ${response.message}")
                    Toast.makeText(context, "加载失败：${response.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载异常", e)
                Toast.makeText(context, "加载失败：${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }
} 