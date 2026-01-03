package me.sweettie.oma.ui.home

import me.sweettie.oma.data.Server

sealed class ServerListItem {
    data class ServerItem(val server: Server) : ServerListItem()
    object DividerItem : ServerListItem()
    object PrivateMessagesItem : ServerListItem()
    object AddButtonItem : ServerListItem()
    object SettingsButtonItem : ServerListItem()
}