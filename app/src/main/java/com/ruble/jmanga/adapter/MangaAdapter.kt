package com.ruble.jmanga.adapter

import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.ruble.jmanga.R
import com.ruble.jmanga.model.Manga

class MangaAdapter : ListAdapter<Manga, MangaAdapter.ViewHolder>(MangaDiffCallback()) {
    private val TAG = "MangaAdapter"
    
    // 定义点击事件接口
    interface OnMangaClickListener {
        fun onMangaClick(mangaId: String)
    }
    
    // 点击事件监听器
    private var mangaClickListener: OnMangaClickListener? = null
    
    // 设置点击监听器的方法
    fun setOnMangaClickListener(listener: OnMangaClickListener) {
        this.mangaClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_manga, parent, false)
        return ViewHolder(view, mangaClickListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val manga = getItem(position)
        holder.bind(manga)
    }

    class ViewHolder(
        itemView: View, 
        private val clickListener: OnMangaClickListener?
    ) : RecyclerView.ViewHolder(itemView) {
        private val coverImage: ImageView = itemView.findViewById(R.id.cover_image)
        private val titleText: TextView = itemView.findViewById(R.id.title_text)
        private val TAG = "MangaViewHolder"

        fun bind(manga: Manga) {
            titleText.text = manga.title
            
            // 设置请求选项，包括占位图和错误图
            val requestOptions = RequestOptions()
                .placeholder(R.drawable.placeholder_image)
                .error(R.drawable.error_image)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
            
            // 加载图片，添加错误处理
            Glide.with(itemView.context)
                .load(manga.cover)
                .apply(requestOptions)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        Log.e(TAG, "图片加载失败: ${manga.cover}", e)
                        return false // 返回false允许错误占位图显示
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        return false // 返回false允许资源显示
                    }
                })
                .into(coverImage)
                
            // 设置点击事件
            itemView.setOnClickListener {
                // 从链接中提取漫画ID
                val mangaId = extractMangaIdFromLink(manga.link)
                Log.d(TAG, "点击漫画: $mangaId")
                clickListener?.onMangaClick(mangaId)
            }
        }
        
        /**
         * 从链接中提取漫画ID
         * 示例链接: https://g-mh.org/manga/meilongyanxiaotanziwokendingganchaodidelongjimeishaonvmowangyongzheyongaijiangqijibaidegushi
         */
        private fun extractMangaIdFromLink(link: String): String {
            return try {
                // 尝试从完整URL中提取
                if (link.contains("/manga/")) {
                    link.substringAfter("/manga/").trim('/')
                } else {
                    // 如果链接本身就是ID，直接返回
                    link
                }
            } catch (e: Exception) {
                Log.e(TAG, "提取漫画ID失败: $link", e)
                link // 如果提取失败，返回原始链接
            }
        }
    }

    private class MangaDiffCallback : DiffUtil.ItemCallback<Manga>() {
        override fun areItemsTheSame(oldItem: Manga, newItem: Manga): Boolean {
            return oldItem.link == newItem.link
        }

        override fun areContentsTheSame(oldItem: Manga, newItem: Manga): Boolean {
            return oldItem == newItem
        }
    }
} 