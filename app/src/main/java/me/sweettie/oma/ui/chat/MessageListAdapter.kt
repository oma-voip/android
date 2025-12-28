package me.sweettie.oma.ui.chat

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.sweettie.oma.R
import me.sweettie.oma.data.MessageList
import me.sweettie.oma.ui.custom.chatfile.ChatFile
import me.sweettie.oma.ui.custom.chatimage.ChatImage
import me.sweettie.oma.ui.custom.chatvideo.ChatVideo
import me.sweettie.oma.ui.photoview.FullScreenActivity
import me.sweettie.oma.utils.SessionManager
import me.sweettie.oma.utils.Utils
import me.sweettie.oma.utils.getById
import me.sweettie.oma.utils.getMimeType
import me.sweettie.oma.utils.getOnlyDateFormat
import me.sweettie.oma.utils.getTimeFormat1
import me.sweettie.oma.utils.getTimeFormat2
import me.sweettie.oma.utils.isImage
import me.sweettie.oma.utils.isVideo
import proto.OmaOuterClass
import proto.OmaOuterClass.Message

class MessageListAdapter(
    private val fragmentActivity: FragmentActivity,
    private val coroutineScope: CoroutineScope,
    private val onMessageClick: (Message) -> Unit
) : ListAdapter<List<Message>, MessageListAdapter.MessageViewHolder>(DiffCallback()) {

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nickname: TextView = itemView.findViewById(R.id.tvNickname)
        val timestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        val listL: LinearLayoutCompat = itemView.findViewById(R.id.tvMessageLinearLayout)

        init {
            val position = adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                onMessageClick(getItem(position)[0])
            }
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.chat_message, parent, false)
        return MessageViewHolder(view)
    }

    class DiffCallback : DiffUtil.ItemCallback<List<Message>>() {
        override fun areItemsTheSame(oldItem: List<Message>, newItem: List<Message>): Boolean {
            if (oldItem.isEmpty() || newItem.isEmpty()) return false
            return oldItem[0].id == newItem[0].id
        }

        override fun areContentsTheSame(oldItem: List<Message>, newItem: List<Message>): Boolean {
            if (oldItem.size != newItem.size) return false
            for (i in oldItem.indices) {
                if (oldItem[i].id != newItem[i].id) return false
            }
            return true
        }
    }


    fun updateData(ml: MessageList) {
        submitList(ml.stackedMessageList.toList())
    }


    @OptIn(UnstableApi::class)
    private fun putMessage(container: LinearLayoutCompat, messagesGroup: List<Message>) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(container.context)

        messagesGroup.forEach { message ->
            val messageView = inflater.inflate(R.layout.stacked_message, container, false)

            val attachmentList: LinearLayout = messageView.findViewById(R.id.tvListAttachments)
            attachmentList.removeAllViews()
            val text: TextView = messageView.findViewById(R.id.tvText)
            val timestamp: TextView = messageView.findViewById(R.id.tvTimestamp)

            text.text = message.text


            message.attachmentsList.forEach { attach ->
                val attachmentLayout =
                    inflater.inflate(R.layout.stacked_message_attachment, attachmentList, false)
                if (attach.isImage()) {
                    val attachmentImage = ChatImage(attachmentLayout.context, attach).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        onPhotoClick = {
                            val intent =
                                Intent(fragmentActivity, FullScreenActivity::class.java)
                            intent.putExtra("photoUri", file!!.toUri())
                            intent.putExtra(
                                "actionBarName",
                                fragmentActivity.findViewById<TextView>(R.id.channel_name).text
                            )

                            val options =
                                ActivityOptionsCompat.makeSceneTransitionAnimation(
                                    fragmentActivity,
                                    imageView,
                                    "shared_image"
                                )
                            fragmentActivity.startActivity(intent, options.toBundle())
                        }
                    }
                    attachmentLayout.findViewById<LinearLayout>(R.id.linearLayout)
                        .addView(attachmentImage, 0)
                } else if (attach.isVideo()) {

                    val attachmentImage = ChatVideo(attachmentLayout.context, attach).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        onPhotoClick = {
                            val intent =
                                Intent(fragmentActivity, FullScreenActivity::class.java)
                            intent.putExtra("videoId", attach.id)
                            intent.putExtra(
                                "actionBarName",
                                fragmentActivity.findViewById<TextView>(R.id.channel_name).text
                            )

                            val options =
                                ActivityOptionsCompat.makeSceneTransitionAnimation(
                                    fragmentActivity,
                                    imageView,
                                    "shared_image"
                                )
                            fragmentActivity.startActivity(intent, options.toBundle())
                        }
                        onOptionClicked = {
                            val popup = PopupMenu(context, it)
                            popup.menu.add(0, 1, 0, "Save")
                            popup.menu.add(0, 2, 1, "Save to gallery")
                            popup.setOnMenuItemClickListener { item ->
                                when (item.itemId) {
                                    1 -> {
                                        if (it.save())
                                            Toast.makeText(
                                                this.context,
                                                "Already saved!",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        true
                                    }

                                    2 -> {
                                        it.save()
                                        println(attach.filename)
                                        CoroutineScope(Dispatchers.IO).launch {
                                            val isLoaded =
                                                SessionManager.cacheManager.attachmentIsCachedExtra(
                                                    attach.id
                                                )
                                            if (!isLoaded)
                                                return@launch
                                            file =
                                                SessionManager.cacheManager.getFileById(attach.id)
                                            Utils.saveVideoToGallery(
                                                file!!,
                                                attach.filename,
                                                context
                                            )
                                        }
                                        Toast.makeText(
                                            context,
                                            "Saved to gallery!",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        true
                                    }

                                    else -> false
                                }
                            }
                            popup.show()
                        }
                    }
                    attachmentLayout.findViewById<LinearLayout>(R.id.linearLayout)
                        .addView(attachmentImage, 0)
                } else {
                    val attachmentImage = ChatFile(attachmentLayout.context, attach).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        transitionName = "shared_image"
                        onOptionClick = {
                            val popup = PopupMenu(context, it)
                            popup.menu.add(0, 1, 0, "Save to downloads")
                            popup.menu.add(0, 2, 1, "Open")
                            popup.menu.add(0, 3, 2, "Share")
                            popup.setOnMenuItemClickListener { item ->
                                when (item.itemId) {
                                    1 -> {
                                        val contentUri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            this.file!!
                                        )
                                        val savedUri = Utils.saveUriToDownloads(
                                            context,
                                            attach.filename,
                                            getMimeType(context, contentUri),
                                            contentUri
                                        )
                                        Toast.makeText(
                                            context,
                                            "Saved to downloads!",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        true
                                    }

                                    2 -> {
                                        val contentUri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            this.file!!
                                        )
                                        val share = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(
                                                contentUri,
                                                getMimeType(context, contentUri)
                                            )
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        val chooser = Intent.createChooser(share, "Open file")
                                        chooser.clipData = ClipData.newUri(
                                            context.contentResolver,
                                            "file",
                                            contentUri
                                        )
                                        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                                        context.startActivity(chooser)
                                        true
                                    }

                                    3 -> {
                                        val contentUri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            this.file!!
                                        )

                                        val share = Intent(Intent.ACTION_SEND).apply {
                                            type = getMimeType(context, contentUri)
                                            putExtra(Intent.EXTRA_STREAM, contentUri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(
                                            Intent.createChooser(
                                                share,
                                                "Share file"
                                            )
                                        )
                                        true
                                    }

                                    else -> false
                                }
                            }
                            popup.show()
                        }
                    }
                    attachmentLayout.findViewById<LinearLayout>(R.id.linearLayout)
                        .addView(attachmentImage, 0)
                }
                val attachmentText: TextView = attachmentLayout.findViewById(R.id.tvAttachmentText)

                if (!attach.isImage()) {
                    attachmentText.visibility = View.GONE
                }
                if (attachmentText.text.isEmpty())
                    attachmentText.visibility = View.GONE
                attachmentList.addView(attachmentLayout)
            }

            if (messagesGroup.size <= 1)
                timestamp.text = ""
            else {
                timestamp.text = message.getTimeFormat2()
            }

            container.addView(messageView)

        }

    }


    @SuppressLint("ResourceAsColor")
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val sm = getItem(position) ?: return
        val userId = sm[0].userId
        val user = SessionManager.selectedServer?.members?.getById(userId)

        if (user != null) {
            bindMessage(holder, sm, user)
        } else {
            holder.nickname.text = "Loading..."
            holder.listL.removeAllViews()

            Utils.getCacheUser(userId) { loadedUser ->
                holder.nickname.post {
                    if (loadedUser != null)
                        bindMessage(holder, sm, loadedUser)
                }
            }
        }


        // Можно менять цвет фона в зависимости от сервера (например, хэшем)
        // holder.letter.setBackgroundResource(getColorForServer(serverName))
    }


    private fun bindMessage(
        holder: MessageViewHolder,
        sm: List<Message>,
        user: OmaOuterClass.User
    ) {
        holder.nickname.text = user.username
        holder.listL.removeAllViews()
        val stacked = sm.size > 1
        holder.timestamp.text = if (!stacked) sm[0].getTimeFormat1() else sm[0].getOnlyDateFormat()
        putMessage(holder.listL, sm.reversed())
    }

//    @SuppressLint("NotifyDataSetChanged")
//    fun updateServer(server: Server) {
//        this.server = server
//        notifyDataSetChanged()
//    }
}