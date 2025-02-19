package com.ruble.jmanga.adapter

import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.ruble.jmanga.R
import com.ruble.jmanga.model.Manga

class MangaAdapter : RecyclerView.Adapter<MangaAdapter.MangaViewHolder>() {
    private var mangas: List<Manga> = emptyList()

    fun updateData(newMangas: List<Manga>) {
        mangas = newMangas
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MangaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_manga, parent, false)
        return MangaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MangaViewHolder, position: Int) {
        holder.bind(mangas[position])
    }

    override fun getItemCount(): Int = mangas.size

    class MangaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val coverImageView: ImageView = itemView.findViewById(R.id.manga_cover)
        private val titleTextView: TextView = itemView.findViewById(R.id.manga_title)
        private val updateTextView: TextView = itemView.findViewById(R.id.manga_update)

        fun bind(manga: Manga) {
            titleTextView.text = manga.title
            updateTextView.text = if (manga.updateTime.isNotEmpty()) {
                "${manga.updateTime} ${manga.latestChapter}"
            } else {
                manga.latestChapter
            }

            Log.d("MangaAdapter", "加载图片: ${manga.title} - ${manga.coverUrl}")
            
            if (manga.coverUrl.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(manga.coverUrl)
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
                            Log.e("MangaAdapter", "图片加载失败: ${manga.title} - ${manga.coverUrl}", e)
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