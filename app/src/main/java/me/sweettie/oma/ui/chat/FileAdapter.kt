package me.sweettie.oma.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import androidx.recyclerview.widget.RecyclerView
import me.sweettie.oma.R

class FileAdapter(
    private val items: Map<String, String>,
    private val onClicked: (String) -> Unit,
    private val onHeaderClicked: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) TYPE_HEADER else TYPE_ITEM
    }

    override fun getItemCount(): Int = items.size + 1


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_recent_files_header, parent, false)
            HeaderViewHolder(view, onHeaderClicked)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.stacked_message_attachment, parent, false)
            AttachmentViewHolder(view)
        }
    }

    private val keysList = items.keys.toList() // чтобы обращаться по индексу


    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is AttachmentViewHolder) {
            val id = keysList[position - 1] // сдвиг на 1 из-за хедера
            val name = items[id] ?: "unknown"
            holder.bind(id, name)
        }
    }

    class HeaderViewHolder(itemView: View, onHeaderClicked: () -> Unit) :
        RecyclerView.ViewHolder(itemView) {

        init {
            itemView.findViewById<View>(R.id.hui2).setOnClickListener {
                onHeaderClicked()
            }
        }
    }


    inner class AttachmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvAttachmentText)
        private val layout: LinearLayout = itemView.findViewById(R.id.linearLayout)

        private val attachmentImage: ImageView by lazy {
            val img = ImageView(itemView.context).apply {
                transitionName = "shared_image"
                adjustViewBounds = false
                setImageResource(R.drawable.ic_draft)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setColorFilter(
                    ContextCompat.getColor(itemView.context, android.R.color.white)
                )
            }
            layout.addView(img, 0)
            img
        }

        fun bind(id: String, name: String) {
            tvName.text = name
            attachmentImage
            itemView.setOnClickListener { onClicked(id) }
            itemView.setPadding(10)
        }
    }


}

