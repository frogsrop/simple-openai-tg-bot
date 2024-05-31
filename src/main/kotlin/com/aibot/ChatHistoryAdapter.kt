package com.aibot

import com.aallam.openai.api.chat.ChatMessage
import com.aibot.domain.models.MessageData
import com.aibot.domain.models.MessageData.Companion.toChatMessage
import com.aibot.domain.models.Role
import com.aibot.domain.models.User
import com.aibot.repositories.MessagesRepository
import com.aibot.repositories.UsersRepository
import com.github.kotlintelegrambot.Bot


class ChatHistoryAdapter(
    private val usersRepository: UsersRepository,
    private val messagesRepository: MessagesRepository
) {
    suspend fun getMessages(bot: Bot, userId: Long): List<ChatMessage> {
        val messages = messagesRepository.getMessages(userId)
        val user = getUser(userId)
        return messages.map {
            val name = when (it.role) {
                Role.USER -> user?.name
                Role.ASSISTANT -> "Megumin"
                Role.SYSTEM -> null
            }
            it.toChatMessage(bot, name)
        }
    }

    fun addMessage(messageData: MessageData) {
        return messagesRepository.addMessage(messageData)
    }

    fun addUser(newUser: User) {
        return usersRepository.addUser(newUser)
    }

    fun getUser(userId: Long): User? {
        return usersRepository.getUser(userId)
    }

    fun hasUser(userId: Long): Boolean {
        return getUser(userId) != null
    }

    fun deleteMessages(userId: Long) {
        return messagesRepository.deleteMessages(userId)
    }
}