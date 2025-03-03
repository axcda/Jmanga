package com.ruble.jmanga.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ruble.jmanga.R
import com.ruble.jmanga.model.Chapter

class ChapterAdapter : ListAdapter<Chapter, ChapterAdapter.ViewHolder>(ChapterDiffCallback()) {
    
    // 点击事件接口
    interface OnChapterClickListener {
        fun onChapterClick(chapter: Chapter)
    }
    
    // 点击事件监听器
    private var chapterClickListener: OnChapterClickListener? = null
    
    // 设置点击监听器的方法
    fun setOnChapterClickListener(listener: OnChapterClickListener) {
        this.chapterClickListener = listener
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chapter, parent, false)
        return ViewHolder(view, chapterClickListener)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val chapter = getItem(position)
        holder.bind(chapter)
    }
    
    class ViewHolder(
        itemView: View,
        private val clickListener: OnChapterClickListener?
    ) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.chapter_title)
        
        fun bind(chapter: Chapter) {
            titleText.text = chapter.title
            
            // 设置点击事件
            itemView.setOnClickListener {
                clickListener?.onChapterClick(chapter)
            }
        }
    }
    
    private class ChapterDiffCallback : DiffUtil.ItemCallback<Chapter>() {
        override fun areItemsTheSame(oldItem: Chapter, newItem: Chapter): Boolean {
            return oldItem.link == newItem.link
        }
        
        override fun areContentsTheSame(oldItem: Chapter, newItem: Chapter): Boolean {
            return oldItem == newItem
        }
    }
} 