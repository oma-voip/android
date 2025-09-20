package me.sweettie.oma.ui.home

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import me.sweettie.oma.R
import me.sweettie.oma.data.Server
import me.sweettie.oma.utils.SessionManager
import me.sweettie.oma.utils.setStartDrawable
import proto.OmaOuterClass
import proto.OmaOuterClass.Channel

class ChannelAdapter(
    private var server: Server?,
    private val onServerClick: (Server, Channel) -> Unit
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

        init {
            itemView.setOnClickListener {
                if (lastSelected != null && lastSelected!!.id != itemView.id)
                    lastSelected!!.background = null
                if (itemView.background == null)
                    itemView.background = AppCompatResources.getDrawable(
                        itemView.context,
                        R.drawable.bg_selected_channel
                    )

                lastSelected = itemView
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onServerClick(server!!, server!!.channels[position])
                }
            }
        }
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
        if (channel.type.equals(OmaOuterClass.ChannelType.VOICE))
            holder.letter.setStartDrawable(R.drawable.ic_voice_channel, R.color.white)
        else if (channel.type.equals(OmaOuterClass.ChannelType.TEXT))
            holder.letter.setStartDrawable(R.drawable.ic_text_channel, R.color.white)
        if (SessionManager.selectedChannel != null && SessionManager.selectedChannel == channel.id && SessionManager.selectedServer!!.space.id == server!!.space.id)
            holder.itemView.background = AppCompatResources.getDrawable(
                holder.itemView.context,
                R.drawable.bg_selected_channel
            )
        else if (holder.itemView.background != null)
            holder.itemView.background = null
        // Можно менять цвет фона в зависимости от сервера (например, хэшем)
        // holder.letter.setBackgroundResource(getColorForServer(serverName))
    }


}