package me.sweettie.oma.ui.chat

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.placeholder
import me.sweettie.oma.R

class ImagesAdapter(
    private val images: List<Uri>,
    private val onImageClick: (Uri) -> Unit
) : RecyclerView.Adapter<ImagesAdapter.ImageVH>() {

    inner class ImageVH(val imageView: SquareImageView) : RecyclerView.ViewHolder(imageView) {
        init {
            imageView.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onImageClick(images[pos])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageVH {
        val imageView = SquareImageView(parent.context).apply {
            val paddingPx = (5 * context.resources.displayMetrics.density).toInt()

            layoutParams = ViewGroup.MarginLayoutParams(
                (parent.measuredWidth / 3.25f).toInt(),
                0,
            ).apply {
                setMargins(paddingPx, paddingPx, paddingPx, paddingPx)
            }
            adjustViewBounds = true

            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        return ImageVH(imageView)
    }

    override fun onBindViewHolder(holder: ImageVH, position: Int) {
//        holder.imageView.setImageURI(images[position])
        holder.imageView.apply {
            load(images[position]) {
                placeholder(R.color.colorUserBarBackground)
            }
        }

    }

    override fun getItemCount(): Int = images.size
}


class SquareImageView(context: Context, attrs: AttributeSet? = null) :
    AppCompatImageView(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}