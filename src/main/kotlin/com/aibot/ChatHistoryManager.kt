package com.aibot

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.concurrent.ConcurrentHashMap

@OptIn(BetaOpenAI::class)
class ChatHistoryManager {

    private val messagesHistory = ConcurrentHashMap<Long, MutableList<ChatMessage>>()

    fun get(id: Long): MutableList<ChatMessage> {
        return messagesHistory.getOrPut(id) { return mutableListOf() }
    }

    fun set(id: Long, chat: MutableList<ChatMessage>) {
        return messagesHistory.set(id, chat)
    }

    fun check(id: Long): Boolean {
        return messagesHistory.containsKey(id)
    }

    private class SerializableChatMessage(
        private val role: ChatRole,
        private val content: String,
        private val name: String?
    ) :
        Serializable {
        constructor(chatMessage: ChatMessage) : this(
            role = chatMessage.role,
            content = chatMessage.content,
            name = chatMessage.name
        ) {
        }

        fun getChatMessage(): ChatMessage {
            return ChatMessage(role, content, name)
        }
    }

    suspend fun makeCheckpoint() {
        withContext(Dispatchers.IO) {
            val file = FileOutputStream("../../history.txt")
            val outStream = ObjectOutputStream(file)
            val serializable = messagesHistory.map {
                Pair(
                    it.key,
                    it.value.map { SerializableChatMessage(it) }.toMutableList()
                )
            }
            outStream.writeObject(serializable)
            outStream.close()
            file.close()
        }
    }

    suspend fun readCheckpoint() {
        withContext(Dispatchers.IO) {
            try {
                val file = FileInputStream("../../history.txt")
                val inStream = ObjectInputStream(file)
                val serializedData =
                    inStream.readObject() as List<Pair<Long, MutableList<SerializableChatMessage>>>
                serializedData.forEach {
                    messagesHistory[it.first] = it.second.map { it.getChatMessage() }.toMutableList()
                }
                inStream.close()
                file.close()
            } catch (ex: Exception) {
                println("skippgin with exception:$ex")
            }
        }
    }
}