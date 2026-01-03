package me.sweettie.oma.ui.home

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import me.sweettie.oma.R
import me.sweettie.oma.data.Server
import me.sweettie.oma.utils.SessionManager
import me.sweettie.oma.utils.Utils
import me.sweettie.oma.utils.getById
import me.sweettie.oma.utils.setStartDrawable
import proto.OmaOuterClass
import proto.OmaOuterClass.Channel

class ChannelAdapter(
    private var server: Server?,
    private val onServerClick: (Server, Channel) -> Unit,
    private val onLongPress: (Server, Channel, View, Int) -> Unit,
) : ListAdapter<Channel, ChannelAdapter.ServerViewHolder>(DiffCallback()) {

    class DiffCallback : DiffUtil.ItemCallback<Channel>() {
        override fun areItemsTheSame(
            oldItem: Channel,
            newItem: Channel
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: Channel,
            newItem: Channel
        ): Boolean {
            return oldItem.id == newItem.id
        }
    }


    private var lastSelected: View? = null

    inner class ServerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val letter: TextView = itemView.findViewById(R.id.tv_channel_name)
        val memberList: LinearLayout = itemView.findViewById(R.id.tvMemberList)
        val badge: TextView = itemView.findViewById(R.id.badge)

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel, parent, false)
        return ServerViewHolder(view)
    }

    fun updateServer(server: Server) {
        this.server = server
        val sortedChannels = server.channels.sortedWith(
            compareBy<Channel> { it.type != OmaOuterClass.ChannelType.TEXT }
                .thenBy { it.name.lowercase() }
        )
        submitList(sortedChannels)
    }

    @SuppressLint("ResourceAsColor")
    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        val channel = getItem(position)
        holder.letter.text = channel.name
        holder.itemView.setOnClickListener {
            if (lastSelected != null && lastSelected!!.id != holder.itemView.id)
                lastSelected!!.background = null
            if (holder.itemView.background == null)
                holder.itemView.background = AppCompatResources.getDrawable(
                    holder.itemView.context,
                    R.drawable.bg_selected_channel
                )

            lastSelected = holder.itemView
            val position = holder.adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                onServerClick(server!!, getItem(position))
            }
        }
        val server = SessionManager.getServerBySpaceId(channel.spaceId)!!
        var isPrivate = false
        if (channel.type.equals(OmaOuterClass.ChannelType.VOICE))
            holder.letter.setStartDrawable(R.drawable.ic_voice_channel, R.color.white)
        else if (channel.type.equals(OmaOuterClass.ChannelType.TEXT))
            holder.letter.setStartDrawable(R.drawable.ic_text_channel, R.color.white)
        else if (channel.type.equals(OmaOuterClass.ChannelType.UNSPECIFIED)) {
            isPrivate = true
            holder.letter.setStartDrawable(R.drawable.ic_member, R.color.white)
            holder.itemView.setOnClickListener {
                if (lastSelected != null && lastSelected!!.id != holder.itemView.id)
                    lastSelected!!.background = null
                if (holder.itemView.background == null)
                    holder.itemView.background = AppCompatResources.getDrawable(
                        holder.itemView.context,
                        R.drawable.bg_selected_channel
                    )

                lastSelected = holder.itemView
                val position = holder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onServerClick(
                        server,
                        getItem(position)
                    )
                }
            }
        }

//        if (SessionManager.selectedChannel != null && SessionManager.selectedChannel == channel.id && SessionManager.selectedServer!!.space.id == server!!.space.id)
//            holder.itemView.background = AppCompatResources.getDrawable(
//                holder.itemView.context,
//                R.drawable.bg_selected_channel
//            )
//        else if (holder.itemView.background != null)
        holder.itemView.background = null
        val cm = SessionManager.channelMembers.getOrDefault(channel.id, ArrayList())
        holder.memberList.removeAllViews()
        if (cm.isNotEmpty()) {
            val inflater = LayoutInflater.from(holder.memberList.context)
            cm.forEach {
                val ml = inflater.inflate(R.layout.item_member, holder.memberList, false)
                val un = ml.findViewById<TextView>(R.id.tvMemberName)
                if (server.members.getById(it.id) != null) {
                    un.text =
                        server.members.getById(it.id)!!.username
                } else {
                    Utils.getCacheUser(it.id) { loadedUser ->
                        un.post {
                            if (loadedUser != null)
                                un.text = loadedUser.username
                        }
                    }
                }

                holder.memberList.addView(ml)
            }
            holder.memberList.visibility = View.VISIBLE
        } else {
            holder.memberList.visibility = View.GONE
        }

        holder.badge.visibility = View.GONE
        if (!isPrivate) {
            if (SessionManager.unreadedMessages.getOrDefault(channel.spaceId, arrayListOf())
                    .contains(channel.id)
            ) {
                holder.badge.visibility = View.VISIBLE
            }
        } else {
            if (SessionManager.unreadedMessages.contains(channel.spaceId)) {
                holder.badge.visibility = View.VISIBLE
            }
        }
        holder.itemView.setOnLongClickListener {
            onLongPress.invoke(server, channel, holder.itemView, position)
            true
        }
    }

    fun updatePrivateDialogs(servers: List<Server>) {
        val tempChannels = arrayListOf<Channel>()
        servers.forEach {
            if (it.space.private)
                tempChannels.add(
                    Channel.newBuilder().setId(it.space.id).setSpaceId(it.space.id)
                        .setName(it.space.name).setType(
                            OmaOuterClass.ChannelType.UNSPECIFIED
                        ).build()
                )
        }
        tempChannels.sortBy { it.name }
        submitList(tempChannels)
    }

    fun updateChannel(channelId: String) {
        currentList.forEachIndexed { i, it ->
            if (it.id == channelId) {
                notifyItemChanged(i)
                return
            }
        }
    }


}