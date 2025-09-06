package me.sweettie.oma.ui.home

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import me.sweettie.oma.R
import me.sweettie.oma.data.Server
import me.sweettie.oma.utils.setStartDrawable
import proto.OmaOuterClass

class ChannelAdapter(
    private val server: Server,
    private val onServerClick: (OmaOuterClass.Channel) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.ServerViewHolder>() {

    inner class ServerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val letter: TextView = itemView.findViewById(R.id.tv_channel_name)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onServerClick(server.channels[position])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel, parent, false)
        return ServerViewHolder(view)
    }

    @SuppressLint("ResourceAsColor")
    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        val channel = server.channels[position]
        holder.letter.text = channel.name
        if (channel.type.equals(OmaOuterClass.ChannelType.VOICE))
            holder.letter.setStartDrawable(R.drawable.ic_voice_channel, R.color.white)
        // Можно менять цвет фона в зависимости от сервера (например, хэшем)
        // holder.letter.setBackgroundResource(getColorForServer(serverName))
    }

    override fun getItemCount() = server.channels.size
}