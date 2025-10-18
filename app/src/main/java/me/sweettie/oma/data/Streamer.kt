package me.sweettie.oma.data

import me.sweettie.oma.utils.Utils

class Streamer(
    relay: String,
    spaceID: String,
    channelID: String,
    userID: String,
    id: String
) {

    val url: String = Utils.makeReadURL(relay, spaceID, channelID, userID, id)
}