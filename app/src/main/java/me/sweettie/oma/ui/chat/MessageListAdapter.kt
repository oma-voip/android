package me.sweettie.oma.ui.chat

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.sweettie.oma.R
import me.sweettie.oma.data.MessageList
import me.sweettie.oma.utils.SessionManager
import me.sweettie.oma.utils.Utils
import me.sweettie.oma.utils.getById
import me.sweettie.oma.utils.getOnlyDateFormat
import me.sweettie.oma.utils.getTimeFormat1
import me.sweettie.oma.utils.getTimeFormat2
import me.sweettie.oma.utils.isImage
import proto.OmaOuterClass
import proto.OmaOuterClass.Message

class MessageListAdapter(
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

                val attachmentImage: ImageView =
                    attachmentLayout.findViewById(R.id.tvAttachmentImage)
                val attachmentText: TextView = attachmentLayout.findViewById(R.id.tvAttachmentText)


                attachmentImage.apply {
                    adjustViewBounds = attach.isImage()

                    if (attach.isImage()) {
                        attachmentText.visibility = View.GONE
                        coroutineScope.launch {
                            val file = SessionManager.cacheManager.getFile(attach)
                            if (file != null) {
                                this@apply.post {
                                    println("Try to set image with uri: ${file.toUri()}")
                                    this@apply.setImageURI(file.toUri())
                                }
                            } else {
                                println("Failed to download file with id ${attach.id}")
                            }
                        }
                    } else {
                        setImageResource(R.drawable.ic_save)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        setColorFilter(
                            ContextCompat.getColor(
                                container.context,
                                android.R.color.white
                            )
                        )
                    }
                }

                if (!attach.isImage()) {
                    attachmentText.text = attach.filename
                }
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