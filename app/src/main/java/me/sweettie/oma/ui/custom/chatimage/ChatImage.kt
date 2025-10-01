package me.sweettie.oma.ui.custom.chatimage

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.sweettie.oma.R
import me.sweettie.oma.utils.SessionManager
import me.sweettie.oma.utils.Utils
import proto.OmaOuterClass
import java.io.File

@SuppressLint("ViewConstructor")
class ChatImage @JvmOverloads constructor(
    context: Context,
    attachment: OmaOuterClass.Attachment,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    val imageView: ImageView
    private val loadButton: ImageButton
    private val progressBar: ProgressBar
    private var isLoading = false

    var onPhotoClick: ((ChatImage) -> Unit)? = null
    var file: File? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_chat_image, this, true)
        imageView = findViewById(R.id.imageView)
        loadButton = findViewById(R.id.view_chat_image_image)
        progressBar = findViewById(R.id.view_chat_image_progress)
        val isLoaded = SessionManager.cacheManager.attachmentIsCached(attachment)

        if (isLoaded) {
            loadButton.visibility = GONE
            progressBar.visibility = GONE
            CoroutineScope(Dispatchers.IO).launch {
                file = SessionManager.cacheManager.getFile(attachment)
                file?.let {
                    imageView.setImageURI(file!!.toUri())
                    CoroutineScope(Dispatchers.Main).launch {
                        imageView.setOnClickListener { onPhotoClick?.invoke(this@ChatImage) }
                    }
                }
            }
        } else {
            val preview = attachment.preview
            imageView.setImageBitmap(
                Utils.createBitmapFromRGBA(
                    preview.content,
                    preview.stride.toInt()
                )
            )
            loadButton.visibility = VISIBLE

            loadButton.setOnClickListener {
                attachment.let {
                    if (!isLoading) {
                        isLoading = true
                        loadButton.visibility = GONE
                        progressBar.visibility = VISIBLE
                        CoroutineScope(Dispatchers.IO).launch {
                            file = SessionManager.cacheManager.getFile(attachment)
                            file?.let {
                                CoroutineScope(Dispatchers.Main).launch {
                                    progressBar.visibility = GONE
                                    imageView.setImageURI(file!!.toUri())
                                    imageView.setOnClickListener { onPhotoClick?.invoke(this@ChatImage) }
                                }

                            }
                        }
                    }
                }
            }
        }


    }


}