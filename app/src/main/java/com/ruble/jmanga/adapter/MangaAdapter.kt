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
import com.bumptech.glide.request.target.Target
import com.ruble.jmanga.R
import com.ruble.jmanga.model.MangaItem

class MangaAdapter : ListAdapter<MangaItem, MangaAdapter.MangaViewHolder>(MangaDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MangaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_manga, parent, false)
        return MangaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MangaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MangaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val coverImageView: ImageView = itemView.findViewById(R.id.manga_cover)
        private val titleTextView: TextView = itemView.findViewById(R.id.manga_title)

        fun bind(manga: MangaItem) {
            titleTextView.text = manga.title

            Log.d("MangaAdapter", "加载图片: ${manga.title} - ${manga.image_url}")
            
            if (!manga.image_url.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(manga.image_url)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.placeholder_manga)
                    .error(R.drawable.error_manga)
                    .addListener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable>,
                            isFirstResource: Boolean
                        ): Boolean {
                            Log.e("MangaAdapter", "图片加载失败: ${manga.title} - ${manga.image_url}", e)
                            e?.logRootCauses("MangaAdapter")
                            return false
                        }

                        override fun onResourceReady(
                            resource: Drawable,
                            model: Any,
                            target: Target<Drawable>,
                            dataSource: DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            Log.d("MangaAdapter", "图片加载成功: ${manga.title}")
                            return false
                        }
                    })
                    .centerCrop()
                    .into(coverImageView)
            } else {
                coverImageView.setImageResource(R.drawable.placeholder_manga)
            }
        }
    }
}

private class MangaDiffCallback : DiffUtil.ItemCallback<MangaItem>() {
    override fun areItemsTheSame(oldItem: MangaItem, newItem: MangaItem): Boolean {
        return oldItem.link == newItem.link
    }

    override fun areContentsTheSame(oldItem: MangaItem, newItem: MangaItem): Boolean {
        return oldItem == newItem
    }
} 