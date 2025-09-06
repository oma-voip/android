package me.sweettie.oma.data

import proto.OmaOuterClass

class Channel(private val sid: String, private val channel2: OmaOuterClass.Channel) {
    lateinit var channel: proto.OmaOuterClass.Channel

}