package com.aibot

import com.aallam.openai.api.chat.ChatMessage
import com.aibot.domain.models.Message
import com.aibot.domain.models.Role
import com.aibot.domain.models.User
import com.aibot.repositories.MessagesRepository
import com.aibot.repositories.UsersRepository


class ChatHistoryAdapter(
    private val usersRepository: UsersRepository,
    private val messagesRepository: MessagesRepository
) {
    fun getMessages(userId: Long): List<ChatMessage> {
        val messages = messagesRepository.getMessages(userId)
        val user = getUser(userId)
        return messages.map {
            val name = when (it.role) {
                Role.USER -> user?.name
                Role.ASSISTANT -> "Megumin"
                Role.SYSTEM -> null
            }
            ChatMessage(it.role.chatRole, it.message, name)
        }
    }

    fun addMessage(userId: Long, message: ChatMessage) {
        return messagesRepository.addMessage(userId, Message(System.currentTimeMillis(), userId, message.content ?: "", Role.from(message.role)))
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

    private fun deleteMessages(userId: Long) {
        return messagesRepository.deleteMessages(userId)
    }
}