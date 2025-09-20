package me.sweettie.oma.ui.chat

import androidx.recyclerview.widget.DiffUtil
import proto.OmaOuterClass

class StackedMessagesDiffCallback(
    private val oldList: List<List<OmaOuterClass.Message>>,
    private val newList: List<List<OmaOuterClass.Message>>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int {
        var sizes = 0
        oldList.forEach {
            sizes += it.size
        }
        return sizes
    }

    override fun getNewListSize(): Int {
        var sizes = 0
        newList.forEach {
            sizes += it.size
        }
        return sizes
    }

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldGroup = oldList[oldItemPosition]
        val newGroup = newList[newItemPosition]

        if (oldGroup.isEmpty() || newGroup.isEmpty()) return false

        return oldGroup[0].id == newGroup[0].id
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldGroup = oldList[oldItemPosition]
        val newGroup = newList[newItemPosition]

        if (oldGroup.size != newGroup.size) return false

        for (i in oldGroup.indices) {
            if (oldGroup[i].id != newGroup[i].id) return false
        }
        return true
    }
}