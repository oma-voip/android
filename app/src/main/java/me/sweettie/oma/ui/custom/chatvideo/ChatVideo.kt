package me.sweettie.oma.ui.custom.chatvideo

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.net.toUri
import coil3.load
import coil3.request.placeholder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.sweettie.oma.R
import me.sweettie.oma.utils.SessionManager
import me.sweettie.oma.utils.Utils
import proto.OmaOuterClass
import java.io.File

@SuppressLint("ViewConstructor")
class ChatVideo
@JvmOverloads constructor(
    context: Context,
    private val attachment: OmaOuterClass.Attachment,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    fun save(): Boolean {
        val isLoaded =
            SessionManager.cacheManager.attachmentIsCachedExtra(this.attachment.id)
        if (!isLoaded) {
            playButton.visibility = GONE
            attachment.let {
                loadButton.visibility = GONE
                progressBar.visibility = VISIBLE
                CoroutineScope(Dispatchers.IO).launch {
                    file =
                        SessionManager.cacheManager.getFileById(attachment.id)
                    file?.let {
                        CoroutineScope(Dispatchers.Main).launch {

                            progressBar.visibility = GONE
                            playButton.visibility = VISIBLE
                        }

                    }

                }
            }
            return false
        } else {
            return true
        }
    }

    val imageView: ImageView
    private val loadButton: ImageButton
    private val progressBar: ProgressBar
    private val videoText: TextView
    private val playButton: ImageButton
    private var isLoading = false

    var onPhotoClick: ((ChatVideo) -> Unit)? = null
    var onOptionClicked: ((ChatVideo) -> Unit)? = null
    var file: File? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_chat_video, this, true)
        imageView = findViewById(R.id.imageView)
        videoText = findViewById(R.id.videoText)
        loadButton = findViewById(R.id.view_chat_image_image)
        playButton = findViewById(R.id.view_chat_play_video)
        progressBar = findViewById(R.id.view_chat_image_progress)

        videoText.setOnClickListener { onOptionClicked?.invoke(this) }

        val isLoaded =
            SessionManager.cacheManager.attachmentIsCachedExtra(attachment.videoPreview.videoThumbnailId)
        if (isLoaded) {
            CoroutineScope(Dispatchers.IO).launch {
                file =
                    SessionManager.cacheManager.getFileById(attachment.videoPreview.videoThumbnailId)
                file?.let {
                    withContext(Dispatchers.Main) {
                        imageView.apply {
                            load(file!!.toUri()) {
                                placeholder(R.color.colorUserBarBackground)
                            }
//                            setOnClickListener { onPhotoClick?.invoke(this@ChatImage) }
                        }
                    }
                }
            }
        } else {

            val preview = attachment.videoPreview.imagePreview
            imageView.setImageBitmap(
                Utils.createBitmapFromRGBA(
                    preview,
                    preview.stride.toInt()
                )
            )
            attachment.let {
                loadButton.visibility = GONE
                progressBar.visibility = VISIBLE
                CoroutineScope(Dispatchers.IO).launch {
                    file =
                        SessionManager.cacheManager.getFileById(attachment.videoPreview.videoThumbnailId)
                    file?.let {
                        CoroutineScope(Dispatchers.Main).launch {

                            imageView.apply {
                                load(file!!.toUri()) {
                                    placeholder(R.color.colorUserBarBackground)
                                }
//                                    setOnClickListener { onPhotoClick?.invoke(this@ChatImage) }
                            }
                            progressBar.visibility = GONE
                        }

                    }

                }
            }
        }


        playButton.setOnClickListener {
            onPhotoClick?.invoke(this)
        }
        playButton.visibility = VISIBLE


    }


}