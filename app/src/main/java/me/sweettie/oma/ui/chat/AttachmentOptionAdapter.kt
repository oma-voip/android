package me.sweettie.oma.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.devrj.superellipse.SuperEllipseImageView
import me.sweettie.oma.R

class AttachmentOptionAdapter(
    private val items: List<AttachmentItem>,
    private val onPhotoClicked: (AttachmentItem.Photo) -> Unit,
    private val onFileClicked: (AttachmentItem.File) -> Unit
) : RecyclerView.Adapter<AttachmentOptionAdapter.AttachmentViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttachmentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.attachment_adapter_item, parent, false)
        return AttachmentViewHolder(view)
    }

    override fun onBindViewHolder(holder: AttachmentViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = items.size

    inner class AttachmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvAttachmentItemName)
        private val image: ImageView = itemView.findViewById(R.id.tvAttachmentItemImage)
        private val box: SuperEllipseImageView = itemView.findViewById(R.id.hui)

        fun bind(item: AttachmentItem) {

            when (item) {
                is AttachmentItem.Photo -> {
                    tvName.text = item.name
                    image.setImageResource(R.drawable.ic_photo)
                    itemView.setOnClickListener {
                        onPhotoClicked(item)
                    }
                }

                is AttachmentItem.File -> {
                    tvName.text = item.name
                    image.setImageResource(R.drawable.ic_draft)
                    itemView.setOnClickListener {
                        onFileClicked(item)
                    }
                }
            }
        }
    }

    sealed class AttachmentItem {
        data class Photo(val name: String) : AttachmentItem()
        data class File(val name: String) : AttachmentItem()
    }

}

