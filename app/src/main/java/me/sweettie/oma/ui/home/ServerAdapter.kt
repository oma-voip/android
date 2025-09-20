package me.sweettie.oma.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.devrj.superellipse.SuperEllipseImageView
import me.sweettie.oma.R
import me.sweettie.oma.data.Server
import me.sweettie.oma.utils.SessionManager

class ServerAdapter(
    private val onServerClick: (Server) -> Unit,
    private val onAddClick: () -> Unit
) : ListAdapter<ServerListItem, RecyclerView.ViewHolder>(DiffCallback()) {

    open inner class ServerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val letter: TextView = itemView.findViewById(R.id.tv_server_letter)
        val ellipse: SuperEllipseImageView = itemView.findViewById(R.id.tv_server_ellipse)
    }

    inner class DividerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
    inner class AddButtonViewHolder(itemView: View) : ServerViewHolder(itemView)

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ServerListItem.ServerItem -> 0
            is ServerListItem.DividerItem -> 1
            is ServerListItem.AddButtonItem -> 2
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ServerListItem>() {
        override fun areItemsTheSame(oldItem: ServerListItem, newItem: ServerListItem): Boolean {
            return when {
                oldItem is ServerListItem.ServerItem && newItem is ServerListItem.ServerItem ->
                    oldItem.server.space.id == newItem.server.space.id
                oldItem is ServerListItem.DividerItem && newItem is ServerListItem.DividerItem ->
                    true
                oldItem is ServerListItem.AddButtonItem && newItem is ServerListItem.AddButtonItem ->
                    true
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: ServerListItem, newItem: ServerListItem): Boolean {
            return oldItem == newItem
        }
    }

    fun updateServer(servers: List<ServerListItem>) {
        submitList(servers.toList())
    }

    fun prepareItems(servers: List<Server>) {
        val items = mutableListOf<ServerListItem>()
        servers.forEach {
            items.add(ServerListItem.ServerItem(it))
        }
        items.add(ServerListItem.DividerItem)
        items.add(ServerListItem.AddButtonItem)
        updateServer(items)
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> {
                val view = inflater.inflate(R.layout.item_server, parent, false)
                ServerViewHolder(view)
            }
            1 -> {
                val view = inflater.inflate(R.layout.divider_layout, parent, false)
                DividerViewHolder(view)
            }
            2 -> {
                val view = inflater.inflate(R.layout.item_server, parent, false)
                AddButtonViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown viewType $viewType")
        }
    }

    private var selectedSpaceId: String = SessionManager.servers[0].space.id

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ServerListItem.ServerItem -> {
                val serverHolder = holder as ServerViewHolder
                val serverName = item.server.space.name.first().uppercaseChar()
                serverHolder.letter.text = serverName.toString()
                serverHolder.itemView.setOnClickListener {
                    val previousSelectedId = selectedSpaceId
                    selectedSpaceId = item.server.space.id

                    val previousPosition = currentList.indexOfFirst {
                        it is ServerListItem.ServerItem && it.server.space.id == previousSelectedId
                    }
                    val newPosition = currentList.indexOfFirst {
                        it is ServerListItem.ServerItem && it.server.space.id == selectedSpaceId
                    }

                    println("$previousPosition | $newPosition")



                    onServerClick(item.server)

                    if (previousPosition != -1)
                        notifyItemChanged(previousPosition)
                    if (newPosition != -1)
                        notifyItemChanged(newPosition)

                }
                if (selectedSpaceId == item.server.space.id) {
                    serverHolder.ellipse.shapeBackgroundColor =
                        holder.itemView.context.getColor(R.color.colorContrastBackgroundSelected)
                } else {
                    serverHolder.ellipse.shapeBackgroundColor =
                        holder.itemView.context.getColor(R.color.colorContrastBackground)
                }


            }
            is ServerListItem.DividerItem -> {
            }
            is ServerListItem.AddButtonItem -> {
                val addHolder = holder as AddButtonViewHolder
                addHolder.letter.text = "+"
                addHolder.itemView.setOnClickListener {
                    onAddClick()
                }
            }
        }
    }

}