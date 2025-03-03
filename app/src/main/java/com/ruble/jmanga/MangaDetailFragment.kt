package com.ruble.jmanga

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.ruble.jmanga.adapter.ChapterAdapter
import com.ruble.jmanga.api.RetrofitClient
import com.ruble.jmanga.model.Chapter
import kotlinx.coroutines.launch

class MangaDetailFragment : Fragment() {
    private lateinit var coverImage: ImageView
    private lateinit var titleText: TextView
    private lateinit var statusText: TextView
    private lateinit var descriptionText: TextView
    private lateinit var chaptersRecycler: RecyclerView
    private lateinit var progressBar: ProgressBar
    
    private val chapterAdapter = ChapterAdapter()
    private val TAG = "MangaDetailFragment"
    
    private var mangaId: String = ""

    companion object {
        private const val ARG_MANGA_ID = "manga_id"
        
        fun newInstance(mangaId: String): MangaDetailFragment {
            val fragment = MangaDetailFragment()
            val args = Bundle()
            args.putString(ARG_MANGA_ID, mangaId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            mangaId = it.getString(ARG_MANGA_ID, "")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_manga_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 初始化视图
        coverImage = view.findViewById(R.id.cover_image)
        titleText = view.findViewById(R.id.title_text)
        statusText = view.findViewById(R.id.status_text)
        descriptionText = view.findViewById(R.id.description_text)
        chaptersRecycler = view.findViewById(R.id.chapters_recycler)
        progressBar = view.findViewById(R.id.progress_bar)
        
        // 设置RecyclerView
        chaptersRecycler.layoutManager = LinearLayoutManager(context)
        chaptersRecycler.adapter = chapterAdapter
        
        // 设置点击事件
        chapterAdapter.setOnChapterClickListener(object : ChapterAdapter.OnChapterClickListener {
            override fun onChapterClick(chapter: Chapter) {
                navigateToReader(chapter)
            }
        })
        
        // 加载漫画详情
        if (mangaId.isNotEmpty()) {
            loadMangaDetail(mangaId)
        } else {
            Toast.makeText(context, "漫画ID为空", Toast.LENGTH_SHORT).show()
            requireActivity().supportFragmentManager.popBackStack()
        }
    }
    
    private fun navigateToReader(chapter: Chapter) {
        // 从链接中提取章节ID
        val chapterId = extractChapterIdFromLink(chapter.link)
        
        Log.d(TAG, "准备阅读章节: ${chapter.title}, mangaId: $mangaId, chapterId: $chapterId")
        
        if (chapterId.isNotEmpty()) {
            // 跳转到阅读页面
            val readerFragment = MangaReaderFragment.newInstance(
                mangaId = mangaId,
                chapterId = chapterId,
                mangaTitle = titleText.text.toString(),
                chapterTitle = chapter.title,
                coverUrl = coverImage.tag as? String
            )
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, readerFragment)
                .addToBackStack(null)
                .commit()
        } else {
            Toast.makeText(context, "无法解析章节ID", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 从链接中提取章节ID
     * 示例链接: /manga/meilongyanxiaotanziwokendingganchaodidelongjimeishaonvmowangyongzheyongaijiangqijibaidegushi/31973-040762270-4
     */
    private fun extractChapterIdFromLink(link: String): String {
        return try {
            // 链接格式: /manga/{mangaId}/{chapterId}
            val parts = link.split("/")
            if (parts.size >= 3) {
                parts.last() // 获取最后一部分作为章节ID
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "提取章节ID失败: $link", e)
            ""
        }
    }
    
    private fun loadMangaDetail(mangaId: String) {
        progressBar.visibility = View.VISIBLE
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.mangaApi.getChapterList(mangaId)
                
                if (response.code == 200) {
                    // 更新漫画信息
                    val mangaInfo = response.data.manga_info
                    titleText.text = mangaInfo.title
                    statusText.text = mangaInfo.status
                    descriptionText.text = mangaInfo.description
                    
                    // 加载封面图
                    if (mangaInfo.cover.isNotEmpty()) {
                        coverImage.tag = mangaInfo.cover // 保存URL到tag中
                        Glide.with(this@MangaDetailFragment)
                            .load(mangaInfo.cover)
                            .apply(RequestOptions()
                                .placeholder(R.drawable.placeholder_image)
                                .error(R.drawable.error_manga))
                            .into(coverImage)
                    }
                    
                    // 更新章节列表
                    chapterAdapter.submitList(response.data.chapters)
                    
                    Log.d(TAG, "加载漫画详情成功: ${mangaInfo.title}, 章节数量: ${response.data.chapters.size}")
                } else {
                    Log.e(TAG, "加载失败: ${response.message}")
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