package me.sweettie.oma.data

import proto.OmaOuterClass
import proto.OmaOuterClass.Message
import java.time.Duration
import java.time.Instant

class MessageList(
    val spaceId: String,
    val chatId: String,
    val message_: OmaOuterClass.GetMessagesResponse
) {

    fun addMessage(message: Message) {
        messageLis.add(0, message)
        createStackedMessages()
    }

    fun addAll(ml: List<Message>) {
        messageLis.addAll(ml)
        createStackedMessages()
    }

    val messageLis: ArrayList<Message> = ArrayList(message_.messagesList)
    val stackedMessageList: ArrayList<List<Message>> = ArrayList()

    companion object {

        fun areMessagesWithin30Minutes(msg1: Message, msg2: Message): Boolean {
            if (!msg1.hasTime() || !msg2.hasTime()) return false

            val instant1 = Instant.ofEpochSecond(msg1.time.seconds, msg1.time.nanos.toLong())
            val instant2 = Instant.ofEpochSecond(msg2.time.seconds, msg2.time.nanos.toLong())

            val duration = Duration.between(instant1, instant2).abs()
            return duration.toMinutes() < 30
        }

    }

    private fun createStackedMessages() {
        stackedMessageList.clear()
        var lastUserId: String? = null
        var firstMessageInGroup: Message? = null
        val tempList = ArrayList<Message>()

        for (message in messageLis) {
            if (tempList.isEmpty()) {
                tempList.add(message)
                firstMessageInGroup = message
                lastUserId = message.userId
                continue
            }

            val isSameUser = (lastUserId == message.userId)
            val isWithin30Min = firstMessageInGroup?.let { first ->
                areMessagesWithin30Minutes(message, first)
            } ?: false

            if (isSameUser && isWithin30Min) {
                tempList.add(message)
            } else {
                stackedMessageList.add(ArrayList(tempList))
                tempList.clear()

                tempList.add(message)
                firstMessageInGroup = message
                lastUserId = message.userId
            }
        }

        if (tempList.isNotEmpty()) {
            stackedMessageList.add(ArrayList(tempList))
        }

        stackedMessageList.reverse()
    }

    init {
        createStackedMessages()
    }
}