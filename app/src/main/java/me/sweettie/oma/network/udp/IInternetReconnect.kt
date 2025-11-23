package me.sweettie.oma.network.udp

import android.net.Network

interface IInternetReconnect {
    fun reconnect(network: Network)
}