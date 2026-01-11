package me.sweettie.oma.ui.custom.chatfile

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.sweettie.oma.R
import me.sweettie.oma.utils.SessionManager
import proto.OmaOuterClass
import java.io.File

@SuppressLint("ViewConstructor")
class ChatFile @JvmOverloads constructor(
    context: Context,
    attachment: OmaOuterClass.Attachment,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    val imageView: ImageView
    val fileName: TextView
    private val loadButton: ImageButton
    private val optionButton: ImageButton
    private val progressBar: ProgressBar
    private var isLoading = false

    var onOptionClick: ((ChatFile) -> Unit)? = null
    var file: File? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_chat_file, this, true)
        imageView = findViewById(R.id.fileView)
        loadButton = findViewById(R.id.view_chat_file_save)
        fileName = findViewById(R.id.file_name)
        progressBar = findViewById(R.id.view_chat_file_progress)
        optionButton = findViewById(R.id.view_chat_file_option)
        val isLoaded = SessionManager.cacheManager.attachmentIsCached(attachment)
        fileName.text = attachment.filename
        optionButton.setOnClickListener {
            onOptionClick?.invoke(this@ChatFile)
        }

        if (isLoaded) {
            loadButton.visibility = GONE
            progressBar.visibility = GONE
            optionButton.visibility = VISIBLE

            CoroutineScope(Dispatchers.IO).launch {
                file = SessionManager.cacheManager.getFile(attachment)

//                file?.let {
//                    withContext(Dispatchers.Main) {
//                        imageView.apply {
//                            load(file!!.toUri()) {
//                                placeholder(R.color.colorUserBarBackground)
//                            }
//                            setOnClickListener { onPhotoClick?.invoke(this@ChatImage) }
//                        }
//                    }
//                }
            }
        } else {
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

//                                    imageView.apply {
//                                        load(file!!.toUri()) {
//                                            placeholder(R.color.colorUserBarBackground)
//                                        }
//                                        setOnClickListener { onPhotoClick?.invoke(this@ChatImage) }
//                                    }
                                    progressBar.visibility = GONE
                                    optionButton.visibility = VISIBLE
                                }

                            }
                        }
                    }
                }
            }
        }


    }
}