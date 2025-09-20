package me.sweettie.oma.ui.members

import android.annotation.SuppressLint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import me.sweettie.oma.R
import me.sweettie.oma.utils.*
import proto.OmaOuterClass.User

class MemberListAdapter() : ListAdapter<User, MemberListAdapter.MessageViewHolder>(DiffCallback()) {

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val memberName: TextView = itemView.findViewById(R.id.tvMemberName)

    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_member, parent, false)
        return MessageViewHolder(view)
    }

    class DiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem.id == newItem.id
        }
    }


    fun updateData(users: List<User>) {
        val sortedUsers = users.sortedWith(
            compareByDescending<User> { it.online }
                .thenBy { it.id }
        )
        submitList(sortedUsers)
    }


    @SuppressLint("ResourceAsColor")
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val sm = getItem(position) ?: return
        holder.memberName.text = sm.username
        if (sm.online) {
            println("User online ${sm.username}")
            val drawables = holder.memberName.compoundDrawablesRelative
            val drawable = drawables[0] ?: return
            drawable.colorFilter =
                PorterDuffColorFilter(
                    ContextCompat.getColor(
                        holder.itemView.context,
                        R.color.colorGreen
                    ), PorterDuff.Mode.SRC_IN
                )
        }
    }


}