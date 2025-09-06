package me.sweettie.oma.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import me.sweettie.oma.R
import me.sweettie.oma.data.Server
import me.sweettie.oma.utils.SessionManager

class ServerAdapter(
    private val onServerClick: (Server) -> Unit
) : RecyclerView.Adapter<ServerAdapter.ServerViewHolder>() {

    inner class ServerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val letter: TextView = itemView.findViewById(R.id.tv_server_letter)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onServerClick(SessionManager.servers[position])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_server, parent, false)
        return ServerViewHolder(view)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        val serverName = SessionManager.servers[position].space.name
        val firstLetter = serverName.firstOrNull()?.uppercaseChar()
        holder.letter.text = firstLetter.toString()
        // Можно менять цвет фона в зависимости от сервера (например, хэшем)
        // holder.letter.setBackgroundResource(getColorForServer(serverName))
    }

    override fun getItemCount() = SessionManager.servers.size
}