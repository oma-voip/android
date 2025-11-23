package me.sweettie.oma.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.devrj.superellipse.SuperEllipseImageView
import me.sweettie.oma.R
import me.sweettie.oma.data.Server
import me.sweettie.oma.utils.SessionManager
import me.sweettie.oma.utils.toDp

class ServerAdapter(
    private val onServerClick: (Server) -> Unit,
    private val onPrivateListClick: () -> Unit,
    private val onAddClick: () -> Unit
) : ListAdapter<ServerListItem, RecyclerView.ViewHolder>(DiffCallback()) {

    open inner class ServerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val letter: TextView = itemView.findViewById(R.id.tv_server_letter)
        val ellipse: SuperEllipseImageView = itemView.findViewById(R.id.tv_server_ellipse)
        val badge: TextView = itemView.findViewById(R.id.badge)

    }

    open inner class PrivateMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.tv_server_letter)
        val ellipse: SuperEllipseImageView = itemView.findViewById(R.id.tv_server_ellipse)
        val badge: TextView = itemView.findViewById(R.id.badge)

    }

    inner class DividerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
    inner class AddButtonViewHolder(itemView: View) : ServerViewHolder(itemView)
    inner class PrivateMessageHolder(itemView: View) : PrivateMessageViewHolder(itemView)

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ServerListItem.ServerItem -> 0
            is ServerListItem.DividerItem -> 1
            is ServerListItem.PrivateMessagesItem -> 3
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

                oldItem is ServerListItem.PrivateMessagesItem && newItem is ServerListItem.PrivateMessagesItem -> true
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
        var hasprivate = false
        val items = mutableListOf<ServerListItem>()
        val tempItems = mutableListOf<ServerListItem>()
        servers.forEach {
            if (!it.space.private)
                tempItems.add(ServerListItem.ServerItem(it))
            else
                hasprivate = true
        }
        if (hasprivate) {
            items.add(ServerListItem.PrivateMessagesItem)
        }
        items.addAll(tempItems)
//        items.add(ServerListItem.DividerItem)
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
                (view.layoutParams as RecyclerView.LayoutParams).setMargins(0, 15.toDp(), 0, 0)
                AddButtonViewHolder(view)
            }

            3 -> {
                val view = inflater.inflate(R.layout.item_private_message, parent, false)
                PrivateMessageHolder(view)
            }

            else -> throw IllegalArgumentException("Unknown viewType $viewType")
        }
    }

    public var selectedSpaceId: String = SessionManager.servers.getFirstNotPrivate().space.id

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ServerListItem.ServerItem -> {
                val serverHolder = holder as ServerViewHolder
                val serverName = item.server.space.name.first().uppercaseChar()
                serverHolder.letter.text = serverName.toString()
                serverHolder.itemView.setOnClickListener {
                    val previousSelectedId = selectedSpaceId
                    selectedSpaceId = item.server.space.id

                    var previousPosition = currentList.indexOfFirst {
                        it is ServerListItem.ServerItem && it.server.space.id == previousSelectedId
                    }
                    val newPosition = currentList.indexOfFirst {
                        it is ServerListItem.ServerItem && it.server.space.id == selectedSpaceId
                    }

                    println("$previousPosition | $newPosition")



                    onServerClick(item.server)
                    if (previousSelectedId == "private")
                        previousPosition = 0
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

                if (SessionManager.unreadedMessages.keys.contains(item.server.space.id))
                    holder.badge.visibility = View.VISIBLE
                else
                    holder.badge.visibility = View.GONE


            }

            is ServerListItem.DividerItem -> {
            }

            is ServerListItem.PrivateMessagesItem -> {
                val holder = holder as PrivateMessageHolder
                holder.itemView.setOnClickListener {


                    val previousSelectedId = selectedSpaceId
                    selectedSpaceId = "private"
                    val previousPosition = currentList.indexOfFirst {
                        it is ServerListItem.ServerItem && it.server.space.id == previousSelectedId
                    }
                    val newPosition = currentList.indexOfFirst {
                        "private" == selectedSpaceId
                    }
                    println("$previousPosition | $newPosition")
                    if (previousPosition != -1)
                        notifyItemChanged(previousPosition)
                    if (newPosition != -1)
                        notifyItemChanged(newPosition)
                    onPrivateListClick()
                }

                if (selectedSpaceId == "private") {
                    holder.ellipse.shapeBackgroundColor =
                        holder.itemView.context.getColor(R.color.colorContrastBackgroundSelected)
                } else {
                    holder.ellipse.shapeBackgroundColor =
                        holder.itemView.context.getColor(R.color.colorContrastBackground)
                }
                holder.badge.visibility = View.GONE
                SessionManager.servers.filter { it.space.private }.forEach {
                    if (SessionManager.unreadedMessages.keys.contains(it.space.id)) {
                        holder.badge.visibility = View.VISIBLE
                        return@forEach
                    }
                }
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

    fun notifyItemChanged(position: Server) {
        currentList.forEachIndexed { i, it ->
            if (it is ServerListItem.ServerItem && it.server.space.id == position.space.id) {
                notifyItemChanged(i)
                return
            }
        }
    }

}

fun ArrayList<Server>.getFirstNotPrivate(): Server {
    return filter { !it.space.private }.toList().first()
}
